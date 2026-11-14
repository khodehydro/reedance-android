package com.hydro.reedance.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Never compose hidden text/actions: blur is presentation, not access control.
 * Placeholder bars also work below API 31, where Compose's blur is unsupported. */
@Composable internal fun PremiumPreview() {
    Panel {
        Column(Modifier.fillMaxWidth().then(if(Build.VERSION.SDK_INT >= 31) Modifier.blur(7.dp) else Modifier), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(.8f, 1f, .65f).forEach { width ->
                Box(Modifier.fillMaxWidth(width).height(16.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .15f)))
            }
        }
        Text(tr("ادامهٔ آموزش با اشتراک", "More learning with a subscription"), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable internal fun SubscriptionOffer(onClick: () -> Unit) {
    Surface(color = Color(0xFFFFE69A), contentColor = Color(0xFF493300), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIcon(UiIcons.Crown, null, tint = Color(0xFF936000))
                Text(tr("آموزش کامل با اشتراک پریمیوم", "Unlock the complete lesson"), style = MaterialTheme.typography.titleMedium)
            }
            Text(tr("پخش و دانلود به اشتراک نیاز ندارند؛ برای دیدن تمام لغت‌ها، لیریک و نکته‌ها اشتراک تهیه کن.", "Playback and downloads do not require a subscription. Subscribe for all words, lyrics and learning notes."))
            PressButton(onClick, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF493300), contentColor = Color(0xFFFFE69A))) {
                Text(tr("تهیهٔ اشتراک", "Get a subscription"))
            }
        }
    }
}
