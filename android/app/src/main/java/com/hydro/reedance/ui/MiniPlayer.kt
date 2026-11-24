package com.hydro.reedance.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hydro.reedance.data.ActivePlayback

@Composable fun MiniPlayer(
    song: ActivePlayback,
    playing: Boolean,
    toggle: () -> Unit,
    openSong: () -> Unit,
    compact: Boolean = false,
    menuWidth: () -> Dp = { navigationMenuWidth(compact) }
) {
    var shareDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Box(Modifier.fillMaxWidth().padding(horizontal = navigationMenuHorizontalInset), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.navigationBarWidth(menuWidth),
            shape = GroupedCardShape,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 2.dp,
            tonalElevation = 0.dp
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clip(InsetControlShape).clickable(onClick = openSong)
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
    val image = installedArtwork(song.coverPath.takeIf { song.coverUrl.isBlank() })
    Box(Modifier.size(28.dp).clip(CircleShape)) {
        if(image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else CoverImage(song.songId, song.title, Modifier.fillMaxSize(), coverUrl = song.coverUrl)
    }
}
