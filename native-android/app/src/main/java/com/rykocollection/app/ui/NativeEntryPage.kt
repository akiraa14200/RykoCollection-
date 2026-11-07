package com.rykocollection.app.ui

import android.content.Intent
import android.net.Uri
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.rykocollection.app.data.*
import kotlinx.coroutines.launch

private val EBg=Color(0xFF080808)
private val ESurface=Color(0xB31B1B1F)
private val EAccent=Color(0xFFA99BFF)
private val EMuted=Color(0xFFAAA8B5)

@Composable
fun NativeEntryPage(id:Int,mangaMode:Boolean) {
    val context= LocalContext.current
    val scope= rememberCoroutineScope()
    var media by remember(id){ mutableStateOf<AniListDetail?>(null) }
    var loading by remember(id){ mutableStateOf(true) }
    var error by remember(id){ mutableStateOf<String?>(null) }
    var tab by remember(id){ mutableIntStateOf(0) }
    var chars by remember(id){ mutableStateOf<List<AniListCharacterEdge>>(emptyList()) }
    var page by remember(id){ mutableIntStateOf(1) }
    var hasMore by remember(id){ mutableStateOf(false) }
    var loadingMore by remember{ mutableStateOf(false) }
    var selected by remember{ mutableStateOf<AniListCharacterEdge?>(null) }

    LaunchedEffect(id) {
        val api=AniListRepository()
        try {
            media=api.getMedia(id)
            chars=media?.characters?.edges.orEmpty()
            hasMore=chars.size>=18
            if(media==null) error="AniList could not find this entry."
        } catch(t:Throwable){ error=t.message ?: "Entry details could not be loaded." }
        finally { api.close(); loading=false }
    }
    if(loading && media==null) {
        Box(Modifier.fillMaxSize().background(Color.Black),contentAlignment=Alignment.Center){CircularProgressIndicator(color=EAccent)}
        return
    }
    val item=media
    if(item==null) {
        Column(Modifier.fillMaxSize().background(EBg).padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
            Text("Entry unavailable",color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold)
            Text(error ?: "No entry data was returned.",color=EMuted,modifier=Modifier.padding(top=8.dp))
        }
        return
    }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        EntryHero(item,mangaMode) {
            val share=Intent(Intent.ACTION_SEND).apply {
                type="text/plain"
                putExtra(Intent.EXTRA_TEXT,"https://anilist.co/${if(mangaMode) "manga" else "anime"}/${item.id}")
            }
            context.startActivity(Intent.createChooser(share,"Share entry"))
        }
        TabRow(selectedTabIndex=tab,modifier=Modifier.padding(horizontal=10.dp).rykoGlass(18.dp, Color(0xD91B1B1F), 0.34f),containerColor=Color.Transparent,contentColor=EAccent) {
            listOf("Overview","Characters","Details","Comments").forEachIndexed { i,label ->
                Tab(selected=tab==i,onClick={tab=i},text={Text(label,fontSize=11.sp,maxLines=1)})
            }
        }
        when(tab) {
            0 -> EntryOverview(item,mangaMode)
            1 -> LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(11.dp)) {
                item {
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){Text("Characters",color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Bold);Text("Profiles and roles from AniList",color=EMuted,fontSize=11.sp)}
                        Text("${chars.size} loaded",color=EAccent,fontSize=11.sp)
                    }
                }
                if(chars.isEmpty()) item { EEmpty("Character information is not available.") }
                chars.chunked(3).forEach { group ->
                    item {
                        Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){
                            group.forEach { edge ->
                                Column(Modifier.weight(1f).rykoGlass(13.dp).clickable{selected=edge}.padding(7.dp)){
                                    AsyncImage(edge.node.image?.large ?: edge.node.image?.medium,edge.node.name.full,Modifier.fillMaxWidth().height(126.dp).clip(RoundedCornerShape(9.dp)),contentScale=ContentScale.Crop)
                                    Text(edge.node.name.full,color=Color.White,fontSize=11.sp,fontWeight=FontWeight.SemiBold,maxLines=2,minLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=6.dp))
                                    Text(edge.role.lowercase().replaceFirstChar{it.uppercase()},color=EAccent,fontSize=9.sp)
                                }
                            }
                            repeat(3-group.size){Spacer(Modifier.weight(1f))}
                        }
                    }
                }
                if(hasMore && chars.isNotEmpty()) item {
                    Button(onClick={
                        if(!loadingMore) scope.launch {
                            loadingMore=true
                            val api=AniListRepository()
                            try {
                                val next=api.getCharacters(id,page+1)
                                if(next.isEmpty()) hasMore=false else {
                                    val known=chars.map{it.node.id}.toSet()
                                    chars=chars+next.filter{it.node.id !in known}
                                    page++
                                    hasMore=next.size>=18
                                }
                            } catch(_:Throwable) { } finally { api.close(); loadingMore=false }
                        }
                    },modifier=Modifier.fillMaxWidth(),enabled=!loadingMore,colors=ButtonDefaults.buttonColors(containerColor=ESurface,contentColor=EAccent)){
                        if(loadingMore) CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp) else Text("Load more characters")
                    }
                }
            }
            2 -> EntryMetadata(item,mangaMode)
            else -> EntryCommentsPlaceholder()
        }
    }
    selected?.let { CharacterDialog(it){selected=null} }
}

@Composable
private fun EntryHero(m:AniListDetail,manga:Boolean,onShare:()->Unit) {
    Box(Modifier.fillMaxWidth().height(276.dp).background(ESurface)){
        AsyncImage(m.bannerImage ?: m.coverImage?.imageUrl,m.title.display,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x44000000),Color(0xFF08080C)))))
        val context=LocalContext.current
    Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp).rykoGlass(18.dp, Color(0xD91B1B1F), 0.28f).padding(7.dp),verticalAlignment=Alignment.Bottom){
            AsyncImage(m.coverImage?.imageUrl,m.title.display,Modifier.width(105.dp).height(148.dp).clip(RoundedCornerShape(12.dp)),contentScale=ContentScale.Crop)
            Column(Modifier.weight(1f).padding(start=12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                Text("${m.format ?: if(manga) "MANGA" else "ANIME"} · ${m.seasonYear ?: m.startDate?.year ?: "YEAR N/A"}",color=EAccent,fontSize=9.sp,fontWeight=FontWeight.Bold)
                Text(m.title.display,color=Color.White,fontSize=19.sp,fontWeight=FontWeight.ExtraBold,maxLines=3,overflow=TextOverflow.Ellipsis,lineHeight=22.sp)
                m.title.native?.takeIf{it.isNotBlank() && it!=m.title.display}?.let{Text(it,color=Color.LightGray,fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}
                Text(listOfNotNull(m.averageScore?.let{"★ ${it/10.0}/10"},m.status?.replace('_',' '),m.popularity?.let{"Popularity $it"}).joinToString(" · "),color=Color.White,fontSize=9.sp,maxLines=2)
                Text(m.genres.take(3).joinToString(" • "),color=Color(0xFFD6D6D6),fontSize=9.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                Row(verticalAlignment=Alignment.CenterVertically){
                    IconButton(onClick=onShare,modifier=Modifier.size(34.dp)){Icon(Icons.Default.Share,"Share",tint=Color.White)}
                    TextButton(onClick={
                        context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://anilist.co/" + (if(manga) "manga" else "anime") + "/" + m.id)))
                    }){Text("AniList ↗",color=Color.White,fontSize=9.sp)}
                }
            }
        }
    }
    m.nextAiringEpisode?.takeIf{!manga && it.episode>0}?.let{
        Text("NEXT EPISODE · EPISODE ${it.episode} · ${countdown(it.timeUntilAiring)}",color=EAccent,fontSize=10.sp,modifier=Modifier.fillMaxWidth().background(ESurface).padding(9.dp))
    }
}

@Composable
private fun ColumnScope.EntryOverview(m:AniListDetail,manga:Boolean) {
    val context=LocalContext.current
    LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item {
            Text("STORY",color=EAccent,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.4.sp)
            Text(clean(m.description).ifBlank{"No synopsis has been provided by AniList."},color=Color(0xFFE0E0E0),fontSize=13.sp,lineHeight=20.sp,modifier=Modifier.fillMaxWidth().rykoGlass(16.dp, Color(0xD91B1B1F), 0.20f).padding(14.dp).padding(top=7.dp))
        }
        item {
            Text("OFFICIAL TRAILER",color=EAccent,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.4.sp)
            val t=m.trailer
            if(!t?.id.isNullOrBlank() && t?.site.equals("youtube",true)){
                Box(Modifier.fillMaxWidth().padding(top=8.dp).height(194.dp).rykoGlass(16.dp, Color(0xD91B1B1F), 0.24f).clickable{
                    context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/watch?v=${t!!.id}")))
                }){
                    AsyncImage(t?.thumbnail ?: m.coverImage?.imageUrl,"Official trailer",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                    Icon(Icons.Default.PlayCircle,null,tint=Color.White,modifier=Modifier.align(Alignment.Center).size(56.dp))
                }
                Text("Trailer supplied by AniList · YouTube",color=EMuted,fontSize=10.sp)
            } else EEmpty("No official trailer is listed on AniList.")
        }
        item {
            Text("ENTRY SNAPSHOT",color=EAccent,fontSize=10.sp,fontWeight=FontWeight.Bold)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(top=7.dp)){
                EMetric(if(manga)"CHAPTERS" else "EPISODES",(if(manga)m.chapters else m.episodes)?.toString() ?: "—",Modifier.weight(1f))
                EMetric(if(manga)"VOLUMES" else "DURATION",(if(manga)m.volumes else m.duration)?.let{if(manga)"$it" else "$it min"} ?: "—",Modifier.weight(1f))
                EMetric("FAVOURITES",m.favourites?.toString() ?: "—",Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ColumnScope.EntryMetadata(m:AniListDetail,manga:Boolean) {
    LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{Text("METADATA",color=EAccent,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.4.sp)}
        item{EInfo("Type",m.format)}
        item{EInfo("Status",m.status?.replace('_',' '))}
        item{EInfo("Release",listOfNotNull(m.startDate?.display()?.takeIf{it!="—"},m.endDate?.display()?.takeIf{it!="—"}).joinToString(" → ").ifBlank{m.seasonYear?.toString()})}
        item{EInfo("Season",listOfNotNull(m.season,m.seasonYear?.toString()).joinToString(" "))}
        if(manga){item{EInfo("Chapters",m.chapters?.toString())};item{EInfo("Volumes",m.volumes?.toString())}}
        else{item{EInfo("Episodes",m.episodes?.toString())};item{EInfo("Duration",m.duration?.let{"$it minutes"})}}
        item{EInfo("Country",m.countryOfOrigin)}
        item{EInfo("Source material",m.source?.replace('_',' '))}
        item{EInfo("Studio",m.studios?.nodes?.joinToString{it.name})}
        item{EInfo("Average score",m.averageScore?.let{"${it/10.0}/10"})}
        item{EInfo("Popularity",m.popularity?.toString())}
        item{EInfo("Favourites",m.favourites?.toString())}
        if(m.genres.isNotEmpty()) item{EInfo("Genres",m.genres.joinToString())}
        val staff=m.staff?.edges.orEmpty()
        if(staff.isNotEmpty()) item {
            Column(Modifier.fillMaxWidth().rykoGlass(12.dp).padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
                Text("STAFF & CREATORS",color=EAccent,fontSize=10.sp,fontWeight=FontWeight.Bold)
                staff.take(15).forEach{Text("${it.node.name.full} · ${it.role}",color=Color.White,fontSize=12.sp)}
            }
        }
        val titles=(listOfNotNull(m.title.english,m.title.romaji,m.title.native)+m.synonyms).filter{it.isNotBlank()}.distinct()
        if(titles.isNotEmpty()) item {
            Column(Modifier.fillMaxWidth().rykoGlass(12.dp).padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text("ALTERNATIVE TITLES",color=EAccent,fontSize=10.sp,fontWeight=FontWeight.Bold)
                titles.forEach{Text(it,color=Color.White,fontSize=12.sp)}
            }
        }
        val links=m.externalLinks.distinctBy{it.url}.filter{it.url.startsWith("https://")}
        if(links.isNotEmpty()) item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ESurface).padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
                Text("EXTERNAL SOURCES",color=EAccent,fontSize=10.sp,fontWeight=FontWeight.Bold)
                val context=LocalContext.current
                links.forEach{link->Row(Modifier.fillMaxWidth().clickable {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(link.url))) }
                }.padding(vertical=4.dp)){Text(link.site,color=Color.White,fontSize=12.sp)}}
            }
        }
        item{Text("Metadata provided by AniList. Missing fields are not estimated.",color=EMuted,fontSize=10.sp)}
    }
}

@Composable
private fun ColumnScope.EntryCommentsPlaceholder(){
    Column(Modifier.fillMaxWidth().weight(1f).padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
        Text("COMMUNITY DISCUSSION",color=EAccent,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.4.sp)
        Text("Comments connection pending",color=Color.White,fontSize=19.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=9.dp))
        Text("This build does not yet have the verified entry-comment table and moderation rules. No sample comments are shown or submitted.",color=EMuted,fontSize=12.sp,lineHeight=18.sp,modifier=Modifier.padding(top=7.dp))
    }
}

@Composable
private fun CharacterDialog(edge:AniListCharacterEdge,onClose:()->Unit){
    val c=edge.node
    Dialog(onDismissRequest=onClose){
        Column(Modifier.fillMaxWidth().heightIn(max=580.dp).rykoGlass(22.dp, Color(0xF01A1A1D), 0.42f)){
            Box(Modifier.fillMaxWidth().height(205.dp)){
                AsyncImage(c.image?.large ?: c.image?.medium,c.name.full,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color(0xFF111117)))))
                Text("×",color=Color.White,fontSize=27.sp,modifier=Modifier.align(Alignment.TopEnd).clickable(onClick=onClose).padding(12.dp))
                Text(c.name.full,color=Color.White,fontSize=21.sp,fontWeight=FontWeight.Bold,modifier=Modifier.align(Alignment.BottomStart).padding(14.dp))
            }
            LazyColumn(contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
                item{Text("ROLE IN THIS ENTRY · ${edge.role}",color=EAccent,fontSize=10.sp,fontWeight=FontWeight.Bold)}
                item{EInfo("Age",c.age)}
                item{EInfo("Gender",c.gender)}
                item{EInfo("Birthday",c.dateOfBirth?.display())}
                item{EInfo("Favourites",c.favourites?.toString())}
                val bio=clean(c.description)
                if(bio.isNotBlank()) item{Text("BIOGRAPHY",color=EAccent,fontSize=10.sp,fontWeight=FontWeight.Bold);Text(bio,color=Color(0xFFE0E0E0),fontSize=12.sp,lineHeight=18.sp)}
                item{Text("Character information supplied by AniList.",color=EMuted,fontSize=10.sp)}
            }
        }
    }
}

@Composable private fun EMetric(label:String,value:String,modifier:Modifier=Modifier){
    Column(modifier.rykoGlass(12.dp).padding(9.dp)){
        Text(label,color=EMuted,fontSize=8.sp,maxLines=1);Text(value,color=Color.White,fontSize=13.sp,fontWeight=FontWeight.Bold,maxLines=1)
    }
}
@Composable private fun EInfo(label:String,value:String?){
    Row(Modifier.fillMaxWidth().rykoGlass(10.dp).padding(11.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Top){
        Text(label,color=EMuted,fontSize=11.sp,modifier=Modifier.weight(.9f))
        Text(value?.takeIf{it.isNotBlank()} ?: "—",color=Color.White,fontSize=11.sp,modifier=Modifier.weight(1.1f),textAlign=androidx.compose.ui.text.style.TextAlign.End)
    }
}
@Composable private fun EEmpty(text:String){Text(text,color=EMuted,fontSize=12.sp,modifier=Modifier.fillMaxWidth().rykoGlass(12.dp).padding(14.dp))}
private fun clean(v:String?)=v.orEmpty().replace(Regex("<[^>]*>")," ").replace("&mdash;","—").replace("&amp;","&").replace(Regex("\\s+")," ").trim()
private fun countdown(s:Long):String{if(s<=0)return "Soon";val d=s/86400;val h=s%86400/3600;val m=s%3600/60;return if(d>0)"${d}d ${h}h" else if(h>0)"${h}h ${m}m" else "${m}m"}
