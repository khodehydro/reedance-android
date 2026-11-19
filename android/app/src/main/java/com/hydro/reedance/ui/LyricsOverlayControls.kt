package com.hydro.reedance.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydro.reedance.data.AppModel
import com.hydro.reedance.domain.PremiumEntitlement

@Composable internal fun LyricsOverlayControls(model: AppModel, premium: () -> Unit) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val subscribed by PremiumEntitlement.active.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var granted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var settingsUnavailable by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = Settings.canDrawOverlays(context) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        granted = Settings.canDrawOverlays(context)
        if(granted && PremiumEntitlement.active.value) model.lyricsOverlay(true)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("متن شناور آهنگ", "Floating lyrics"), Modifier.weight(1f))
            PressSwitch(settings.lyricsOverlay && granted && subscribed, { enabled ->
                if(!enabled) model.lyricsOverlay(false)
                else if(granted) model.lyricsOverlay(true)
                else {
                    val request = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    settingsUnavailable = runCatching { permission.launch(request) }.isFailure &&
                        runCatching { permission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }.isFailure
                }
            }, enabled = subscribed)
        }
        Text(tr("متن آهنگ در زمان پخش، روی برنامه‌های دیگر نمایش داده می‌شود. این قابلیت پریمیوم به مجوز نمایش روی برنامه‌ها نیاز دارد.",
            "Show the current lyric over other apps while music plays. This Premium feature requires Android’s display-over-other-apps permission."),
            style = MaterialTheme.typography.bodySmall)
        if(!subscribed) {
            PressTextButton(premium) { Text(tr("مشاهدهٔ پریمیوم", "Explore Premium")) }
        } else {
            if(!granted) Text(tr("در تنظیمات اندروید، ری‌دنس را انتخاب کن و مجوز را بده.",
                "In Android settings, select Reedance and allow display over other apps."), style = MaterialTheme.typography.bodySmall)
            if(settingsUnavailable) Text(tr("این تنظیم در دستگاه در دسترس نیست.", "This setting is unavailable on this device."),
                style = MaterialTheme.typography.bodySmall)
            if(settings.lyricsOverlay && granted) {
                ChoiceRow(listOf(tr("اصلی", "Original"), tr("خط‌به‌خط", "Line by line"), tr("کلمه‌به‌کلمه", "Word by word")),
                    settings.lyricsOverlayMode, model::lyricsOverlayMode)
            }
        }
    }
}
