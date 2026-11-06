package com.rykocollection.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.content.Context
import android.net.Uri
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF08080B), titleContentColor = Color(0xFFF7F7FB), actionIconContentColor = Color(0xFFE8E5FF))
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF09090D), contentColor = Color(0xFFA99BFF), tonalElevation = 0.dp) {
                MainTab.entries.forEach { item ->
                    val icon = when (item) {
                        MainTab.HOME -> Icons.Default.Home
                        MainTab.BROWSE -> Icons.Default.Explore
                        MainTab.VIDEO -> Icons.Default.PlayCircle
                        MainTab.LIBRARY -> Icons.Default.BookmarkBorder
                        MainTab.ACCOUNT -> Icons.Default.Person
                    }
                    NavigationBarItem(selected = tab == item && extraPage == null && selectedEntry == null, onClick = { tab = item; extraPage = null; selectedEntry = null }, icon = { Icon(icon, contentDescription = item.label) }, label = { Text(item.label, fontSize = 10.sp, fontWeight = if (tab == item) FontWeight.SemiBold else FontWeight.Normal) })
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
    var trending by remember(mangaMode) { mutableStateOf<List<AniListMedia>>(emptyList()) }
    var featured by remember(mangaMode) { mutableStateOf<List<AniListMedia>>(emptyList()) }
    var recommendations by remember(mangaMode) { mutableStateOf<List<AniListMedia>>(emptyList()) }
    var episodes by remember(mangaMode) { mutableStateOf<List<com.rykocollection.app.data.AniListAiring>>(emptyList()) }
    var updatedManga by remember(mangaMode) { mutableStateOf<List<AniListMedia>>(emptyList()) }
    var videos by remember(mangaMode) { mutableStateOf<List<AniListMedia>>(emptyList()) }
    var loading by remember(mangaMode) { mutableStateOf(true) }
    var error by remember(mangaMode) { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(mangaMode) {
        loading = true
        error = null
        val repository = AniListRepository()
        try {
            featured = repository.featuredMedia(mangaMode)
            trending = repository.trendingMedia(mangaMode)
            recommendations = repository.popularRecommendations(mangaMode)
            if (mangaMode) {
                updatedManga = repository.recentlyUpdatedManga()
                episodes = emptyList()
                videos = emptyList()
            } else {
                episodes = repository.latestAiringEpisodes()
                videos = repository.popularVideoTrailers()
                updatedManga = emptyList()
            }
        } catch (t: Throwable) {
            error = t.message ?: "Some home sections could not be loaded."
        } finally {
            repository.close()
            loading = false
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(Color.Black),
        contentPadding = PaddingValues(top = 6.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        if (featured.isNotEmpty()) {
            item {
                val slides = featured.take(5)
                val pagerState = rememberPagerState(pageCount = { slides.size })
                LaunchedEffect(slides.size) {
                    if (slides.size > 1) {
                        while (true) {
                            kotlinx.coroutines.delay(4500)
                            pagerState.animateScrollToPage((pagerState.currentPage + 1) % slides.size)
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(horizontal = 18.dp),
                        pageSpacing = 12.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        val media = slides[page]
                        Card(
                            Modifier.fillMaxWidth().height(224.dp).clickable { onEntry(media.id) },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Surface)
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                AsyncImage(
                                    media.bannerImage ?: media.coverImage?.imageUrl,
                                    media.title.display,
                                    Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Column(
                                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                                        .background(Color(0xCC09090F)).padding(horizontal = 14.dp, vertical = 11.dp)
                                ) {
                                    Text("FEATURED", color = Violet, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                                    Text(media.title.display, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(listOfNotNull(media.format, media.seasonYear?.toString()).joinToString(" · "), color = Color.LightGray, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(slides.size) { index ->
                            Box(
                                Modifier.padding(horizontal = 3.dp)
                                    .size(if (pagerState.currentPage == index) 16.dp else 6.dp, 6.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (pagerState.currentPage == index) Violet else Color(0xFF55515F))
                            )
                        }
                    }
                }
            }
        } else if (loading) {
            item { FeaturedSkeleton() }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeSectionTitle("Trending now", "RIGHT NOW")
                if (trending.isEmpty() && loading) HomeLoading()
                else LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(trending, key = { it.id }) { media -> NativeMediaCard(media) { onEntry(media.id) } }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeSectionTitle("Popular picks", "DISCOVER")
                if (recommendations.isEmpty() && loading) HomeLoading()
                else LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recommendations, key = { it.id }) { media -> NativeMediaCard(media) { onEntry(media.id) } }
                }
            }
        }

        if (mangaMode) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeSectionTitle("Recently updated manga", "LATEST CHAPTER ACTIVITY")
                    if (updatedManga.isEmpty() && loading) HomeLoading()
                    else LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(updatedManga, key = { it.id }) { media ->
                            Row(
                                Modifier.width(260.dp).clip(RoundedCornerShape(14.dp)).background(Surface)
                                    .clickable { onEntry(media.id) }.padding(9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(media.coverImage?.imageUrl, media.title.display, Modifier.size(58.dp, 78.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                Column(Modifier.padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(media.title.display, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("Latest catalogue update", color = Muted, fontSize = 10.sp)
                                    Text(media.chapters?.let { "$it chapters listed" } ?: "Chapter details unavailable", color = Violet, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeSectionTitle("Latest episodes", "RECENT AIRINGS")
                    if (episodes.isEmpty() && loading) HomeLoading()
                    else LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(episodes, key = { "${it.media.id}-${it.episode}" }) { airing ->
                            Row(
                                Modifier.width(260.dp).clip(RoundedCornerShape(14.dp)).background(Surface)
                                    .clickable { onEntry(airing.media.id) }.padding(9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(airing.media.coverImage?.imageUrl, airing.media.title.display, Modifier.size(58.dp, 78.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                Column(Modifier.padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(airing.media.title.display, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("Episode ${airing.episode}", color = Violet, fontSize = 11.sp)
                                    Text("Recently aired", color = Muted, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!mangaMode) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeSectionTitle("Popular trailers", "WATCH")
                    if (videos.isEmpty() && loading) HomeLoading()
                    else LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(videos, key = { it.id }) { media ->
                            Column(Modifier.width(190.dp).height(164.dp).clickable {
                                val trailerId = media.trailer?.id ?: return@clickable
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$trailerId"))
                                context.startActivity(intent)
                            }) {
                                Box {
                                    AsyncImage(media.trailer?.thumbnail ?: media.coverImage?.imageUrl, media.title.display, Modifier.fillMaxWidth().height(108.dp).clip(RoundedCornerShape(12.dp)))
                                    Icon(Icons.Default.PlayCircle, contentDescription = "Play trailer", tint = Color.White, modifier = Modifier.align(Alignment.Center).size(38.dp))
                                }
                                Text(media.title.display, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp).height(34.dp))
                                Text("Official trailer", color = Muted, fontSize = 10.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        error?.let { message -> item { Text(message, color = Color(0xFFFF9C9C), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 18.dp)) } }
        if (loading && featured.isEmpty() && trending.isEmpty()) item { HomeLoading() }
    }
}

@Composable
private fun HomeSectionTitle(title: String, eyebrow: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        Text(eyebrow, color = Violet, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SkeletonBlock(modifier: Modifier = Modifier) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.32f,
        targetValue = 0.72f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(850),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )
    Box(modifier.background(Color.White.copy(alpha = alpha), RoundedCornerShape(9.dp)))
}

@Composable
private fun FeaturedSkeleton() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SkeletonBlock(Modifier.fillMaxWidth().height(224.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(5) { SkeletonBlock(Modifier.padding(horizontal = 3.dp).size(6.dp)) }
        }
    }
}

@Composable
private fun HomeLoading() {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(3) {
            Column(
                Modifier.width(132.dp).height(270.dp).clip(RoundedCornerShape(13.dp))
                    .background(Surface).padding(0.dp)
            ) {
                SkeletonBlock(Modifier.fillMaxWidth().height(188.dp))
                Column(Modifier.fillMaxWidth().padding(9.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    SkeletonBlock(Modifier.fillMaxWidth(0.88f).height(11.dp))
                    SkeletonBlock(Modifier.fillMaxWidth(0.58f).height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun readSearchHistory(context: Context, key: String): List<String> =
    context.getSharedPreferences("ryko_search", Context.MODE_PRIVATE)
        .getString(key, "").orEmpty().split("¦").filter { it.isNotBlank() }

private fun saveSearchHistory(context: Context, key: String, value: String) {
    val clean = value.trim()
    if (clean.length < 2) return
    val history = (listOf(clean) + readSearchHistory(context, key).filterNot { it.equals(clean, true) }).take(8)
    context.getSharedPreferences("ryko_search", Context.MODE_PRIVATE)
        .edit().putString(key, history.joinToString("¦")).apply()
}

@Composable
private fun BrowsePage(mangaMode: Boolean, onEntry: (Int) -> Unit) {
    val context = LocalContext.current
    val historyKey = if (mangaMode) "browse_manga" else "browse_anime"
    var search by remember(mangaMode) { mutableStateOf("") }
    var results by remember(mangaMode) { mutableStateOf<List<AniListMedia>>(emptyList()) }
    var loading by remember(mangaMode) { mutableStateOf(false) }
    var error by remember(mangaMode) { mutableStateOf<String?>(null) }
    var format by remember(mangaMode) { mutableStateOf("All") }
    var status by remember(mangaMode) { mutableStateOf("All") }
    var genre by remember(mangaMode) { mutableStateOf("All") }
    var sort by remember(mangaMode) { mutableStateOf("Match") }
    val history = remember(mangaMode) { readSearchHistory(context, historyKey) }
    val genres = listOf("All", "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Romance", "Sci-Fi", "Slice of Life", "Sports", "Supernatural")
    LaunchedEffect(mangaMode, search) {
        if (search.trim().length < 2) { results = emptyList(); error = null; return@LaunchedEffect }
        kotlinx.coroutines.delay(400)
        loading = true; error = null
        val repository = AniListRepository()
        try {
            results = repository.searchMedia(search, mangaMode)
            saveSearchHistory(context, historyKey, search)
        } catch (t: Throwable) { error = t.message ?: "Search failed." }
        finally { repository.close(); loading = false }
    }
    val visible = results.filter { media ->
        (format == "All" || media.format.equals(format, true)) &&
        (status == "All" || media.status.equals(status, true)) &&
        (genre == "All" || media.genres.any { it.equals(genre, true) })
    }.let { list -> when (sort) {
        "Score" -> list.sortedByDescending { it.averageScore ?: 0 }
        "Newest" -> list.sortedByDescending { it.seasonYear ?: 0 }
        else -> list
    } }
    Column(Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text(if (mangaMode) "DISCOVER STORIES" else "DISCOVER ANIME", color = Violet, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text(if (mangaMode) "Find your next manga" else "Find your next anime", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            label = { Text("Search titles on AniList") }, singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = { if (search.isNotBlank()) IconButton(onClick = { search = "" }) { Text("×", color = Color.White, fontSize = 20.sp) } })
        if (search.isBlank() && history.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("RECENT SEARCHES", color = Violet, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("Clear", color = Muted, fontSize = 10.sp, modifier = Modifier.clickable {
                    context.getSharedPreferences("ryko_search", Context.MODE_PRIVATE).edit().remove(historyKey).apply()
                })
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                history.forEach { term -> androidx.compose.material3.AssistChip(onClick = { search = term }, label = { Text(term, maxLines = 1) }) }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("All", "TV", "TV_SHORT", "MOVIE", "OVA", "ONA", "SPECIAL", "MANGA", "NOVEL", "ONE_SHOT").forEach { value ->
                FilterChip(selected = format == value, onClick = { format = value }, label = { Text(if (value == "TV_SHORT") "Short" else value.replace("_", " ")) })
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("All", "RELEASING", "FINISHED", "NOT_YET_RELEASED", "CANCELLED").forEach { value ->
                FilterChip(selected = status == value, onClick = { status = value }, label = { Text(value.replace("_", " ")) })
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            genres.forEach { value -> FilterChip(selected = genre == value, onClick = { genre = value }, label = { Text(value) }) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            listOf("Match", "Score", "Newest").forEach { value ->
                Text(value, color = if (sort == value) Violet else Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { sort = value }.padding(start = 14.dp, top = 8.dp, bottom = 4.dp))
            }
        }
        if (loading) CircularProgressIndicator(color = Violet, modifier = Modifier.padding(12.dp).size(22.dp))
        error?.let { Text(it, color = Color(0xFFFF9C9C), modifier = Modifier.padding(top = 8.dp)) }
        if (search.length < 2) {
            Text("Search AniList's anime and manga catalogue. Suggestions and recent searches appear here.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        } else if (!loading && visible.isEmpty() && error == null) {
            Text("No titles match these filters. Try another genre or format.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(visible, key = { it.id }) { media -> SearchResultRow(media) { onEntry(media.id) } }
        }
    }
}

@Composable
private fun SearchResultRow(media: AniListMedia, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface).clickable(onClick = onClick).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(media.coverImage?.imageUrl, media.title.display, Modifier.size(72.dp, 96.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(media.title.display, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(media.format, media.seasonYear?.toString(), media.averageScore?.let { "$it%" }).joinToString(" · "), color = Muted, fontSize = 11.sp)
            Text(media.genres.take(3).joinToString(" · "), color = Violet, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                AsyncImage(entry!!.bannerImage ?: entry!!.coverImage?.imageUrl, entry!!.title.display, Modifier.fillMaxWidth().height(210.dp), contentScale = ContentScale.Crop)
                Column(Modifier.padding(18.dp)) {
                    Text(entry!!.title.display, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(listOfNotNull(entry!!.format, entry!!.seasonYear?.toString(), entry!!.averageScore?.let { "$it%" }).joinToString(" · "), color = Muted, fontSize = 12.sp)
                    Text(entry!!.genres.joinToString("  •  "), color = Violet, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
            item {
                TabRow(selectedTabIndex = selectedTab, containerColor = Color(0xFF090909), contentColor = Violet) {
                    listOf("Overview", "Characters", "Details", "Comments").forEachIndexed { index, label -> Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(label) }) }
                }
            }
            when (selectedTab) {
                0 -> {
                    item { Text(entry!!.description?.replace(Regex("<[^>]*>"), "")?.replace("&mdash;", "—") ?: "No description available.", color = Color(0xFFCCCCD2), lineHeight = 21.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) }
                    val trailer = entry!!.trailer
                    if (!trailer?.id.isNullOrBlank() && trailer?.site.equals("youtube", true)) item {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                            Text("OFFICIAL TRAILER", color = Violet, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                            Spacer(Modifier.height(8.dp))
                            Box(Modifier.fillMaxWidth().height(205.dp).clip(RoundedCornerShape(16.dp)).background(Surface).clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${trailer?.id}"))
                                LocalContext.current.startActivity(intent)
                            }) {
                                AsyncImage(trailer?.thumbnail ?: entry!!.coverImage?.imageUrl, "Official trailer", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                Icon(Icons.Default.PlayCircle, contentDescription = "Play trailer", tint = Color.White, modifier = Modifier.align(Alignment.Center).size(54.dp))
                            }
                        }
                    } else item { Text("No official trailer is listed on AniList.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(18.dp)) }
                }
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
                2 -> item {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DetailLine("Type", entry!!.format)
                        DetailLine("Status", entry!!.status)
                        DetailLine("Year", entry!!.seasonYear?.toString())
                        DetailLine("Episodes", entry!!.episodes?.toString())
                        DetailLine("Chapters", entry!!.chapters?.toString())
                        DetailLine("Country", entry!!.countryOfOrigin)
                        DetailLine("Source", entry!!.source)
                        DetailLine("Studio", entry!!.studios?.nodes?.joinToString { it.name })
                        DetailLine("Genres", entry!!.genres.joinToString())
                    }
                }
                else -> item {
                    Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("Community discussion", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Comments are not connected to the native app yet. No sample comments are shown.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
    Card(
        Modifier.width(132.dp).height(270.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(Modifier.fillMaxSize()) {
            AsyncImage(
                media.coverImage?.imageUrl,
                media.title.display,
                Modifier.fillMaxWidth().height(188.dp).clip(RoundedCornerShape(topStart = 13.dp, topEnd = 13.dp)),
                contentScale = ContentScale.Crop
            )
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(9.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    media.title.display,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(media.format, media.seasonYear?.toString()).joinToString(" · ").ifBlank { " " },
                    color = Muted,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NativePlaceholder(title: String, subtitle: String) {
    val icon = when (title) {
        "My Library" -> Icons.Default.BookmarkBorder
        "Account" -> Icons.Default.Person
        "Settings" -> Icons.Default.Menu
        "Ryko Newsroom" -> Icons.Default.Explore
        else -> Icons.Default.EmojiEvents
    }
    val eyebrow = when (title) {
        "My Library" -> "YOUR COLLECTION"
        "Account" -> "RYKO PROFILE"
        "Settings" -> "PREFERENCES"
        "Ryko Newsroom" -> "THE LATEST"
        else -> "COMMUNITY RANKING"
    }
    Column(
        Modifier.fillMaxSize().background(Color(0xFF050507)).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(eyebrow, color = Violet, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.7).sp)
            Text(subtitle, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        }
        Box(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF101014)).padding(22.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(Color(0x22A99BFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = Violet, modifier = Modifier.size(32.dp))
                }
                Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(
                    "This area is being prepared for RykoCollection.",
                    color = Muted, fontSize = 12.sp, lineHeight = 18.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0x1FA99BFF)).padding(horizontal = 13.dp, vertical = 7.dp)) {
                    Text("COMING SOON", color = Violet, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                }
            }
        }
    }
}

private val Violet = Color(0xFFA99BFF)
private val Muted = Color(0xFF9999A5)
private val Surface = Color(0xFF111111)
