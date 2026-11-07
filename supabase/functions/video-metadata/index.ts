const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const cache = new Map<string, { at: number; value: Record<string, unknown> }>();
const TTL = 6 * 60 * 60 * 1000;
const MAX_CACHE_ENTRIES = 1000;
const MAX_BODY_BYTES = 16 * 1024;
const MAX_URLS = 20;

function json(body: unknown, status = 200, headers: Record<string, string> = {}) {
  return Response.json(body, { status, headers: { ...corsHeaders, ...headers } });
}
function clientAddress(req: Request): string {
  return (req.headers.get("cf-connecting-ip") || req.headers.get("x-real-ip") || "unknown").slice(0, 100);
}
async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, "0")).join("");
}
async function allowRequest(req: Request): Promise<{ allowed: boolean; retryAfter: number }> {
  const url = Deno.env.get("SUPABASE_URL");
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !key) throw new Error("Rate-limit service is not configured");
  const subject = await sha256(clientAddress(req));
  const response = await fetch(`${url}/rest/v1/rpc/consume_ryko_rate_limit`, {
    method: "POST",
    headers: { "Content-Type": "application/json", apikey: key, Authorization: `Bearer ${key}` },
    body: JSON.stringify({ p_scope: "video-metadata", p_subject: subject, p_window_seconds: 60, p_max_requests: 20 }),
    signal: AbortSignal.timeout(5000),
  });
  if (!response.ok) throw new Error("Rate-limit check failed");
  const result = await response.json();
  const row = Array.isArray(result) ? result[0] : result;
  return { allowed: row?.allowed === true, retryAfter: Number(row?.retry_after_seconds) || 60 };
}
function canonicalYouTubeUrl(input: unknown): string | null {
  if (typeof input !== "string" || input.length > 2048) return null;
  try {
    const url = new URL(input);
    const host = url.hostname.toLowerCase().replace(/^www\./, "").replace(/^m\./, "");
    const parts = url.pathname.split("/").filter(Boolean);
    let id = "";
    if (host === "youtu.be") id = parts[0] || "";
    else if (host === "youtube.com" || host === "youtube-nocookie.com") {
      id = url.searchParams.get("v") || (["shorts", "embed", "live"].includes(parts[0]) ? parts[1] : "") || "";
    } else return null;
    return /^[a-zA-Z0-9_-]{6,}$/.test(id) ? `https://www.youtube.com/watch?v=${id}` : null;
  } catch { return null; }
}
function extractMetaImage(html: string): string {
  const tags = html.match(/<meta\b[^>]*>/gi) || [];
  for (const tag of tags) {
    const property = tag.match(/(?:property|name)=["']([^"']+)["']/i)?.[1]?.toLowerCase() || "";
    const content = tag.match(/content=["']([^"']+)["']/i)?.[1]?.replace(/&amp;/g, "&") || "";
    if (["og:image", "twitter:image", "twitter:image:src"].includes(property) && content.startsWith("https://")) return content;
  }
  const patterns = [
    /"avatar"\s*:\s*\{[\s\S]{0,300}?"thumbnails"\s*:\s*\[\s*\{\s*"url"\s*:\s*"([^"]+)"/,
    /"channelThumbnail"\s*:\s*\{[\s\S]{0,300}?"url"\s*:\s*"([^"]+)"/,
    /"thumbnail"\s*:\s*\{[\s\S]{0,300}?"thumbnails"\s*:\s*\[\s*\{\s*"url"\s*:\s*"([^"]+)"/,
  ];
  for (const pattern of patterns) {
    const value = html.match(pattern)?.[1]?.replace(/\\u0026/g, "&").replace(/\\\\/g, "") || "";
    if (value.startsWith("https://")) return value;
  }
  return "";
}
async function getMetadata(input: string) {
  const canonical = canonicalYouTubeUrl(input);
  if (!canonical) return { input, ok: false, reason: "not_youtube" };
  const cached = cache.get(canonical);
  if (cached && Date.now() - cached.at < TTL) return { input, ...cached.value };
  try {
    const endpoint = new URL("https://www.youtube.com/oembed");
    endpoint.searchParams.set("url", canonical);
    endpoint.searchParams.set("format", "json");
    const response = await fetch(endpoint, {
      headers: { Accept: "application/json", "User-Agent": "Mozilla/5.0" },
      signal: AbortSignal.timeout(8000),
    });
    if (!response.ok) return { input, ok: false, reason: `oembed_${response.status}` };
    const data = await response.json();
    const authorUrl = typeof data.author_url === "string" ? data.author_url : "";
    let avatar = "";
    if (/^https:\/\/(?:www\.)?youtube\.com\//i.test(authorUrl)) {
      try {
        const page = await fetch(authorUrl, {
          headers: { Accept: "text/html", "User-Agent": "Mozilla/5.0 (compatible; RykoCollection/1.0)" },
          signal: AbortSignal.timeout(5000),
        });
        if (page.ok) avatar = extractMetaImage((await page.text()).slice(0, 500000));
      } catch { /* Avatar is optional metadata. */ }
    }
    const value = {
      ok: true,
      title: typeof data.title === "string" ? data.title.slice(0, 500) : "",
      channelName: typeof data.author_name === "string" ? data.author_name.slice(0, 200) : "",
      channelAvatarUrl: avatar,
      thumbnailUrl: typeof data.thumbnail_url === "string" ? data.thumbnail_url : "",
    };
    if (cache.size >= MAX_CACHE_ENTRIES) {
      const oldest = cache.keys().next().value;
      if (oldest) cache.delete(oldest);
    }
    cache.set(canonical, { at: Date.now(), value });
    return { input, ...value };
  } catch { return { input, ok: false, reason: "metadata_fetch_failed" }; }
}
Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);
  const declaredLength = Number(req.headers.get("content-length") || 0);
  if (declaredLength > MAX_BODY_BYTES) return json({ error: "Request body too large" }, 413);
  try {
    const raw = await req.text();
    if (new TextEncoder().encode(raw).byteLength > MAX_BODY_BYTES) return json({ error: "Request body too large" }, 413);
    const body = JSON.parse(raw);
    if (!Array.isArray(body?.urls) || body.urls.length < 1 || body.urls.length > MAX_URLS) {
      return json({ error: `Provide between 1 and ${MAX_URLS} YouTube URLs` }, 400);
    }
    const urls = body.urls;
    if (urls.some((value: unknown) => typeof value !== "string") || new Set(urls).size !== urls.length) {
      return json({ error: "URLs must be unique strings" }, 400);
    }
    const rate = await allowRequest(req);
    if (!rate.allowed) return json({ error: "Too many metadata requests. Please try again shortly." }, 429, { "Retry-After": String(rate.retryAfter) });
    const results: Record<string, unknown>[] = [];
    for (let i = 0; i < urls.length; i += 5) {
      results.push(...await Promise.all(urls.slice(i, i + 5).map((value: string) => getMetadata(value))));
    }
    return json({ results }, 200, { "Cache-Control": "public, max-age=300" });
  } catch {
    // Fail closed: never silently disable the shared rate limit during an outage.
    return json({ error: "Request could not be processed" }, 503);
  }
});
