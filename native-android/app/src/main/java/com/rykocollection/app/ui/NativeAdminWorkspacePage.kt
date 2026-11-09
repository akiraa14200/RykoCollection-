package com.rykocollection.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

private val AdminBg = Color(0xFF000000)
private val AdminPanel = Color(0xFF0C0C0C)
private val AdminCard = Color(0xFF101010)
private val AdminMuted = Color(0xFFA1A1AA)
private val AdminAccent = Color(0xFF9B8CFF)
private val AdminLine = Color(0x24FFFFFF)
private val AdminDanger = Color(0xFFFF8D9A)

private data class AdminTab(val id: String, val label: String)
private val adminTabs = listOf(
    AdminTab("overview", "Overview"), AdminTab("catalogue", "Catalogue"),
    AdminTab("characters", "Characters"), AdminTab("videos", "Related videos"),
    AdminTab("moderation", "Moderation"), AdminTab("reports", "Reports"),
    AdminTab("settings", "Website settings"), AdminTab("storage", "Storage")
)

private fun JsonObject.str(key: String, fallback: String = ""): String =
    this[key]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() } ?: fallback
private fun JsonObject.int(key: String): Int = str(key).toIntOrNull() ?: 0
private fun JsonObject.arrayText(key: String): String = (this[key] as? JsonArray)?.joinToString(", ") { it.jsonPrimitive.content } ?: ""
private fun jsonString(value: String): JsonPrimitive = JsonPrimitive(value)

@Composable
internal fun NativeAdminWorkspacePage(
    client: SupabaseClient,
    email: String,
    onBack: () -> Unit,
    onRefreshAccess: () -> Unit,
    checkingAccess: Boolean
) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("overview") }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var titles by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var characters by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var videos by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var pending by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var reports by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var videoReports by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var socialLinks by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var storage by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var titleName by remember { mutableStateOf("") }
    var titleType by remember { mutableStateOf("Manga") }
    var titleStatus by remember { mutableStateOf("Ongoing") }
    var titleCover by remember { mutableStateOf("") }
    var titleSynopsis by remember { mutableStateOf("") }
    var titleBanner by remember { mutableStateOf("") }
    var titleAltNames by remember { mutableStateOf("") }
    var titleGenres by remember { mutableStateOf("") }
    var titleThemes by remember { mutableStateOf("") }
    var titleYear by remember { mutableStateOf("") }
    var titleRating by remember { mutableStateOf("") }
    var titleChapters by remember { mutableStateOf("") }
    var titleEpisodes by remember { mutableStateOf("") }
    var titleAuthor by remember { mutableStateOf("") }
    var titleArtist by remember { mutableStateOf("") }
    var titleStudio by remember { mutableStateOf("") }
    var titleCountry by remember { mutableStateOf("") }
    var titleSource by remember { mutableStateOf("") }
    var titleTrailer by remember { mutableStateOf("") }
    var titleReadUrl by remember { mutableStateOf("") }
    var titleFeatured by remember { mutableStateOf(false) }
    var titleTrending by remember { mutableStateOf(false) }
    var characterEditingId by remember { mutableStateOf<String?>(null) }
    var videoEditingId by remember { mutableStateOf<String?>(null) }
    var characterSearch by remember { mutableStateOf("") }
    var videoSearch by remember { mutableStateOf("") }
    var creatorAvatar by remember { mutableStateOf("") }
    var videoSortOrder by remember { mutableStateOf("0") }
    var videoActionType by remember { mutableStateOf("watch") }
    var videoActionUrl by remember { mutableStateOf("") }
    var aniSyncBusy by remember { mutableStateOf(false) }
    var aniSyncProgress by remember { mutableStateOf<JsonObject?>(null) }
    var editingTitleId by remember { mutableStateOf<String?>(null) }
    var characterTitle by remember { mutableStateOf("") }
    var characterName by remember { mutableStateOf("") }
    var characterRole by remember { mutableStateOf("Main") }
    var characterImage by remember { mutableStateOf("") }
    var characterBio by remember { mutableStateOf("") }
    var videoTitleId by remember { mutableStateOf("") }
    var videoUrl by remember { mutableStateOf("") }
    var videoPlatform by remember { mutableStateOf("youtube") }
    var videoTitle by remember { mutableStateOf("") }
    var creatorName by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }

    suspend fun loadAll() {
        loading = true
        error = null
        try {
            titles = client.postgrest["titles"].select { order("name", Order.ASCENDING); limit(5000) }.decodeList<JsonObject>()
            characters = client.postgrest["characters"].select { order("created_at", Order.DESCENDING); limit(300) }.decodeList<JsonObject>()
            videos = client.postgrest["related_videos"].select { order("sort_order", Order.ASCENDING); limit(300) }.decodeList<JsonObject>()
            pending = client.postgrest["community_comments"].select {
                filter { eq("moderation_status", "pending") }
                order("created_at", Order.DESCENDING); limit(100)
            }.decodeList<JsonObject>()
            reports = client.postgrest["community_comment_reports"].select {
                order("created_at", Order.DESCENDING); limit(100)
            }.decodeList<JsonObject>()
            videoReports = runCatching {
                client.postgrest["video_reports"].select { order("created_at", Order.DESCENDING); limit(100) }.decodeList<JsonObject>()
            }.getOrDefault(emptyList())
            val settings = client.postgrest["site_settings"].select {
                filter { eq("key", "social_links") }
            }.decodeList<JsonObject>().firstOrNull()
            val links = settings?.get("value") as? JsonArray
            socialLinks = links?.mapNotNull { item ->
                val obj = item as? JsonObject ?: return@mapNotNull null
                obj.str("platform", obj.str("label")) to obj.str("url")
            } ?: emptyList()
            storage = runCatching { client.postgrest.rpc("admin_storage_usage").decodeList<JsonObject>() }.getOrDefault(storage)
        } catch (t: Throwable) {
            error = t.message ?: "Could not load workspace data."
        } finally { loading = false }
    }

    suspend fun loadStorage() {
        loading = true
        error = null
        try {
            storage = client.postgrest.rpc("admin_storage_usage").decodeList<JsonObject>()
        } catch (t: Throwable) {
            error = t.message ?: "Storage usage could not be loaded."
        } finally { loading = false }
    }

    LaunchedEffect(Unit) { loadAll() }
    LaunchedEffect(tab) { if (tab == "storage") loadStorage() }

    Column(Modifier.fillMaxSize().background(AdminBg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Icon(Icons.Outlined.ArrowBack, null, tint = AdminAccent, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("Account", color = Color.White, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("RYKOCOLLECTION", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onRefreshAccess, enabled = !checkingAccess, contentPadding = PaddingValues(horizontal = 4.dp)) {
                if (checkingAccess) CircularProgressIndicator(Modifier.size(14.dp), color = AdminAccent, strokeWidth = 2.dp)
                else Text("Recheck", color = AdminMuted, fontSize = 10.sp)
            }
        }
        HorizontalDivider(color = AdminLine)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 13.dp)) {
                Text("RYKOCOLLECTION · ADMIN", color = AdminAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                Text("Control workspace.", color = Color(0xFFF7F7FB), fontSize = 30.sp, lineHeight = 35.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.2).sp)
                Text("Manage the catalogue, community and website settings.", color = AdminMuted, fontSize = 11.sp, lineHeight = 17.sp)
            }
            Row(
                Modifier.fillMaxWidth().background(AdminPanel, RoundedCornerShape(13.dp)).padding(13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.VerifiedUser, null, tint = AdminAccent, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("SIGNED IN AS", color = AdminMuted, fontSize = 8.sp, letterSpacing = 1.2.sp)
                    Text(email.ifBlank { "Administrator" }, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("ADMIN", color = AdminAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("WORKSPACE", color = AdminMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    adminTabs.forEach { item ->
                        val active = tab == item.id
                        Text(
                            item.label,
                            modifier = Modifier.background(if (active) Color(0xFF211C36) else AdminPanel, RoundedCornerShape(9.dp))
                                .clickable { tab = item.id }.padding(horizontal = 13.dp, vertical = 10.dp),
                            color = if (active) AdminAccent else AdminMuted,
                            fontSize = 10.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
                OutlinedButton(
                    onClick = { scope.launch { loadAll(); if (tab == "storage") loadStorage() } },
                    enabled = !loading,
                    contentPadding = PaddingValues(horizontal = 11.dp, vertical = 2.dp),
                    modifier = Modifier.height(34.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AdminLine),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AdminMuted)
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(13.dp), color = AdminAccent, strokeWidth = 2.dp)
                    else { Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(5.dp)); Text("Refresh data", fontSize = 10.sp) }
                }
            }
            error?.let { AdminNotice(it, true) }
            notice?.let { AdminNotice(it, false) { notice = null } }
            if (loading && titles.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(35.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AdminAccent)
                }
            } else when (tab) {
                "overview" -> {
                    AdminSectionHeading("LIVE DATABASE", "Dashboard overview", "Current counts from the shared website database.")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AdminStat("Catalogue entries", titles.size.toString(), Modifier.weight(1f))
                            AdminStat("Character profiles", characters.size.toString(), Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AdminStat("Related videos", videos.size.toString(), Modifier.weight(1f))
                            AdminStat("Pending moderation", pending.size.toString(), Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AdminStat("Community reports", (reports.size + videoReports.size).toString(), Modifier.weight(1f))
                            AdminStat("Stored files", storage.sumOf { it.int("file_count") }.toString(), Modifier.weight(1f))
                        }
                    }
                    AdminPanelCard("Quick actions") {
                        AdminActionButton("Add catalogue entry", Icons.Outlined.Add) { tab = "catalogue"; editingTitleId = null }
                        AdminActionButton("Add related video", Icons.Outlined.PlayCircle) { tab = "videos" }
                        AdminActionButton("Review comments", Icons.Outlined.Shield) { tab = "moderation" }
                    }
                    Text("Admin membership is verified against admin_users. Supabase row-level security remains responsible for protecting database operations.", color = AdminMuted, fontSize = 9.sp, lineHeight = 14.sp)
                }
                "catalogue" -> {
                    AdminSectionHeading("PUBLISHING", if (editingTitleId == null) "Catalogue manager" else "Edit catalogue entry", "Add, update, feature or remove entries shared with the website.")
                    AdminPanelCard(if (editingTitleId == null) "Add catalogue entry" else "Update catalogue entry") {
                        AdminInput("Title name *", titleName) { titleName = it }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AdminSelect("Media type", titleType, listOf("Manga","Manhwa","Manhua","Anime","Movie","TV Series","Light Novel","Web Novel","Video"), Modifier.weight(1f)) { titleType = it }
                            AdminSelect("Status", titleStatus, listOf("Airing","Ongoing","Completed","Hiatus","Upcoming","Cancelled"), Modifier.weight(1f)) { titleStatus = it }
                        }
                        AdminInput("Cover image URL", titleCover) { titleCover = it }
                        AdminInput("Banner image URL", titleBanner) { titleBanner = it }
                        AdminInput("Alternative names · comma separated", titleAltNames) { titleAltNames = it }
                        AdminInput("Genres · comma separated", titleGenres) { titleGenres = it }
                        AdminInput("Themes · comma separated", titleThemes) { titleThemes = it }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { AdminInput("Release year", titleYear) { titleYear = it }; AdminInput("Rating (0–10)", titleRating) { titleRating = it } }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { AdminInput("Chapters", titleChapters) { titleChapters = it }; AdminInput("Episodes", titleEpisodes) { titleEpisodes = it } }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { AdminInput("Author", titleAuthor) { titleAuthor = it }; AdminInput("Artist", titleArtist) { titleArtist = it } }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { AdminInput("Studio", titleStudio) { titleStudio = it }; AdminInput("Country", titleCountry) { titleCountry = it } }
                        AdminInput("Source URL", titleSource) { titleSource = it }
                        AdminInput("Trailer URL", titleTrailer) { titleTrailer = it }
                        AdminInput("Read / Watch URL", titleReadUrl) { titleReadUrl = it }
                        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(titleFeatured, { titleFeatured = it }); Text("Featured", color = AdminMuted, fontSize = 10.sp); Checkbox(titleTrending, { titleTrending = it }); Text("Trending", color = AdminMuted, fontSize = 10.sp) }
                        AdminInput("Synopsis", titleSynopsis, minLines = 3) { titleSynopsis = it }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (titleName.isBlank()) { error = "Enter a title name."; return@Button }
                                    scope.launch {
                                        saving = true; error = null
                                        try {
                                            val payload = buildJsonObject {
                                                put("name", titleName.trim()); put("media_type", titleType); put("status", titleStatus)
                                                if (titleCover.isBlank()) put("cover_url", JsonNull) else put("cover_url", titleCover)
                                                if (titleSynopsis.isBlank()) put("synopsis", JsonNull) else put("synopsis", titleSynopsis)
                                                if (titleBanner.isBlank()) put("banner_url", JsonNull) else put("banner_url", titleBanner)
                                                put("alternative_names", JsonArray(titleAltNames.split(",").map { it.trim() }.filter { it.isNotBlank() }.map { JsonPrimitive(it) }))
                                                put("genres", JsonArray(titleGenres.split(",").map { it.trim() }.filter { it.isNotBlank() }.map(::JsonPrimitive)))
                                                put("themes", JsonArray(titleThemes.split(",").map { it.trim() }.filter { it.isNotBlank() }.map(::JsonPrimitive)))
                                                put("release_year", titleYear.toIntOrNull()?.let(::JsonPrimitive) ?: JsonNull)
                                                put("rating", titleRating.toDoubleOrNull()?.let(::JsonPrimitive) ?: JsonNull)
                                                put("chapters", titleChapters.toIntOrNull()?.let(::JsonPrimitive) ?: JsonNull)
                                                put("episodes", titleEpisodes.toIntOrNull()?.let(::JsonPrimitive) ?: JsonNull)
                                                put("author", titleAuthor.takeIf { it.isNotBlank() }?.let(::JsonPrimitive) ?: JsonNull)
                                                put("artist", titleArtist.takeIf { it.isNotBlank() }?.let(::JsonPrimitive) ?: JsonNull)
                                                put("studio", titleStudio.takeIf { it.isNotBlank() }?.let(::JsonPrimitive) ?: JsonNull)
                                                put("country", titleCountry.takeIf { it.isNotBlank() }?.let(::JsonPrimitive) ?: JsonNull)
                                                put("source_url", titleSource.takeIf { it.isNotBlank() }?.let(::JsonPrimitive) ?: JsonNull)
                                                put("trailer_url", titleTrailer.takeIf { it.isNotBlank() }?.let(::JsonPrimitive) ?: JsonNull)
                                                put("read_url", titleReadUrl.takeIf { it.isNotBlank() }?.let(::JsonPrimitive) ?: JsonNull)
                                                put("is_featured", titleFeatured); put("is_trending", titleTrending)
                                            }
                                            if (editingTitleId == null) client.postgrest["titles"].insert(payload)
                                            else client.postgrest["titles"].update(payload) { filter { eq("id", editingTitleId!!) } }
                                            titleName = ""; titleCover = ""; titleSynopsis = ""; editingTitleId = null
                                            loadAll(); notice = "Catalogue entry saved to the shared website."
                                        } catch (t: Throwable) { error = t.message ?: "Couldn't save catalogue entry." }
                                        finally { saving = false }
                                    }
                                },
                                enabled = !saving,
                                colors = ButtonDefaults.buttonColors(containerColor = AdminAccent, contentColor = Color(0xFF17151F))
                            ) { Text(if (saving) "Saving…" else if (editingTitleId == null) "Publish entry" else "Save changes", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                            if (editingTitleId != null) TextButton(onClick = { editingTitleId = null; titleName = ""; titleCover = ""; titleSynopsis = "" }) { Text("Cancel", color = AdminMuted) }
                        }
                    }
                    AdminInput("Find a title", search) { search = it }
                    AdminActionButton(if (aniSyncBusy) "AniList sync running…" else "Start / continue AniList full sync", Icons.Outlined.CloudDownload) {
                        if (!aniSyncBusy) scope.launch {
                            aniSyncBusy = true; error = null; var done = false; var batches = 0
                            try {
                                while (!done && batches < 8) {
                                    val response = client.functions.invoke("anilist-catalogue-sync", body = buildJsonObject { put("reset", batches == 0 && aniSyncProgress == null) })
                                    val result = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                                    if (result["ok"]?.jsonPrimitive?.booleanOrNull == false) error = result["error"]?.jsonPrimitive?.content ?: "AniList sync failed."
                                    aniSyncProgress = result["state"]?.jsonObject
                                    done = result["done"]?.jsonPrimitive?.booleanOrNull == true
                                    batches++
                                    if (error != null) break
                                }
                                if (error == null) notice = if (done) "AniList catalogue sync completed." else "Sync checkpoint saved. Tap Continue sync to process the next batch."
                                loadAll()
                            } catch (t: Throwable) { error = t.message ?: "AniList sync failed." }
                            finally { aniSyncBusy = false }
                        }
                    }
                    aniSyncProgress?.let { Text("Sync checkpoint: " + it.toString(), color = AdminMuted, fontSize = 9.sp) }
                    if (aniSyncBusy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = AdminAccent)
                    Text("Published entries · ${titles.size}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    titles.filter { it.str("name").contains(search, true) }.take(150).forEach { item ->
                        AdminRecord(item.str("name"), "${item.str("media_type")} · ${item.str("status")} · ${item.str("release_year", "Year unknown")}") {
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                TextButton(onClick = {
                                    editingTitleId = item.str("id"); titleName = item.str("name"); titleType = item.str("media_type", "Manga")
                                    titleStatus = item.str("status", "Ongoing"); titleCover = item.str("cover_url"); titleSynopsis = item.str("synopsis")
                                    titleBanner = item.str("banner_url"); titleAltNames = item.arrayText("alternative_names"); titleGenres = item.arrayText("genres"); titleThemes = item.arrayText("themes")
                                    titleYear = item.str("release_year"); titleRating = item.str("rating"); titleChapters = item.str("chapters"); titleEpisodes = item.str("episodes")
                                    titleAuthor = item.str("author"); titleArtist = item.str("artist"); titleStudio = item.str("studio"); titleCountry = item.str("country")
                                    titleSource = item.str("source_url"); titleTrailer = item.str("trailer_url"); titleReadUrl = item.str("read_url")
                                    titleFeatured = item.str("is_featured") == "true"; titleTrending = item.str("is_trending") == "true"
                                }) { Text("Edit", color = AdminAccent, fontSize = 10.sp) }
                                TextButton(onClick = {
                                    scope.launch { runCatching { client.postgrest["titles"].delete { filter { eq("id", item.str("id")) } }; loadAll(); notice = "Entry deleted." }.onFailure { error = it.message } }
                                }) { Text("Delete", color = AdminDanger, fontSize = 10.sp) }
                            }
                        }
                    }
                }
                "characters" -> {
                    AdminSectionHeading("CAST DATABASE", "Character profiles", "Create, edit, search and remove character profiles.")
                    AdminInput("Search characters", characterSearch) { characterSearch = it }
                    AdminPanelCard(if (characterEditingId == null) "Add character" else "Edit character") {
                        AdminSelectPairs("Related title", characterTitle, titles.map { it.str("id") to it.str("name") }, Modifier.fillMaxWidth()) { characterTitle = it }
                        AdminInput("Character name *", characterName) { characterName = it }
                        AdminInput("Role", characterRole) { characterRole = it }
                        AdminInput("Image URL", characterImage) { characterImage = it }
                        AdminInput("Biography", characterBio, minLines = 3) { characterBio = it }
                        Button(onClick = {
                            if (characterTitle.isBlank() || characterName.isBlank()) { error = "Choose a title and enter a character name."; return@Button }
                            scope.launch {
                                saving = true
                                try {
                                    val payload = buildJsonObject {
                                        put("title_id", characterTitle); put("name", characterName.trim())
                                        if (characterRole.isBlank()) put("role", JsonNull) else put("role", characterRole); if (characterImage.isBlank()) put("image_url", JsonNull) else put("image_url", characterImage)
                                        if (characterBio.isBlank()) put("description", JsonNull) else put("description", characterBio)
                                    }
                                    if (characterEditingId == null) client.postgrest["characters"].insert(payload)
                                    else client.postgrest["characters"].update(payload) { filter { eq("id", characterEditingId!!) } }
                                    characterName = ""; characterImage = ""; characterBio = ""; characterEditingId = null; loadAll(); notice = "Character saved."
                                } catch (t: Throwable) { error = t.message ?: "Couldn't add character." }
                                finally { saving = false }
                            }
                        }, enabled = !saving, colors = ButtonDefaults.buttonColors(containerColor = AdminAccent, contentColor = Color(0xFF17151F))) { Text("Add character", fontSize = 11.sp) }
                    }
                    characters.filter { it.str("name").contains(characterSearch, true) }.forEach { item ->
                        AdminRecord(item.str("name"), "${titles.firstOrNull { t -> t.str("id") == item.str("title_id") }?.str("name") ?: "Unknown title"} · ${item.str("role")}") {
                            TextButton(onClick = { characterEditingId = item.str("id"); characterTitle = item.str("title_id"); characterName = item.str("name"); characterRole = item.str("role"); characterImage = item.str("image_url"); characterBio = item.str("description") }) { Text("Edit", color = AdminAccent, fontSize = 10.sp) }
                            TextButton(onClick = {
                                scope.launch { runCatching { client.postgrest["characters"].delete { filter { eq("id", item.str("id")) } }; loadAll() }.onFailure { error = it.message } }
                            }) { Text("Delete", color = AdminDanger, fontSize = 10.sp) }
                        }
                    }
                }
                "videos" -> {
                    AdminSectionHeading("CURATED CONTENT", "Related videos", "Create, edit, search and remove video cards.")
                    AdminInput("Search videos", videoSearch) { videoSearch = it }
                    AdminPanelCard(if (videoEditingId == null) "Add related video" else "Edit related video") {
                        AdminSelectPairs("Related title (optional)", videoTitleId, listOf("" to "General video (no title)") + titles.map { it.str("id") to it.str("name") }, Modifier.fillMaxWidth()) { videoTitleId = it }
                        AdminSelect("Platform", videoPlatform, listOf("youtube","instagram","dailymotion","tiktok"), Modifier.fillMaxWidth()) { videoPlatform = it }
                        AdminInput("Video URL *", videoUrl) { videoUrl = it }
                        AdminInput("Video title", videoTitle) { videoTitle = it }
                        AdminInput("Creator / channel", creatorName) { creatorName = it }
                        AdminInput("Channel avatar URL", creatorAvatar) { creatorAvatar = it }
                        AdminInput("Display order", videoSortOrder) { videoSortOrder = it }
                        AdminSelect("Action type", videoActionType, listOf("watch", "read"), Modifier.fillMaxWidth()) { videoActionType = it }
                        AdminInput("Action URL", videoActionUrl) { videoActionUrl = it }
                        Button(onClick = {
                            if (!videoUrl.startsWith("https://")) { error = "Enter a valid HTTPS video URL."; return@Button }
                            scope.launch {
                                saving = true
                                try {
                                    val payload = buildJsonObject {
                                        if (videoTitleId.isNotBlank()) put("title_id", videoTitleId) else put("title_id", JsonNull)
                                        put("video_url", videoUrl.trim()); put("platform", videoPlatform)
                                        if (videoTitle.isBlank()) put("video_title", JsonNull) else put("video_title", videoTitle); if (creatorName.isBlank()) put("creator_name", JsonNull) else put("creator_name", creatorName)
                                        if (creatorAvatar.isBlank()) put("creator_avatar_url", JsonNull) else put("creator_avatar_url", creatorAvatar)
                                        put("action_type", videoActionType); put("action_url", videoActionUrl.ifBlank { videoUrl.trim() }); put("sort_order", videoSortOrder.toIntOrNull() ?: 0)
                                    }
                                    if (videoEditingId == null) client.postgrest["related_videos"].insert(payload)
                                    else client.postgrest["related_videos"].update(payload) { filter { eq("id", videoEditingId!!) } }
                                    videoUrl = ""; videoTitle = ""; creatorName = ""; creatorAvatar = ""; videoSortOrder = "0"; videoActionUrl = ""; videoEditingId = null; loadAll(); notice = "Related video saved."
                                } catch (t: Throwable) { error = t.message ?: "Couldn't add video." }
                                finally { saving = false }
                            }
                        }, enabled = !saving, colors = ButtonDefaults.buttonColors(containerColor = AdminAccent, contentColor = Color(0xFF17151F))) { Text("Add related video", fontSize = 11.sp) }
                    }
                    videos.filter { it.str("video_title", it.str("video_url")).contains(videoSearch, true) }.forEach { item ->
                        AdminRecord(item.str("video_title", item.str("video_url")), "${item.str("platform")} · ${item.str("creator_name", "Creator not set")}") {
                            TextButton(onClick = { videoEditingId = item.str("id"); videoTitleId = item.str("title_id"); videoUrl = item.str("video_url"); videoPlatform = item.str("platform", "youtube"); videoTitle = item.str("video_title"); creatorName = item.str("creator_name"); creatorAvatar = item.str("creator_avatar_url"); videoSortOrder = item.str("sort_order", "0"); videoActionType = item.str("action_type", "watch"); videoActionUrl = item.str("action_url") }) { Text("Edit", color = AdminAccent, fontSize = 10.sp) }
                            TextButton(onClick = {
                                scope.launch { runCatching { client.postgrest["related_videos"].delete { filter { eq("id", item.str("id")) } }; loadAll() }.onFailure { error = it.message } }
                            }) { Text("Remove", color = AdminDanger, fontSize = 10.sp) }
                        }
                    }
                }
                "moderation" -> {
                    AdminSectionHeading("COMMUNITY SAFETY", "Pending moderation", "Approve or reject comments awaiting review.")
                    if (pending.isEmpty()) AdminEmpty("No pending comments right now.")
                    pending.forEach { item ->
                        AdminPanelCard(item.str("kind", "Community post")) {
                            Text(item.str("body", "[Image / GIF attachment]"), color = Color.White, fontSize = 12.sp, lineHeight = 18.sp)
                            Text(item.str("created_at"), color = AdminMuted, fontSize = 9.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { scope.launch { moderateComment(client, item.str("id"), "approved", { error = it }, { loadAll() }) } }, colors = ButtonDefaults.buttonColors(containerColor = AdminAccent, contentColor = Color(0xFF17151F))) { Text("Approve", fontSize = 10.sp) }
                                OutlinedButton(onClick = { scope.launch { moderateComment(client, item.str("id"), "rejected", { error = it }, { loadAll() }) } }) { Text("Reject", color = AdminDanger, fontSize = 10.sp) }
                            }
                        }
                    }
                }
                "reports" -> {
                    AdminSectionHeading("READER FEEDBACK", "Community reports", "Dismissing a report does not delete the reported comment.")
                    reports.forEach { item ->
                        AdminPanelCard(item.str("reason", "Report")) {
                            Text(item.str("details", "No extra details provided."), color = AdminMuted, fontSize = 11.sp)
                            Text("Comment ID: ${item.str("comment_id")}", color = AdminMuted, fontSize = 9.sp)
                            Text("Reporter: " + item.str("user_id", item.str("reporter_id", "Unknown")), color = AdminMuted, fontSize = 9.sp)
                            Text("Submitted: " + item.str("created_at"), color = AdminMuted, fontSize = 9.sp)
                            TextButton(onClick = {
                                scope.launch { runCatching { client.postgrest["community_comment_reports"].delete { filter { eq("id", item.str("id")) } }; loadAll(); notice = "Report dismissed." }.onFailure { error = it.message } }
                            }) { Text("Dismiss report", color = AdminDanger, fontSize = 10.sp) }
                        }
                    }
                    AdminSectionHeading("VIDEO FEEDBACK", "Video reports", "Reports submitted from the video-card menu.")
                    if (videoReports.isEmpty()) AdminEmpty("No video reports found, or the video_reports table is not available.")
                    videoReports.forEach { item ->
                        AdminPanelCard(item.str("reason", "Video report")) {
                            Text(item.str("details", "No extra details provided."), color = AdminMuted, fontSize = 11.sp)
                            Text(item.str("video_title", item.str("video_url")), color = AdminMuted, fontSize = 9.sp)
                            TextButton(onClick = {
                                scope.launch { runCatching { client.postgrest["video_reports"].delete { filter { eq("id", item.str("id")) } }; loadAll(); notice = "Video report dismissed." }.onFailure { error = it.message } }
                            }) { Text("Dismiss report", color = AdminDanger, fontSize = 10.sp) }
                        }
                    }
                }
                "settings" -> {
                    AdminSectionHeading("SITE CONFIGURATION", "Website settings", "Update social and contact links used by the live website footer.")
                    AdminPanelCard("Social links") {
                        socialLinks.forEachIndexed { index, pair ->
                            AdminInput("Platform", pair.first) { value -> socialLinks = socialLinks.toMutableList().also { it[index] = value to pair.second } }
                            AdminInput("URL", pair.second) { value -> socialLinks = socialLinks.toMutableList().also { it[index] = pair.first to value } }
                            TextButton(onClick = { socialLinks = socialLinks.filterIndexed { i, _ -> i != index } }) { Text("Remove link", color = AdminDanger, fontSize = 10.sp) }
                            HorizontalDivider(color = AdminLine)
                        }
                        AdminActionButton("Add link", Icons.Outlined.Add) { socialLinks = socialLinks + ("Website" to "https://") }
                        Button(onClick = {
                            scope.launch {
                                saving = true
                                try {
                                    val values = buildJsonArray {
                                        socialLinks.forEach { (platform, url) -> add(buildJsonObject { put("platform", platform); put("label", platform); put("url", url) }) }
                                    }
                                    client.postgrest["site_settings"].upsert(buildJsonObject {
                                        put("key", "social_links"); put("value", values)
                                    }) { onConflict = "key" }
                                    notice = "Social links saved to the live website footer."
                                } catch (t: Throwable) { error = t.message ?: "Couldn't save website settings." }
                                finally { saving = false }
                            }
                        }, enabled = !saving, colors = ButtonDefaults.buttonColors(containerColor = AdminAccent, contentColor = Color(0xFF17151F))) { Text("Save live website links", fontSize = 11.sp) }
                    }
                }
                "storage" -> {
                    AdminSectionHeading("FILE USAGE", "Storage overview", "Usage from the website's protected admin_storage_usage function.")
                    val bytes = storage.sumOf { it.str("bytes_used").toLongOrNull() ?: 0L }
                    val files = storage.sumOf { it.int("file_count") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AdminStat("Total files", files.toString(), Modifier.weight(1f))
                        AdminStat("Storage used", "${"%.2f".format(bytes / 1048576.0)} MB", Modifier.weight(1f))
                    }
                    storage.forEach { item -> AdminRecord(item.str("bucket_id"), "${item.int("file_count")} files · ${"%.2f".format((item.str("bytes_used").toLongOrNull() ?: 0L) / 1048576.0)} MB") {} }
                    if (storage.isEmpty() && !loading) AdminEmpty("No storage usage data returned.")
                    TextButton(onClick = { scope.launch { loadStorage() } }) { Text("Refresh storage", color = AdminAccent) }
                }
            }
            Spacer(Modifier.height(25.dp))
        }
    }
}

private suspend fun moderateComment(client: SupabaseClient, id: String, status: String, onError: (String) -> Unit, reload: suspend () -> Unit) {
    runCatching {
        client.postgrest["community_comments"].update(buildJsonObject { put("moderation_status", status) }) {
            filter { eq("id", id) }
        }
        reload()
    }.onFailure { onError(it.message ?: "Moderation update failed.") }
}

@Composable
private fun AdminSectionHeading(kicker: String, title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(kicker, color = AdminAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
        Text(description, color = AdminMuted, fontSize = 10.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun AdminStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(AdminCard, RoundedCornerShape(13.dp)).padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, color = AdminMuted, fontSize = 9.sp, lineHeight = 13.sp)
        Text(value, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AdminPanelCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(AdminPanel, RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun AdminInput(label: String, value: String, minLines: Int = 1, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
        label = { Text(label, fontSize = 11.sp) }, minLines = minLines, maxLines = if (minLines == 1) 1 else 5,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AdminAccent, unfocusedBorderColor = AdminLine,
            focusedLabelColor = AdminAccent, unfocusedLabelColor = AdminMuted,
            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
            cursorColor = AdminAccent
        ),
        shape = RoundedCornerShape(10.dp)
    )
}

@Composable
private fun AdminSelect(label: String, value: String, options: List<String>, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    AdminSelectPairs(label, value, options.map { it to it }, modifier, onSelect)
}

@Composable
private fun AdminSelectPairs(label: String, value: String, options: List<Pair<String, String>>, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, color = AdminMuted, fontSize = 9.sp)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AdminLine)) {
                Text(options.firstOrNull { it.first == value }?.second ?: value.ifBlank { "Select" }, color = Color.White, fontSize = 10.sp, maxLines = 1)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option -> DropdownMenuItem(text = { Text(option.second) }, onClick = { onSelect(option.first); expanded = false }) }
            }
        }
    }
}

@Composable
private fun AdminRecord(title: String, subtitle: String, actions: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().background(AdminCard, RoundedCornerShape(11.dp)).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(subtitle, color = AdminMuted, fontSize = 9.sp, maxLines = 2)
        }
        Row(content = actions)
    }
}

@Composable
private fun AdminActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 3.dp)) {
        Icon(icon, null, tint = AdminAccent, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = AdminAccent, fontSize = 11.sp)
    }
}

@Composable
private fun AdminNotice(message: String, isError: Boolean, onDismiss: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().background(if (isError) Color(0xFF241317) else Color(0xFF151327), RoundedCornerShape(10.dp)).padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(message, color = if (isError) AdminDanger else AdminAccent, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.weight(1f))
        if (onDismiss != null) TextButton(onClick = onDismiss) { Text("×", color = AdminMuted) }
    }
}

@Composable
private fun AdminEmpty(message: String) {
    Text(message, color = AdminMuted, fontSize = 10.sp, modifier = Modifier.fillMaxWidth().padding(18.dp))
}
