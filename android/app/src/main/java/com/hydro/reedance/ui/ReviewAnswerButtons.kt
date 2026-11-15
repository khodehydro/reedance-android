package com.hydro.reedance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable internal fun ReviewAnswerButtons(enabled: Boolean, onAnswer: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
    val green = if(dark) Color(0xFF1C3D2C) else Color(0xFFE1F3E8)
    val greenInk = if(dark) Color(0xFFB1E5C4) else Color(0xFF205C38)
    val red = if(dark) Color(0xFF40272C) else Color(0xFFFBE8EB)
    val redInk = if(dark) Color(0xFFF2B9C2) else Color(0xFF8B3041)
    // Intrinsic height lets both buttons grow equally when a translated label wraps.
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PressButton({ onAnswer(true) }, Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp),
            enabled = enabled, shape = shape, colors = ButtonDefaults.buttonColors(containerColor = green, contentColor = greenInk)) {
            Text(tr("یادم بود", "Remembered"), textAlign = TextAlign.Center)
        }
        PressOutlinedButton({ onAnswer(false) }, Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp),
            enabled = enabled, shape = shape, colors = ButtonDefaults.outlinedButtonColors(containerColor = red, contentColor = redInk)) {
            Text(tr("دوباره مرور کنم", "Review again"), textAlign = TextAlign.Center)
        }
    }
}
