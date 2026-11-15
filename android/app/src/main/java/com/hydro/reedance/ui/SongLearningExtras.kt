package com.hydro.reedance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import com.hydro.reedance.data.*

/** Only supplied text is shown; no guessed pronunciation or device TTS. */
@Composable internal fun WrittenPronunciation(item: JsonObject) {
    val fa = LearningLanguage.persian
    val readings = listOf(item.s("ipa"), item.s("reading"), if(fa) item.s("faReading") else "")
        .filter { it.isNotBlank() }.distinct()
    readings.forEach { reading ->
        if(reading == item.s("faReading")) PersianText(reading)
        else Foreign(reading)
    }
}

@Composable internal fun SongQuizInvitation(onClick: () -> Unit) {
    PressOutlinedButton(onClick, Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { AppIcon(UiIcons.Quiz, null) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tr("آماده‌ای؟", "Ready for a challenge?"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(tr("آموخته‌هایت را با آزمون آهنگ مرور کن", "Put your learning into practice with the song quiz."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AppIcon(UiIcons.PlayFilled, null, Modifier.size(18.dp))
        }
    }
}
