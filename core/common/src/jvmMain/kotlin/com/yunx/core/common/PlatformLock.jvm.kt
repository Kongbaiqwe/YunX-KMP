package com.yunx.core.common

actual class PlatformLock actual constructor() {
    private val lock = Any()
    actual fun <T> withLock(action: () -> T): T = synchronized(lock) { action() }
}
