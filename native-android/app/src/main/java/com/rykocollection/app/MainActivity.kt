package com.rykocollection.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rykocollection.app.data.SupabaseProvider
import io.github.jan.supabase.auth.handleDeeplinks
import com.rykocollection.app.ui.RykoApp
import com.rykocollection.app.ui.theme.RykoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        SupabaseProvider.initialize()
        if (com.rykocollection.app.BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) {
            runCatching { SupabaseProvider.client.handleDeeplinks(intent) }
        }
        setContent {
            RykoTheme {
                RykoApp()
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (com.rykocollection.app.BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) {
            runCatching { SupabaseProvider.client.handleDeeplinks(intent) }
        }
    }
}
