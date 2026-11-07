package com.rykocollection.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Divider
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.CompositionLocalProvider
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
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import android.content.Intent
import android.content.Context
import android.net.Uri
import coil.compose.AsyncImage
import com.rykocollection.app.data.AniListMedia
import com.rykocollection.app.data.AniListDetail
import com.rykocollection.app.data.AniListRepository
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.rememberHazeState

private enum class MainTab(val label: String) { HOME("Home"), BROWSE("Browse"), VIDEO("Video"), LIBRARY("Library"), ACCOUNT("Account") }
private enum class ExtraPage(val label: String) { TRENDING("Ryko Top Trending"), NEWS("Newsroom"), SETTINGS("Settings") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RykoApp() {
    val context = LocalContext.current
    val visitedTabs = remember { androidx.compose.runtime.mutableStateListOf(MainTab.HOME) }
    var tab by remember { mutableStateOf(MainTab.HOME) }
    var extraPage by remember { mutableStateOf<ExtraPage?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var mangaMode by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<Int?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }

    BackHandler {
        when {
            selectedEntry != null -> selectedEntry = null
            extraPage != null -> extraPage = null
            tab != MainTab.HOME -> tab = MainTab.HOME
            else -> showExitDialog = true
        }
    }

    val hazeState = rememberHazeState()
    CompositionLocalProvider(LocalRykoHazeState provides hazeState) {
    Scaffold(
        modifier = Modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {},
        containerColor = Color.Black
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize().background(Color.Black)) {


            // Keep visited tabs composed while hidden so their data and scroll state survive tab changes.
            if (visitedTabs.contains(MainTab.HOME)) {
                Box(Modifier.fillMaxSize().hazeSource(hazeState).graphicsLayer { alpha = if (tab == MainTab.HOME && selectedEntry == null && extraPage == null) 1f else 0f }.zIndex(if (tab == MainTab.HOME && selectedEntry == null && extraPage == null) 1f else -1f).then(if (tab == MainTab.HOME && selectedEntry == null && extraPage == null) Modifier else Modifier.offset(x = 10000.dp))) {
                    Box(Modifier.fillMaxSize()) { NativeHome(mangaMode, onEntry = { selectedEntry = it }) }
                }
            }
            if (visitedTabs.contains(MainTab.BROWSE)) {
                Box(Modifier.fillMaxSize().hazeSource(hazeState).graphicsLayer { alpha = if (tab == MainTab.BROWSE && selectedEntry == null && extraPage == null) 1f else 0f }.zIndex(if (tab == MainTab.BROWSE && selectedEntry == null && extraPage == null) 1f else -1f).then(if (tab == MainTab.BROWSE && selectedEntry == null && extraPage == null) Modifier else Modifier.offset(x = 10000.dp))) {
                    Box(Modifier.fillMaxSize()) { BrowsePage(mangaMode, onEntry = { selectedEntry = it }) }
                }
            }
            if (visitedTabs.contains(MainTab.VIDEO)) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (tab == MainTab.VIDEO && selectedEntry == null && extraPage == null) 1f else 0f }.zIndex(if (tab == MainTab.VIDEO && selectedEntry == null && extraPage == null) 1f else -1f).then(if (tab == MainTab.VIDEO && selectedEntry == null && extraPage == null) Modifier else Modifier.offset(x = 10000.dp))) {
                    NativeRykoTube()
                }
            }
            if (visitedTabs.contains(MainTab.LIBRARY)) {
                Box(Modifier.fillMaxSize().hazeSource(hazeState).graphicsLayer { alpha = if (tab == MainTab.LIBRARY && selectedEntry == null && extraPage == null) 1f else 0f }.zIndex(if (tab == MainTab.LIBRARY && selectedEntry == null && extraPage == null) 1f else -1f).then(if (tab == MainTab.LIBRARY && selectedEntry == null && extraPage == null) Modifier else Modifier.offset(x = 10000.dp))) {
                    Column(Modifier.fillMaxSize()) { Spacer(Modifier.height(109.dp)); NativePlaceholder("My Library", "Watching, plan-to-watch, completed and saved entries.") }
                }
            }
            if (visitedTabs.contains(MainTab.ACCOUNT)) {
                Box(Modifier.fillMaxSize().hazeSource(hazeState).graphicsLayer { alpha = if (tab == MainTab.ACCOUNT && selectedEntry == null && extraPage == null) 1f else 0f }.zIndex(if (tab == MainTab.ACCOUNT && selectedEntry == null && extraPage == null) 1f else -1f).then(if (tab == MainTab.ACCOUNT && selectedEntry == null && extraPage == null) Modifier else Modifier.offset(x = 10000.dp))) {
                    Column(Modifier.fillMaxSize()) { Spacer(Modifier.height(109.dp)); NativePlaceholder("Account", "Sign in, profile, avatar, banner and account settings.") }
                }
            }

            if (selectedEntry != null) Box(Modifier.fillMaxSize().hazeSource(hazeState)) { Column(Modifier.fillMaxSize()) { Spacer(Modifier.height(109.dp)); EntryDetails(selectedEntry!!, mangaMode) } }
            else when (extraPage) {
                ExtraPage.TRENDING -> Box(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize()) { Spacer(Modifier.height(109.dp)); NativePlaceholder("Ryko Top Trending", "Entry ranking will use real community likes once account services are configured.") } }
                ExtraPage.NEWS -> Box(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize()) { Spacer(Modifier.height(109.dp)); NativeNewsroomPage() } }
                ExtraPage.SETTINGS -> Box(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize()) { Spacer(Modifier.height(109.dp)); NativePlaceholder("Settings", "Appearance, language, notifications and app preferences.") } }
                null -> Unit
            }

            if (tab != MainTab.VIDEO && tab != MainTab.BROWSE) {
                Row(
                        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                            .statusBarsPadding().padding(horizontal = 12.dp, vertical = 7.dp)
                            .rykoGlass(22.dp, Color(0xF01A1A1D), 0.42f)
                            .zIndex(30f)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedEntry != null) {
                            IconButton(onClick = { selectedEntry = null }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                        }
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(29.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFFA99BFF)), contentAlignment = Alignment.Center) {
                                Text("R", color = Color(0xFF101010), fontWeight = FontWeight.Black)
                            }
                            Spacer(Modifier.width(9.dp))
                            Text("RYKOCOLLECTION", color = Color(0xFFF7F7FB), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.7.sp)
                        }
                        IconButton(onClick = { selectedEntry = null; tab = MainTab.BROWSE; extraPage = null }) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.Menu, contentDescription = "Ryko menu", tint = Color.White) }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.rykoGlass(22.dp, Color(0xDD17171B), 0.46f, 30.dp), shape = RoundedCornerShape(22.dp), containerColor = Color.Transparent, tonalElevation = 0.dp, shadowElevation = 0.dp) {
                                DropdownMenuItem(text = { Text(if (mangaMode) "Switch to Anime" else "Switch to Manga") }, onClick = { mangaMode = !mangaMode; menuOpen = false; selectedEntry = null })
                                DropdownMenuItem(text = { Text("Ryko Top Trending") }, onClick = { extraPage = ExtraPage.TRENDING; selectedEntry = null; menuOpen = false })
                                DropdownMenuItem(text = { Text("Newsroom") }, onClick = { extraPage = ExtraPage.NEWS; selectedEntry = null; menuOpen = false })
                                DropdownMenuItem(text = { Text("Settings") }, onClick = { extraPage = ExtraPage.SETTINGS; selectedEntry = null; menuOpen = false })
                            }
                        }
                    }
            }


            // Draw last so it floats above every page instead of being covered by page content.
            RykoBottomNavigation(
                selectedTab = tab,
                visible = selectedEntry == null && extraPage == null,
                onSelect = { item ->
                    tab = item
                    if (!visitedTabs.contains(item)) visitedTabs.add(item)
                    extraPage = null
                    selectedEntry = null
                },
                modifier = Modifier.align(Alignment.BottomCenter).zIndex(20f)
            )
        }
        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text("Exit RykoCollection?") },
                text = { Text("Are you sure you want to close the app?") },
                confirmButton = { TextButton(onClick = { showExitDialog = false; (context as? Activity)?.finish() }) { Text("Exit") } },
                dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text("Stay") } }
            )
        }
    }
}
}


@Composable
private fun RykoBottomNavigation(
    selectedTab: MainTab,
    visible: Boolean,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .navigationBarsPadding()
            // Match the character detail dialog's exact shared frosted-glass treatment.
            .rykoGlass(22.dp, Color(0xF01A1A1D), 0.42f)
            .padding(horizontal = 7.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MainTab.entries.forEach { item ->
            val icon = when (item) {
                MainTab.HOME -> Icons.Default.Home
                MainTab.BROWSE -> Icons.Default.Explore
                MainTab.VIDEO -> Icons.Default.PlayCircle
                MainTab.LIBRARY -> Icons.Default.BookmarkBorder
                MainTab.ACCOUNT -> Icons.Default.Person
            }
            val selected = selectedTab == item
            Column(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .clickable { onSelect(item) }
                    .padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(if (selected) Color(0x32FFFFFF) else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = item.label,
                        tint = if (selected) Color.White else Color(0xFFB9B9C0),
                        modifier = Modifier.size(23.dp))
                }
                Text(item.label, color = if (selected) Color.White else Color(0xFFB9B9C0),
                    fontSize = 10.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
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
        contentPadding = PaddingValues(top = 109.dp, bottom = 144.dp),
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
                            Modifier.fillMaxWidth().height(252.dp).rykoGlass(18.dp, Color(0xD91B1B1F), 0.30f).clickable { onEntry(media.id) },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xB31B1B1F))
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
                                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF508080C))))
                                    .padding(horizontal = 15.dp, vertical = 12.dp)
                                ) {
                                    Text("RYKO SPOTLIGHT", color = Violet, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                                    Text(media.title.display, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(listOfNotNull(media.format, media.seasonYear?.toString(), media.averageScore?.let { "★ $it%" }).joinToString("  ·  "), color = Color(0xFFE0E0E0), fontSize = 10.sp)
                                    Text(media.description?.replace(Regex("<[^>]*>"), "")?.replace("&mdash;", "—").orEmpty(), color = Color(0xFFE2E2E2), fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp, modifier = Modifier.padding(top = 5.dp))
                                    Text("VIEW DETAILS  ›", color = Color(0xFFBDF56A), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 7.dp))
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
                                    .background(if (pagerState.currentPage == index) Violet else Color(0xFF55555A))
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
                                Modifier.width(260.dp).rykoGlass(16.dp).clip(RoundedCornerShape(16.dp))
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
                                val intent = Intent(context, TubePlayerActivity::class.java).apply {
                                    putExtra(TubePlayerActivity.EXTRA_VIDEO_ID, trailerId)
                                    putExtra(TubePlayerActivity.EXTRA_TITLE, media.title.display + " — Official Trailer")
                                    putExtra(TubePlayerActivity.EXTRA_CREATOR, "Official trailer")
                                    putExtra(TubePlayerActivity.EXTRA_THUMB, media.trailer?.thumbnail ?: media.coverImage?.imageUrl.orEmpty())
                                }
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
    var catalogue by remember(mangaMode) { mutableStateOf<List<AniListMedia>>(emptyList()) }
    var loading by remember(mangaMode) { mutableStateOf(true) }
    var error by remember(mangaMode) { mutableStateOf<String?>(null) }
    var filtersExpanded by remember { mutableStateOf(false) }
    var format by remember(mangaMode) { mutableStateOf("All") }
    var status by remember(mangaMode) { mutableStateOf("All") }
    var genre by remember(mangaMode) { mutableStateOf("All") }
    var sort by remember(mangaMode) { mutableStateOf("Popular") }
    val history = remember(mangaMode) { readSearchHistory(context, historyKey) }
    val genres = listOf("All", "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Romance", "Sci-Fi", "Slice of Life", "Sports", "Supernatural")
    LaunchedEffect(mangaMode) {
        loading = true
        val repository = AniListRepository()
        try {
            val trending = repository.trendingMedia(mangaMode)
            val popular = repository.popularRecommendations(mangaMode)
            catalogue = (trending + popular).distinctBy { it.id }.take(50)
        } catch (t: Throwable) { error = t.message ?: "Catalogue could not be loaded." }
        finally { repository.close(); loading = false }
    }
    LaunchedEffect(mangaMode, search) {
        if (search.trim().length < 2) return@LaunchedEffect
        kotlinx.coroutines.delay(400)
        loading = true; error = null
        val repository = AniListRepository()
        try {
            catalogue = repository.searchMedia(search, mangaMode).also { saveSearchHistory(context, historyKey, search) }
        } catch (t: Throwable) { error = t.message ?: "Search failed." }
        finally { repository.close(); loading = false }
    }
    val visible = catalogue.filter { media ->
        (format == "All" || media.format.equals(format, true)) &&
        (status == "All" || media.status.equals(status, true)) &&
        (genre == "All" || media.genres.any { it.equals(genre, true) })
    }.let { list -> when (sort) {
        "Score" -> list.sortedByDescending { it.averageScore ?: 0 }
        "Newest" -> list.sortedByDescending { it.seasonYear ?: 0 }
        "Title" -> list.sortedBy { it.title.display.lowercase() }
        else -> list.sortedByDescending { it.averageScore ?: 0 }
    } }
    LazyColumn(
        Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 144.dp)
    ) {
        item { Text(if (mangaMode) "Explore Manga" else "Explore Anime", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 12.dp)) }
        stickyHeader {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .rykoGlass(24.dp, Color(0x22191A20), 0.52f, 42.dp)
                    .then(if (hazeState != null) Modifier.hazeEffect(state = hazeState, style = HazeStyle(backgroundColor = Color.Transparent, tints = emptyList(), blurRadius = 42.dp, noiseFactor = 0.10f)) else Modifier)
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search anime", tint = Violet, modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (search.isEmpty()) Text("Search anime, manga, titles...", color = Muted, fontSize = 14.sp)
                    BasicTextField(
                        value = search,
                        onValueChange = { search = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Violet),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (search.isNotBlank()) {
                    IconButton(onClick = { search = "" }, modifier = Modifier.size(28.dp)) {
                        Text("×", color = Color.White, fontSize = 21.sp)
                    }
                }
            }
        }
        if (search.isBlank() && history.isNotEmpty()) item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                history.take(3).forEach { term -> FilterChip(selected = false, onClick = { search = term }, label = { Text(term, maxLines = 1) }) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${visible.size} AniList titles loaded", color = Muted, fontSize = 12.sp)
                    if (loading) Text("Updating catalogue…", color = Violet, fontSize = 10.sp)
                }
                androidx.compose.material3.OutlinedButton(onClick = { filtersExpanded = !filtersExpanded }) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp)); Text("Filters"); Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
            }
        }
        if (filtersExpanded) item {
            Column(Modifier.fillMaxWidth().rykoGlass(16.dp, Color(0xE018181B), 0.22f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("GENRE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { genres.forEach { value -> FilterChip(selected = genre == value, onClick = { genre = value }, label = { Text(value) }) } }
                Text("STATUS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("All", "RELEASING", "FINISHED", "NOT_YET_RELEASED", "CANCELLED").forEach { value -> FilterChip(selected = status == value, onClick = { status = value }, label = { Text(value.replace("_", " ")) }) } }
                Text("FORMAT", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("All", "TV", "TV_SHORT", "MOVIE", "OVA", "ONA", "SPECIAL", "MANGA", "NOVEL", "ONE_SHOT").forEach { value -> FilterChip(selected = format == value, onClick = { format = value }, label = { Text(value.replace("_", " ")) }) } }
                Text("SORT", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Popular", "Score", "Newest", "Title").forEach { value -> FilterChip(selected = sort == value, onClick = { sort = value }, label = { Text(value) }) } }
            }
        }
        error?.let { message -> item { Text(message, color = Color(0xFFFF9C9C), fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp)) } }
        if (visible.isEmpty() && !loading) item { Text("No titles match these filters.", color = Muted, modifier = Modifier.padding(20.dp).fillMaxWidth().rykoGlass(16.dp).padding(16.dp)) }
        visible.chunked(3).forEachIndexed { rowIndex, row ->
            item(key = "catalogue-row-$rowIndex") {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { media ->
                        Column(Modifier.weight(1f).clickable { onEntry(media.id) }) {
                            Box(Modifier.fillMaxWidth().height(190.dp).rykoGlass(16.dp).clip(RoundedCornerShape(16.dp))) {
                                AsyncImage(media.coverImage?.imageUrl, media.title.display, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                Text(media.format?.replace("_", " ") ?: "TITLE", Modifier.align(Alignment.TopStart).padding(7.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xE6101012)).padding(horizontal = 8.dp, vertical = 5.dp), color = Color(0xFFE0E0E0), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                if (media.averageScore != null) Row(Modifier.align(Alignment.TopEnd).padding(7.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xE6101012)).padding(horizontal = 7.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFD36B), modifier = Modifier.size(12.dp))
                                    Text(" ${media.averageScore / 10.0}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(media.title.display, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                            Text(listOfNotNull(media.seasonYear?.toString(), media.episodes?.let { "$it eps" }, media.chapters?.let { "$it ch" }).joinToString("  ·  "), color = Muted, fontSize = 10.sp, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun EntryDetails(id: Int, mangaMode: Boolean) {
    NativeEntryPage(id, mangaMode)
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
        Modifier.width(132.dp).height(270.dp).rykoGlass(16.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
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
        Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 20.dp, vertical = 18.dp),
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
                    Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(Color(0x22FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = Violet, modifier = Modifier.size(32.dp))
                }
                Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(
                    "This area is being prepared for RykoCollection.",
                    color = Muted, fontSize = 12.sp, lineHeight = 18.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0x1FFFFFFF)).padding(horizontal = 13.dp, vertical = 7.dp)) {
                    Text("COMING SOON", color = Violet, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                }
            }
        }
    }
}

private val Violet = Color(0xFFA99BFF)
private val Muted = Color(0xFF9999A5)
private val Surface = Color(0xFF111111)
