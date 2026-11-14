package com.hydro.reedance.install

import android.content.Context
import android.media.MediaMetadataRetriever
import android.graphics.BitmapFactory
import androidx.room.withTransaction
import com.hydro.reedance.contract.PackVerifier
import com.hydro.reedance.data.*
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Fail-closed private-storage installer. No key/URL is learned from untrusted pack content. */
class PackInstaller(private val context: Context) {
    companion object {
        private val lock = Mutex()
    }
    private val database = LearningDatabase.get(context)
    private val dao = database.learning()
    private val root = File(context.filesDir, "packages")
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(8, TimeUnit.MINUTES).build()
    private fun config(): JsonObject = try {
        context.assets.open("pack_distribution.json").use { PackVerifier.strictJson(it.readBytes(), 65536) }
    } catch(_: java.io.FileNotFoundException) { error("پیکربندی ناشر و کلید توزیع موجود نیست / Publisher configuration and distribution keys are missing") }
    private suspend fun download(url: String, file: File, limit: Long, hosts: Set<String>, progress: suspend (Long) -> Unit) {
        var current = url.toHttpUrl()
        require(current.isHttps && current.username.isEmpty() && current.password.isEmpty() && current.fragment == null && current.query == null)
        val origin = current.host
        val port = current.port
        require(origin in hosts)
        repeat(4) {
            currentCoroutineContext().ensureActive()
            client.newCall(Request.Builder().url(current).build()).execute().use { response ->
                if(response.code in 300..399) {
                    current = current.resolve(response.header("Location") ?: error("Missing redirect")) ?: error("Invalid redirect")
                    require(current.isHttps && current.host == origin && current.port == port && current.host in hosts && current.username.isEmpty() && current.password.isEmpty() && current.query == null && current.fragment == null)
                } else {
                    require(response.isSuccessful) { "HTTP ${response.code}" }
                    val body = response.body ?: error("Empty response")
                    require(body.contentLength() <= limit)
                    body.byteStream().use { input -> file.outputStream().use { output ->
                        val buffer = ByteArray(65536); var count = 0L; var last = 0L
                        while(true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer); if(n < 0) break
                            count += n; require(count <= limit); output.write(buffer, 0, n)
                            if(count - last >= 262144) { progress(count); last = count }
                        }
                    } }
                    return
                }
            }
        }
        error("Too many redirects")
    }
    private suspend fun hash(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(65536)
            while(true) { currentCoroutineContext().ensureActive(); val n = input.read(buffer); if(n < 0) break; hash.update(buffer, 0, n) }
        }
        return hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private suspend fun decrypt(pack: File, zip: File, payload: JsonObject, keys: JsonObject) {
        RandomAccessFile(pack, "r").use { input ->
            require(ByteArray(8).also { input.readFully(it) }.contentEquals("RDPACK1\u0000".toByteArray()))
            val length = input.readUnsignedByte(); require(length in 1..32)
            val header = ByteArray(29 + length); input.seek(0); input.readFully(header)
            val keyId = header.copyOfRange(9, 9 + length).toString(Charsets.US_ASCII)
            require(keyId == payload.obj("package").s("encryptionKeyId"))
            val key = PackVerifier.base64(keys.s(keyId)); require(key.size == 32)
            val plain = ByteBuffer.wrap(header, 21 + length, 8).long
            require(plain in 1..268435456L && pack.length() == header.size + plain + 16)
            val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
            cipher.init(false, AEADParameters(KeyParameter(key), 128, header.copyOfRange(9 + length, 21 + length), header))
            try {
                zip.outputStream().use { out ->
                    val buffer = ByteArray(65536); val result = ByteArray(65568); var total = 0L
                    while(true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer); if(n < 0) break
                        val written = cipher.processBytes(buffer, 0, n, result, 0)
                        total += written; require(total <= plain); out.write(result, 0, written)
                    }
                    val written = cipher.doFinal(result, 0) // No ZIP extraction before tag verification.
                    out.write(result, 0, written); require(total + written == plain)
                }
            } finally { key.fill(0) }
        }
    }
    private fun syncDirectory(directory: File) {
        val fd = android.system.Os.open(directory.path, android.system.OsConstants.O_RDONLY or android.system.OsConstants.O_DIRECTORY, 0)
        try { android.system.Os.fsync(fd) } finally { android.system.Os.close(fd) }
    }
    private suspend fun recoverLocked() {
        File(root, ".staging").listFiles()?.forEach { it.deleteRecursively() }
        val active = dao.installedPackages().map { it.activePath }.toSet()
        root.listFiles()?.filter { it.isDirectory && it.name != ".staging" }?.forEach { song ->
            song.listFiles()?.filter { File(it, ".activation-pending").isFile }?.forEach { generation ->
                if(generation.path in active) File(generation, ".activation-pending").delete()
                else check(generation.deleteRecursively())
            }
        }
    }
    suspend fun recover() = lock.withLock { withContext(Dispatchers.IO) { recoverLocked() } }
    suspend fun install(song: String, progress: suspend (String, Long) -> Unit): JsonObject = lock.withLock { withContext(Dispatchers.IO) {
        require(song.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*")))
        recoverLocked()
        val config = config()
        val source = config.obj("songs").obj(song)
        require(source.has("manifestUrl")) { "آدرس latest.json این آهنگ تنظیم نشده / Signed manifest URL missing" }
        val language = source.s("language"); require(language in listOf("en", "es", "ko", "tr"))
        val hosts = config.strings("allowedHosts").toSet()
        val publicKeys = config.obj("publicKeys").entrySet().associate { it.key to PackVerifier.base64(it.value.asString) }
        val stagingRoot = File(root, ".staging").apply { mkdirs() }
        // Protected by the process-wide install lock. Previous crashes never change active pointers.
        stagingRoot.listFiles()?.forEach { it.deleteRecursively() }
        val stage = File(stagingRoot, java.util.UUID.randomUUID().toString()).apply { check(mkdirs()) }
        try {
            val manifest = File(stage, "latest.json")
            progress("manifest", 0)
            download(source.s("manifestUrl"), manifest, 32768, hosts) { }
            val seen = dao.seen(song)
            val payload = PackVerifier.manifest(manifest.readBytes(), publicKeys, song, language, seen?.highestVersionCode?.toLong() ?: 0, seen?.packageSha256 ?: "")
            val spec = payload.obj("package")
            // Persist highest authenticated observation even if the subsequent transfer fails.
            dao.saveSeen(SeenManifest(song, payload.n("contentVersionCode").toInt(), spec.s("sha256")))
            require(config.obj("aesKeys").has(spec.s("encryptionKeyId"))) { "کلید توزیع موجود نیست / Distribution key missing" }
            val url = source.s("manifestUrl").toHttpUrl().resolve(spec.s("fileName")) ?: error("Invalid package URL")
            val pack = File(stage, "download.part")
            require(root.usableSpace > spec.n("sizeBytes") * 3 + 32L * 1024 * 1024) { "فضای خالی کافی نیست / Insufficient storage" }
            progress("download", 0)
            download(url.toString(), pack, spec.n("sizeBytes"), hosts) { progress("download", it) }
            require(pack.length() == spec.n("sizeBytes") && hash(pack) == spec.s("sha256")) { "Package hash/length mismatch" }
            progress("decrypt", pack.length())
            val zip = File(stage, "authenticated.zip"); decrypt(pack, zip, payload, config.obj("aesKeys"))
            val output = File(stage, "content"); StrictPackZip.extract(zip, output)
            progress("validate", 0)
            require(File(output, "lesson.json").length() <= 4 * 1024 * 1024)
            val lesson = PackVerifier.strictJson(File(output, "lesson.json").readBytes(), 4 * 1024 * 1024)
            require(File(output, "lesson.json").length() <= 4 * 1024 * 1024)
            val schema = context.assets.open("lesson.schema.json").use { PackVerifier.strictJson(it.readBytes(), 131072) }
            LessonValidation(schema).apply { validate(lesson); semantics(lesson) }
            require(lesson.s("songId") == song && lesson.s("targetLanguage") == language)
            val mappings = lesson.a("vocabulary").associate { it.s("lexemeId") to it.s("normalizedKey") }
            dao.installedPackages().filter { it.state == "VERIFIED" }.forEach { previous ->
                val oldFile = File(previous.activePath, "lesson.json")
                require(oldFile.length() <= 4 * 1024 * 1024 && oldFile.isFile)
                val old = PackVerifier.strictJson(oldFile.readBytes(), 4 * 1024 * 1024)
                old.a("vocabulary").forEach { word ->
                    mappings[word.s("lexemeId")]?.let { require(it == word.s("normalizedKey") && old.s("targetLanguage") == language) { "Lexeme identity changed" } }
                }
            }
            val media = lesson.obj("media")
            val expected = mutableSetOf("lesson.json")
            for(kind in listOf("audio", "cover")) if(media.has(kind)) {
                val item = media.obj(kind); val file = File(output, item.s("path")); expected += file.name
                val mime = mapOf("audio.mp3" to "audio/mpeg", "audio.m4a" to "audio/mp4", "audio.wav" to "audio/wav", "cover.jpg" to "image/jpeg", "cover.png" to "image/png", "cover.webp" to "image/webp")
                require(mime[file.name] == item.s("mimeType"))
                val prefix = ByteArray(12)
                file.inputStream().use { require(it.read(prefix) == 12) }
                val magic = prefix.toString(Charsets.ISO_8859_1)
                when(file.extension) {
                    "wav" -> require(magic.startsWith("RIFF") && magic.substring(8) == "WAVE")
                    "m4a" -> require(magic.substring(4, 8) == "ftyp")
                    "mp3" -> require(magic.startsWith("ID3") || ((prefix[0].toInt() and 255) == 255 && (prefix[1].toInt() and 224) == 224))
                    "jpg" -> require((prefix[0].toInt() and 255) == 255 && (prefix[1].toInt() and 255) == 216)
                    "png" -> require(prefix.copyOfRange(0, 8).contentEquals(byteArrayOf(137.toByte(),80,78,71,13,10,26,10)))
                    "webp" -> require(magic.startsWith("RIFF") && magic.substring(8) == "WEBP")
                }
                require(file.isFile && hash(file) == item.s("sha256")) { "Media hash mismatch" }
                if(kind == "cover") {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(file.path, options)
                    require(options.outWidth in 1..8192 && options.outHeight in 1..8192 && options.outWidth.toLong() * options.outHeight <= 32_000_000 && options.outMimeType == item.s("mimeType"))
                } else {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(file.path)
                        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: error("Invalid audio")
                        require(kotlin.math.abs(duration - item.n("durationMs")) <= 1000)
                    } finally { retriever.release() }
                }
            }
            require(output.listFiles()!!.map { it.name }.toSet() == expected)
            val version = payload.n("contentVersionCode").toInt()
            val parent = File(root, song).apply { mkdirs() }; syncDirectory(root)
            // Unique immutable generation: a crash can only leave an unreferenced directory.
            val target = File(parent, "$version-${spec.s("sha256")}-${stage.name}")
            withContext(NonCancellable) {
                File(output, ".activation-pending").writeText("pending")
                output.listFiles()!!.forEach { file -> java.io.FileOutputStream(file, true).use { it.fd.sync() } }
                syncDirectory(output)
                check(output.renameTo(target))
                syncDirectory(parent)
                try {
                    database.withTransaction {
                        dao.saveInstalled(InstalledPackage(song, version, PackVerifier.sha256(PackVerifier.base64(PackVerifier.strictJson(manifest.readBytes(), 32768).s("payload"))), target.path, "VERIFIED", System.currentTimeMillis()))
                        dao.saveSeen(SeenManifest(song, version, spec.s("sha256")))
                    }
                } catch(e: Exception) { target.deleteRecursively(); throw e }
                File(target, ".activation-pending").delete(); syncDirectory(target)
            }
            progress("installed", 0)
            lesson
        } finally { withContext(NonCancellable) { stage.deleteRecursively() } }
    } }
}
