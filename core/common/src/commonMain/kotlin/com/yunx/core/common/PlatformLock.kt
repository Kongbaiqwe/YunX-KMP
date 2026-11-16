package com.yunx.core.common

expect class PlatformLock() {
    fun <T> withLock(action: () -> T): T
}
