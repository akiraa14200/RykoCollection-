package com.rykocollection.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.time.Instant

private val ProfileAccent = Color(0xFFA99BFF)
private val ProfileBackground = Color(0xFF08080C)
private val ProfileSurface = Color(0xFF191820)
private val ProfileMuted = Color(0xFFAAA8B5)

@Serializable
private data class RykoProfileRecord(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String = "",
    val bio: String = "",
    @SerialName("banner_url") val bannerUrl: String = "",
    @SerialName("public_lists") val publicLists: Boolean = true,
    @SerialName("is_public") val isPublic: Boolean = true,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Composable
internal fun NativeProfilePage(
    client: SupabaseClient,
    userId: String,
    onSignOut: suspend () -> Unit,
    isAdmin: Boolean = false,
    onOpenAdmin: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var profile by remember(userId) { mutableStateOf<RykoProfileRecord?>(null) }
    var loading by remember(userId) { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var ratingsCount by remember { mutableIntStateOf(0) }
    var reviewsCount by remember { mutableIntStateOf(0) }
    var libraryCount by remember { mutableIntStateOf(0) }
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }

    fun copyProfileToFields(value: RykoProfileRecord) {
        displayName = value.displayName
        username = value.username
        bio = value.bio
        avatarUrl = value.avatarUrl
        isPublic = value.isPublic
    }

    LaunchedEffect(userId) {
        loading = true
        error = null
        try {
            val existing = client.postgrest["profiles"].select {
                filter { eq("id", userId) }
            }.decodeList<RykoProfileRecord>().firstOrNull()

            val loaded = existing ?: RykoProfileRecord(
                id = userId,
                username = "reader_${userId.replace("-", "").take(10)}",
                displayName = "Reader"
            ).also { client.postgrest["profiles"].insert(it) }

            profile = loaded
            copyProfileToFields(loaded)

            ratingsCount = runCatching {
                client.postgrest["ryko_media_opinions"].select {
                    filter { eq("user_id", userId) }
                }.decodeList<JsonObject>().size
            }.getOrDefault(0)

            reviewsCount = runCatching {
                client.postgrest["title_reviews"].select {
                    filter { eq("user_id", userId) }
                }.decodeList<JsonObject>().size
            }.getOrDefault(0)

            libraryCount = runCatching {
                client.postgrest["user_library"].select {
                    filter { eq("user_id", userId) }
                }.decodeList<JsonObject>().size
            }.getOrDefault(0)
        } catch (t: Throwable) {
            error = t.message ?: "Couldn't load your profile. Please try again."
        } finally {
            loading = false
        }
    }

    Column(
        Modifier.fillMaxSize().background(ProfileBackground).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (loading) {
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ProfileAccent)
            }
        } else if (profile != null) {
            val current = profile!!
            Column(
                Modifier.fillMaxWidth().rykoGlass(24.dp, Color(0xC51B1925), 0.42f).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                if (current.avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = current.avatarUrl,
                        contentDescription = "Profile picture",
                        modifier = Modifier.size(86.dp).clip(CircleShape)
                    )
                } else {
                    Box(
                        Modifier.size(86.dp).clip(CircleShape).background(Color(0xFF39324E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            current.displayName.firstOrNull()?.uppercase() ?: "R",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(current.displayName, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                Text("@${current.username}", color = ProfileMuted, fontSize = 12.sp)
                if (current.bio.isNotBlank()) {
                    Text(current.bio, color = Color(0xFFE0DDE8), fontSize = 12.sp, lineHeight = 18.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (current.isPublic) "Public profile" else "Private profile", color = ProfileAccent, fontSize = 11.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { editing = true; notice = null; error = null },
                        colors = ButtonDefaults.buttonColors(containerColor = ProfileAccent, contentColor = Color(0xFF17151F))
                    ) { Text("Edit profile", fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            scope.launch {
                                saving = true
                                try { onSignOut() }
                                catch (t: Throwable) { error = t.message ?: "Sign out failed." }
                                finally { saving = false }
                            }
                        },
                        enabled = !saving,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) { Text("Sign out") }
                }
            }

            if (isAdmin) {
                Button(
                    onClick = onOpenAdmin,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ProfileAccent, contentColor = Color(0xFF17151F))
                ) { Text("Open Admin Workspace", fontWeight = FontWeight.Bold) }
            }


            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ProfileStatCard("Ratings", ratingsCount, Modifier.weight(1f))
                ProfileStatCard("Reviews", reviewsCount, Modifier.weight(1f))
                ProfileStatCard("Library", libraryCount, Modifier.weight(1f))
            }

            Column(
                Modifier.fillMaxWidth().rykoGlass(18.dp, Color(0xB51B1A22), 0.34f).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text("Your Ryko activity", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "Your ratings, reviews and saved titles will appear here. Profile visibility controls what other readers can see.",
                    color = ProfileMuted,
                    fontSize = 11.sp,
                    lineHeight = 17.sp
                )
            }

            if (editing) {
                ProfileEditCard(
                    displayName = displayName,
                    onDisplayNameChange = { displayName = it.take(60) },
                    username = username,
                    onUsernameChange = { username = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' }.take(32) },
                    bio = bio,
                    onBioChange = { bio = it.take(280) },
                    avatarUrl = avatarUrl,
                    onAvatarUrlChange = { avatarUrl = it.take(500) },
                    isPublic = isPublic,
                    onPublicChange = { isPublic = it },
                    saving = saving,
                    onCancel = { copyProfileToFields(current); editing = false; error = null },
                    onSave = {
                        if (displayName.isBlank()) {
                            error = "Display name cannot be empty."
                        } else if (username.length < 2) {
                            error = "Username must contain at least 2 characters."
                        } else {
                            scope.launch {
                                saving = true
                                error = null
                                notice = null
                                try {
                                    val updated = current.copy(
                                        username = username,
                                        displayName = displayName.trim(),
                                        bio = bio.trim(),
                                        avatarUrl = avatarUrl.trim(),
                                        isPublic = isPublic,
                                        publicLists = isPublic,
                                        updatedAt = Instant.now().toString()
                                    )
                                    client.postgrest["profiles"].upsert(updated)
                                    profile = updated
                                    editing = false
                                    notice = "Profile updated."
                                } catch (t: Throwable) {
                                    error = t.message ?: "Couldn't save your profile. Check whether the username is already taken."
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    }
                )
            }
        }

        error?.let { Text(it, color = Color(0xFFFFA6AE), fontSize = 11.sp, lineHeight = 16.sp) }
        notice?.let { Text(it, color = ProfileAccent, fontSize = 11.sp) }
        Text(
            "Your email and sign-in details are never displayed on your public profile.",
            color = ProfileMuted,
            fontSize = 10.sp,
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun ProfileStatCard(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier.rykoGlass(15.dp, Color(0xB51B1A22), 0.30f).padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(value.toString(), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text(label, color = ProfileMuted, fontSize = 10.sp)
    }
}

@Composable
private fun ProfileEditCard(
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    bio: String,
    onBioChange: (String) -> Unit,
    avatarUrl: String,
    onAvatarUrlChange: (String) -> Unit,
    isPublic: Boolean,
    onPublicChange: (Boolean) -> Unit,
    saving: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().background(ProfileSurface, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Text("Edit profile", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        OutlinedTextField(
            value = displayName, onValueChange = onDisplayNameChange,
            modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Display name") }
        )
        OutlinedTextField(
            value = username, onValueChange = onUsernameChange,
            modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Username") },
            leadingIcon = { Text("@", color = ProfileMuted) }
        )
        OutlinedTextField(
            value = bio, onValueChange = onBioChange,
            modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4, label = { Text("Bio") }
        )
        OutlinedTextField(
            value = avatarUrl, onValueChange = onAvatarUrlChange,
            modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Avatar image URL") }
        )
        HorizontalDivider(color = Color(0xFF34313D))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Public profile", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(
                    if (isPublic) "Other readers can see your profile and activity."
                    else "Only you can see your profile and activity.",
                    color = ProfileMuted,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
            Switch(checked = isPublic, onCheckedChange = onPublicChange)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, enabled = !saving, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            Button(
                onClick = onSave,
                enabled = !saving,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = ProfileAccent, contentColor = Color(0xFF17151F))
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Save changes", fontWeight = FontWeight.Bold)
            }
        }
    }
}
