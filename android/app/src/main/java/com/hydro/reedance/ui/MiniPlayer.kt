package com.hydro.reedance.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hydro.reedance.data.ActivePlayback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun MiniPlayer(song: ActivePlayback, playing: Boolean, toggle: () -> Unit, openSong: () -> Unit) {
    var shareDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Surface(modifier = Modifier.padding(horizontal = 20.dp), shape = CircleShape,
        color = Color(0xFF2D2D2D), contentColor = Color.White) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clip(CircleShape).clickable(onClick = openSong).heightIn(min = 48.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniCover(song)
                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White,
                    style = MaterialTheme.typography.bodyMedium)
            }
            // Keep accessible touch targets; only the actual filled glyphs are smaller.
            PressIconButton(toggle, Modifier.size(48.dp)) {
                AppIcon(if(playing) UiIcons.PauseFilled else UiIcons.PlayFilled,
                    if(playing) tr("مکث", "Pause") else tr("پخش", "Play"), Modifier.size(18.dp), tint = Color.White)
            }
            PressIconButton({ shareDialog = true }, Modifier.size(48.dp)) {
                AppIcon(UiIcons.CoffeeFilled, tr("دعوت دوستان", "Invite friends"), Modifier.size(20.dp), tint = Color.White)
            }
        }
    }
    if(shareDialog) AlertDialog(onDismissRequest = { shareDialog = false },
        title = { Text(tr("یک فنجان موسیقی با دوستان", "Share a cup of music")) },
        text = { Text(tr("ری‌دنس را برای دوستانت بفرست تا آن‌ها هم با موسیقی زبان یاد بگیرند.", "Send Reedance to your friends so they can learn languages through music too.")) },
        confirmButton = { PressTextButton({
            shareDialog = false
            val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,
                "Reedance — learn languages through music. https://github.com/khodehydro/reedance-android")
            context.startActivity(Intent.createChooser(share, null))
        }) { Text(tr("اشتراک‌گذاری برنامه", "Share Reedance")) } },
        dismissButton = { PressTextButton({ shareDialog = false }) { Text(tr("فعلاً نه", "Not now")) } })
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
    Box(Modifier.size(32.dp).clip(CircleShape)) {
        val image = bitmap
        if(image != null) Image(image.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else CoverImage(song.songId, song.title, Modifier.fillMaxSize())
    }
}
