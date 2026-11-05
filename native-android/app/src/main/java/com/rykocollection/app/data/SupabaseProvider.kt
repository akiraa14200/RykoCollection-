package com.rykocollection.app.data

import com.rykocollection.app.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

object SupabaseProvider {
    val client by lazy {
        require(BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) {
            "Set SUPABASE_PUBLISHABLE_KEY before using account features."
        }
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
        }
    }

    fun initialize() {
        // Public AniList browsing works without Supabase credentials.
        // Account features initialize Supabase only when a publishable key is configured.
        if (BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) client
    }
}
