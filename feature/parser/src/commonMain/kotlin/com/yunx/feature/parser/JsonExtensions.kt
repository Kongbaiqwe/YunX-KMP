package com.yunx.feature.parser

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

fun JsonElement?.str(): String = this.strOrNull().orEmpty()

fun JsonElement?.strOrNull(): String? {
    if (this == null || this is JsonNull) return null
    return (this as? JsonPrimitive)?.content
}

fun JsonElement?.longOr(default: Long = 0L): Long {
    if (this == null || this is JsonNull) return default
    return (this as? JsonPrimitive)?.longOrNull ?: default
}

fun JsonElement?.intOr(default: Int = 0): Int {
    if (this == null || this is JsonNull) return default
    return (this as? JsonPrimitive)?.intOrNull ?: default
}

fun JsonElement?.boolOr(default: Boolean = false): Boolean {
    if (this == null || this is JsonNull) return default
    return (this as? JsonPrimitive)?.booleanOrNull ?: default
}

val JsonElement?.obj: JsonObject? get() = this as? JsonObject
val JsonElement?.arr: JsonArray? get() = this as? JsonArray
