package com.rykocollection.app.ui

import androidx.compose.foundation.background
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
import com.rykocollection.app.data.AniListRepository

private enum class MainTab(val label: String) {
    HOME("Home"), BROWSE("Browse"), VIDEO("Video"), LIBRARY("Library"), ACCOUNT("Account")
}
private enum class ExtraPage(val label: String) { TRENDING("Ryko Top Trending"), NEWS("Newsroom"), SETTINGS("Settings") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RykoApp() {
    var tab by remember { mutableStateOf(MainTab.HOME) }
    var extraPage by remember { mutableStateOf<ExtraPage?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var mangaMode by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(29.dp).clip(RoundedCornerShape(9.dp))
                                .background(Color(0xFFA99BFF)),
                            contentAlignment = Alignment.Center
                        ) { Text("R", color = Color(0xFF100D1D), fontWeight = FontWeight.Black) }
                        Spacer(Modifier.width(9.dp))
                        Text("RYKOCOLLECTION", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.7.sp)
                    }
                },
                actions = {
                    IconButton(onClick = { tab = MainTab.BROWSE; extraPage = null }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.Menu, contentDescription = "Ryko menu")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (mangaMode) "Switch to Anime" else "Switch to Manga") },
                                onClick = { mangaMode = !mangaMode; menuOpen = false }
                            )
                            DropdownMenuItem(text = { Text("Ryko Top Trending") }, onClick = {
                                extraPage = ExtraPage.TRENDING; menuOpen = false
                            })
                            DropdownMenuItem(text = { Text("Newsroom") }, onClick = {
                                extraPage = ExtraPage.NEWS; menuOpen = false
                            })
                            DropdownMenuItem(text = { Text("Settings") }, onClick = {
                                extraPage = ExtraPage.SETTINGS; menuOpen = false
                            })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xE6000000),
                    titleContentColor = Color(0xFFF7F7FB),
                    actionIconContentColor = Color(0xFFE8E5FF)
                )
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
                    NavigationBarItem(
                        selected = tab == item && extraPage == null,
                        onClick = { tab = item; extraPage = null },
                        icon = { Icon(icon, contentDescription = item.label) },
                        label = { Text(item.label, fontSize = 10.sp) }
                    )
                }
            }
        },
        containerColor = Color.Black
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            when (extraPage) {
                ExtraPage.TRENDING -> NativePlaceholder("Ryko Top Trending", "Community-ranked anime and manga entries.")
                ExtraPage.NEWS -> NativePlaceholder("Ryko Newsroom", "Anime and manga headlines, release updates and studio announcements.")
                ExtraPage.SETTINGS -> NativePlaceholder("Settings", "Appearance, language, notifications and app preferences.")
                null -> when (tab) {
                    MainTab.HOME -> NativeHome(mangaMode)
                    MainTab.BROWSE -> NativePlaceholder("Browse & Explore", "Search, genres, formats and catalogue filters.")
                    MainTab.VIDEO -> NativePlaceholder("RykoTube", "Videos, trailers, channels, playlists and Shorts.")
                    MainTab.LIBRARY -> NativePlaceholder("My Library", "Watching, plan-to-watch, completed and saved entries.")
                    MainTab.ACCOUNT -> NativePlaceholder("Account", "Sign in, profile, avatar, banner and account settings.")
                }
            }
        }
    }
}

@Composable
private fun NativeHome(mangaMode: Boolean) {
    var items by remember(mangaMode) { mutableStateOf<List<AniListMedia>>(emptyList()) }
    var loading by remember(mangaMode) { mutableStateOf(true) }
    var error by remember(mangaMode) { mutableStateOf<String?>(null) }
    LaunchedEffect(mangaMode) {
        loading = true
        error = null
        val repository = AniListRepository()
        try {
            items = repository.trendingMedia(mangaMode)
        } catch (t: Throwable) {
            error = t.message ?: "Could not load the catalogue."
        } finally {
            repository.close()
            loading = false
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            Column {
                Text(if (mangaMode) "DISCOVER MANGA" else "DISCOVER ANIME", color = Color(0xFFA99BFF), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.height(10.dp))
                Text("Find your next favourite.", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                Spacer(Modifier.height(7.dp))
                Text("Explore trending stories from across the Ryko catalogue.", color = Color(0xFFAAAAB6), fontSize = 12.sp)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Trending now", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFFA99BFF))
            }
        }
        if (loading) item { Text("Loading the catalogue…", color = Color(0xFF999999)) }
        error?.let { message -> item { Text(message, color = Color(0xFFFF9C9C), fontSize = 12.sp) } }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items, key = { it.id }) { media -> NativeMediaCard(media) }
            }
        }
    }
}

@Composable
private fun NativeMediaCard(media: AniListMedia) {
    Card(
        modifier = Modifier.width(132.dp),
        shape = RoundedCornerShape(13.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101010))
    ) {
        Column {
            AsyncImage(
                model = media.coverImage?.imageUrl,
                contentDescription = media.title.display,
                modifier = Modifier.fillMaxWidth().height(178.dp).clip(RoundedCornerShape(topStart = 13.dp, topEnd = 13.dp))
            )
            Column(Modifier.padding(9.dp)) {
                Text(media.title.display, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Text(listOfNotNull(media.format, media.seasonYear?.toString()).joinToString(" · "), color = Color(0xFF888892), fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun NativePlaceholder(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(22.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(subtitle, color = Color(0xFF999999), fontSize = 13.sp)
    }
}
