package com.hydro.reedance.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.hydro.reedance.R

@Composable internal fun SongTelegramCard() {
    val context = LocalContext.current
    val unavailable = tr("لینک تلگرام باز نشد.", "Could not open Telegram.")
    Surface(onClick = {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/reedance"))) }
        catch(_: ActivityNotFoundException) { Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show() }
    }, modifier = Modifier.fillMaxWidth(), shape = GroupedCardShape, color = Color(0xFF075582), contentColor = Color.White) {
        Box {
            Image(painterResource(R.drawable.telegram_card_art), contentDescription = null,
                modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = Color.White, contentColor = Color(0xFF075582)) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        AppIcon(R.drawable.uicon_telegram, null, Modifier.size(24.dp))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("کانال تلگرام ری‌دنس", "Reedance on Telegram"), style = MaterialTheme.typography.titleMedium)
                    Text(tr("کانال را دنبال کنید", "Follow the channel"), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
