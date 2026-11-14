package com.hydro.reedance.install

import com.google.gson.*
import com.hydro.reedance.data.*

/** Evaluator for the bundled contract's JSON-schema vocabulary; no remote refs. */
class LessonValidation(private val root: JsonObject) {
    fun validate(value: JsonElement) { check(value, root, 0) }
    private fun check(v: JsonElement, s: JsonObject, depth: Int): Set<String> {
        require(depth < 80)
        val evaluated = mutableSetOf<String>()
        if(s.has("\$ref")) {
            val ref = s.s("\$ref"); require(ref.startsWith("#/\$defs/"))
            evaluated += check(v, root.obj("\$defs").getAsJsonObject(ref.removePrefix("#/\$defs/")), depth + 1)
        }
        s.getAsJsonArray("allOf")?.forEach { evaluated += check(v, it.asJsonObject, depth + 1) }
        s.getAsJsonArray("oneOf")?.let { choices ->
            val matches = choices.mapNotNull { candidate ->
                try { check(v, candidate.asJsonObject, depth + 1) } catch(_: IllegalArgumentException) { null }
            }
            require(matches.size == 1) { "Schema oneOf mismatch" }; evaluated += matches.single()
        }
        if(s.has("const")) require(v == s.get("const"))
        s.getAsJsonArray("enum")?.let { require(it.any { item -> item == v }) }
        when(s.s("type")) {
            "object" -> require(v.isJsonObject)
            "array" -> require(v.isJsonArray)
            "string" -> require(v.isJsonPrimitive && v.asJsonPrimitive.isString)
            "boolean" -> require(v.isJsonPrimitive && v.asJsonPrimitive.isBoolean)
            "integer" -> require(v.isJsonPrimitive && v.asJsonPrimitive.isNumber && v.asBigDecimal.stripTrailingZeros().scale() <= 0)
            "" -> Unit
            else -> error("Unsupported schema type")
        }
        if(v.isJsonObject) {
            val obj = v.asJsonObject
            s.getAsJsonArray("required")?.forEach { require(obj.has(it.asString)) }
            val props = s.getAsJsonObject("properties") ?: JsonObject()
            props.entrySet().forEach { (key, schema) -> if(obj.has(key)) { check(obj.get(key), schema.asJsonObject, depth + 1); evaluated += key } }
            if(s.get("additionalProperties")?.asBoolean == false) require(obj.keySet().all { props.has(it) })
            if(s.get("unevaluatedProperties")?.asBoolean == false) require(obj.keySet().all { it in evaluated })
        }
        if(v.isJsonArray) {
            val arr = v.asJsonArray
            require(arr.size().toLong() in s.n("minItems", 0)..s.n("maxItems", Long.MAX_VALUE))
            if(s.get("uniqueItems")?.asBoolean == true) require(arr.toList().toSet().size == arr.size())
            s.getAsJsonObject("items")?.let { schema -> arr.forEach { check(it, schema, depth + 1) } }
        }
        if(v.isJsonPrimitive && v.asJsonPrimitive.isString) {
            val text = v.asString
            require(text.codePointCount(0, text.length).toLong() in s.n("minLength", 0)..s.n("maxLength", Long.MAX_VALUE))
            if(s.has("pattern")) require(Regex(s.s("pattern")).containsMatchIn(text))
        }
        if(v.isJsonPrimitive && v.asJsonPrimitive.isNumber) {
            if(s.has("minimum")) require(v.asBigDecimal >= s.get("minimum").asBigDecimal)
            if(s.has("maximum")) require(v.asBigDecimal <= s.get("maximum").asBigDecimal)
        }
        return evaluated
    }
    fun semantics(lesson: JsonObject) {
        val lang = lesson.s("targetLanguage")
        require(lesson.s("locale").startsWith("$lang-"))
        // Do not silently reinterpret learning history without a migration implementation.
        require(lesson.a("idAliases").isEmpty()) { "ID alias migration requires an app update" }
        fun tables(items: List<JsonObject>) {
            items.forEach { table ->
                val columns = table.getAsJsonArray("columns").size()
                table.getAsJsonArray("rows").forEach { require(it.asJsonArray.size() == columns) }
            }
        }
        tables(lesson.a("vocabularyTables"))
        lesson.a("vocabulary").forEach { word ->
            val key = word.s("normalizedKey")
            require(key == java.text.Normalizer.normalize(key, java.text.Normalizer.Form.NFC).lowercase(java.util.Locale.forLanguageTag(lesson.s("locale"))))
            tables(word.a("tables"))
        }
        val words = lesson.a("vocabulary").map { it.s("lexemeId") }
        require(words.toSet().size == words.size && words.all { it.startsWith("$lang:") })
        val lineIds = lesson.a("lyrics").map { it.s("id") }.toSet()
        val duration = lesson.obj("media").obj("audio").n("durationMs")
        var previous = -1L
        lesson.a("lyrics").forEach { line ->
            val start = line.n("startMs"); require(start > previous && start < duration); previous = start
            if(line.has("endMs")) require(line.n("endMs") in (start + 1)..duration)
            line.a("tokens").forEach { if(it.has("lexemeId")) require(it.s("lexemeId") in words) }
        }
        for(section in listOf("lyrics", "grammar", "idioms", "sentences", "questions")) {
            val ids = lesson.a(section).map { it.s("id") }; require(ids.toSet().size == ids.size)
        }
        for(section in listOf("sentences", "idioms")) lesson.a(section).forEach { item ->
            if(item.has("lineId")) require(item.s("lineId") in lineIds)
        }
        lesson.a("questions").forEach { q ->
            if(q.has("lineId")) require(q.s("lineId") in lineIds)
            require(q.strings("relatedLexemeIds").all { it in words })
            q.a("pairs").forEach { if(it.has("lexemeId")) require(it.s("lexemeId") in words) }
            when(q.s("type")) {
                "typing" -> {
                    val accepted = q.strings("accepted").map {
                        java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFC).trim()
                            .lowercase(java.util.Locale.forLanguageTag(lesson.s("locale"))).trimEnd('.', '!', '?', ',', '…')
                    }
                    require(accepted.none { it.isBlank() } && accepted.toSet().size == accepted.size)
                }
                "choice" -> { val ids = q.a("options").map { it.s("id") }; require(ids.toSet().size == ids.size && q.s("correctId") in ids) }
                "match" -> { val ids = q.a("pairs").map { it.s("id") }; require(ids.toSet().size == ids.size && (!q.has("meaningOrder") || q.strings("meaningOrder").toSet() == ids.toSet())) }
                "order" -> { val ids = q.a("chunks").map { it.s("id") }; require(ids.toSet().size == ids.size && (!q.has("presentationOrder") || q.strings("presentationOrder").toSet() == ids.toSet())) }
            }
        }
    }
}
