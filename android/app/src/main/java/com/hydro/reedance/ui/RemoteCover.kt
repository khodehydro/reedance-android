package com.hydro.reedance.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

private const val coverByteLimit = 4 * 1024 * 1024L
private const val diskCoverByteLimit = 64 * 1024 * 1024L
private const val diskCoverEntryLimit = 256
private val remoteCovers = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
}
private val remoteCoverLock = Mutex()
private val coverClient = OkHttpClient.Builder().followRedirects(false)
    .callTimeout(15, TimeUnit.SECONDS).build()

internal suspend fun remoteCover(context: Context, link: String): Bitmap? {
    val url = link.toHttpUrlOrNull() ?: return null
    val allowed = url.host == "raw.githubusercontent.com" ||
        (url.host == "dl.reedance.ir" && url.encodedPath.matches(Regex("/reetalk/en/01/(01|01(0[1-9]|1[0-2]))[.]png")))
    if(!url.isHttps || !allowed || url.port != 443 ||
        url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null) return null
    val key = url.toString()
    return remoteCovers.get(key) ?: withContext(Dispatchers.IO) {
        remoteCoverLock.withLock {
            remoteCovers.get(key) ?: run {
                val dir = File(context.cacheDir, "remote-artwork-v1")
                val name = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
                    .joinToString("") { "%02x".format(it.toInt() and 255) }
                val file = File(dir, name)
                readCachedCover(file)?.let { bitmap ->
                    file.setLastModified(System.currentTimeMillis())
                    remoteCovers.put(key, bitmap)
                    return@withLock bitmap
                }
                val bytes = try {
                    coverClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                        val body = response.body ?: return@use null
                        if(!response.isSuccessful || body.contentLength() > coverByteLimit) return@use null
                        val source = body.source()
                        if(source.request(coverByteLimit + 1)) return@use null
                        source.readByteArray()
                    }
                } catch(_: IOException) { null } ?: return@withLock null
                decodeRemoteCover(bytes)?.also { bitmap ->
                    remoteCovers.put(key, bitmap)
                    cacheCover(dir, file, bytes)
                }
            }
        }
    }
}

private fun decodeRemoteCover(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if(bounds.outWidth !in 1..4096 || bounds.outHeight !in 1..4096) return null
    var sample = 1
    while(maxOf(bounds.outWidth, bounds.outHeight) / sample > 512) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

private fun readCachedCover(file: File): Bitmap? {
    if(!file.isFile) return null
    val decoded = try {
        if(file.length() in 1..coverByteLimit) decodeRemoteCover(file.readBytes()) else null
    } catch(_: IOException) { null }
    if(decoded == null) file.delete()
    return decoded
}

// Keep original validated bytes in app cache so offline/restarted screens share the artwork.
// Writes and eviction are serialized by remoteCoverLock; failure never prevents display.
private fun cacheCover(dir: File, file: File, bytes: ByteArray) {
    if(!dir.isDirectory && !dir.mkdirs()) return
    val pending = File(dir, ".pending")
    try {
        pending.writeBytes(bytes)
        if(!pending.renameTo(file)) return
        val files = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        var count = files.size
        for(entry in files) {
            if(total <= diskCoverByteLimit && count <= diskCoverEntryLimit) break
            val size = entry.length()
            if(entry.delete()) { total -= size; count-- }
        }
    } catch(_: IOException) {
        // In low storage the in-memory image remains usable.
    } finally { pending.delete() }
}
