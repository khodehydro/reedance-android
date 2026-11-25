package com.hydro.reedance.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

@Composable internal fun DialoguePalette(enabled: Boolean, paperIndex: Int, dim: Boolean, content: @Composable () -> Unit) {
    val app = MaterialTheme.colorScheme
    if(!enabled) {
        content()
        return
    }
    val colors = remember(app, paperIndex, dim) { dialogueColors(app, paperIndex, dim) }
    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize(), color = colors.background, contentColor = colors.onBackground, content = content)
    }
}

private fun dialogueColors(app: ColorScheme, paperIndex: Int, dim: Boolean): ColorScheme {
    val dark = dim || app.background.luminance() < .5f
    val tones = when(paperIndex) {
        1 -> if(dark) DialogueTones(0xFF241C14, 0xFFFFEBD5, 0xFFF3BE81, 0xFF412808, 0xFFD9C394)
             else DialogueTones(0xFFF7EBD4, 0xFF302219, 0xFF784617, 0xFFFFF5E8, 0xFF615325)
        2 -> if(dark) DialogueTones(0xFF14242D, 0xFFE2F4FF, 0xFF9CD2F7, 0xFF113247, 0xFFA9D9D1)
             else DialogueTones(0xFFE5F1F6, 0xFF182D3A, 0xFF225A79, 0xFFF0FAFF, 0xFF22625B)
        else -> if(dim) DialogueTones(0xFF17191D, 0xFFE8EBF1, 0xFFB8CBFA, 0xFF21304F, 0xFFB5D8CA) else return app
    }
    val paper = Color(tones.paper)
    val ink = Color(tones.ink)
    val accent = Color(tones.accent)
    val onAccent = Color(tones.onAccent)
    val alt = Color(tones.alt)
    fun neutral(amount: Float) = lerp(paper, ink, amount)
    return app.copy(
        background = paper, onBackground = ink,
        surface = neutral(.035f), onSurface = ink, surfaceTint = accent,
        surfaceDim = neutral(if(dark) .01f else .14f), surfaceBright = neutral(if(dark) .19f else .015f),
        surfaceContainerLowest = paper, surfaceContainerLow = neutral(.045f), surfaceContainer = neutral(.08f),
        surfaceContainerHigh = neutral(.12f), surfaceContainerHighest = neutral(.16f),
        surfaceVariant = neutral(.14f), onSurfaceVariant = lerp(ink, paper, .20f),
        outline = neutral(.58f), outlineVariant = neutral(.27f),
        primary = accent, onPrimary = onAccent, primaryContainer = lerp(paper, accent, .30f), onPrimaryContainer = ink,
        secondary = alt, onSecondary = onAccent, secondaryContainer = lerp(paper, alt, .15f), onSecondaryContainer = ink,
        tertiary = alt, onTertiary = onAccent, tertiaryContainer = lerp(paper, alt, .22f), onTertiaryContainer = ink,
        error = if(dark) Color(0xFFFFB4AB) else Color(0xFF9B2921),
    )
}

private data class DialogueTones(val paper: Long, val ink: Long, val accent: Long, val onAccent: Long, val alt: Long)
