package com.yunx.shared

import java.util.prefs.Preferences

actual object PlatformSettingsStore {
    private val prefs = Preferences.userRoot().node("com/yunx/shared")

    actual fun getString(key: String): String? = prefs.get(key, null)

    actual fun putString(key: String, value: String) {
        prefs.put(key, value)
    }
}
