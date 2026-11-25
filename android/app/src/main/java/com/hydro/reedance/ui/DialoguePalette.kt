package com.hydro.reedance.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

internal enum class DialoguePaper {
    System, Sepia, Blue, Peach, Mint, Lavender;

    @Composable fun label(): String = when(this) {
        System -> tr("سیستم", "System")
        Sepia -> tr("کاهی", "Sepia")
        Blue -> tr("آبی", "Blue")
        Peach -> tr("هلویی", "Peach")
        Mint -> tr("نعنایی", "Mint")
        Lavender -> tr("یاسی", "Lavender")
    }
}

@Composable internal fun DialoguePalette(enabled: Boolean, paperIndex: Int, dim: Boolean, content: @Composable () -> Unit) {
    val app = MaterialTheme.colorScheme
    val colors = remember(app, enabled, paperIndex, dim) { if(enabled) dialogueColors(app, paperIndex, dim) else app }
    val window = (LocalContext.current as? Activity)?.window
    val controller = remember(window) { window?.let { WindowCompat.getInsetsController(it, it.decorView) } }
    DisposableEffect(controller) {
        val appLightBars = app.background.luminance() > .5f
        onDispose {
            controller?.isAppearanceLightStatusBars = appLightBars
            controller?.isAppearanceLightNavigationBars = appLightBars
        }
    }
    val lightBars = colors.background.luminance() > .5f
    SideEffect {
        controller?.isAppearanceLightStatusBars = lightBars
        controller?.isAppearanceLightNavigationBars = lightBars
    }
    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize(), color = colors.background, contentColor = colors.onBackground, content = content)
    }
}

private fun dialogueColors(app: ColorScheme, paperIndex: Int, dim: Boolean): ColorScheme {
    val dark = dim || app.background.luminance() < .5f
    val tones = when(DialoguePaper.entries[paperIndex]) {
        DialoguePaper.Sepia -> if(dark) DialogueTones(0xFF241C14, 0xFFFFEBD5, 0xFFF3BE81, 0xFF412808, 0xFFD9C394)
             else DialogueTones(0xFFF7EBD4, 0xFF302219, 0xFF784617, 0xFFFFF5E8, 0xFF615325)
        DialoguePaper.Blue -> if(dark) DialogueTones(0xFF14242D, 0xFFE2F4FF, 0xFF9CD2F7, 0xFF113247, 0xFFA9D9D1)
             else DialogueTones(0xFFE5F1F6, 0xFF182D3A, 0xFF225A79, 0xFFF0FAFF, 0xFF22625B)
        DialoguePaper.Peach -> if(dark) DialogueTones(0xFF33211D, 0xFFFFEBE1, 0xFFFFAF97, 0xFF47221C, 0xFFFFCC85)
            else DialogueTones(0xFFFFE7DC, 0xFF3E231F, 0xFF973E30, 0xFFFFF5F1, 0xFF785014)
        DialoguePaper.Mint -> if(dark) DialogueTones(0xFF142D27, 0xFFDEFFF1, 0xFF87E2BA, 0xFF153A2C, 0xFFC7E892)
            else DialogueTones(0xFFE2F7E9, 0xFF17332B, 0xFF17664B, 0xFFF3FFF8, 0xFF4B612B)
        DialoguePaper.Lavender -> if(dark) DialogueTones(0xFF2B203B, 0xFFF6EBFF, 0xFFD7BAFF, 0xFF39234E, 0xFFF4B4D8)
            else DialogueTones(0xFFF2E7FC, 0xFF302347, 0xFF65449B, 0xFFFFF7FF, 0xFF8A3F70)
        DialoguePaper.System -> if(dim) DialogueTones(0xFF17191D, 0xFFE8EBF1, 0xFFB8CBFA, 0xFF21304F, 0xFFB5D8CA) else return app
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
