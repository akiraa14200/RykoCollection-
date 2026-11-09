package com.rykocollection.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rykocollection.app.BuildConfig
import com.rykocollection.app.data.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch

private val AccountAccent = Color(0xFFA99BFF)
private val AccountMuted = Color(0xFFAAA8B5)

@Composable
fun NativeAccountPage() {
    if (BuildConfig.SUPABASE_PUBLISHABLE_KEY.isBlank()) {
        AccountMessage(
            "Connect Ryko to Supabase",
            "Authentication is implemented, but this build has no Supabase publishable key. Add SUPABASE_PUBLISHABLE_KEY to the Android build environment to enable accounts."
        )
        return
    }

    val client = remember { SupabaseProvider.client }
    val status by client.auth.sessionStatus.collectAsState()
    val scope = rememberCoroutineScope()
    var createAccount by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { client.auth.awaitInitialization() }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF08080C))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        when (val current = status) {
            SessionStatus.Initializing -> Box(Modifier.fillMaxWidth().padding(35.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccountAccent)
            }
            is SessionStatus.Authenticated -> {
                NativeProfilePage(
                    client = client,
                    userId = current.session.user?.id.orEmpty(),
                    onSignOut = { client.auth.signOut() }
                )
            }
            else -> {
                Column(
                    Modifier.fillMaxWidth().background(Color(0xFF191820), RoundedCornerShape(20.dp)).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(if (createAccount) "CREATE ACCOUNT" else "WELCOME BACK", color = AccountAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(if (createAccount) "Join the Ryko community" else "Sign in to Ryko", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = email, onValueChange = { email = it.trim() },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("Email") }, shape = RoundedCornerShape(13.dp)
                    )
                    OutlinedTextField(
                        value = password, onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(13.dp)
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && email.contains("@") && password.length >= 6,
                        onClick = {
                            scope.launch {
                                busy = true
                                message = null
                                try {
                                    if (createAccount) {
                                        val result = client.auth.signUpWith(Email) {
                                            this.email = email
                                            this.password = password
                                        }
                                        message = if (result == null) "Account created and signed in."
                                            else "Account created. Check your email to confirm, then sign in."
                                    } else {
                                        client.auth.signInWith(Email) {
                                            this.email = email
                                            this.password = password
                                        }
                                        message = "You are signed in."
                                    }
                                } catch (t: Throwable) {
                                    message = t.message ?: "Authentication failed. Please try again."
                                } finally { busy = false }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccountAccent, contentColor = Color(0xFF17151F))
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(17.dp), color = Color(0xFF17151F), strokeWidth = 2.dp)
                        else Text(if (createAccount) "Create account" else "Sign in", fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { createAccount = !createAccount; message = null }) {
                        Text(if (createAccount) "Already have an account? Sign in" else "New to Ryko? Create account", color = AccountAccent)
                    }
                    if (!createAccount) {
                        TextButton(
                            enabled = !busy && email.contains("@"),
                            onClick = {
                                scope.launch {
                                    busy = true
                                    message = null
                                    try {
                                        client.auth.resetPasswordForEmail(email)
                                        message = "Password reset instructions sent if this email is registered."
                                    } catch (t: Throwable) { message = t.message ?: "Could not send reset email." }
                                    finally { busy = false }
                                }
                            }
                        ) { Text("Forgot password?", color = AccountMuted) }
                    }
                }
            }
        }
        message?.let {
            Text(it, color = if (it.contains("failed", true) || it.contains("error", true)) Color(0xFFFFA6AE) else AccountAccent, fontSize = 11.sp, lineHeight = 16.sp)
        }
        Text("Authentication and sessions are managed securely by Supabase Auth. Passwords are never stored in Ryko.", color = AccountMuted, fontSize = 10.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun AccountMessage(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().background(Color(0xFF08080C)).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(body, color = AccountMuted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 9.dp))
    }
}
