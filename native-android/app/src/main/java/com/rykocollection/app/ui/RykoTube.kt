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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.rykocollection.app.BuildConfig
import com.rykocollection.app.data.AniListMedia
import com.rykocollection.app.data.AniListRepository
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
import android.widget.Toast

private const val TUBE_PAGE_SIZE = 40
private const val TUBE_CACHE_MS = 10 * 60 * 1000L
private const val TUBE_PUBLIC_KEY_FALLBACK = "sb_publishable_XrJYTYAGNjlfPsWlP1SfUg_-yr9HBrq"

private data class TubeVideo(
    val id: String,
    val title: String,
    val url: String,
    val platform: String,
    val thumb: String,
    val creator: String,
    val creatorAvatar: String,
    val titleName: String,
    val titleFormat: String,
    val sourceId: String,
    val short: Boolean
)

private data class TubeSource(val id: String, val type: String, val label: String)

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
    val select = "id,title_id,legacy_title_id,video_url,platform,video_title,creator_name,creator_avatar_url,action_type,action_url,sort_order,created_at,source_id,titles!related_videos_title_id_fkey(name,media_type,cover_url)"
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
            val title = row["titles"]?.let { runCatching { it.jsonObject }.getOrNull() }
            TubeVideo(
                id = row.string("id").ifBlank { videoUrl },
                title = row.string("video_title").ifBlank { "Untitled video" },
                url = videoUrl,
                platform = platform,
                thumb = thumbnailFor(videoUrl, title?.string("cover_url").orEmpty()),
                creator = row.string("creator_name").ifBlank { "RykoCollection" },
                creatorAvatar = row.string("creator_avatar_url"),
                titleName = title?.string("name").orEmpty(),
                titleFormat = title?.string("media_type").orEmpty(),
                sourceId = row.string("source_id"),
                short = isTubeShort(videoUrl, platform)
            )
        }.distinctBy { it.id }
    } finally {
        client.close()
    }
}

private suspend fun fetchTubeSources(): List<TubeSource> = withContext(Dispatchers.IO) {
    val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY.ifBlank { TUBE_PUBLIC_KEY_FALLBACK }
    val client = HttpClient(Android)
    try {
        val response = client.get("$baseUrl/rest/v1/video_discovery_sources?select=id,source_type,label&enabled=eq.true&order=label.asc") {
            header("apikey", key)
            header("Authorization", "Bearer $key")
            header("Accept", "application/json")
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw IllegalStateException("Could not load channels and playlists (HTTP ${response.status.value}).")
        }
        Json.parseToJsonElement(body).jsonArray.map { item ->
            val row = item.jsonObject
            TubeSource(row.string("id"), row.string("source_type"), row.string("label"))
        }.filter { it.id.isNotBlank() && it.label.isNotBlank() }
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
    var sources by remember { mutableStateOf<List<TubeSource>>(emptyList()) }
    var selectedSource by remember { mutableStateOf("") }
    var sourceError by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var catalogueResults by remember { mutableStateOf<List<AniListMedia>>(emptyList()) }
    var catalogueLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val tubeHistory = remember { context.getSharedPreferences("ryko_search", android.content.Context.MODE_PRIVATE).getString("tube", "").orEmpty().split("¦").filter { it.isNotBlank() }.take(8) }

    LaunchedEffect(search) {
        val term = search.trim()
        if (term.length < 2) { catalogueResults = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(420)
        catalogueLoading = true
        val repository = AniListRepository()
        try {
            val anime = repository.searchMedia(term, false)
            val manga = repository.searchMedia(term, true)
            catalogueResults = (anime + manga).distinctBy { it.id }.take(12)
            val history = (listOf(term) + tubeHistory.filterNot { it.equals(term, true) }).take(8)
            context.getSharedPreferences("ryko_search", android.content.Context.MODE_PRIVATE).edit().putString("tube", history.joinToString("¦")).apply()
        } catch (_: Throwable) { catalogueResults = emptyList() }
        finally { repository.close(); catalogueLoading = false }
    }

    LaunchedEffect(Unit) {
        try {
            sources = fetchTubeSources()
            sourceError = null
        } catch (t: Throwable) {
            sourceError = t.message ?: "Channels and playlists could not be loaded."
        }
    }

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
    fun relevance(video: TubeVideo): Int {
        if (query.isBlank()) return 0
        val title = video.title.lowercase(Locale.ROOT)
        val linkedTitle = video.titleName.lowercase(Locale.ROOT)
        val creator = video.creator.lowercase(Locale.ROOT)
        val format = video.titleFormat.lowercase(Locale.ROOT)
        val platform = video.platform.lowercase(Locale.ROOT)
        return when {
            title == query -> 1000
            title.startsWith(query) -> 850
            title.contains(query) -> 700
            linkedTitle == query -> 650
            linkedTitle.startsWith(query) -> 550
            linkedTitle.contains(query) -> 450
            creator.startsWith(query) -> 350
            creator.contains(query) -> 250
            format.contains(query) || platform.contains(query) -> 150
            else -> 0
        }
    }
    val filtered = videos.filter { video ->
        val formatMatches = when (selectedFormat) {
            "Videos" -> !video.short
            "Shorts" -> video.short
            else -> true
        }
        val textMatches = query.isBlank() || relevance(video) > 0
        formatMatches && textMatches && (selectedSource.isBlank() || video.sourceId == selectedSource)
    }.let { matches ->
        if (query.isBlank()) matches else matches.sortedByDescending(::relevance)
    }

    Column(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF242039), Color(0xFF10101B), Color(0xFF07070C))))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(142.dp)) {
                Box(Modifier.size(31.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFFA99BFF)), contentAlignment = Alignment.Center) {
                    Text("R", color = Color(0xFF100D1D), fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
                Spacer(Modifier.width(8.dp))
                Text("RykoCollection", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }
            OutlinedTextField(
                value = search, onValueChange = { search = it }, modifier = Modifier.weight(1f),
                singleLine = true, placeholder = { Text("Search videos, creators or titles", maxLines = 1) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFA99BFF), unfocusedBorderColor = Color.DarkGray,
                    focusedPlaceholderColor = Color.Gray, unfocusedPlaceholderColor = Color.Gray
                )
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Videos", "Shorts").forEach { format ->
                FilterChip(selected = selectedFormat == format, onClick = { selectedFormat = format }, label = { Text(format) })
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { refreshToken++ }) { Icon(Icons.Default.Refresh, "Refresh", tint = Color.White) }
        }
        if (search.isBlank() && tubeHistory.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(tubeHistory) { term -> AssistChip(onClick = { search = term }, label = { Text(term, maxLines = 1) }) }
            }
        }
        if (search.trim().length >= 2) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("ANIME & MANGA ON ANILIST", color = Color(0xFFA99BFF), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (catalogueLoading) CircularProgressIndicator(color = Color(0xFFA99BFF), modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                if (catalogueResults.isEmpty() && !catalogueLoading) Text("No matching AniList titles.", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(catalogueResults, key = { "anilist-${it.id}" }) { media ->
                        Column(Modifier.width(112.dp).rykoGlass(12.dp)) {
                            Box(Modifier.fillMaxWidth().height(142.dp).clickable {
                                val trailer = media.trailer
                                if (!trailer?.id.isNullOrBlank() && trailer?.site.equals("youtube", true)) {
                                    selected = TubeVideo("anilist-${media.id}", media.title.display, "https://www.youtube.com/watch?v=${trailer?.id}", "youtube", trailer?.thumbnail ?: media.coverImage?.imageUrl.orEmpty(), "AniList", "", media.title.display, media.format.orEmpty(), "", false)
                                }
                            }) {
                                AsyncImage(media.coverImage?.imageUrl, media.title.display, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                if (!media.trailer?.id.isNullOrBlank() && media.trailer?.site.equals("youtube", true)) Icon(Icons.Default.PlayArrow, "Play trailer", Modifier.align(Alignment.Center).size(34.dp), tint = Color.White)
                            }
                            Text(media.title.display, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, modifier = Modifier.padding(7.dp), overflow = TextOverflow.Ellipsis)
                            Text(if (media.trailer?.id.isNullOrBlank()) "No trailer" else "Watch trailer", color = Color(0xFFA99BFF), fontSize = 9.sp, modifier = Modifier.padding(start = 7.dp, bottom = 7.dp))
                        }
                    }
                }
            }
        }
        if (sources.isNotEmpty()) {
            Column(Modifier.fillMaxWidth()) {
                Text("Channels & playlists", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(selected = selectedSource.isBlank(), onClick = { selectedSource = "" }, label = { Text("All sources") })
                    }
                    items(sources, key = { it.id }) { source ->
                        FilterChip(selected = selectedSource == source.id, onClick = { selectedSource = if (selectedSource == source.id) "" else source.id }, label = { Text(source.label, maxLines = 1) })
                    }
                }
            }
        } else if (sourceError != null) {
            Text("Channel and playlist filters unavailable", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp))
        }

        if (selected != null) {
            TubePlayer(selected!!, filtered, filtered.filter { it.short }) { selected = null }
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
                    val regular = filtered.filterNot { it.short }
                    val shorts = filtered.filter { it.short }
                    if (selectedFormat == "Shorts") {
                        // The Shorts tab mirrors the web feed: portrait cards stay in swipeable rows.
                        shorts.chunked(8).forEachIndexed { rowIndex, shortBatch ->
                            item(key = "shorts-filter-row-$rowIndex") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(if (rowIndex == 0) "Shorts" else "More Shorts", color = Color(0xFFA99BFF), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        contentPadding = PaddingValues(end = 4.dp)
                                    ) {
                                        items(shortBatch, key = { it.id }) { video ->
                                            TubeVideoCard(video) { selected = video }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (selectedFormat == "All") {
                        // Match the web feed: 10 regular videos, then a taste-ranked Shorts row (6–10 items).
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
                                val batchSize = 6 + ((shorts[shortIndex].id.hashCode().toUInt().toLong() % 5L).toInt())
                                val shortBatch = shorts.drop(shortIndex).take(batchSize)
                                item(key = "shorts-row-$shortIndex") {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Shorts", color = Color(0xFFA99BFF), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            contentPadding = PaddingValues(end = 4.dp)
                                        ) {
                                            items(shortBatch, key = { it.id }) { video ->
                                                TubeVideoCard(video) { selected = video }
                                            }
                                        }
                                    }
                                }
                                shortIndex += shortBatch.size
                            }
                        }
                        if (regular.isEmpty() && shorts.isNotEmpty()) {
                            shorts.chunked(8).forEachIndexed { rowIndex, shortBatch ->
                                item(key = "shorts-only-row-$rowIndex") {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(if (rowIndex == 0) "Shorts" else "More Shorts", color = Color(0xFFA99BFF), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            items(shortBatch, key = { it.id }) { video ->
                                                TubeVideoCard(video) { selected = video }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        items(regular, key = { it.id }) { video -> TubeVideoCard(video) { selected = video } }
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
    val context = LocalContext.current
    var menuExpanded by remember(video.id) { mutableStateOf(false) }
    val saved = remember(video.id) {
        context.getSharedPreferences("ryko_saved_videos", android.content.Context.MODE_PRIVATE)
            .getStringSet("ids", emptySet()).orEmpty().contains(video.id)
    }
    Column(
        (if (video.short) Modifier.width(170.dp) else Modifier.fillMaxWidth())
            .rykoGlass(18.dp)
    ) {
        Box(
            Modifier.fillMaxWidth()
                .then(if (video.short) Modifier.height(300.dp) else Modifier.aspectRatio(16f / 9f))
                .background(Color(0xFF202020)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (video.thumb.isNotBlank()) AsyncImage(
                model = video.thumb, contentDescription = video.title,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
            )
            Box(Modifier.align(Alignment.Center).size(52.dp).clip(RoundedCornerShape(50)).background(Color(0x55000000)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, "Play", Modifier.size(36.dp), tint = Color.White)
            }
            if (video.short) Text("SHORT", Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color.Black).padding(5.dp), color = Color.White, fontSize = 10.sp)
        }
        Row(Modifier.fillMaxWidth().padding(start = 13.dp, end = 6.dp, top = 12.dp, bottom = 14.dp), verticalAlignment = Alignment.Top) {
            if (video.creatorAvatar.isNotBlank()) {
                AsyncImage(video.creatorAvatar, video.creator, Modifier.size(42.dp).clip(RoundedCornerShape(50)), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(50)).background(Color(0xFF25232D)), contentAlignment = Alignment.Center) {
                    Text(video.creator.firstOrNull()?.uppercase() ?: "R", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f).padding(start = 11.dp, end = 5.dp).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(video.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
                Text(video.creator.ifBlank { video.platform }, color = Color(0xFF9A9AA2), fontSize = 12.sp, maxLines = 1)
                if (video.titleName.isNotBlank()) Text(video.titleName, color = Color(0xFFB6A5FF), fontSize = 12.sp, maxLines = 1)
                Text("Related link  ↗", color = Color(0xFFB6A5FF), fontSize = 11.sp, modifier = Modifier.clickable {
                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(video.url))) }
                }.padding(top = 2.dp))
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.MoreVert, "Video options", tint = Color.LightGray)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(if (saved) "Saved" else "Save") },
                        leadingIcon = { Icon(if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null) },
                        onClick = {
                            val prefs = context.getSharedPreferences("ryko_saved_videos", android.content.Context.MODE_PRIVATE)
                            val ids = prefs.getStringSet("ids", emptySet()).orEmpty().toMutableSet()
                            if (video.id in ids) ids.remove(video.id) else ids.add(video.id)
                            prefs.edit().putStringSet("ids", ids).apply()
                            Toast.makeText(context, if (video.id in ids) "Saved to Library" else "Removed from saved videos", Toast.LENGTH_SHORT).show()
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, "${video.title}\n${video.url}")
                        }
                        context.startActivity(android.content.Intent.createChooser(send, "Share video")); menuExpanded = false
                    })
                    DropdownMenuItem(text = { Text("Copy link") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Video link", video.url))
                        Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show(); menuExpanded = false
                    })
                    DropdownMenuItem(text = { Text("Download") }, onClick = {
                        Toast.makeText(context, "Downloads aren't available for this source. Use the original link.", Toast.LENGTH_LONG).show()
                        menuExpanded = false
                    })
                    DropdownMenuItem(text = { Text("Report") }, leadingIcon = { Icon(Icons.Default.Flag, null) }, onClick = {
                        val send = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:rykocollection@gmail.com")).apply {
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "RykoTube video report")
                            putExtra(android.content.Intent.EXTRA_TEXT, "Please review this video:\n${video.title}\n${video.url}")
                        }
                        runCatching { context.startActivity(send) }
                        menuExpanded = false
                    })
                }
            }
        }
    }
}

@Composable
private fun TubePlayer(video: TubeVideo, recommendations: List<TubeVideo>, shortQueue: List<TubeVideo>, onClose: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(video.id) {
        val youtubeId = youtubeVideoId(video.url)
        if (youtubeId != null) {
            val intent = android.content.Intent(context, TubePlayerActivity::class.java).apply {
                putExtra(TubePlayerActivity.EXTRA_VIDEO_ID, youtubeId)
                putExtra(TubePlayerActivity.EXTRA_TITLE, video.title)
                putExtra(TubePlayerActivity.EXTRA_CREATOR, video.creator)
                putExtra(TubePlayerActivity.EXTRA_THUMB, video.thumb)
                putExtra(TubePlayerActivity.EXTRA_AVATAR, video.creatorAvatar)
                val related = recommendations.filter { it.id != video.id && !it.short && youtubeVideoId(it.url) != null }.take(24)
                putStringArrayListExtra(TubePlayerActivity.EXTRA_RECOMMEND_IDS, ArrayList(related.mapNotNull { youtubeVideoId(it.url) }))
                putStringArrayListExtra(TubePlayerActivity.EXTRA_RECOMMEND_TITLES, ArrayList(related.map { it.title }))
                putStringArrayListExtra(TubePlayerActivity.EXTRA_RECOMMEND_CREATORS, ArrayList(related.map { it.creator }))
                putStringArrayListExtra(TubePlayerActivity.EXTRA_RECOMMEND_THUMBS, ArrayList(related.map { it.thumb }))
                putExtra(TubePlayerActivity.EXTRA_IS_SHORT, video.short)
                if (video.short) {
                    val queue = shortQueue.filter { youtubeVideoId(it.url) != null }
                    putStringArrayListExtra(TubePlayerActivity.EXTRA_SHORT_QUEUE, ArrayList(queue.map { youtubeVideoId(it.url).orEmpty() }))
                    putStringArrayListExtra(TubePlayerActivity.EXTRA_SHORT_TITLES, ArrayList(queue.map { it.title }))
                    putStringArrayListExtra(TubePlayerActivity.EXTRA_SHORT_CREATORS, ArrayList(queue.map { it.creator }))
                    putExtra(TubePlayerActivity.EXTRA_SHORT_INDEX, queue.indexOfFirst { it.id == video.id }.coerceAtLeast(0))
                }
            }
            context.startActivity(intent)
        } else {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(video.url))
            runCatching { context.startActivity(intent) }
        }
        onClose()
    }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color(0xFFA99BFF))
    }
}
