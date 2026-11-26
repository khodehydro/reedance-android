package com.hydro.reedance.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private data class ArtworkRequest(val path: String, val width: Int, val height: Int)
private data class DecodedArtwork(val image: ImageBitmap, val bytes: Int)
private val artworkCache = object : LruCache<ArtworkRequest, DecodedArtwork>(16 * 1024 * 1024) {
    override fun sizeOf(key: ArtworkRequest, value: DecodedArtwork) = value.bytes
}
private val artworkDecode = Mutex()

/** Crop needs enough source pixels on both axes. BitmapFactory efficiently decodes powers of two. */
internal fun artworkSampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
    if(width <= 0 || height <= 0 || targetWidth <= 0 || targetHeight <= 0) return 1
    var sample = 1
    while(width / (sample * 2) >= targetWidth && height / (sample * 2) >= targetHeight) sample *= 2
    // Bound allocations even for unusually large/panoramic source artwork.
    while(width / sample > 2048 || height / sample > 2048) sample *= 2
    return sample
}

/** Immutable generation paths and size buckets keep thumbnails out of full-size cover cache hits. */
@Composable internal fun installedArtwork(path: String?, size: IntSize): ImageBitmap? {
    fun bucket(px: Int) = ((px.coerceAtMost(2048) + 63) / 64) * 64
    val request = if(path != null && size.width > 0 && size.height > 0)
        ArtworkRequest(path, bucket(size.width), bucket(size.height)) else null
    val decoded = produceState<Pair<ArtworkRequest, ImageBitmap>?>(null, request) {
        value = request?.let { key ->
            val image = artworkCache.get(key) ?: withContext(Dispatchers.IO) {
                artworkDecode.withLock {
                    artworkCache.get(key) ?: run {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(key.path, bounds)
                        val sample = artworkSampleSize(bounds.outWidth, bounds.outHeight, key.width, key.height)
                        BitmapFactory.decodeFile(key.path, BitmapFactory.Options().apply { inSampleSize = sample })
                            ?.let { DecodedArtwork(it.asImageBitmap(), it.allocationByteCount) }
                            ?.also { artworkCache.put(key, it) }
                    }
                }
            }
            image?.let { key to it.image }
        }
    }
    return decoded.value?.takeIf { it.first == request }?.second
}
