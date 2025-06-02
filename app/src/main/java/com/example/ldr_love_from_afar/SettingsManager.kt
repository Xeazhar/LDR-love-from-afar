package com.example.ldr_love_from_afar

import android.content.Context

class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("user_settings", Context.MODE_PRIVATE)

    init {
        // Only run this block once, on the very first time the app is installed/started.
        if (!prefs.contains("initialized")) {
            prefs.edit()
                .putBoolean("initialized", true)
                // Default: Kikay Mode = ON
                .putBoolean("kikay_mode", true)
                // Default: “me” = New York
                .putString("timezone_mine", "America/New_York")
                // Default: “him” = Manila
                .putString("timezone_other", "Asia/Manila")
                .apply()
        }
    }

    var timezoneMine: String
        get() = prefs.getString("timezone_mine", "America/New_York") ?: "America/New_York"
        set(value) = prefs.edit().putString("timezone_mine", value).apply()

    var timezoneOther: String
        get() = prefs.getString("timezone_other", "Asia/Manila") ?: "Asia/Manila"
        set(value) = prefs.edit().putString("timezone_other", value).apply()

    var isKikay: Boolean
        get() = prefs.getBoolean("kikay_mode", true)
        set(value) = prefs.edit().putBoolean("kikay_mode", value).apply()
}
