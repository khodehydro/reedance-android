package com.hydro.reedance.ui

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
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val coverByteLimit = 4 * 1024 * 1024L
private val remoteCovers = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
}
private val remoteCoverLock = Mutex()
private val coverClient = OkHttpClient.Builder().followRedirects(false)
    .callTimeout(15, TimeUnit.SECONDS).build()

internal suspend fun remoteCover(link: String): Bitmap? {
    val url = link.toHttpUrlOrNull() ?: return null
    if(!url.isHttps || url.host != "raw.githubusercontent.com" || url.port != 443 ||
        url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null) return null
    return remoteCovers.get(link) ?: withContext(Dispatchers.IO) {
        remoteCoverLock.withLock {
            remoteCovers.get(link) ?: try {
                coverClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    val body = response.body ?: return@use null
                    if(!response.isSuccessful || body.contentLength() > coverByteLimit) return@use null
                    val source = body.source()
                    if(source.request(coverByteLimit + 1)) return@use null
                    val bytes = source.readByteArray()
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    if(bounds.outWidth !in 1..4096 || bounds.outHeight !in 1..4096) return@use null
                    var sample = 1
                    while(maxOf(bounds.outWidth, bounds.outHeight) / sample > 512) sample *= 2
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                        ?.also { remoteCovers.put(link, it) }
                }
            } catch(_: IOException) { null }
        }
    }
}
