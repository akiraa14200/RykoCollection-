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
