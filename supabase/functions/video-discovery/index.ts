import { createClient } from "npm:@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function parseFeed(xml: string) {
  const entries = [...xml.matchAll(/<entry\b[\s\S]*?<\/entry>/gi)].map(match => match[0]);
  return entries.map(entry => {
    const read = (tag: string) => {
      const value = entry.match(new RegExp(`<${tag}\\b[^>]*>([\\s\\S]*?)<\\/${tag}>`, "i"))?.[1] || "";
      return value.replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, "$1")
        .replace(/&amp;/g, "&").replace(/&quot;/g, '"').replace(/&#39;/g, "'")
        .replace(/&lt;/g, "<").replace(/&gt;/g, ">").trim();
    };
    const id = read("yt:videoId") || entry.match(/<id>yt:video:([^<]+)<\/id>/i)?.[1] || "";
    const title = read("title");
    const author = read("name");
    const thumbnail = entry.match(/<media:thumbnail\b[^>]*url=["']([^"']+)["']/i)?.[1] || "";
    if (!/^[a-zA-Z0-9_-]{6,32}$/.test(id) || !title) return null;
    return {
      video_id: id,
      video_url: `https://www.youtube.com/watch?v=${id}`,
      video_title: title.slice(0, 300),
      creator_name: author.slice(0, 160) || null,
      thumbnail_url: thumbnail.replace(/&amp;/g, "&").slice(0, 2048) || null,
    };
  }).filter(Boolean);
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return Response.json({ error: "Method not allowed" }, { status: 405, headers: corsHeaders });
  try {
    const auth = req.headers.get("Authorization") || "";
    const token = auth.replace(/^Bearer\s+/i, "");
    if (!token) return Response.json({ error: "Sign in required" }, { status: 401, headers: corsHeaders });

    const url = Deno.env.get("SUPABASE_URL")!;
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const adminClient = createClient(url, serviceKey);
    const { data: userData, error: userError } = await adminClient.auth.getUser(token);
    if (userError || !userData.user) return Response.json({ error: "Invalid session" }, { status: 401, headers: corsHeaders });
    const { data: adminRow } = await adminClient.from("admin_users").select("user_id").eq("user_id", userData.user.id).maybeSingle();
    if (!adminRow && userData.user.id !== "4da14151-419b-4872-8e5b-6ec38185cdbf") {
      return Response.json({ error: "Administrator access required" }, { status: 403, headers: corsHeaders });
    }

    const body = await req.json();
    const type = body?.source_type;
    const key = String(body?.source_key || "").trim();
    if (!["channel", "playlist"].includes(type) || !/^[a-zA-Z0-9_-]{10,80}$/.test(key)) {
      return Response.json({ error: "Enter a valid YouTube channel ID or playlist ID." }, { status: 400, headers: corsHeaders });
    }
    const feedUrl = new URL("https://www.youtube.com/feeds/videos.xml");
    feedUrl.searchParams.set(type === "channel" ? "channel_id" : "playlist_id", key);
    const response = await fetch(feedUrl, { headers: { "User-Agent": "RykoCollection/1.0", "Accept": "application/atom+xml, application/xml, text/xml" } });
    if (!response.ok) return Response.json({ error: `YouTube feed returned HTTP ${response.status}` }, { status: 502, headers: corsHeaders });
    const xml = await response.text();
    const videos = parseFeed(xml);
    return Response.json({ ok: true, videos, count: videos.length }, { headers: { ...corsHeaders, "Cache-Control": "no-store" } });
  } catch (error) {
    return Response.json({ error: error instanceof Error ? error.message : "Discovery failed" }, { status: 500, headers: corsHeaders });
  }
});
