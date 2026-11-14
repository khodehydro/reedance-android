package com.hydro.reedance.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.*
import com.hydro.reedance.R
import com.hydro.reedance.data.Settings
val Blue = Color(0xFF2E70DF)
val BrandFont = FontFamily(Font(R.font.manrope_bold, FontWeight.ExtraBold))
val Deep = Color(0xFF102D5C)
val Paper = Color(0xFFF6F9FE)
val LocalOverlayBottomInset = staticCompositionLocalOf { 0.dp }
val LocalFa = staticCompositionLocalOf { true }
val LocalReducedMotion = staticCompositionLocalOf { false }
@Composable fun tr(fa: String, en: String) = if(LocalFa.current) fa else en
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ReedanceTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = when(settings.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val colors = if(dark) darkColorScheme(primary = Color(0xFF9BC4FF), background = Color(0xFF101D30), surface = Color(0xFF192B44), onSurface = Color(0xFFE3EDFA), secondaryContainer = Color(0xFF274463))
        else lightColorScheme(primary = Blue, background = Paper, surface = Color.White, onSurface = Color(0xFF172945), secondaryContainer = Color(0xFFEDF4FF))
    val font = FontFamily(Font(R.font.vazirmatn, FontWeight.Normal), Font(R.font.vazirmatn_bold, FontWeight.Bold))
    val base = Typography()
    MaterialTheme(colorScheme = colors, typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = font), displayMedium = base.displayMedium.copy(fontFamily = font), displaySmall = base.displaySmall.copy(fontFamily = font),
        headlineSmall = base.headlineSmall.copy(fontFamily = font, fontWeight = FontWeight.Bold),
        bodyLarge = base.bodyLarge.copy(fontFamily = font), bodyMedium = base.bodyMedium.copy(fontFamily = font), bodySmall = base.bodySmall.copy(fontFamily = font),
        titleLarge = base.titleLarge.copy(fontFamily = font, fontWeight = FontWeight.Bold), titleMedium = base.titleMedium.copy(fontFamily = font, fontWeight = FontWeight.Bold), titleSmall = base.titleSmall.copy(fontFamily = font),
        headlineLarge = base.headlineLarge.copy(fontFamily = font, fontWeight = FontWeight.Bold), headlineMedium = base.headlineMedium.copy(fontFamily = font, fontWeight = FontWeight.Bold),
        labelLarge = base.labelLarge.copy(fontFamily = font), labelMedium = base.labelMedium.copy(fontFamily = font), labelSmall = base.labelSmall.copy(fontFamily = font)
    )) {
        CompositionLocalProvider(LocalIndication provides rememberPressFeedback(), LocalRippleConfiguration provides null, content = content)
    }
}

// Opaque and elevation-independent: headers never reveal scrolling content or
// acquire a Material tonal overlay when the song header collapses.
@Composable internal fun HeaderSurface(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        content = content
    )
}
