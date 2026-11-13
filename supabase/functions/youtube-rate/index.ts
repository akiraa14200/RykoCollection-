import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

  // The Supabase gateway verifies the caller's Supabase JWT (verify_jwt=true).
  // The Google provider token is used only for this request and is never stored or logged.
  const authorization = req.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) {
    return json({ error: "Authentication required" }, 401);
  }

  let body: { videoId?: string; rating?: string; googleAccessToken?: string };
  try {
    body = await req.json();
  } catch {
    return json({ error: "Invalid JSON body" }, 400);
  }

  const videoId = body.videoId ?? "";
  const rating = body.rating ?? "";
  const googleAccessToken = body.googleAccessToken ?? "";

  if (!/^[A-Za-z0-9_-]{11}$/.test(videoId)) {
    return json({ error: "Invalid YouTube video ID" }, 400);
  }
  if (!["like", "dislike", "none"].includes(rating)) {
    return json({ error: "Invalid rating" }, 400);
  }
  if (!googleAccessToken || googleAccessToken.length > 8192) {
    return json({ error: "YouTube is not connected. Reconnect your Google account." }, 401);
  }

  const youtubeUrl = new URL("https://www.googleapis.com/youtube/v3/videos/rate");
  youtubeUrl.searchParams.set("id", videoId);
  youtubeUrl.searchParams.set("rating", rating);

  const youtubeResponse = await fetch(youtubeUrl, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${googleAccessToken}`,
      Accept: "application/json",
    },
  });

  if (youtubeResponse.ok) {
    return new Response(null, { status: 204, headers: corsHeaders });
  }

  // Return a small, non-sensitive error payload; never echo tokens or request headers.
  const detail = await youtubeResponse.text().catch(() => "");
  return json({
    error: youtubeResponse.status === 401
      ? "YouTube authorization expired. Reconnect your YouTube account."
      : youtubeResponse.status === 403
        ? "YouTube could not apply this rating. The video may not allow ratings."
        : `YouTube rating failed (HTTP ${youtubeResponse.status}).`,
    upstreamStatus: youtubeResponse.status,
    detail: detail.slice(0, 1200),
  }, youtubeResponse.status === 401 ? 401 : 502);
});
