package com.yunx.core.network

import io.ktor.client.engine.HttpClientEngine

/**
 * 跨平台底层 HTTP 引擎工厂
 */
expect fun createPlatformEngine(): HttpClientEngine
