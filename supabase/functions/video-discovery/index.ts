import { createClient } from "npm:@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const OWNER_ID = "4da14151-419b-4872-8e5b-6ec38185cdbf";
const decode = (value:string) => value.replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g,"$1").replace(/&amp;/g,"&").replace(/&quot;/g,'"').replace(/&#39;/g,"'").replace(/&lt;/g,"<").replace(/&gt;/g,">").trim();

function parseFeed(xml:string) {
  return [...xml.matchAll(/<entry\b[\s\S]*?<\/entry>/gi)].map(match => {
    const entry=match[0];
    const read=(tag:string)=>decode(entry.match(new RegExp(`<${tag}\\b[^>]*>([\\s\\S]*?)<\\/${tag}>`,"i"))?.[1]||"");
    const id=read("yt:videoId")||entry.match(/<id>yt:video:([^<]+)<\/id>/i)?.[1]||"";
    const title=read("title"), creator=read("name");
    const thumbnail=entry.match(/<media:thumbnail\b[^>]*url=["']([^"']+)["']/i)?.[1]||"";
    if(!/^[a-zA-Z0-9_-]{6,32}$/.test(id)||!title)return null;
    return {video_id:id,video_url:`https://www.youtube.com/watch?v=${id}`,video_title:title.slice(0,300),creator_name:creator.slice(0,160)||null,thumbnail_url:thumbnail.replace(/&amp;/g,"&").slice(0,2048)||null,category:"channel upload",relevance_score:50,description:null,view_count:null,published_at:null};
  }).filter(Boolean);
}

function classify(text:string) {
  const t=text.toLowerCase();
  if(/trailer|teaser|pv\b|official preview/.test(t))return "trailer";
  if(/opening|ending|ost|soundtrack|music video/.test(t))return "music";
  if(/news|announcement|confirmed|release date/.test(t))return "news";
  if(/review|explained|analysis|recommendation/.test(t))return "review";
  if(/making of|behind the scenes|sakuga|animation process|studio/.test(t))return "production";
  if(/amv|edit|fan animation/.test(t))return "amv";
  return "anime";
}
function relevance(query:string,title:string,description:string,channel:string) {
  const terms=[...new Set(query.toLowerCase().split(/[^a-z0-9]+/).filter(x=>x.length>2))];
  const t=title.toLowerCase(), d=description.toLowerCase(), c=channel.toLowerCase();
  let score=15;
  for(const term of terms){if(t.includes(term))score+=12;else if(d.includes(term))score+=4;else if(c.includes(term))score+=2;}
  if(/anime|animation|manga|studio|amv|sakuga/.test(t+" "+d))score+=15;
  if(/official|music|trailer|teaser/.test(t))score+=8;
  return Math.min(100,score);
}

Deno.serve(async (req:Request) => {
  if(req.method==="OPTIONS")return new Response("ok",{headers:corsHeaders});
  if(req.method!=="POST")return Response.json({error:"Method not allowed"},{status:405,headers:corsHeaders});
  try {
    const token=(req.headers.get("Authorization")||"").replace(/^Bearer\s+/i,"");
    if(!token)return Response.json({error:"Sign in required"},{status:401,headers:corsHeaders});
    const url=Deno.env.get("SUPABASE_URL")!;
    const serviceKey=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const adminClient=createClient(url,serviceKey);
    const {data:userData,error:userError}=await adminClient.auth.getUser(token);
    if(userError||!userData.user)return Response.json({error:"Invalid session"},{status:401,headers:corsHeaders});
    const {data:adminRow}=await adminClient.from("admin_users").select("user_id").eq("user_id",userData.user.id).maybeSingle();
    if(!adminRow&&userData.user.id!==OWNER_ID)return Response.json({error:"Administrator access required"},{status:403,headers:corsHeaders});

    const body=await req.json();
    const type=body?.source_type,key=String(body?.source_key||"").trim();
    const validKey=type==="channel"?(/^[a-zA-Z0-9_-]{10,80}$/.test(key)||/^@[a-zA-Z0-9._-]{3,100}$/.test(key)):/^[a-zA-Z0-9_-]{10,80}$/.test(key);
    if(!["channel","playlist"].includes(type)||!validKey)return Response.json({error:"Enter a valid YouTube channel ID, handle, or playlist ID."},{status:400,headers:corsHeaders});
    let channelId=key;
    if(type==="channel"&&key.startsWith("@")){
      const page=await fetch("https://www.youtube.com/"+key,{headers:{"User-Agent":"Mozilla/5.0 (compatible; RykoCollection/2.0)"}});
      if(!page.ok)return Response.json({error:"Could not resolve that YouTube channel handle."},{status:502,headers:corsHeaders});
      const html=(await page.text()).slice(0,3000000);
      channelId=html.match(/"channelId"\s*:\s*"(UC[a-zA-Z0-9_-]{20,})"/)?.[1]||html.match(/itemprop=["']channelId["']\s+content=["'](UC[a-zA-Z0-9_-]{20,})["']/i)?.[1]||"";
      if(!channelId)return Response.json({error:"YouTube did not expose a channel ID for this handle. Try its channel ID instead."},{status:404,headers:corsHeaders});
    }
    const feedUrl=new URL("https://www.youtube.com/feeds/videos.xml");
    feedUrl.searchParams.set(type==="channel"?"channel_id":"playlist_id",channelId);
    const feed=await fetch(feedUrl,{headers:{"User-Agent":"RykoCollection/2.0","Accept":"application/atom+xml, application/xml, text/xml"}});
    if(!feed.ok)return Response.json({error:`YouTube feed returned HTTP ${feed.status}`},{status:502,headers:corsHeaders});
    const videos=parseFeed(await feed.text());
    return Response.json({ok:true,videos,count:videos.length},{headers:{...corsHeaders,"Cache-Control":"no-store"}});
  }catch(error){
    return Response.json({error:error instanceof Error?error.message:"Discovery failed"},{status:500,headers:corsHeaders});
  }
});
