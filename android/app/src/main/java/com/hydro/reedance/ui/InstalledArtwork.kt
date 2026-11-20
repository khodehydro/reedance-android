package com.hydro.reedance.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private data class DecodedArtwork(val image: ImageBitmap, val bytes: Int)
private val artworkCache = object : LruCache<String, DecodedArtwork>(2 * 1024 * 1024) {
    override fun sizeOf(key: String, value: DecodedArtwork) = value.bytes
}
private val artworkDecode = Mutex()

/** Installed packs use immutable generation paths, so a new cover gets a new cache key. */
@Composable internal fun installedArtwork(path: String?): ImageBitmap? {
    val decoded = produceState<Pair<String, ImageBitmap>?>(null, path) {
        value = path?.let { file ->
            val image = artworkCache.get(file) ?: withContext(Dispatchers.IO) {
                artworkDecode.withLock {
                    artworkCache.get(file) ?: run {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file, bounds)
                        var sample = 1
                        while(bounds.outWidth / sample > 128 || bounds.outHeight / sample > 128) sample *= 2
                        BitmapFactory.decodeFile(file, BitmapFactory.Options().apply { inSampleSize = sample })
                            ?.let { DecodedArtwork(it.asImageBitmap(), it.allocationByteCount) }
                            ?.also { artworkCache.put(file, it) }
                    }
                }
            }
            image?.let { file to it.image }
        }
    }
    return decoded.value?.takeIf { it.first == path }?.second
}
