package com.rykocollection.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun LibraryStatusDialog(
    title: String,
    subtitle: String,
    statuses: List<String>,
    selectedStatus: String?,
    onSelect: (String) -> Unit,
    onRemove: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().rykoGlass(24.dp, Color(0xF01A1A20), 0.48f).padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(subtitle, color = Color(0xFFAAA8B5), fontSize = 11.sp)
            statuses.forEach { status ->
                val active = status == selectedStatus
                Row(Modifier.fillMaxWidth().rykoGlass(13.dp,
                    if (active) Color(0x554F466B) else Color(0x661D1D24),
                    if (active) 0.52f else 0.28f).clickable { onSelect(status) }.padding(horizontal = 13.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(status, color = if (active) Color.White else Color(0xFFE5E3EC), fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                    if (active) Icon(Icons.Default.CheckCircle, null, tint = Color(0xFFA99BFF), modifier = Modifier.size(18.dp))
                }
            }
            if (onRemove != null) TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.BookmarkRemove, null, tint = Color(0xFFFF9292), modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text("Remove from Library", color = Color(0xFFFF9292), fontSize = 12.sp)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cancel", color = Color(0xFFAAA8B5)) }
        }
    }
}
