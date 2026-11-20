package com.hydro.reedance.data

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.hydro.reedance.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.io.File

/** Main-safe catalog and installed production lesson reads. */
class LocalContentRepository(context: Context, private val dao: LearningDao,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val cpu: CoroutineDispatcher = Dispatchers.Default) {
    private val app = context.applicationContext
    // Bound retained parsed lessons; catalog reads must not grow memory with the whole library.
    private data class CachedLesson(val row: InstalledPackage, val data: JsonObject)
    private val lessonCache = java.util.Collections.synchronizedMap(object : LinkedHashMap<String, CachedLesson>(4, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedLesson>?): Boolean = size > 4
    })
    fun cachedLesson(id: String): JsonObject? = lessonCache[id]?.data

    private suspend fun assetText(name: String) = withContext(io) { app.assets.open(name).bufferedReader().use { it.readText() } }
    private suspend fun parseObject(text: String) = withContext(cpu) { JsonParser.parseString(text).asJsonObject }
    suspend fun catalog(): List<JsonObject> = withContext(cpu) {
        val text = assetText("catalog_preview.json")
        val songs = JsonParser.parseString(text).asJsonArray.map { it.asJsonObject }
        val distribution = try { parseObject(assetText("pack_distribution.json")) } catch(_: java.io.FileNotFoundException) { JsonObject() }
        val bundled = try { parseObject(assetText("bundled_packs.json")) } catch(_: java.io.FileNotFoundException) { JsonObject() }
        val directTest = if(BuildConfig.DEBUG && BuildConfig.PACK_ENC_TEST_KEY) {
            try { parseObject(assetText("pack_direct_test.json")) } catch(_: java.io.FileNotFoundException) { JsonObject() }
        } else JsonObject()
        songs.forEach { song ->
            val bundledEntry = bundled.obj(song.s("id"))
            val directTestEntry = directTest.obj(song.s("id"))
            val packSize = bundledEntry.n("sizeBytes").takeIf { it > 0 } ?: directTestEntry.n("sizeBytes")
            if(packSize > 0) song.addProperty("packageSizeBytes", packSize)
            if(bundledEntry.s("fileName").isNotEmpty()) {
                song.addProperty("bundledPackReady", bundledEntry.get("requiresReexport")?.asBoolean != true)
            }
            if(directTestEntry.has("url")) song.addProperty("directTestPack", true)
            val actualId = distribution.obj("catalogBindings").s(song.s("distributionRef"))
            val distributionId = actualId.ifEmpty { song.s("id") }
            if(distribution.obj("songs").obj(distributionId).has("manifestUrl") || directTestEntry.has("url")) {
                song.addProperty("remotePackReady", true)
            }
            if(actualId.isNotEmpty()) song.addProperty("id", actualId)
        }
        val replacedPreviewIds = mutableSetOf<String>()
        val installed = dao.installedPackages().filter { it.state in listOf("VERIFIED", "BUNDLED", "DIRECT_TEST", "LOCAL_PASSWORD") }.mapNotNull { row ->
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
                    addProperty("bundled", row.state == "BUNDLED")
                    addProperty("directTest", row.state == "DIRECT_TEST")
                }
            }
        }
        installed + songs.filter { it.s("id") !in replacedPreviewIds }
    }
    suspend fun installedLesson(id: String): JsonObject? = withContext(io) {
        val row = dao.installed(id) ?: run { lessonCache.remove(id); return@withContext null }
        val file = File(row.activePath, "lesson.json")
        check(file.isFile && row.state in listOf("VERIFIED", "BUNDLED", "DIRECT_TEST", "LOCAL_PASSWORD")) { "Installed package is unavailable" }
        require(file.length() <= 4 * 1024 * 1024)
        lessonCache[id]?.takeIf { it.row == row }?.let { return@withContext it.data }
        val bytes = file.inputStream().use { it.readBytes() }
        withContext(cpu) { com.hydro.reedance.contract.PackVerifier.strictJson(bytes, 4 * 1024 * 1024) }
            .also { lessonCache[id] = CachedLesson(row, it) }
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
