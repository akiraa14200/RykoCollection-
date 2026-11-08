package com.rykocollection.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val LibraryBg = Color(0xFF08080B)
private val LibraryAccent = Color(0xFFA99BFF)
private val LibraryMuted = Color(0xFFAAA8B5)

@Composable
fun NativeLibraryPage(refreshToken: Int = 0) {
    val context = LocalContext.current
    var selectedType by remember { mutableIntStateOf(0) }
    var selectedStatus by remember { mutableStateOf("All") }
    var search by remember { mutableStateOf("") }
    var newestFirst by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val types = listOf("Anime", "Manga", "Videos")
    val mediaType = types[selectedType]
    val statusOptions = when (mediaType) {
        "Anime" -> listOf("All", "Watching", "Plan to Watch", "Completed", "On Hold", "Dropped")
        "Manga" -> listOf("All", "Reading", "Plan to Read", "Completed", "On Hold", "Dropped")
        else -> listOf("All", "Videos", "Shorts")
    }
    val allEntries = remember(refreshKey, selectedType, refreshToken) {
        if (mediaType == "Videos") emptyList() else LibraryStore.entries(context, mediaType)
    }
    val filtered = allEntries
        .filter { selectedStatus == "All" || it.status == selectedStatus }
        .filter { search.isBlank() || it.title.contains(search, ignoreCase = true) || it.format.contains(search, ignoreCase = true) }
        .let { if (newestFirst) it.sortedByDescending(LibraryEntry::savedAt) else it.sortedBy { entry -> entry.title.lowercase() } }

    Column(Modifier.fillMaxSize().background(LibraryBg)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("My Library", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Your personal anime, manga & video collection", color = LibraryMuted, fontSize = 11.sp)
                }
                IconButton(onClick = { newestFirst = !newestFirst }) {
                    Icon(Icons.Default.Sort, contentDescription = "Change sorting", tint = LibraryAccent)
                }
            }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search your library", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (search.isNotEmpty()) IconButton(onClick = { search = "" }) {
                        Icon(Icons.Default.Close, "Clear search")
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = LibraryAccent, unfocusedBorderColor = Color(0xFF33333A),
                    focusedLeadingIconColor = LibraryAccent, unfocusedLeadingIconColor = LibraryMuted,
                    focusedPlaceholderColor = LibraryMuted, unfocusedPlaceholderColor = LibraryMuted,
                    cursorColor = LibraryAccent
                )
            )
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF17171C)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                types.forEachIndexed { index, label ->
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (selectedType == index) Color(0xFF34313F) else Color.Transparent)
                            .clickable { selectedType = index; selectedStatus = "All" }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (selectedType == index) Color.White else LibraryMuted, fontSize = 12.sp, fontWeight = if (selectedType == index) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    statusOptions.forEach { status ->
                        FilterChip(
                            selected = selectedStatus == status,
                            onClick = { selectedStatus = status },
                            label = { Text(status, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF34313F),
                                selectedLabelColor = Color.White,
                            )
                        )
                    }
            }
        }

        when (mediaType) {
            "Videos" -> NativeLibraryVideos(
                refreshToken = refreshToken,
                searchQuery = search,
                formatFilter = selectedStatus,
                modifier = Modifier.weight(1f)
            )
            else -> {
                val counts = allEntries.groupingBy(LibraryEntry::status).eachCount()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("${allEntries.size} titles", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("${counts.values.sum()} saved", color = LibraryMuted, fontSize = 11.sp)
                }
                if (filtered.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 45.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Icon(Icons.Default.BookmarkBorder, null, tint = LibraryAccent, modifier = Modifier.size(38.dp))
                        Text(if (search.isBlank()) "Your $mediaType library is empty" else "No matching titles", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (search.isBlank()) "Open an anime or manga entry and tap the bookmark icon to add it here."
                            else "Try another title or clear your search.",
                            color = LibraryMuted, fontSize = 12.sp
                        )
                    }
                } else {
                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(start = 13.dp, end = 13.dp, top = 9.dp, bottom = 145.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filtered, key = { "${it.type}-${it.id}" }) { entry ->
                            LibraryEntryRow(
                                entry = entry,
                                onStatus = { status ->
                                    LibraryStore.update(context, entry.copy(status = status))
                                    refreshKey++
                                },
                                onRemove = {
                                    LibraryStore.remove(context, entry.type, entry.id)
                                    refreshKey++
                                },
                                onProgress = { delta ->
                                    val maximum = entry.total.takeIf { it > 0 } ?: Int.MAX_VALUE
                                    LibraryStore.update(context, entry.copy(progress = (entry.progress + delta).coerceIn(0, maximum)))
                                    refreshKey++
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryEntryRow(entry: LibraryEntry, onStatus: (String) -> Unit, onRemove: () -> Unit, onProgress: (Int) -> Unit) {
    var menuOpen by remember(entry.id) { mutableStateOf(false) }
    val statuses = if (entry.type == "Manga") {
        listOf("Reading", "Plan to Read", "Completed", "On Hold", "Dropped")
    } else {
        listOf("Watching", "Plan to Watch", "Completed", "On Hold", "Dropped")
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Color(0xFF151519)).padding(9.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = entry.cover.takeIf(String::isNotBlank),
            contentDescription = entry.title,
            modifier = Modifier.width(76.dp).height(105.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF29292F)),
            contentScale = ContentScale.Crop
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(entry.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOf(entry.format, entry.type).filter(String::isNotBlank).joinToString(" · "), color = LibraryMuted, fontSize = 10.sp)
            Text(entry.status, color = LibraryAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    if (entry.type == "Manga") {
                        if (entry.total > 0) "Ch. ${entry.progress}/${entry.total}" else "Ch. ${entry.progress}"
                    } else {
                        if (entry.total > 0) "Ep. ${entry.progress}/${entry.total}" else "Ep. ${entry.progress}"
                    },
                    color = LibraryMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onProgress(-1) }, modifier = Modifier.size(27.dp), enabled = entry.progress > 0) {
                    Icon(Icons.Default.Remove, "Decrease progress", tint = LibraryMuted, modifier = Modifier.size(15.dp))
                }
                IconButton(onClick = { onProgress(1) }, modifier = Modifier.size(27.dp), enabled = entry.total <= 0 || entry.progress < entry.total) {
                    Icon(Icons.Default.Add, "Increase progress", tint = LibraryAccent, modifier = Modifier.size(15.dp))
                }
            }
            if (entry.total > 0) {
                LinearProgressIndicator(
                    progress = { (entry.progress.toFloat() / entry.total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
                    color = LibraryAccent,
                    trackColor = Color(0xFF34343A)
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.Sort, contentDescription = "Entry options", tint = LibraryMuted)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                statuses.forEach { status ->
                    DropdownMenuItem(text = { Text("Move to $status") }, onClick = { onStatus(status); menuOpen = false })
                }
                Divider()
                DropdownMenuItem(text = { Text("Remove from Library", color = Color(0xFFFF8D8D)) }, onClick = { onRemove(); menuOpen = false })
            }
        }
    }
}
