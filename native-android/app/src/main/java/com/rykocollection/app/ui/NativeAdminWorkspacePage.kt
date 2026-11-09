package com.rykocollection.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

private val AdminAccent = Color(0xFFA99BFF)
private val AdminMuted = Color(0xFFAAA8B5)
private val AdminBackground = Color(0xFF08080C)
private val AdminSurface = Color(0xFF191820)
private val AdminSurfaceAlt = Color(0xFF20202A)

private data class AdminSection(
    val title: String,
    val subtitle: String,
    val number: String,
    val description: String,
    val actions: List<String>
)

private val adminSections = listOf(
    AdminSection("Overview", "Dashboard and activity", "01", "A live snapshot of catalogue health, community activity and pending work.", listOf("Refresh dashboard", "Recent activity", "Quick actions")),
    AdminSection("Catalogue", "Anime and manga entries", "02", "Find, import, add and edit the shared anime and manga catalogue.", listOf("Search entries", "Filter anime and manga", "Add entry", "Edit metadata", "AniList import")),
    AdminSection("Characters", "Character profiles", "03", "Manage character profiles and connect each character to its catalogue entry.", listOf("Search characters", "Filter by title", "Add character", "Edit profile")),
    AdminSection("Related videos", "RykoTube library", "04", "Manage published videos, Shorts, trusted sources and the discovery review queue.", listOf("Published videos", "Shorts", "Trusted sources", "Discovery queue", "Add video")),
    AdminSection("Moderation", "Community comments", "05", "Review pending community comments before they appear publicly.", listOf("Pending comments", "Approved comments", "Rejected comments", "Open related entry")),
    AdminSection("Reports", "User-submitted reports", "06", "Review reports about comments and videos without deleting the reported content when dismissing a report.", listOf("Open reports", "Resolved reports", "Dismissed reports", "Review reported content")),
    AdminSection("Website settings", "Public links and contact", "07", "Manage the public social and contact links used by RykoCollection.", listOf("Social links", "Contact information", "Save website settings")),
    AdminSection("Storage", "Storage usage", "08", "Review storage usage reported by the protected admin storage function.", listOf("Total files", "Storage used", "Usage by bucket"))
)

@Composable
internal fun NativeAdminWorkspacePage(
    email: String,
    onBack: () -> Unit,
    onRefreshAccess: () -> Unit,
    checkingAccess: Boolean
) {
    var selectedSection by remember { mutableStateOf("Overview") }
    val selected = adminSections.first { it.title == selectedSection }

    Column(
        Modifier.fillMaxSize().background(AdminBackground)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = if (selectedSection == "Overview") onBack else { { selectedSection = "Overview" } }) {
                Text(if (selectedSection == "Overview") "← Account" else "← Workspace", color = AdminAccent, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onRefreshAccess, enabled = !checkingAccess) {
                if (checkingAccess) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = AdminAccent)
                else Text("Verify access", color = AdminAccent, fontSize = 11.sp)
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("RYKO CONTROL CENTER", color = AdminAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text(if (selectedSection == "Overview") "Admin workspace" else selected.title, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                Text(if (selectedSection == "Overview") "A focused place to manage RykoCollection." else selected.subtitle, color = AdminMuted, fontSize = 11.sp)
            }

            if (selectedSection == "Overview") {
                Row(
                    Modifier.fillMaxWidth().background(Color(0xFF18251F), RoundedCornerShape(14.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("✓", color = Color(0xFF8BE0B0), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Admin access verified", color = Color(0xFFB8F0CE), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(email.ifBlank { "Signed-in admin account" }, color = AdminMuted, fontSize = 10.sp)
                    }
                }
                Text("AT A GLANCE", color = AdminMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        AdminMetricCard("Catalogue", "—", "Anime & manga", Modifier.weight(1f))
                        AdminMetricCard("Characters", "—", "Profiles", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        AdminMetricCard("Videos", "—", "Published", Modifier.weight(1f))
                        AdminMetricCard("Moderation", "—", "Pending review", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        AdminMetricCard("Reports", "—", "Awaiting review", Modifier.weight(1f))
                        AdminMetricCard("Storage", "—", "Usage", Modifier.weight(1f))
                    }
                }
                Text("QUICK ACCESS", color = AdminMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    adminSections.drop(1).forEach { section ->
                        AdminSectionRow(section) { selectedSection = section.title }
                    }
                }
                Text("Signed in as $email", color = AdminMuted, fontSize = 10.sp)
            } else {
                Row(
                    Modifier.fillMaxWidth().background(AdminSurfaceAlt, RoundedCornerShape(14.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(selected.number, color = AdminAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(selected.subtitle, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(selected.description, color = AdminMuted, fontSize = 10.sp, lineHeight = 15.sp)
                    }
                }
                Text("SECTION TOOLS", color = AdminMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                selected.actions.forEach { action ->
                    Row(
                        Modifier.fillMaxWidth().background(AdminSurface, RoundedCornerShape(13.dp)).padding(horizontal = 14.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(action, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Management control will be connected in its implementation phase.", color = AdminMuted, fontSize = 9.sp, lineHeight = 13.sp)
                        }
                        Text("—", color = AdminMuted, fontSize = 16.sp)
                    }
                }
                Text("WORKSPACE NAVIGATION", color = AdminMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    adminSections.filter { it.title != selectedSection }.forEach { section ->
                        AdminSectionRow(section) { selectedSection = section.title }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun AdminMetricCard(label: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Column(
        modifier.background(AdminSurface, RoundedCornerShape(14.dp)).padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(label, color = AdminMuted, fontSize = 10.sp)
        Text(value, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(detail, color = AdminMuted, fontSize = 9.sp)
    }
}

@Composable
private fun AdminSectionRow(section: AdminSection, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(AdminSurface, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(section.number, color = AdminAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(section.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(section.subtitle, color = AdminMuted, fontSize = 9.sp)
        }
        Text("›", color = AdminMuted, fontSize = 22.sp)
    }
}
