package com.hydro.reedance.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

@Composable internal fun SongArtwork(songId: String, title: String, path: String?, modifier: Modifier = Modifier) {
    val image = installedArtwork(path)
    Box(modifier) {
        if(image == null) CoverImage(songId, title, Modifier.fillMaxSize())
        else Image(image, title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}
