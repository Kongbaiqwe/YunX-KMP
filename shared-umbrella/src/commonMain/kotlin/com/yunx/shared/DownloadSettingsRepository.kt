package com.yunx.shared

class DownloadSettingsRepository {
    var maxConcurrentDownloads: Int
        get() = PlatformSettingsStore.getString(KEY_MAX_CONCURRENT)
            ?.toIntOrNull()
            ?.coerceIn(MIN_CONCURRENT, MAX_CONCURRENT)
            ?: DEFAULT_MAX_CONCURRENT
        set(value) {
            PlatformSettingsStore.putString(
                KEY_MAX_CONCURRENT,
                value.coerceIn(MIN_CONCURRENT, MAX_CONCURRENT).toString()
            )
        }

    var downloadEngine: String
        get() = PlatformSettingsStore.getString(KEY_ENGINE)
            ?.takeIf { it == ENGINE_GOPEED || it == ENGINE_BUILTIN }
            ?: ENGINE_GOPEED
        set(value) {
            PlatformSettingsStore.putString(
                KEY_ENGINE,
                if (value == ENGINE_BUILTIN) ENGINE_BUILTIN else ENGINE_GOPEED
            )
        }

    companion object {
        const val ENGINE_GOPEED = "gopeed"
        const val ENGINE_BUILTIN = "builtin"

        const val DEFAULT_MAX_CONCURRENT = 3
        const val MIN_CONCURRENT = 1
        const val MAX_CONCURRENT = 10

        private const val KEY_MAX_CONCURRENT = "download_max_concurrent"
        private const val KEY_ENGINE = "download_engine"
    }
}
