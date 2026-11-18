package com.hydro.reedance.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hydro.reedance.data.ActivePlayback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun MiniPlayer(song: ActivePlayback, playing: Boolean, toggle: () -> Unit, openSong: () -> Unit, compact: Boolean = false) {
    val maxWidth by animateDpAsState(if(compact) 276.dp else 300.dp, tween(190), label = "mini-player-width")
    var shareDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.widthIn(max = maxWidth).padding(horizontal = 16.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 2.dp,
            tonalElevation = 0.dp
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).clickable(onClick = openSong)
                    .heightIn(min = 48.dp).padding(horizontal = 7.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniCover(song)
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodySmall)
                }
                PressIconButton(toggle, Modifier.size(44.dp)) {
                    AppIcon(if(playing) UiIcons.PauseFilled else UiIcons.PlayFilled,
                        if(playing) tr("مکث", "Pause") else tr("پخش", "Play"), Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurface)
                }
                PressIconButton({ shareDialog = true }, Modifier.size(44.dp)) {
                    AppIcon(UiIcons.CoffeeFilled, tr("دعوت دوستان", "Invite friends"), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
    if(shareDialog) ShareAppDialog(onDismiss = { shareDialog = false }, onShare = {
        shareDialog = false
        val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,
            "Reedance — learn languages through music. https://github.com/khodehydro/reedance-android")
        context.startActivity(Intent.createChooser(share, null))
    })
}

@Composable private fun MiniCover(song: ActivePlayback) {
    val bitmap by produceState<Bitmap?>(null, song.coverPath) {
        value = song.coverPath?.let { path -> withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while(bounds.outWidth / sample > 128 || bounds.outHeight / sample > 128) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        } }
    }
    Box(Modifier.size(28.dp).clip(CircleShape)) {
        val image = bitmap
        if(image != null) Image(image.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else CoverImage(song.songId, song.title, Modifier.fillMaxSize())
    }
}
