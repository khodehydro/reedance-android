package com.hydro.reedance.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.ContentScale

@Composable internal fun SongArtwork(songId: String, title: String, path: String?, modifier: Modifier = Modifier, coverUrl: String = "") {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val image = installedArtwork(path.takeIf { coverUrl.isBlank() }, size)
    Box(modifier.onSizeChanged { size = it }) {
        if(image == null) CoverImage(songId, title, Modifier.fillMaxSize(), coverUrl = coverUrl)
        else Image(image, title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}
