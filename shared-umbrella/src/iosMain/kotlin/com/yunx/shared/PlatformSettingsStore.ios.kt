package com.yunx.shared

import platform.Foundation.NSUserDefaults

actual object PlatformSettingsStore {
    actual fun getString(key: String): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(key)

    actual fun putString(key: String, value: String) {
        NSUserDefaults.standardUserDefaults.setObject(value, forKey = key)
    }
}
