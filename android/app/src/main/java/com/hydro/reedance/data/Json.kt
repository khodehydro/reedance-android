package com.hydro.reedance.data

import com.google.gson.*
fun JsonObject.s(key: String, fallback: String = "") = get(key)?.takeUnless { it.isJsonNull }?.asString ?: fallback
fun JsonObject.n(key: String, fallback: Long = 0L) = get(key)?.takeUnless { it.isJsonNull }?.asLong ?: fallback
fun JsonObject.a(key: String): List<JsonObject> = getAsJsonArray(key)?.map { it.asJsonObject } ?: emptyList()
fun JsonObject.strings(key: String): List<String> = getAsJsonArray(key)?.map { it.asString } ?: emptyList()
fun JsonObject.obj(key: String): JsonObject = getAsJsonObject(key) ?: JsonObject()
fun JsonObject.tr(key: String, fa: Boolean): String = s(key + if (fa) "Fa" else "En").ifEmpty { s(key) }
fun JsonElement.cell(fa: Boolean): String = if (isJsonObject) asJsonObject.s(if(fa) "fa" else "en") else asString
