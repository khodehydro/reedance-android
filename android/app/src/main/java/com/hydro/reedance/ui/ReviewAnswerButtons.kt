package com.hydro.reedance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable internal fun ReviewAnswerButtons(enabled: Boolean, onAnswer: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    // Intrinsic height lets both buttons grow equally when a translated label wraps.
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PressButton({ onAnswer(true) }, Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp),
            enabled = enabled, shape = shape) {
            Text(tr("یادم بود", "Remembered"), textAlign = TextAlign.Center)
        }
        PressOutlinedButton({ onAnswer(false) }, Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp),
            enabled = enabled, shape = shape) {
            Text(tr("دوباره مرور کنم", "Review again"), textAlign = TextAlign.Center)
        }
    }
}
