package com.duggustore.app.data.remote

import com.duggustore.app.BuildConfig

object SupabaseClient {
    val url: String get() = BuildConfig.SUPABASE_URL
    val anonKey: String get() = BuildConfig.SUPABASE_ANON_KEY
}
