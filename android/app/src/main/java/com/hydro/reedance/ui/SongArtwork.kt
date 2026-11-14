package com.hydro.reedance.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun SongArtwork(songId: String, title: String, path: String?, modifier: Modifier = Modifier) {
    val decoded by produceState<Pair<String, Bitmap>?>(null, path) {
        value = path?.let { file -> withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file, bounds)
            var sample = 1
            while(bounds.outWidth / sample > 128 || bounds.outHeight / sample > 128) sample *= 2
            BitmapFactory.decodeFile(file, BitmapFactory.Options().apply { inSampleSize = sample })?.let { file to it }
        } }
    }
    Box(modifier) {
        val image = decoded?.takeIf { it.first == path }?.second
        if(image == null) CoverImage(songId, title, Modifier.fillMaxSize())
        else Image(image.asImageBitmap(), title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}
