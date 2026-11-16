package com.yunx.core.common

import platform.Foundation.NSLock

actual class PlatformLock actual constructor() {
    private val lock = NSLock()
    actual fun <T> withLock(action: () -> T): T {
        lock.lock()
        try {
            return action()
        } finally {
            lock.unlock()
        }
    }
}
