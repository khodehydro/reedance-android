package com.hydro.reedance.data

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.io.File

/** Main-safe catalog and installed production lesson reads. */
class LocalContentRepository(context: Context, private val dao: LearningDao,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val cpu: CoroutineDispatcher = Dispatchers.Default) {
    private val app = context.applicationContext
    // Bound retained parsed lessons; catalog reads must not grow memory with the whole library.
    private val lessonCache = java.util.Collections.synchronizedMap(object : LinkedHashMap<String, JsonObject>(4, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, JsonObject>?): Boolean = size > 4
    })
    fun cachedLesson(id: String): JsonObject? = lessonCache[id]

    private suspend fun assetText(name: String) = withContext(io) { app.assets.open(name).bufferedReader().use { it.readText() } }
    private suspend fun parseObject(text: String) = withContext(cpu) { JsonParser.parseString(text).asJsonObject }
    suspend fun catalog(): List<JsonObject> {
        val text = assetText("catalog_preview.json")
        val songs = withContext(cpu) { JsonParser.parseString(text).asJsonArray.map { it.asJsonObject } }
        val distribution = try { parseObject(assetText("pack_distribution.json")) } catch(_: java.io.FileNotFoundException) { JsonObject() }
        val bundled = try { parseObject(assetText("bundled_packs.json")) } catch(_: java.io.FileNotFoundException) { JsonObject() }
        songs.forEach { song ->
            val packSize = bundled.obj(song.s("id")).n("sizeBytes")
            if(packSize > 0) song.addProperty("packageSizeBytes", packSize)
            val actualId = distribution.obj("catalogBindings").s(song.s("distributionRef"))
            if(actualId.isNotEmpty()) song.addProperty("id", actualId)
        }
        val replacedPreviewIds = mutableSetOf<String>()
        val installed = dao.installedPackages().filter { it.state in listOf("VERIFIED", "LOCAL_PASSWORD") }.mapNotNull { row ->
            installedLesson(row.songId)?.let { data ->
                // Metadata match is only for merging a display placeholder; identity always comes from lesson.json.
                val preview = songs.firstOrNull { it.s("id") == row.songId } ?: songs.firstOrNull {
                    it.s("title") == data.s("title") && it.s("artist") == data.s("artist") && it.s("language") == data.s("targetLanguage")
                }
                preview?.let { replacedPreviewIds += it.s("id") }
                (preview?.deepCopy() ?: JsonObject()).apply {
                    addProperty("id", row.songId); addProperty("title", data.s("title")); addProperty("artist", data.s("artist"))
                    addProperty("language", data.s("targetLanguage")); addProperty("level", data.s("level"))
                    if(!has("access")) addProperty("access", "free")
                    addProperty("localImport", row.state == "LOCAL_PASSWORD")
                }
            }
        }
        return installed + songs.filter { it.s("id") !in replacedPreviewIds }
    }
    suspend fun installedLesson(id: String): JsonObject? = withContext(io) {
        val row = dao.installed(id) ?: run { lessonCache.remove(id); return@withContext null }
        val file = File(row.activePath, "lesson.json")
        check(file.isFile && row.state in listOf("VERIFIED", "LOCAL_PASSWORD")) { "Installed package is unavailable" }
        require(file.length() <= 4 * 1024 * 1024)
        com.hydro.reedance.contract.PackVerifier.strictJson(file.inputStream().use { it.readBytes() }, 4 * 1024 * 1024).also { lessonCache[id] = it }
    }
    suspend fun audioFile(id: String, lesson: JsonObject): File = withContext(io) {
        val row = dao.installed(id) ?: error("Package not installed")
        File(row.activePath, lesson.obj("media").obj("audio").s("path")).also { require(it.isFile) }
    }
    suspend fun coverPath(id: String, lesson: JsonObject): String? = withContext(io) {
        val row = dao.installed(id) ?: return@withContext null
        val name = lesson.obj("media").obj("cover").s("path")
        if(name.isBlank()) return@withContext null
        File(row.activePath, name).takeIf { it.isFile }?.path
    }
    suspend fun starters(): JsonObject = StarterPreload.courses ?: StarterPreload.loadMutex.withLock {
        StarterPreload.courses ?: parseObject(assetText("starter.json")).also { StarterPreload.courses = it }
    }
}
