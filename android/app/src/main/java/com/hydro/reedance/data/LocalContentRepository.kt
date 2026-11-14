package com.hydro.reedance.data

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.hydro.reedance.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest

/** Main-safe local demo operations. This is not the production rdpack installer. */
class LocalContentRepository(context: Context, private val dao: LearningDao,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val cpu: CoroutineDispatcher = Dispatchers.Default) {
    private val app = context.applicationContext
    val demoDir = File(app.filesDir, "demo/midnight-avenue/1")
    private val staging = File(app.filesDir, "demo-staging")
    private val filesMutex = Mutex()

    private suspend fun assetText(name: String) = withContext(io) { app.assets.open(name).bufferedReader().use { it.readText() } }
    private suspend fun parseObject(text: String) = withContext(cpu) { JsonParser.parseString(text).asJsonObject }
    suspend fun catalog(): List<JsonObject> {
        val text = assetText("catalog_preview.json")
        return withContext(cpu) { JsonParser.parseString(text).asJsonArray.map { it.asJsonObject } }
    }
    suspend fun starters() = parseObject(assetText("starter.json"))
    suspend fun restoreDemo(): JsonObject? = filesMutex.withLock {
        val text = withContext(io) {
            staging.deleteRecursively()
            if(BuildConfig.DEBUG && File(demoDir, "lesson.json").isFile && File(demoDir, "audio.wav").isFile)
                File(demoDir, "lesson.json").readText() else null
        }
        text?.let { parseObject(it) }
    }
    suspend fun installDemo(): JsonObject = filesMutex.withLock {
        check(BuildConfig.DEBUG)
        val text = assetText("demo_lesson.json")
        val data = parseObject(text)
        try {
            withContext(io) {
                staging.deleteRecursively(); check(staging.mkdirs())
                File(staging, "lesson.json").writeText(text)
                val digest = MessageDigest.getInstance("SHA-256")
                app.assets.open("midnight-avenue-demo.wav").use { input ->
                    File(staging, "audio.wav").outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while(true) {
                            ensureActive()
                            val count = input.read(buffer)
                            if(count < 0) break
                            output.write(buffer, 0, count); digest.update(buffer, 0, count)
                        }
                    }
                }
                val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
                check(hash == data.obj("media").obj("audio").s("sha256"))
            }
            // A short non-cancellable commit keeps cancellation from stranding a moved directory.
            withContext(NonCancellable + io) {
                demoDir.parentFile!!.mkdirs()
                check(!demoDir.exists() && staging.renameTo(demoDir))
                try {
                    dao.saveInstalled(InstalledPackage("midnight-avenue", 1, "LOCAL-DEMO-NOT-A-SIGNED-PACK", demoDir.path, "DEMO", System.currentTimeMillis()))
                } catch(error: Exception) { demoDir.renameTo(staging); throw error }
            }
            data
        } finally { withContext(NonCancellable + io) { staging.deleteRecursively() } }
    }
    suspend fun deleteDemo() = filesMutex.withLock {
        withContext(io) { check(!demoDir.exists() || demoDir.deleteRecursively()); dao.deleteInstalled("midnight-avenue") }
    }
}
