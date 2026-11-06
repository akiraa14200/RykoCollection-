package com.rykocollection.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rykocollection.app.data.AniListMedia
import com.rykocollection.app.data.AniListDetail
import com.rykocollection.app.data.AniListRepository

private enum class MainTab(val label: String) { HOME("Home"), BROWSE("Browse"), VIDEO("Video"), LIBRARY("Library"), ACCOUNT("Account") }
private enum class ExtraPage(val label: String) { TRENDING("Ryko Top Trending"), NEWS("Newsroom"), SETTINGS("Settings") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RykoApp() {
    var tab by remember { mutableStateOf(MainTab.HOME) }
    var extraPage by remember { mutableStateOf<ExtraPage?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var mangaMode by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(29.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFFA99BFF)), contentAlignment = Alignment.Center) {
                            Text("R", color = Color(0xFF100D1D), fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.width(9.dp))
                        Text("RYKOCOLLECTION", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.7.sp)
                    }
                },
                navigationIcon = {
                    if (selectedEntry != null) IconButton(onClick = { selectedEntry = null }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { selectedEntry = null; tab = MainTab.BROWSE; extraPage = null }) { Icon(Icons.Default.Search, contentDescription = "Search") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.Menu, contentDescription = "Ryko menu") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text(if (mangaMode) "Switch to Anime" else "Switch to Manga") }, onClick = { mangaMode = !mangaMode; menuOpen = false; selectedEntry = null })
                            DropdownMenuItem(text = { Text("Ryko Top Trending") }, onClick = { extraPage = ExtraPage.TRENDING; selectedEntry = null; menuOpen = false })
                            DropdownMenuItem(text = { Text("Newsroom") }, onClick = { extraPage = ExtraPage.NEWS; selectedEntry = null; menuOpen = false })
                            DropdownMenuItem(text = { Text("Settings") }, onClick = { extraPage = ExtraPage.SETTINGS; selectedEntry = null; menuOpen = false })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xE6000000), titleContentColor = Color(0xFFF7F7FB), actionIconContentColor = Color(0xFFE8E5FF))
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF080808), contentColor = Color(0xFFA99BFF)) {
                MainTab.entries.forEach { item ->
                    val icon = when (item) {
                        MainTab.HOME -> Icons.Default.Home
                        MainTab.BROWSE -> Icons.Default.Explore
                        MainTab.VIDEO -> Icons.Default.PlayCircle
                        MainTab.LIBRARY -> Icons.Default.BookmarkBorder
                        MainTab.ACCOUNT -> Icons.Default.Person
                    }
                    NavigationBarItem(selected = tab == item && extraPage == null && selectedEntry == null, onClick = { tab = item; extraPage = null; selectedEntry = null }, icon = { Icon(icon, contentDescription = item.label) }, label = { Text(item.label, fontSize = 10.sp) })
                }
            }
        },
        containerColor = Color.Black
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            when {
                selectedEntry != null -> EntryDetails(selectedEntry!!, mangaMode)
                extraPage == ExtraPage.TRENDING -> NativePlaceholder("Ryko Top Trending", "Entry ranking will use real community likes once account services are configured.")
                extraPage == ExtraPage.NEWS -> NativePlaceholder("Ryko Newsroom", "Anime and manga headlines, release updates and studio announcements.")
                extraPage == ExtraPage.SETTINGS -> NativePlaceholder("Settings", "Appearance, language, notifications and app preferences.")
                tab == MainTab.HOME -> NativeHome(mangaMode, onEntry = { selectedEntry = it })
                tab == MainTab.BROWSE -> BrowsePage(mangaMode, onEntry = { selectedEntry = it })
                tab == MainTab.VIDEO -> NativeRykoTube()
                tab == MainTab.LIBRARY -> NativePlaceholder("My Library", "Watching, plan-to-watch, completed and saved entries.")
                else -> NativePlaceholder("Account", "Sign in, profile, avatar, banner and account settings.")
            }
        }
    }
}

@Composable
private fun NativeHome(mangaMode: Boolean, onEntry: (Int) -> Unit) {
    var items by remember(mangaMode) { mutableStateOf<List<AniListMedia>>(emptyList()) }
    var loading by remember(mangaMode) { mutableStateOf(true) }
    var error by remember(mangaMode) { mutableStateOf<String?>(null) }
    LaunchedEffect(mangaMode) {
        loading = true; error = null
        val repository = AniListRepository()
        try { items = repository.trendingMedia(mangaMode) }
        catch (t: Throwable) { error = t.message ?: "Could not load the catalogue." }
        finally { repository.close(); loading = false }
    }
    LazyColumn(Modifier.fillMaxSize().background(Color.Black), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Column {
                Text(if (mangaMode) "DISCOVER MANGA" else "DISCOVER ANIME", color = Violet, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.height(10.dp))
                Text("Find your next favourite.", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                Spacer(Modifier.height(7.dp))
                Text("Explore trending stories from across the Ryko catalogue.", color = Muted, fontSize = 12.sp)
            }
        }
        item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Trending now", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Icon(Icons.Default.EmojiEvents, null, tint = Violet) } }
        if (loading) item { CircularProgressIndicator(color = Violet, modifier = Modifier.size(24.dp)) }
        error?.let { item { Text(it, color = Color(0xFFFF9C9C), fontSize = 12.sp) } }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(items, key = { it.id }) { NativeMediaCard(it) { onEntry(it.id) } } } }
    }
}

@Composable
private fun BrowsePage(mangaMode: Boolean, onEntry: (Int) -> Unit) {
    var search by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AniListMedia>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(mangaMode, search) {
        if (search.trim().length < 2) { results = emptyList(); error = null; return@LaunchedEffect }
        kotlinx.coroutines.delay(350)
        loading = true; error = null
        val repository = AniListRepository()
        try { results = repository.searchMedia(search, mangaMode) }
        catch (t: Throwable) { error = t.message ?: "Search failed." }
        finally { repository.close(); loading = false }
    }
    Column(Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 18.dp, vertical = 14.dp)) {
        Text(if (mangaMode) "EXPLORE MANGA" else "EXPLORE ANIME", color = Violet, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("Browse catalogue", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp), label = { Text("Search titles") }, singleLine = true)
        if (loading) CircularProgressIndicator(color = Violet, modifier = Modifier.padding(16.dp).size(24.dp))
        error?.let { Text(it, color = Color(0xFFFF9C9C), modifier = Modifier.padding(top = 12.dp)) }
        LazyColumn(contentPadding = PaddingValues(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(results, key = { it.id }) { media -> SearchResultRow(media) { onEntry(media.id) } }
        }
        if (search.length < 2) Text("Type at least two characters to search AniList.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun SearchResultRow(media: AniListMedia, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface).clickable(onClick = onClick).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(media.coverImage?.imageUrl, media.title.display, Modifier.size(62.dp, 82.dp).clip(RoundedCornerShape(8.dp)))
        Column(Modifier.padding(start = 12.dp)) {
            Text(media.title.display, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(media.format, media.seasonYear?.toString(), media.averageScore?.let { "$it%" }).joinToString(" · "), color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun EntryDetails(id: Int, mangaMode: Boolean) {
    var entry by remember(id) { mutableStateOf<AniListDetail?>(null) }
    var loading by remember(id) { mutableStateOf(true) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var selectedTab by remember(id) { mutableStateOf(0) }
    LaunchedEffect(id) {
        val repository = AniListRepository()
        try { entry = repository.getMedia(id); if (entry == null) error = "Entry not found." }
        catch (t: Throwable) { error = t.message ?: "Entry details could not be loaded." }
        finally { repository.close(); loading = false }
    }
    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Violet) }
        error != null -> NativePlaceholder("Entry unavailable", error!!)
        entry != null -> LazyColumn(Modifier.fillMaxSize().background(Color.Black), contentPadding = PaddingValues(bottom = 28.dp)) {
            item {
                AsyncImage(entry!!.bannerImage ?: entry!!.coverImage?.imageUrl, entry!!.title.display, Modifier.fillMaxWidth().height(190.dp))
                Column(Modifier.padding(18.dp)) {
                    Text(entry!!.title.display, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(listOfNotNull(entry!!.format, entry!!.seasonYear?.toString(), entry!!.averageScore?.let { "$it%" }).joinToString(" · "), color = Muted, fontSize = 12.sp)
                    Text(entry!!.genres.joinToString("  •  "), color = Violet, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
            item {
                TabRow(selectedTabIndex = selectedTab, containerColor = Color(0xFF090909), contentColor = Violet) {
                    listOf("Overview", "Characters", "Details").forEachIndexed { index, label -> Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(label) }) }
                }
            }
            when (selectedTab) {
                0 -> item { Text(entry!!.description?.replace(Regex("<[^>]*>"), "")?.replace("&mdash;", "—") ?: "No description available.", color = Color(0xFFCCCCD2), modifier = Modifier.padding(18.dp)) }
                1 -> {
                    val characters = entry!!.characters?.edges.orEmpty()
                    if (characters.isEmpty()) item { Text("Character information is not available.", color = Muted, modifier = Modifier.padding(18.dp)) }
                    items(characters) { edge ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(edge.node.image?.large ?: edge.node.image?.medium, edge.node.name.full, Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)))
                            Column(Modifier.padding(start = 12.dp)) { Text(edge.node.name.full, color = Color.White, fontWeight = FontWeight.SemiBold); Text(edge.role, color = Muted, fontSize = 11.sp) }
                        }
                    }
                }
                else -> item {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DetailLine("Type", entry!!.format)
                        DetailLine("Status", entry!!.status)
                        DetailLine("Year", entry!!.seasonYear?.toString())
                        DetailLine("Episodes", entry!!.episodes?.toString())
                        DetailLine("Chapters", entry!!.chapters?.toString())
                        DetailLine("Country", entry!!.countryOfOrigin)
                        DetailLine("Source", entry!!.source)
                        DetailLine("Studio", entry!!.studios?.nodes?.joinToString { it.name })
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontSize = 12.sp)
        Text(value ?: "—", color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun NativeMediaCard(media: AniListMedia, onClick: () -> Unit) {
    Card(Modifier.width(132.dp).clickable(onClick = onClick), shape = RoundedCornerShape(13.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
        Column {
            AsyncImage(media.coverImage?.imageUrl, media.title.display, Modifier.fillMaxWidth().height(178.dp).clip(RoundedCornerShape(topStart = 13.dp, topEnd = 13.dp)))
            Column(Modifier.padding(9.dp)) {
                Text(media.title.display, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Text(listOfNotNull(media.format, media.seasonYear?.toString()).joinToString(" · "), color = Muted, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun NativePlaceholder(title: String, subtitle: String) {
    Column(Modifier.fillMaxSize().background(Color.Black).padding(22.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(subtitle, color = Muted, fontSize = 13.sp)
    }
}

private val Violet = Color(0xFFA99BFF)
private val Muted = Color(0xFF9999A5)
private val Surface = Color(0xFF111111)
