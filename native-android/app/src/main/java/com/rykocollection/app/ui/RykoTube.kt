package com.rykocollection.app.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
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
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.rykocollection.app.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.URLEncoder
import java.util.Locale

private const val TUBE_PAGE_SIZE = 40
private const val TUBE_CACHE_MS = 10 * 60 * 1000L
private const val TUBE_PUBLIC_KEY_FALLBACK = "sb_publishable_XrJYTYAGNjlfPsWlP1SfUg_-yr9HBrq"

private data class TubeVideo(
    val id: String,
    val title: String,
    val url: String,
    val thumb: String,
    val creator: String,
    val creatorAvatar: String,
    val titleName: String,
    val titleFormat: String,
    val short: Boolean
)

private object TubeFeedCache {
    var videos: List<TubeVideo> = emptyList()
    var savedAt: Long = 0L
    fun fresh(): Boolean = videos.isNotEmpty() && System.currentTimeMillis() - savedAt < TUBE_CACHE_MS
}

private fun JsonObject.string(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()

private fun youtubeVideoId(raw: String): String? {
    return try {
        val uri = java.net.URI(raw.trim())
        val host = (uri.host ?: "").lowercase(Locale.ROOT).removePrefix("www.").removePrefix("m.")
        val parts = uri.path.orEmpty().split('/').filter(String::isNotBlank)
        val id = when {
            host == "youtu.be" -> parts.firstOrNull()
            host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com") ->
                if (parts.firstOrNull() == "watch") {
                    uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("v=") }?.substringAfter("v=")
                } else if (parts.firstOrNull() in listOf("embed", "shorts", "live", "v")) parts.getOrNull(1)
                else null
            else -> null
        }
        id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{6,}")) }
    } catch (_: Exception) { null }
}

private fun isTubeShort(url: String, platform: String): Boolean {
    val path = try { java.net.URI(url).path.orEmpty().lowercase(Locale.ROOT) } catch (_: Exception) { "" }
    val p = platform.lowercase(Locale.ROOT)
    return path.contains("/shorts/") ||
        (p == "instagram" && (path.startsWith("/reel/") || path.startsWith("/reels/"))) ||
        (p == "tiktok" && path.contains("/video/"))
}

private fun thumbnailFor(url: String, titleCover: String): String {
    val id = youtubeVideoId(url)
    return if (id != null) "https://i.ytimg.com/vi/$id/hqdefault.jpg" else titleCover
}

private suspend fun fetchTubePage(page: Int): List<TubeVideo> = withContext(Dispatchers.IO) {
    val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY.ifBlank { TUBE_PUBLIC_KEY_FALLBACK }
    require(key.isNotBlank()) { "RykoTube is missing its Supabase publishable key." }
    val from = (page - 1) * TUBE_PAGE_SIZE
    val to = from + TUBE_PAGE_SIZE - 1
    // Keep this projection aligned with the proven web getRelatedVideos() query.
    // related_videos does not contain title_name, title_format, or title_cover.
    val select = "id,title_id,legacy_title_id,video_url,platform,video_title,creator_name,creator_avatar_url,action_type,action_url,sort_order,created_at"
    val url = "$baseUrl/rest/v1/related_videos?select=$select&order=sort_order.asc,created_at.desc&offset=$from&limit=$TUBE_PAGE_SIZE"
    val client = HttpClient(Android)
    try {
        val response = client.get(url) {
            header("apikey", key)
            header("Authorization", "Bearer $key")
            header("Accept", "application/json")
            header("Range", "$from-$to")
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            val detail = body.take(240).replace(Regex("\\s+"), " ")
            throw IllegalStateException("Supabase HTTP ${response.status.value}: ${detail.ifBlank { "Unable to read related_videos." }}")
        }
        val rows = Json.parseToJsonElement(body).jsonArray
        rows.mapNotNull { item ->
            val row = item.jsonObject
            val videoUrl = row.string("video_url")
            if (videoUrl.isBlank()) return@mapNotNull null
            val platform = row.string("platform")
            TubeVideo(
                id = row.string("id").ifBlank { videoUrl },
                title = row.string("video_title").ifBlank { "Untitled video" },
                url = videoUrl,
                thumb = thumbnailFor(videoUrl, ""),
                creator = row.string("creator_name").ifBlank { "RykoCollection" },
                creatorAvatar = row.string("creator_avatar_url"),
                titleName = "",
                titleFormat = "",
                short = isTubeShort(videoUrl, platform)
            )
        }.distinctBy { it.id }
    } finally {
        client.close()
    }
}

@Composable
fun NativeRykoTube() {
    var videos by remember { mutableStateOf(TubeFeedCache.videos) }
    var loading by remember { mutableStateOf(videos.isEmpty()) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedFormat by remember { mutableStateOf("All") }
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<TubeVideo?>(null) }
    var page by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var refreshToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshToken) {
        if (refreshToken == 0 && TubeFeedCache.fresh()) {
            videos = TubeFeedCache.videos
            loading = false
            return@LaunchedEffect
        }
        loading = videos.isEmpty()
        error = null
        try {
            val firstPage = fetchTubePage(1)
            videos = firstPage
            page = 1
            hasMore = firstPage.size == TUBE_PAGE_SIZE
            TubeFeedCache.videos = firstPage
            TubeFeedCache.savedAt = System.currentTimeMillis()
        } catch (t: Throwable) {
            if (videos.isEmpty()) error = t.message ?: "Could not load RykoTube."
        } finally {
            loading = false
        }
    }

    val query = search.trim().lowercase(Locale.ROOT)
    val filtered = videos.filter { video ->
        val formatMatches = when (selectedFormat) {
            "Videos" -> !video.short
            "Shorts" -> video.short
            else -> true
        }
        val textMatches = query.isBlank() || listOf(video.title, video.creator, video.titleName, video.titleFormat)
            .any { it.lowercase(Locale.ROOT).contains(query) }
        formatMatches && textMatches
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("RYKOTUBE", color = Color(0xFFA99BFF), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("Anime videos, trailers & Shorts", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = { refreshToken++ }) { Icon(Icons.Default.Refresh, "Refresh", tint = Color.White) }
        }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
            placeholder = { Text("Search videos, creators or titles") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFFA99BFF), unfocusedBorderColor = Color.DarkGray,
                focusedPlaceholderColor = Color.Gray, unfocusedPlaceholderColor = Color.Gray
            )
        )
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Videos", "Shorts").forEach { format ->
                FilterChip(selected = selectedFormat == format, onClick = { selectedFormat = format }, label = { Text(format) })
            }
        }

        if (selected != null) {
            TubePlayer(selected!!) { selected = null }
        } else {
            when {
                loading && videos.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFA99BFF))
                }
                error != null && videos.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("RykoTube couldn't load", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = Color.LightGray)
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { refreshToken++ }) { Text("Retry") }
                }
                filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (videos.isEmpty()) "No videos are published yet." else "No matching videos. Try another search or filter.", color = Color.LightGray)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (selectedFormat == "All" && query.isBlank()) {
                        val regular = filtered.filterNot { it.short }
                        val shorts = filtered.filter { it.short }
                        var regularIndex = 0
                        var shortIndex = 0
                        while (regularIndex < regular.size) {
                            val batch = regular.drop(regularIndex).take(10)
                            item(key = "regular-heading-$regularIndex") {
                                Text(if (regularIndex == 0) "Recommended videos" else "More videos", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            items(batch, key = { it.id }) { video -> TubeVideoCard(video) { selected = video } }
                            regularIndex += batch.size
                            if (shortIndex < shorts.size) {
                                val shortBatch = shorts.drop(shortIndex).take(8)
                                item(key = "shorts-heading-$shortIndex") {
                                    Text("Shorts", color = Color(0xFFA99BFF), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                                items(shortBatch, key = { it.id }) { video -> TubeVideoCard(video) { selected = video } }
                                shortIndex += shortBatch.size
                            }
                        }
                        if (regular.isEmpty() && shorts.isNotEmpty()) {
                            item { Text("Shorts", color = Color(0xFFA99BFF), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                            items(shorts, key = { it.id }) { video -> TubeVideoCard(video) { selected = video } }
                        }
                    } else {
                        items(filtered, key = { it.id }) { video -> TubeVideoCard(video) { selected = video } }
                    }
                    if (hasMore) {
                        item(key = "load-more") {
                            Button(
                                onClick = {
                                    if (!loadingMore) {
                                        loadingMore = true
                                        error = null
                                    }
                                },
                                enabled = !loadingMore,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (loadingMore) "Loading…" else "Load more") }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(loadingMore) {
        if (!loadingMore) return@LaunchedEffect
        try {
            val nextPage = page + 1
            val next = fetchTubePage(nextPage)
            val ids = videos.mapTo(mutableSetOf()) { it.id }
            videos = videos + next.filterNot { it.id in ids }
            page = nextPage
            hasMore = next.size == TUBE_PAGE_SIZE
            TubeFeedCache.videos = videos
            TubeFeedCache.savedAt = System.currentTimeMillis()
        } catch (t: Throwable) {
            error = t.message ?: "Could not load more videos."
        } finally {
            loadingMore = false
        }
    }
}

@Composable
private fun TubeVideoCard(video: TubeVideo, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF111111)).clickable(onClick = onClick)
    ) {
        Box(
            Modifier.fillMaxWidth()
                .then(if (video.short) Modifier.height(300.dp) else Modifier.aspectRatio(16f / 9f))
                .background(Color(0xFF202020)),
            contentAlignment = Alignment.Center
        ) {
            if (video.thumb.isNotBlank()) {
                AsyncImage(
                    model = video.thumb,
                    contentDescription = video.title,
                    modifier = if (video.short) Modifier.width(169.dp).fillMaxHeight() else Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Icon(Icons.Default.PlayArrow, "Play", Modifier.size(48.dp), tint = Color.White)
            if (video.short) Text("SHORT", Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color.Black).padding(5.dp), color = Color.White, fontSize = 10.sp)
        }
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (video.creatorAvatar.isNotBlank()) {
                AsyncImage(video.creatorAvatar, video.creator, Modifier.size(38.dp).clip(RoundedCornerShape(50)))
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(video.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(video.creator, color = Color.LightGray, fontSize = 11.sp)
                if (video.titleName.isNotBlank()) Text(video.titleName, color = Color(0xFFA99BFF), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun TubePlayer(video: TubeVideo, onClose: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(video.id) {
        val intent = android.content.Intent(context, TubePlayerActivity::class.java).apply {
            putExtra(TubePlayerActivity.EXTRA_VIDEO_ID, youtubeVideoId(video.url))
            putExtra(TubePlayerActivity.EXTRA_TITLE, video.title)
            putExtra(TubePlayerActivity.EXTRA_CREATOR, video.creator)
        }
        context.startActivity(intent)
        onClose()
    }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color(0xFFA99BFF))
    }
}
