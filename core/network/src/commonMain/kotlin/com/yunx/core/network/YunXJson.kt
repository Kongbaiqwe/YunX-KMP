package com.yunx.core.network

import kotlinx.serialization.json.Json

/**
 * 全局统一 JSON 解析配置：
 * - ignoreUnknownKeys: 宽松解析未知字段，防止网盘服务端增加字段时崩溃；
 * - isLenient: 允许宽松格式；
 * - encodeDefaults: 编码时输出默认值；
 * - coerceInputValues: 遇到不匹配类型（如 null 赋给非空默认值）自动回退到默认值。
 */
val YunXJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    prettyPrint = false
    coerceInputValues = true
}
