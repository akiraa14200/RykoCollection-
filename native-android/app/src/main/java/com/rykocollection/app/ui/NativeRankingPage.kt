package com.rykocollection.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rykocollection.app.data.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val RankAccent = Color(0xFFA99BFF)
private val RankMuted = Color(0xFFAAA8B5)

@Serializable
private data class ManhwaRankRow(
    @SerialName("anilist_media_id") val mediaId: Long,
    @SerialName("media_title") val title: String,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("vote_count") val voteCount: Long,
    @SerialName("average_opinion") val average: Double,
    @SerialName("ranking_score") val score: Double,
    @SerialName("bad_count") val bad: Long,
    @SerialName("normal_count") val normal: Long,
    @SerialName("good_count") val good: Long,
    @SerialName("very_good_count") val veryGood: Long,
    @SerialName("peak_count") val peak: Long,
    val rank: Long
)

private enum class RankingPeriod(val key: String, val label: String, val subtitle: String) {
    OVERALL("overall", "Overall", "All-time community favourites"),
    WEEKLY("weekly", "Weekly", "Opinions from the last 7 days"),
    MONTHLY("monthly", "Monthly", "Opinions from the last 30 days")
}

@Composable
fun NativeRankingPage() {
    var period by remember { mutableStateOf(RankingPeriod.OVERALL) }
    var rows by remember { mutableStateOf<List<ManhwaRankRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf("All") }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            loading = true
            error = null
            try {
                rows = SupabaseProvider.client.from("manhwa_ranking_periods")
                    .select { filter { eq("period", period.key) } }
                    .decodeList<ManhwaRankRow>()
                    .sortedBy { it.rank }
            } catch (t: Throwable) {
                error = t.message ?: "Rankings could not be loaded."
            } finally { loading = false }
        }
    }

    LaunchedEffect(period) { load() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFF08080C)),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF30264D), Color(0xFF17151F), Color(0xFF111116))))
                    .padding(19.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFFFFD77A), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("RYKO COMMUNITY", color = RankAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                }
                Text("Manhwa Rankings", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                Text("Ranked by readers. Powered by opinions.", color = Color(0xFFD4D0DF), fontSize = 12.sp)
                Text(period.subtitle, color = RankMuted, fontSize = 10.sp)
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF19191F)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RankingPeriod.entries.forEach { item ->
                    val active = period == item
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .background(if (active) Color(0xFF393247) else Color.Transparent)
                            .clickable { period = item }.padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(item.label, color = if (active) Color.White else RankMuted, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("THE LEADERBOARD", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                    Text("Titles need at least 3 opinions to qualify", color = RankMuted, fontSize = 10.sp)
                }
                IconButton(onClick = { load() }) { Icon(Icons.Default.Refresh, "Refresh rankings", tint = RankAccent) }
            }
        }
        if (loading) {
            item { Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = RankAccent) } }
        } else if (error != null) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF1B1A22)).padding(18.dp)) {
                    Text("Rankings unavailable", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(error.orEmpty(), color = RankMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                    TextButton(onClick = { load() }) { Text("Try again", color = RankAccent) }
                }
            }
        } else if (rows.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF17171D)).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No rankings yet", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Be among the first readers to share an opinion on a manhwa.", color = RankMuted, fontSize = 11.sp)
                }
            }
        } else {
            items(rows, key = { "${period.key}-${it.mediaId}" }) { row ->
                ManhwaRankCard(row)
            }
        }
        item {
            Text(
                "Ranking score is confidence-adjusted to reduce the impact of very small vote counts. The displayed opinion average and vote total remain transparent.",
                color = RankMuted, fontSize = 10.sp, lineHeight = 15.sp,
                modifier = Modifier.padding(top = 5.dp, bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun ManhwaRankCard(row: ManhwaRankRow) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp))
            .background(Color(0xFF17171D)).padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.width(31.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("#${row.rank}", color = if (row.rank <= 3) Color(0xFFFFD77A) else RankMuted, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            if (row.rank == 1L) Text("TOP", color = Color(0xFFFFD77A), fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
        AsyncImage(
            model = row.coverUrl,
            contentDescription = row.title,
            modifier = Modifier.width(65.dp).height(88.dp).clip(RoundedCornerShape(11.dp)),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(row.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(String.format("%.2f", row.average), color = RankAccent, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(" / 5", color = RankMuted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 3.dp))
                Spacer(Modifier.width(7.dp))
                Text("${row.voteCount} opinions", color = RankMuted, fontSize = 9.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
            Row(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(Color(0xFF34313D))) {
                val total = row.voteCount.coerceAtLeast(1).toFloat()
                listOf(row.bad, row.normal, row.good, row.veryGood, row.peak).forEachIndexed { index, count ->
                    if (count > 0) Box(
                        Modifier.weight(count.toFloat()).fillMaxHeight()
                            .background(listOf(Color(0xFFCF6B75), Color(0xFF8E8A98), Color(0xFF6D9ACB), Color(0xFF9A83D5), Color(0xFFFFC66D))[index])
                    )
                }
            }
            Text("🔥 ${row.peak} Peak  ·  ✨ ${row.veryGood} Very Good", color = RankMuted, fontSize = 9.sp, maxLines = 1)
        }
    }
}
