import { createClient } from "npm:@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const OWNER_ID = "4da14151-419b-4872-8e5b-6ec38185cdbf";
const requestHeaders = {
  "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36",
  "Accept": "application/atom+xml, application/rss+xml, application/xml, text/xml, text/html;q=0.9, */*;q=0.8",
};

function decodeXml(value: string): string {
  return value
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, "$1")
    .replace(/&amp;/g, "&").replace(/&quot;/g, '"')
    .replace(/&#39;|&apos;/g, "'").replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">").replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(Number(n)))
    .replace(/&#x([\da-f]+);/gi, (_, n) => String.fromCodePoint(parseInt(n, 16))).trim();
}

function readTag(xml: string, tag: string): string {
  const safe = tag.replace(/[.*+?^\${}()|[\]\\]/g, "\\$&");
  const match = xml.match(new RegExp("<" + safe + "(?:\\s[^>]*)?>([\\s\\S]*?)</" + safe + ">", "i"));
  return decodeXml(match?.[1] || "");
}

function parseFeed(xml: string): any[] {
  const entries = [...xml.matchAll(/<(?:entry|item)\b[\s\S]*?<\/(?:entry|item)>/gi)];
  const output: any[] = [];
  for (const match of entries) {
    const entry = match[0];
    const title = readTag(entry, "title");
    const creator = readTag(entry, "name") || readTag(entry, "author") || readTag(entry, "dc:creator");
    const rawLink = entry.match(/<link\b[^>]*href=["']([^"']+)["']/i)?.[1] || readTag(entry, "link");
    const id = readTag(entry, "yt:videoId") ||
      entry.match(/<id>yt:video:([^<]+)<\/id>/i)?.[1] ||
      rawLink.match(/(?:v=|youtu\.be\/|youtube\.com\/shorts\/)([A-Za-z0-9_-]{6,32})/)?.[1] || "";
    const thumbnail = entry.match(/<(?:media:thumbnail|thumbnail)\b[^>]*(?:url|href)=["']([^"']+)["']/i)?.[1] || "";
    if (!/^[A-Za-z0-9_-]{6,32}$/.test(id) || !title || /^(want to save this playlist|youtube)$/i.test(title.trim())) continue;
    output.push({
      video_id: id,
      video_url: "https://www.youtube.com/watch?v=" + id,
      video_title: title.slice(0, 300),
      creator_name: creator.slice(0, 160) || null,
      thumbnail_url: decodeXml(thumbnail).slice(0, 2048) || null,
      category: "anime",
      relevance_score: 50,
      description: readTag(entry, "media:description").slice(0, 4000) || null,
      view_count: null,
      published_at: readTag(entry, "published") || readTag(entry, "pubDate") || null,
    });
  }
  return [...new Map(output.map(v => [v.video_id, v])).values()].slice(0, 100);
}

async function fetchText(url: string): Promise<{ok:boolean;status:number;text:string;error?:string}> {
  try {
    const response = await fetch(url, { headers: requestHeaders, redirect: "follow", signal: AbortSignal.timeout(12000) });
    const text = await response.text();
    return { ok: response.ok, status: response.status, text: text.slice(0, 14000000) };
  } catch (error) {
    return { ok: false, status: 0, text: "", error: error instanceof Error ? error.message : String(error) };
  }
}

function extractChannelId(html: string): string {
  const patterns = [
    /"channelId"\s*:\s*"(UC[A-Za-z0-9_-]{20,})"/,
    /"externalId"\s*:\s*"(UC[A-Za-z0-9_-]{20,})"/,
    /"browseId"\s*:\s*"(UC[A-Za-z0-9_-]{20,})"/,
    /itemprop=["']channelId["']\s+content=["'](UC[A-Za-z0-9_-]{20,})["']/i,
  ];
  for (const pattern of patterns) {
    const match = html.match(pattern);
    if (match?.[1]) return match[1];
  }
  return "";
}

async function discoverFeed(type: "channel" | "playlist", key: string): Promise<{videos:any[];provider:string;channelId?:string;diagnostics:string[]}> {
  const diagnostics: string[] = [];
  let channelId = key;

  if (type === "channel" && key.startsWith("@")) {
    const page = await fetchText("https://www.youtube.com/" + key);
    if (page.ok) channelId = extractChannelId(page.text);
    else diagnostics.push("YouTube channel page: " + (page.status || page.error));
    if (!channelId) {
      const routes = [
        "https://rsshub.app/youtube/user/" + encodeURIComponent(key),
        "https://rsshub.app/youtube/user/" + encodeURIComponent(key.slice(1)),
      ];
      for (const route of routes) {
        const result = await fetchText(route);
        if (result.ok) {
          const videos = parseFeed(result.text);
          if (videos.length) return { videos, provider: "RSSHub", diagnostics };
          diagnostics.push("RSSHub handle feed returned no entries");
        } else diagnostics.push("RSSHub handle feed: " + (result.status || result.error));
      }
      throw new Error("Could not resolve this YouTube handle or read its RSS feed. Try pasting the channel's UC… ID. " + diagnostics.join("; "));
    }
  }

  const feedUrl = new URL("https://www.youtube.com/feeds/videos.xml");
  feedUrl.searchParams.set(type === "channel" ? "channel_id" : "playlist_id", channelId);
  const youtubeFeed = await fetchText(feedUrl.toString());
  if (youtubeFeed.ok) {
    const videos = parseFeed(youtubeFeed.text);
    if (videos.length) return { videos, provider: "YouTube RSS", channelId: type === "channel" ? channelId : undefined, diagnostics };
    diagnostics.push("YouTube RSS returned no parseable entries");
  } else diagnostics.push("YouTube RSS: " + (youtubeFeed.status || youtubeFeed.error));

  const route = type === "playlist"
    ? "https://rsshub.app/youtube/playlist/" + encodeURIComponent(key)
    : "https://rsshub.app/youtube/channel/" + encodeURIComponent(channelId);
  const alternate = await fetchText(route);
  if (alternate.ok) {
    const videos = parseFeed(alternate.text);
    if (videos.length) return { videos, provider: "RSSHub", channelId: type === "channel" ? channelId : undefined, diagnostics };
    diagnostics.push("RSSHub returned no parseable entries");
  } else diagnostics.push("RSSHub: " + (alternate.status || alternate.error));

  if (type === "playlist") {
    const page = await fetchText("https://www.youtube.com/playlist?list=" + encodeURIComponent(key));
    if (page.ok) {
      const found: any[] = [];
      const idPattern = /"videoId"\s*:\s*"([A-Za-z0-9_-]{6,32})"/g;
      let match: RegExpExecArray | null;
      while ((match = idPattern.exec(page.text)) !== null && found.length < 100) {
        const id = match[1];
        const fragment = page.text.slice(Math.max(0, match.index - 1200), Math.min(page.text.length, match.index + 3000));
        const titleMatch = fragment.match(/"title"\s*:\s*\{"(?:runs":\[\{"text"|simpleText")\s*:\s*"([^"]+)/);
        const title = decodeXml(titleMatch?.[1] || "");
        // YouTube playlist pages also contain playlist-level prompts that can be
        // adjacent to a videoId in the page source. Never queue those as videos.
        if (!title || /^(want to save this playlist|youtube)$/i.test(title.trim()) || found.some(v => v.video_id === id)) continue;
        found.push({
          video_id: id, video_url: "https://www.youtube.com/watch?v=" + id,
          video_title: title.slice(0, 300), creator_name: null,
          thumbnail_url: "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg",
          category: "anime", relevance_score: 50, description: null,
          view_count: null, published_at: null,
        });
      }
      if (found.length) return { videos: found, provider: "YouTube playlist page", diagnostics };
      diagnostics.push("YouTube playlist page contained no readable video entries");
    } else diagnostics.push("YouTube playlist page: " + (page.status || page.error));
  }

  throw new Error("No videos could be fetched from this source. " + diagnostics.join("; "));
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return Response.json({ error: "Method not allowed" }, { status: 405, headers: corsHeaders });

  try {
    const token = (req.headers.get("Authorization") || "").replace(/^Bearer\s+/i, "");
    if (!token) return Response.json({ error: "Sign in required" }, { status: 401, headers: corsHeaders });

    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    if (!supabaseUrl || !serviceKey) throw new Error("Server database credentials are not configured.");

    const adminClient = createClient(supabaseUrl, serviceKey);
    const { data: userData, error: userError } = await adminClient.auth.getUser(token);
    if (userError || !userData.user) return Response.json({ error: "Invalid session. Sign in again." }, { status: 401, headers: corsHeaders });
    const { data: adminRow, error: adminError } = await adminClient.from("admin_users").select("user_id").eq("user_id", userData.user.id).maybeSingle();
    if (adminError) throw new Error("Could not verify administrator access: " + adminError.message);
    if (!adminRow && userData.user.id !== OWNER_ID) return Response.json({ error: "Administrator access required" }, { status: 403, headers: corsHeaders });

    const body = await req.json();
    const sourceId = String(body?.source_id || "").trim();
    let source: any = null;
    let sourceError: any = null;
    const isUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(sourceId);
    if (isUuid) {
      const result = await adminClient.from("video_discovery_sources")
        .select("id,source_type,source_key,label,enabled").eq("id", sourceId).maybeSingle();
      source = result.data;
      sourceError = result.error;
    } else {
      // Backward compatibility for the already-deployed website client, which
      // still sends source_type/source_key rather than source_id.
      const legacyType = body?.source_type;
      const legacyKey = String(body?.source_key || "").trim();
      if (!["channel", "playlist"].includes(legacyType) || !legacyKey) {
        return Response.json({ error: "The saved source ID is missing. Refresh the workspace and try again." }, { status: 400, headers: corsHeaders });
      }
      const result = await adminClient.from("video_discovery_sources")
        .select("id,source_type,source_key,label,enabled")
        .eq("source_type", legacyType).eq("source_key", legacyKey).maybeSingle();
      source = result.data;
      sourceError = result.error;
    }
    if (sourceError) throw sourceError;
    if (!source || !source.enabled) return Response.json({ error: "This source was removed or disabled. Refresh the source list." }, { status: 404, headers: corsHeaders });

    const type = source.source_type as "channel" | "playlist";
    const key = String(source.source_key || "").trim();
    const validKey = type === "channel"
      ? (/^[A-Za-z0-9_-]{10,80}$/.test(key) || /^@[A-Za-z0-9._-]{3,100}$/.test(key))
      : /^[A-Za-z0-9_-]{10,80}$/.test(key);
    if (!["channel", "playlist"].includes(type) || !validKey) return Response.json({ error: "Saved YouTube source ID is invalid. Edit or replace this source." }, { status: 400, headers: corsHeaders });

    const result = await discoverFeed(type, key);
    if (!result.videos.length) throw new Error("The source returned zero videos.");

    const { data: publishedRows, error: publishedError } = await adminClient.from("related_videos").select("video_url").eq("platform", "youtube").limit(10000);
    if (publishedError) throw new Error("Could not check existing published videos: " + publishedError.message);
    const publishedIds = new Set((publishedRows || []).map((row: any) => {
      const url = String(row.video_url || "");
      return url.match(/(?:v=|youtu\.be\/|shorts\/)([A-Za-z0-9_-]{6,32})/)?.[1] || "";
    }).filter(Boolean));

    const rows = result.videos
      .filter(video => !publishedIds.has(video.video_id))
      .map(video => ({ ...video, source_id: source.id, status: "pending" }));
    let inserted = 0;
    if (rows.length) {
      const { data, error } = await adminClient.from("video_discovery_queue")
        .upsert(rows, { onConflict: "video_id", ignoreDuplicates: true })
        .select("video_id");
      if (error) throw new Error("Videos were fetched but could not be added to the approval queue: " + error.message);
      inserted = data?.length || 0;
    }

    const { error: updateError } = await adminClient.from("video_discovery_sources")
      .update({ last_checked_at: new Date().toISOString() }).eq("id", source.id);
    if (updateError) throw new Error("Videos were queued, but the source check time could not be saved: " + updateError.message);

    return Response.json({
      ok: true, source_id: source.id, source_type: type, source_label: source.label,
      fetched: result.videos.length, added: inserted,
      already_published: result.videos.filter(v => publishedIds.has(v.video_id)).length,
      provider: result.provider,
    }, { headers: { ...corsHeaders, "Cache-Control": "no-store" } });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Discovery failed";
    console.error("[video-discovery]", message);
    return Response.json({ ok: false, error: message }, { status: 500, headers: corsHeaders });
  }
});
