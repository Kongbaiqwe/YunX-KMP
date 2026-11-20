package com.yunx.shared

expect object PlatformSettingsStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}
