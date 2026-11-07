const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const MAX_BODY_BYTES = 8 * 1024;
const ALLOWED_REASONS = new Set([
  "Video is broken or unavailable",
  "Wrong title or information",
  "Inappropriate content",
  "Spam or misleading",
  "Copyright concern",
  "Something else",
]);
function json(body: unknown, status = 200, headers: Record<string, string> = {}) {
  return Response.json(body, { status, headers: { ...corsHeaders, ...headers } });
}
function clientAddress(req: Request): string | null {
  const value = req.headers.get("cf-connecting-ip") || req.headers.get("x-real-ip");
  return value && value.length <= 100 ? value : null;
}
async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, "0")).join("");
}
async function getReporterId(req: Request, baseUrl: string, anonKey: string): Promise<string | null> {
  const authorization = req.headers.get("authorization") || "";
  if (!/^Bearer\s+\S+/i.test(authorization)) return null;
  const response = await fetch(`${baseUrl}/auth/v1/user`, {
    headers: { apikey: anonKey, Authorization: authorization },
    signal: AbortSignal.timeout(4000),
  });
  if (response.status === 401 || response.status === 403) return null;
  if (!response.ok) throw new Error("Could not validate reporter session");
  const user = await response.json();
  return typeof user.id === "string" ? user.id : null;
}
async function allowReport(baseUrl: string, serviceKey: string, ipHash: string) {
  const response = await fetch(`${baseUrl}/rest/v1/rpc/consume_ryko_rate_limit`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      apikey: serviceKey,
      Authorization: `Bearer ${serviceKey}`,
    },
    body: JSON.stringify({
      p_scope: "video-report",
      p_subject: ipHash,
      p_window_seconds: 600,
      p_max_requests: 5,
    }),
    signal: AbortSignal.timeout(5000),
  });
  if (!response.ok) throw new Error("Rate-limit check failed");
  const result = await response.json();
  const row = Array.isArray(result) ? result[0] : result;
  return { allowed: row?.allowed === true, retryAfter: Number(row?.retry_after_seconds) || 600 };
}
function validVideoUrl(value: unknown): value is string {
  if (typeof value !== "string" || value.length < 8 || value.length > 2048) return false;
  try {
    const url = new URL(value);
    return url.protocol === "https:" || url.protocol === "http:";
  } catch { return false; }
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
    const videoId = typeof body?.video_id === "string" ? body.video_id.trim() : "";
    const videoTitle = typeof body?.video_title === "string" ? body.video_title.trim() : null;
    const reason = typeof body?.reason === "string" ? body.reason : "";
    const details = typeof body?.details === "string" ? body.details.trim() : null;
    if (!videoId || videoId.length > 200 || !validVideoUrl(body?.video_url) ||
        !ALLOWED_REASONS.has(reason) || (videoTitle !== null && videoTitle.length > 300) ||
        (details !== null && details.length > 500)) {
      return json({ error: "Invalid report details" }, 400);
    }
    const baseUrl = Deno.env.get("SUPABASE_URL");
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
    if (!baseUrl || !serviceKey || !anonKey) return json({ error: "Report service is not configured" }, 503);
    const address = clientAddress(req);
    if (!address) return json({ error: "Could not verify request source" }, 503);
    const ipHash = await sha256(address);
    const rate = await allowReport(baseUrl, serviceKey, ipHash);
    if (!rate.allowed) {
      return json({ error: "Too many reports. Please try again later." }, 429, { "Retry-After": String(rate.retryAfter) });
    }
    const reporterId = await getReporterId(req, baseUrl, anonKey);
    const response = await fetch(`${baseUrl}/rest/v1/video_reports`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        apikey: serviceKey,
        Authorization: `Bearer ${serviceKey}`,
        Prefer: "return=minimal",
      },
      body: JSON.stringify({
        video_id: videoId,
        video_url: body.video_url,
        video_title: videoTitle || null,
        reason,
        details: details || null,
        reporter_id: reporterId,
      }),
      signal: AbortSignal.timeout(5000),
    });
    if (!response.ok) throw new Error("Report insert failed");
    return json({ ok: true });
  } catch {
    return json({ error: "Report could not be submitted right now" }, 503);
  }
});