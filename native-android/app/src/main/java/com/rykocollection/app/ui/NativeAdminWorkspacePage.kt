package com.rykocollection.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AdminBg = Color(0xFF000000)
private val AdminPanel = Color(0xFF0C0C0C)
private val AdminCard = Color(0xFF101010)
private val AdminMuted = Color(0xFFA1A1AA)
private val AdminAccent = Color(0xFF9B8CFF)
private val AdminLine = Color(0x17FFFFFF)

private data class WorkspaceTab(val id: String, val label: String, val icon: String)
private val workspaceTabs = listOf(
    WorkspaceTab("overview", "Overview", "▦"),
    WorkspaceTab("catalogue", "Catalogue", "▤"),
    WorkspaceTab("characters", "Characters", "♙"),
    WorkspaceTab("videos", "Related videos", "↗"),
    WorkspaceTab("moderation", "Moderation", "◇"),
    WorkspaceTab("reports", "Reports", "⚑"),
    WorkspaceTab("settings", "Website settings", "⚙"),
    WorkspaceTab("storage", "Storage", "▣")
)

@Composable
internal fun NativeAdminWorkspacePage(
    email: String,
    onBack: () -> Unit,
    onRefreshAccess: () -> Unit,
    checkingAccess: Boolean
) {
    var selectedTab by remember { mutableStateOf("overview") }
    Column(Modifier.fillMaxSize().background(AdminBg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 5.dp)) {
                Text("‹ Account", color = AdminMuted, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onRefreshAccess, enabled = !checkingAccess) {
                if (checkingAccess) CircularProgressIndicator(Modifier.size(14.dp), color = AdminAccent, strokeWidth = 2.dp)
                else Text("Recheck access", color = AdminMuted, fontSize = 11.sp)
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Website hero: Control workspace.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("◈", color = AdminAccent, fontSize = 13.sp)
                    Text("RYKOCOLLECTION · ADMIN", color = AdminAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                }
                Text("Control workspace.", color = Color(0xFFF7F7FB), fontSize = 29.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.2).sp)
                Text("Changes sync directly to the live RykoCollection website catalogue and community.", color = AdminMuted, fontSize = 11.sp, lineHeight = 17.sp)
                Row(
                    Modifier.fillMaxWidth().padding(top = 5.dp).background(AdminPanel, RoundedCornerShape(12.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("SIGNED IN AS", color = AdminMuted, fontSize = 8.sp, letterSpacing = 1.1.sp)
                        Text(email.ifBlank { "Administrator" }, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("ADMIN", color = AdminAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
            // Website sidebar navigation becomes a horizontally scrollable nav on phones.
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("WORKSPACE", color = AdminMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    workspaceTabs.forEach { tab ->
                        val active = selectedTab == tab.id
                        Row(
                            Modifier.background(if (active) Color(0xFF211C36) else AdminPanel, RoundedCornerShape(10.dp))
                                .clickable { selectedTab = tab.id }.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Text(tab.icon, color = if (active) AdminAccent else AdminMuted, fontSize = 13.sp)
                            Text(tab.label, color = if (active) Color.White else AdminMuted, fontSize = 10.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
                OutlinedButton(
                    onClick = onRefreshAccess,
                    enabled = !checkingAccess,
                    modifier = Modifier.align(Alignment.Start).height(35.dp),
                    contentPadding = PaddingValues(horizontal = 11.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AdminLine),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AdminMuted)
                ) {
                    if (checkingAccess) CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 2.dp, color = AdminAccent)
                    else Text("↻  Refresh data", fontSize = 10.sp)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (selectedTab) {
                    "overview" -> OverviewContent()
                    "catalogue" -> SectionPlaceholder("PUBLISHING", "Catalogue manager", "Add, update, feature or remove entries shared with the website.")
                    "characters" -> SectionPlaceholder("CAST DATABASE", "Character profiles", "Add and remove character profiles for shared title pages.")
                    "videos" -> SectionPlaceholder("RYKOTUBE", "Related videos", "Manage Shorts, trusted sources, discovery and published videos.")
                    "moderation" -> SectionPlaceholder("COMMUNITY SAFETY", "Pending moderation", "Approve or reject comments awaiting review.")
                    "reports" -> SectionPlaceholder("READER FEEDBACK", "Community reports", "Review user-submitted reports. Dismissing a report does not remove its content.")
                    "settings" -> SectionPlaceholder("SITE CONFIGURATION", "Website settings", "Update social and contact links used by the live website footer.")
                    "storage" -> SectionPlaceholder("FILE USAGE", "Storage overview", "Usage from the website's protected admin_storage_usage function.")
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun OverviewContent() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("LIVE DATABASE", color = AdminAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
            Text("Dashboard overview", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
            Text("Current counts from the shared website database.", color = AdminMuted, fontSize = 10.sp, lineHeight = 15.sp)
        }
        Text("↻ Refresh", color = AdminAccent, fontSize = 10.sp)
    }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            WebsiteStatCard("Catalogue entries", "—", Modifier.weight(1f))
            WebsiteStatCard("Character profiles", "—", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            WebsiteStatCard("Related videos", "—", Modifier.weight(1f))
            WebsiteStatCard("Pending moderation", "—", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            WebsiteStatCard("Community reports", "—", Modifier.weight(1f))
            WebsiteStatCard("Stored files", "—", Modifier.weight(1f))
        }
    }
    Column(Modifier.fillMaxWidth().background(AdminPanel, RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Quick actions", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text("＋  Add catalogue entry", color = AdminAccent, fontSize = 11.sp)
        Text("↗  Add related video", color = AdminAccent, fontSize = 11.sp)
        Text("◇  Review comments", color = AdminAccent, fontSize = 11.sp)
    }
    Text("Access is verified against the website's admin_users table. Database row-level security remains responsible for protecting writes.", color = AdminMuted, fontSize = 9.sp, lineHeight = 14.sp)
}

@Composable
private fun WebsiteStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(AdminCard, RoundedCornerShape(13.dp)).padding(horizontal = 13.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = AdminMuted, fontSize = 10.sp, lineHeight = 14.sp)
        Text(value, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionPlaceholder(kicker: String, heading: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(kicker, color = AdminAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
        Text(heading, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(description, color = AdminMuted, fontSize = 11.sp, lineHeight = 17.sp)
    }
    Column(Modifier.fillMaxWidth().background(AdminPanel, RoundedCornerShape(14.dp)).padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("Management controls", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("This section will use the same controls, forms and records as the website Admin Workspace.", color = AdminMuted, fontSize = 10.sp, lineHeight = 15.sp)
    }
}
