package com.rykocollection.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rykocollection.app.data.SupabaseProvider
import com.rykocollection.app.ui.RykoApp
import com.rykocollection.app.ui.theme.RykoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SupabaseProvider.initialize()
        setContent {
            RykoTheme {
                RykoApp()
            }
        }
    }
}
