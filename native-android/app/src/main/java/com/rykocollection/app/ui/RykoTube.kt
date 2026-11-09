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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
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

private val tubeHttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { HttpClient(Android) }

private suspend fun fetchTubePage(page: Int): List<TubeVideo> = withContext(Dispatchers.IO) {
    val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY.ifBlank { TUBE_PUBLIC_KEY_FALLBACK }
    require(baseUrl.startsWith("https://") && baseUrl.contains(".supabase.co")) { "RykoTube has an invalid Supabase project URL." }
    require(key.isNotBlank()) { "RykoTube is missing its Supabase publishable key." }
    val from = (page - 1) * TUBE_PAGE_SIZE
    val to = from + TUBE_PAGE_SIZE - 1
    // Keep this projection aligned with the proven web getRelatedVideos() query.
    // related_videos does not contain title_name, title_format, or title_cover.
    val select = "id,title_id,legacy_title_id,video_url,platform,video_title,creator_name,creator_avatar_url,action_type,action_url,sort_order,created_at,source_id,titles!related_videos_title_id_fkey(name,media_type,cover_url)"
    val url = "$baseUrl/rest/v1/related_videos?select=$select&order=sort_order.asc,created_at.desc&offset=$from&limit=$TUBE_PAGE_SIZE"
    val response = tubeHttpClient.get(url) {
            header("apikey", key)
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
}

private suspend fun fetchTubeSources(): List<TubeSource> = withContext(Dispatchers.IO) {
    val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY.ifBlank { TUBE_PUBLIC_KEY_FALLBACK }
    val response = tubeHttpClient.get("$baseUrl/rest/v1/video_discovery_sources?select=id,source_type,label&enabled=eq.true&order=label.asc") {
            header("apikey", key)
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
}


/**
 * Entirely on-device related ranking. It preserves the feed's source order for the
 * main page; this score is only used for the "Up next" list around a playing video.
 * Metadata fallbacks keep recommendations useful when a video has no linked title.
 */
private fun rankRelatedVideos(
    current: TubeVideo,
    candidates: List<TubeVideo>,
    context: android.content.Context
): List<TubeVideo> {
    val prefs = context.getSharedPreferences("ryko_tube_saved", android.content.Context.MODE_PRIVATE)
    val saved = prefs.getStringSet("ids", emptySet()).orEmpty()
    val likedIds = candidates.filter { prefs.getBoolean("like_" + it.id, false) }.mapTo(mutableSetOf()) { it.id }
    val currentWords = current.titleName.lowercase(Locale.ROOT)
        .split(Regex("[^a-z0-9]+")).filter { it.length > 2 }.toSet()
    val seenCreators = mutableSetOf<String>()
    return candidates.asSequence()
        .filter { it.id != current.id && youtubeVideoId(it.url) != null }
        .map { candidate ->
            val candidateWords = candidate.titleName.lowercase(Locale.ROOT)
                .split(Regex("[^a-z0-9]+")).filter { it.length > 2 }.toSet()
            val sharedWords = currentWords.intersect(candidateWords).size
            var score = 0
            if (current.titleName.isNotBlank() && candidate.titleName.equals(current.titleName, true)) score += 1200
            score += sharedWords * 90
            if (current.titleFormat.isNotBlank() && candidate.titleFormat.equals(current.titleFormat, true)) score += 110
            if (current.sourceId.isNotBlank() && candidate.sourceId == current.sourceId) score += 55
            if (current.creator.isNotBlank() && candidate.creator.equals(current.creator, true)) score += 45
            if (current.short == candidate.short) score += 15
            if (candidate.id in saved || candidate.id in likedIds) score += 35
            candidate to score
        }
        .sortedWith(compareByDescending<Pair<TubeVideo, Int>> { it.second }.thenBy { it.first.id })
        .map { it.first }
        .toList()
        .let { ranked ->
            // Light creator diversification without disturbing the strongest relevance matches.
            val diversified = mutableListOf<TubeVideo>()
            val deferred = mutableListOf<TubeVideo>()
            ranked.forEach { video ->
                val creatorKey = video.creator.lowercase(Locale.ROOT)
                if (creatorKey.isBlank() || seenCreators.add(creatorKey)) diversified.add(video)
                else deferred.add(video)
            }
            (diversified + deferred).take(24)
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
    val tubeHazeState = LocalRykoHazeState.current
    var channelsExpanded by remember { mutableStateOf(true) }
    var playlistsExpanded by remember { mutableStateOf(true) }
    var sourceError by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var catalogueResults by remember { mutableStateOf<List<AniListMedia>>(emptyList()) }
    var catalogueLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val searchFocus = remember { FocusRequester() }
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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (selected != null) {
            TubePlayer(selected!!, rankRelatedVideos(selected!!, filtered, context), filtered.filter { it.short }) { selected = null }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().hazeSource(tubeHazeState ?: error("RykoTube HazeState is missing")).background(Color.Black),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 140.dp, bottom = 144.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                                if (sources.isNotEmpty()) {
                                    item(key = "tube-discovery-sources") {
                                        val channels = sources.filterNot { it.type.contains("playlist", true) }
                                        val playlists = sources.filter { it.type.contains("playlist", true) }
                                        Column(Modifier.fillMaxWidth().rykoGlass(20.dp, Color(0x6618181B), 0.24f).padding(vertical = 5.dp)) {
                                            Row(Modifier.fillMaxWidth().clickable { channelsExpanded = !channelsExpanded }.padding(horizontal = 13.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text("Channels", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                                Text("${channels.size}", color = Color.Gray, fontSize = 11.sp)
                                                Icon(if (channelsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "Toggle channels", tint = Color.LightGray)
                                            }
                                            if (channelsExpanded) LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                item { FilterChip(selected = selectedSource.isBlank(), onClick = { selectedSource = "" }, label = { Text("All channels") }) }
                                                items(channels, key = { it.id }) { source -> FilterChip(selected = selectedSource == source.id, onClick = { selectedSource = if (selectedSource == source.id) "" else source.id }, label = { Text(source.label, maxLines = 1) }) }
                                            }
                                            if (playlists.isNotEmpty()) {
                                                Divider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 12.dp))
                                                Row(Modifier.fillMaxWidth().clickable { playlistsExpanded = !playlistsExpanded }.padding(horizontal = 13.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Text("Playlists", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                                    Text("${playlists.size}", color = Color.Gray, fontSize = 11.sp)
                                                    Icon(if (playlistsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "Toggle playlists", tint = Color.LightGray)
                                                }
                                                if (playlistsExpanded) LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    items(playlists, key = { it.id }) { source -> FilterChip(selected = selectedSource == source.id, onClick = { selectedSource = if (selectedSource == source.id) "" else source.id }, label = { Text(source.label, maxLines = 1) }) }
                                                }
                                            }
                                        }
                                    }
                                } else if (sourceError != null) {
                                    item(key = "tube-source-error") { Text("Channel and playlist filters unavailable", color = Color.Gray, fontSize = 11.sp) }
                                }
                                if (search.isBlank() && tubeHistory.isNotEmpty()) {
                                    item(key = "tube-search-history") {
                                        LazyRow(contentPadding = PaddingValues(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                            items(tubeHistory) { term -> AssistChip(onClick = { search = term }, label = { Text(term, maxLines = 1) }) }
                                        }
                                    }
                                }
                                if (search.trim().length >= 2) {
                                    item(key = "tube-anilist-results") {
                                        Column(Modifier.fillMaxWidth().rykoGlass(18.dp, Color(0x7718181B), 0.18f).padding(vertical = 8.dp)) {
                                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text("ANIME & MANGA ON ANILIST", color = Color(0xFFA99BFF), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                                if (catalogueLoading) CircularProgressIndicator(color = Color(0xFFA99BFF), modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            }
                                            if (catalogueResults.isEmpty() && !catalogueLoading) Text("No matching AniList titles.", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                items(catalogueResults, key = { "anilist-${it.id}" }) { media ->
                                                    Column(Modifier.width(112.dp).rykoGlass(14.dp)) {
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
                                }

                
                                when {
                                    loading && videos.isEmpty() -> item(key = "tube-loading") {
                                        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFFA99BFF)) }
                                    }
                                    error != null && videos.isEmpty() -> item(key = "tube-error") {
                                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("RykoTube couldn't load", color = Color.White, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.height(8.dp)); Text(error!!, color = Color.LightGray)
                                            Spacer(Modifier.height(14.dp)); Button(onClick = { refreshToken++ }) { Text("Retry") }
                                        }
                                    }
                                    filtered.isEmpty() -> item(key = "tube-empty") {
                                        Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                                            Text(if (videos.isEmpty()) "No videos are published yet." else "No matching videos. Try another search or filter.", color = Color.LightGray)
                                        }
                                    }
                                    else -> {
                                        val regular = filtered.filterNot { it.short }
                                        val shorts = filtered.filter { it.short }
                                        if (selectedFormat == "Shorts") {
                                            shorts.chunked(8).forEachIndexed { rowIndex, shortBatch ->
                                                item(key = "shorts-filter-row-$rowIndex") {
                                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Text(if (rowIndex == 0) "Shorts" else "More Shorts", color = Color(0xFFA99BFF), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 4.dp)) {
                                                            items(shortBatch, key = { it.id }) { video -> TubeVideoCard(video) { selected = video } }
                                                        }
                                                    }
                                                }
                                            }
                                        } else if (selectedFormat == "All") {
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
                                                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 4.dp)) {
                                                                items(shortBatch, key = { it.id }) { video -> TubeVideoCard(video) { selected = video } }
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
                                                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(shortBatch, key = { it.id }) { video -> TubeVideoCard(video) { selected = video } } }
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            items(regular, key = { it.id }) { video -> TubeVideoCard(video) { selected = video } }
                                        }
                                        if (hasMore) {
                                            item(key = "load-more") {
                                                Button(onClick = { if (!loadingMore) { loadingMore = true; error = null } }, enabled = !loadingMore, modifier = Modifier.fillMaxWidth()) {
                                                    Text(if (loadingMore) "Loading…" else "Load more")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .background(Color.Transparent)
        ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp).rykoGlass(22.dp, Color(0x221B1B1F), 0.52f, 42.dp).padding(horizontal = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(142.dp)) {
                            Box(Modifier.size(31.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFFA99BFF)), contentAlignment = Alignment.Center) {
                                Text("R", color = Color(0xFF100D1D), fontWeight = FontWeight.Black, fontSize = 18.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("RykoTube", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        }
                        OutlinedTextField(
                            value = search, onValueChange = { search = it },
                            modifier = Modifier.weight(1f).focusRequester(searchFocus),
                            singleLine = true,
                            placeholder = { Text("Search videos, creators or titles", maxLines = 1, fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search videos") },
                            trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = ""; focusManager.clearFocus() }) { Icon(Icons.Default.Close, contentDescription = "Clear search", tint = Color.LightGray) } },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFA99BFF), unfocusedBorderColor = Color.Transparent,
                                focusedPlaceholderColor = Color.Gray, unfocusedPlaceholderColor = Color.Gray,
                                cursorColor = Color(0xFFA99BFF)
                            )
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).rykoGlass(18.dp, Color(0x2218181B), 0.42f, 36.dp).padding(horizontal = 5.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("All", "Videos", "Shorts").forEach { format ->
                            FilterChip(selected = selectedFormat == format, onClick = { selectedFormat = format }, label = { Text(format) })
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { refreshToken++ }) { Icon(Icons.Default.Refresh, "Refresh", tint = Color.White) }
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
    var saved by remember(video.id) {
        mutableStateOf(
            context.getSharedPreferences("ryko_saved_videos", android.content.Context.MODE_PRIVATE)
                .getStringSet("ids", emptySet()).orEmpty().contains(video.id)
        )
    }
    Column(
        (if (video.short) Modifier.width(170.dp) else Modifier.fillMaxWidth())
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF101012))
    ) {
        Box(
            Modifier.fillMaxWidth()
                .then(if (video.short) Modifier.height(300.dp) else Modifier.aspectRatio(16f / 9f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (video.thumb.isNotBlank()) {
                AsyncImage(
                    model = video.thumb,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFF252529)))
            }
            Box(Modifier.align(Alignment.Center).size(54.dp).clip(RoundedCornerShape(50)).background(Color(0x55000000)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, "Play video", Modifier.size(38.dp), tint = Color.White)
            }
            if (video.short) {
                Text("SHORT", Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(5.dp)).padding(horizontal = 7.dp, vertical = 4.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 13.dp, end = 7.dp, top = 12.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (video.creatorAvatar.isNotBlank()) {
                AsyncImage(
                    model = video.creatorAvatar,
                    contentDescription = "${video.creator} channel avatar",
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(50)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(50)).background(Color(0xFF29292E)), contentAlignment = Alignment.Center) {
                    Text(video.creator.firstOrNull()?.uppercase() ?: "R", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
            Column(
                Modifier.weight(1f).padding(start = 11.dp, end = 5.dp).clickable(onClick = onClick),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(video.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 21.sp)
                Text(video.creator.ifBlank { video.platform.ifBlank { "RykoCollection" } }, color = Color(0xFFB7B7BC), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val details = listOfNotNull(
                    video.titleName.takeIf { it.isNotBlank() },
                    video.platform.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }
                ).distinct().joinToString("  ·  ")
                if (details.isNotBlank()) Text(details, color = Color(0xFF8F8F96), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.MoreVert, "Video options", tint = Color.White)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(if (saved) "Remove from Library" else "Save to Library") },
                        leadingIcon = { Icon(if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null) },
                        onClick = {
                            val prefs = context.getSharedPreferences("ryko_saved_videos", android.content.Context.MODE_PRIVATE)
                            val ids = prefs.getStringSet("ids", emptySet()).orEmpty().toMutableSet()
                            saved = if (video.id in ids) { ids.remove(video.id); false } else { ids.add(video.id); true }
                            prefs.edit().putStringSet("ids", ids).apply()
                            Toast.makeText(context, if (saved) "Saved to Library" else "Removed from Library", Toast.LENGTH_SHORT).show()
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Default.Share, null) },
                        onClick = {
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, "${video.title}\n${video.url}")
                            }
                            context.startActivity(android.content.Intent.createChooser(send, "Share video"))
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Download") },
                        leadingIcon = { Icon(Icons.Default.Download, null) },
                        onClick = {
                            val path = runCatching { android.net.Uri.parse(video.url).lastPathSegment.orEmpty().lowercase(Locale.ROOT) }.getOrDefault("")
                            val directFile = path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".m4v") || path.endsWith(".mov")
                            if (directFile) {
                                runCatching {
                                    val request = android.app.DownloadManager.Request(android.net.Uri.parse(video.url))
                                        .setTitle(video.title)
                                        .setDescription("Downloading from RykoTube")
                                        .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                        .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "${video.title.replace(Regex("[^A-Za-z0-9._-]"), "_").take(70)}.${path.substringAfterLast('.')}")
                                    val manager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                    manager.enqueue(request)
                                    Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
                                }.onFailure {
                                    Toast.makeText(context, "Could not start this download", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(context, "This source does not provide a direct downloadable video file.", Toast.LENGTH_LONG).show()
                            }
                            menuExpanded = false
                        }
                    )
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


@Composable
fun NativeLibraryVideos(
    refreshToken: Int = 0,
    searchQuery: String = "",
    formatFilter: String = "All",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ryko_saved_videos", android.content.Context.MODE_PRIVATE) }
    var savedIds by remember {
        mutableStateOf(prefs.getStringSet("ids", emptySet()).orEmpty().toSet())
    }
    var videos by remember { mutableStateOf<List<TubeVideo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<TubeVideo?>(null) }

    LaunchedEffect(refreshToken) {
        savedIds = prefs.getStringSet("ids", emptySet()).orEmpty().toSet()
    }

    LaunchedEffect(savedIds) {
        if (savedIds.isEmpty()) {
            videos = emptyList()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        error = null
        try {
            val found = linkedMapOf<String, TubeVideo>()
            TubeFeedCache.videos.filter { it.id in savedIds }.forEach { found[it.id] = it }
            var page = 1
            while (found.size < savedIds.size && page <= 10) {
                val batch = fetchTubePage(page)
                if (batch.isEmpty()) break
                batch.filter { it.id in savedIds }.forEach { found[it.id] = it }
                if (batch.size < TUBE_PAGE_SIZE) break
                page++
            }
            videos = found.values.toList()
            if (videos.isEmpty() && savedIds.isNotEmpty()) {
                error = "Saved videos could not be found in the current feed."
            }
        } catch (t: Throwable) {
            error = t.message ?: "Saved videos could not be loaded."
        } finally {
            loading = false
        }
    }

    selected?.let { video ->
        TubePlayer(video, videos, videos.filter { it.short }, onClose = { selected = null })
        return
    }

    val visibleVideos = videos.filter { video ->
        val matchesSearch = searchQuery.isBlank() ||
            video.title.contains(searchQuery, ignoreCase = true) ||
            video.creator.contains(searchQuery, ignoreCase = true) ||
            video.titleName.contains(searchQuery, ignoreCase = true)
        val matchesFormat = formatFilter == "All" ||
            (formatFilter == "Shorts" && video.short) ||
            (formatFilter == "Videos" && !video.short)
        matchesSearch && matchesFormat
    }

    if (loading && videos.isEmpty()) {
        Box(modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFA99BFF))
        }
    } else if (videos.isEmpty()) {
        Column(
            modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 42.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Icon(Icons.Default.BookmarkBorder, null, tint = Color(0xFFA99BFF), modifier = Modifier.size(38.dp))
            Text("No saved videos yet", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(error ?: "Save videos or Shorts from RykoTube and they will appear here.", color = Color(0xFFAAA8B5), fontSize = 12.sp)
        }
    } else {
        LazyColumn(
            modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Saved videos", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${visibleVideos.size} shown · ${videos.size} saved", color = Color(0xFFAAA8B5), fontSize = 11.sp)
                }
            }
            items(visibleVideos, key = { it.id }) { video -> TubeVideoCard(video) { selected = video } }
        }
    }
}
