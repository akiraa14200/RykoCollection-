package com.rykocollection.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AdminAccent = Color(0xFFA99BFF)
private val AdminMuted = Color(0xFFAAA8B5)

@Composable
internal fun NativeAdminWorkspacePage(
    email: String,
    onBack: () -> Unit,
    onRefreshAccess: () -> Unit,
    checkingAccess: Boolean
) {
    Column(
        Modifier.fillMaxSize().background(Color(0xFF08080C)).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Account", color = AdminAccent) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onRefreshAccess, enabled = !checkingAccess) {
                if (checkingAccess) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                else Text("Recheck", color = AdminAccent)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("RYKO CONTROL CENTER", color = AdminAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("Admin workspace", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text("Signed in as $email", color = AdminMuted, fontSize = 11.sp)
        }
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF18251F), RoundedCornerShape(15.dp)).padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("✓", color = Color(0xFF8BE0B0), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Column {
                Text("Admin access verified", color = Color(0xFFB8F0CE), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Membership confirmed from the admin_users table.", color = AdminMuted, fontSize = 10.sp)
            }
        }
        Text("WORKSPACE", color = AdminMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        val sections = listOf(
            Triple("Overview", "Catalogue health and moderation summary", "01"),
            Triple("Catalogue", "Add, edit and manage anime and manga entries", "02"),
            Triple("Characters", "Manage character profiles", "03"),
            Triple("Related videos", "Manage RykoTube videos and discovery", "04"),
            Triple("Moderation", "Review community comments", "05"),
            Triple("Reports", "Review community and video reports", "06"),
            Triple("Website settings", "Manage public contact and social links", "07"),
            Triple("Storage", "View protected storage usage", "08")
        )
        sections.forEach { (title, description, number) ->
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF191820), RoundedCornerShape(16.dp)).padding(15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(number, color = AdminAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(description, color = AdminMuted, fontSize = 10.sp, lineHeight = 15.sp)
                }
                Text("›", color = AdminMuted, fontSize = 23.sp)
            }
        }
        Text(
            "This is the secure native workspace shell. Management tools will be enabled in the next implementation phase.",
            color = AdminMuted, fontSize = 10.sp, lineHeight = 15.sp
        )
    }
}
