package com.hydro.reedance.install

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import com.google.gson.JsonObject
import com.hydro.reedance.BuildConfig
import com.hydro.reedance.contract.PackVerifier
import com.hydro.reedance.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.math.abs

/** Direct-link profile: authority is the size/hash pinned in the APK, not a publisher manifest. */
internal data class ReeTalkPackSpec(val id: String, val categoryId: String, val language: String,
    val url: String, val bytes: Long, val sha256: String, val version: Int, val studioQa: Boolean = false,
    val minimumTurnsPerSpeaker: Int = 5)
internal data class InstalledReeTalk(val dialogue: JsonObject, val audio: File, val cover: File)

internal class ReeTalkInstaller(context: Context) {
    private val app = context.applicationContext
    private val root = File(app.filesDir, "reetalk")
    private val transport by lazy { PackInstaller(app) }

    suspend fun installed(spec: ReeTalkPackSpec): InstalledReeTalk? = withContext(Dispatchers.IO) {
        val active = path(spec)
        if(!active.isDirectory) null else readContent(active, spec)
    }

    suspend fun install(spec: ReeTalkPackSpec, allowed: () -> Boolean,
        progress: suspend (String, Long, Long) -> Unit): InstalledReeTalk = lock.withLock { withContext(Dispatchers.IO) {
        check(allowed()) { "Premium required" }
        val active = path(spec)
        if(active.isDirectory) {
            val cached = try { readContent(active, spec) } catch(_: Exception) { null }
            if(cached != null) return@withContext cached
            check(active.deleteRecursively()) { "Could not repair the incomplete installation" }
        }
        check(root.isDirectory || root.mkdirs())
        require(root.usableSpace > spec.bytes * 3 + 32L * 1024 * 1024)
        // Any abandoned staging directory is private and can be removed under the installer lock.
        root.listFiles()?.filter { it.name.startsWith(".stage-") }?.forEach { it.deleteRecursively() }
        val stage = File(root, ".stage-${java.util.UUID.randomUUID()}").apply { check(mkdir()) }
        try {
            val pack = File(stage, "download.part")
            progress("download", 0, spec.bytes)
            transport.download(spec.url, pack, spec.bytes, setOf("github.com", "raw.githubusercontent.com", "dl.reedance.ir")) { received, _ ->
                check(allowed()); progress("download", received, spec.bytes)
            }
            require(pack.length() == spec.bytes && transport.hash(pack) == spec.sha256) { "Package hash/length mismatch" }
            val payload = JsonObject().apply { add("package", JsonObject().apply { addProperty("encryptionKeyId", "enc-main") }) }
            val zip = File(stage, "authenticated.zip")
            progress("decrypt", 0, 0)
            val qaKey = if(BuildConfig.DEBUG && spec.studioQa) transport.studioDebugConfig()?.let {
                PackVerifier.base64(it.s("packKey")).also { key -> require(key.size == 32) }
            } else null
            transport.decrypt(pack, zip, payload, qaKey)
            progress("validate", 0, 0)
            val output = File(stage, "content")
            StrictPackZip.extract(zip, output, reeTalk = true)
            val result = readContent(output, spec)
            verifyMedia(result)
            currentCoroutineContext().ensureActive()
            check(allowed()) { "Premium required" }
            check(output.renameTo(active)) { "Could not activate dialogue" }
            InstalledReeTalk(result.dialogue, File(active, result.audio.name), File(active, result.cover.name))
        } finally { withContext(NonCancellable) { stage.deleteRecursively() } }
    } }

    private fun path(spec: ReeTalkPackSpec): File {
        require(spec.id.matches(Regex("[a-z]{2}-t[0-9]{2}-s[0-9]{2}")))
        require(spec.sha256.matches(Regex("[a-f0-9]{64}")) && spec.bytes in 1..268435456L && spec.version > 0)
        return File(root, "${spec.id}-${spec.sha256}")
    }

    private fun readContent(dir: File, spec: ReeTalkPackSpec): InstalledReeTalk {
        val json = File(dir, "dialogue.json")
        require(json.isFile && json.length() in 1..(4L * 1024 * 1024))
        val d = PackVerifier.strictJson(json.readBytes(), 4 * 1024 * 1024)
        validateReeTalk(d, spec, minimumTurnsPerSpeaker = spec.minimumTurnsPerSpeaker)
        val media = d.obj("media")
        val audio = File(dir, media.obj("audio").s("path"))
        val cover = File(dir, media.obj("cover").s("path"))
        require(audio.name in setOf("audio.wav", "audio.mp3", "audio.m4a") && cover.name in setOf("cover.jpg", "cover.png", "cover.webp"))
        require(audio.parentFile == dir && cover.parentFile == dir && audio.isFile && cover.isFile)
        require(dir.list()?.toSet() == setOf("dialogue.json", audio.name, cover.name))
        return InstalledReeTalk(d, audio, cover)
    }

    private suspend fun verifyMedia(installed: InstalledReeTalk) {
        val media = installed.dialogue.obj("media")
        for((kind, file) in listOf("audio" to installed.audio, "cover" to installed.cover)) {
            val meta = media.obj(kind)
            val mime = mapOf("wav" to "audio/wav", "mp3" to "audio/mpeg", "m4a" to "audio/mp4",
                "jpg" to "image/jpeg", "png" to "image/png", "webp" to "image/webp")
            require(meta.s("mimeType") == mime[file.extension] && transport.hash(file) == meta.s("sha256"))
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(installed.cover.path, options)
        require(options.outWidth in 1..4096 && options.outHeight in 1..4096 && options.outMimeType == media.obj("cover").s("mimeType"))
        val head = ByteArray(12).also { bytes ->
            java.io.DataInputStream(installed.audio.inputStream()).use { it.readFully(bytes) }
        }
        val magic = head.toString(Charsets.ISO_8859_1)
        require(when(installed.audio.extension) {
            "wav" -> magic.startsWith("RIFF") && magic.substring(8) == "WAVE"
            "m4a" -> magic.substring(4, 8) == "ftyp"
            "mp3" -> magic.startsWith("ID3") || ((head[0].toInt() and 255) == 255 && (head[1].toInt() and 224) == 224)
            else -> false
        })
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(installed.audio.path)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            require(duration != null && duration > 0 && abs(duration - media.obj("audio").n("durationMs")) <= 1000)
        } finally { retriever.release() }
    }

    companion object {
        private val lock = Mutex()
    }
}

internal fun validateReeTalk(d: JsonObject, spec: ReeTalkPackSpec, minimumTurnsPerSpeaker: Int = 5) {
    require(d.s("contentType") == "reetalk" && d.n("schemaVersion") == 1L && d.n("contentVersionCode") == spec.version.toLong())
    require(d.s("dialogueId") == spec.id && d.s("categoryId") == spec.categoryId && d.s("targetLanguage") == spec.language)
    require(d.s("locale").startsWith("${spec.language}-") && d.s("level") in setOf("A1", "A2", "B1", "B2", "C1", "C2"))
    fun localized(v: JsonObject) { require(v.s("fa").isNotBlank() && v.s("en").isNotBlank()) }
    localized(d.obj("title")); localized(d.obj("objective"))
    val speakers = d.a("speakers")
    val speakerIds = speakers.map { it.s("id") }.toSet()
    require(speakers.size == 2 && speakerIds.size == 2 && speakerIds.none { it.isBlank() })
    require(speakers.all { it.s("name").isNotBlank() && it.s("gender") in setOf("woman", "man") })
    val duration = d.obj("media").obj("audio").n("durationMs")
    require(duration in 1..86400000)
    val turns = d.a("turns")
    val ids = turns.map { it.s("id") }.toSet()
    require(turns.size <= 120 && ids.size == turns.size && ids.none { it.isBlank() })
    require(speakerIds.all { id -> turns.count { it.s("speakerId") == id } >= minimumTurnsPerSpeaker })
    var end = 0L
    for(t in turns) {
        val start = t.n("startMs", -1)
        require(t.s("speakerId") in speakerIds && start in end until duration)
        end = t.n("endMs"); require(end in (start + 1)..duration)
        localized(t.obj("translation"))
        val tokens = t.a("tokens"); require(tokens.size in 1..200 && tokens.all { it.s("surface").isNotBlank() })
        require(tokens.joinToString(" ") { it.s("surface") } == t.s("text"))
        tokens.forEach { localized(it.obj("gloss")) }
    }
    val learning = d.obj("learning")
    for(section in listOf("words", "grammar", "phrases", "questions")) {
        val items = learning.a(section)
        require(items.size in 1..500 && items.map { it.s("id") }.toSet().size == items.size && items.none { it.s("id").isBlank() })
        for(item in items) when(section) {
            "questions" -> {
                localized(item.obj("prompt")); localized(item.obj("explanation"))
                val options = item.a("options"); val optionIds = options.map { it.s("id") }.toSet()
                require(item.s("type") == "multiple_choice" && item.s("stage") in setOf("gist", "detail", "review"))
                require(options.size in 2..6 && optionIds.size == options.size && optionIds.none { it.isBlank() } && item.s("correctOptionId") in optionIds)
                options.forEach { localized(it.obj("text")) }
                val evidence = item.getAsJsonArray("evidenceTurnIds")
                require(evidence.size() > 0 && evidence.all { it.asString in ids })
            }
            else -> {
                val example = item.obj("example"); require(example.s("text").isNotBlank()); localized(example.obj("translation"))
                if(section == "grammar") {
                    for(key in listOf("title", "explanation", "takeaway")) localized(item.obj(key))
                    val tables = item.a("tables"); require(tables.isNotEmpty())
                    tables.forEach { table ->
                        localized(table.obj("title"))
                        val columns = table.a("columns"); columns.forEach(::localized)
                        val rows = table.getAsJsonArray("rows")
                        require(columns.size in 1..12 && rows.size() in 1..200)
                        rows.forEach { row ->
                            require(row.asJsonArray.size() == columns.size)
                            row.asJsonArray.forEach { cell ->
                                val obj = cell.asJsonObject
                                if(obj.has("text")) require(obj.s("text").isNotBlank()) else localized(obj)
                            }
                        }
                    }
                } else {
                    require(item.s("text").isNotBlank()); localized(item.obj("meaning"))
                    val pronunciation = item.obj("pronunciation")
                    require(pronunciation.s("ipa").isNotBlank() && pronunciation.s("faReading").isNotBlank())
                    if(section == "phrases") localized(item.obj("context"))
                }
            }
        }
    }
}
