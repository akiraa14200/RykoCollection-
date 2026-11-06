package com.rykocollection.app.ui

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

private const val DB_URL = "https://moicrqcddmftjgwjqkvz.supabase.co"
private const val DB_KEY = "sb_publishable_XrJYTYAGNjlfPsWlP1SfUg_-yr9HBrq"
private data class TubeVideo(val id:String,val title:String,val url:String,val thumb:String,val creator:String,val short:Boolean)

private suspend fun fetchTubeVideos():List<TubeVideo> = withContext(Dispatchers.IO) {
    val client=HttpClient(Android)
    try {
        val response=client.get(DB_URL+"/rest/v1/related_videos?select=*&order=created_at.desc&limit=80") {
            header("apikey",DB_KEY); header("Authorization","Bearer "+DB_KEY)
        }
        if(response.status.value !in 200..299) throw IllegalStateException("Supabase returned HTTP "+response.status.value+". Check video table access policies.")
        Json.parseToJsonElement(response.bodyAsText()).jsonArray.mapNotNull { item ->
            val o=item.jsonObject
            fun field(vararg keys:String):String = keys.firstNotNullOfOrNull { o[it]?.jsonPrimitive?.contentOrNull }?.trim().orEmpty()
            val url=field("video_url","url","youtube_url")
            if(url.isBlank()) return@mapNotNull null
            val title=field("video_title","title","name").ifBlank { "Untitled video" }
            val id=field("id","video_id").ifBlank { url }
            val yt=Regex("(?:v=|youtu\\.be/|shorts/|embed/)([A-Za-z0-9_-]{6,})").find(url)?.groupValues?.getOrNull(1)
            val thumb=field("thumbnail_url","thumbnail","cover_url").ifBlank { if(yt!=null) "https://i.ytimg.com/vi/"+yt+"/hqdefault.jpg" else "" }
            val creator=field("creator_name","channel_name","author","creator").ifBlank { "RykoTube" }
            val isShort=url.contains("/shorts/") || field("video_type","category","format").contains("short",true)
            TubeVideo(id,title,url,thumb,creator,isShort)
        }.distinctBy { it.id }
    } finally { client.close() }
}

@Composable
fun NativeRykoTube() {
    var videos by remember { mutableStateOf<List<TubeVideo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var shorts by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<TubeVideo?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) {
        loading=true; error=null
        try { videos=fetchTubeVideos() } catch(t:Throwable) { error=t.message ?: "Could not load videos." } finally { loading=false }
    }
    val filtered=videos.filter { it.short==shorts }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("RYKOTUBE",color=Color(0xFFA99BFF),fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=2.sp)
                Text("Anime videos, trailers & Shorts",color=Color.White,fontSize=21.sp,fontWeight=FontWeight.Bold)
            }
            IconButton(onClick={refresh++}) { Icon(Icons.Default.Refresh,"Refresh",tint=Color.White) }
        }
        Row(Modifier.padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=!shorts,onClick={shorts=false},label={Text("Videos")})
            FilterChip(selected=shorts,onClick={shorts=true},label={Text("Shorts")})
        }
        if(selected!=null) TubePlayer(selected!!){selected=null}
        else if(loading) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator(color=Color(0xFFA99BFF))}
        else if(error!=null) Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
            Text("RykoTube couldn't load",color=Color.White,fontWeight=FontWeight.Bold)
            Text(error!!,color=Color.LightGray)
            Button(onClick={refresh++}){Text("Retry")}
        } else if(filtered.isEmpty()) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
            Text(if(videos.isEmpty()) "No videos are published yet." else "No videos in this section.",color=Color.LightGray)
        } else LazyColumn(contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            items(filtered,key={it.id}) { video ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF111111)).clickable{selected=video}) {
                    Box {
                        AsyncImage(video.thumb,video.title,Modifier.fillMaxWidth().height(if(video.short) 230.dp else 190.dp))
                        Icon(Icons.Default.PlayArrow,"Play",Modifier.align(Alignment.Center).size(48.dp),tint=Color.White)
                        if(video.short) Text("SHORT",Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color.Black).padding(5.dp),color=Color.White,fontSize=10.sp)
                    }
                    Column(Modifier.padding(12.dp)) {
                        Text(video.title,color=Color.White,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text(video.creator,color=Color.LightGray,fontSize=11.sp)
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TubePlayer(video:TubeVideo,onClose:()->Unit) {
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        TextButton(onClick=onClose){Text("‹ Back to RykoTube",color=Color(0xFFA99BFF))}
        val id=Regex("(?:v=|youtu\\.be/|shorts/|embed/)([A-Za-z0-9_-]{6,})").find(video.url)?.groupValues?.getOrNull(1)
        if(id==null) Text("This provider cannot be embedded. Open the original link in your browser.",color=Color.White,modifier=Modifier.padding(18.dp))
        else {
            key(id) {
                AndroidView(modifier=Modifier.fillMaxWidth().aspectRatio(16f/9f),factory={ context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled=true; settings.domStorageEnabled=true
                        webChromeClient=WebChromeClient(); webViewClient=WebViewClient()
                        loadDataWithBaseURL("https://www.youtube.com","<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><body style='margin:0;background:#000'><iframe width='100%' height='100%' style='position:absolute;inset:0;border:0' src='https://www.youtube-nocookie.com/embed/"+id+"?autoplay=1&playsinline=1' allow='autoplay; encrypted-media; picture-in-picture' allowfullscreen></iframe></body>","text/html","UTF-8",null)
                    }
                })
            }
            Text(video.title,color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(16.dp))
            Text(video.creator,color=Color.LightGray,modifier=Modifier.padding(horizontal=16.dp))
        }
    }
}
