package com.hydro.reedance.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.*
import com.hydro.reedance.R
import com.hydro.reedance.data.Settings
val Blue = Color(0xFF2E70DF)
val Deep = Color(0xFF102D5C)
val Paper = Color(0xFFF6F9FE)
val LocalFa = staticCompositionLocalOf { true }
val LocalReducedMotion = staticCompositionLocalOf { false }
@Composable fun tr(fa: String, en: String) = if(LocalFa.current) fa else en
@Composable fun ReedanceTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = when(settings.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val colors = if(dark) darkColorScheme(primary = Color(0xFF9BC4FF), background = Color(0xFF101D30), surface = Color(0xFF192B44), onSurface = Color(0xFFE3EDFA), secondaryContainer = Color(0xFF274463))
        else lightColorScheme(primary = Blue, background = Paper, surface = Color.White, onSurface = Color(0xFF172945), secondaryContainer = Color(0xFFEDF4FF))
    val font = FontFamily(Font(if(LocalFa.current) R.font.vazirmatn else R.font.manrope))
    val base = Typography()
    MaterialTheme(colorScheme = colors, typography = Typography(
        bodyLarge = base.bodyLarge.copy(fontFamily = font), bodyMedium = base.bodyMedium.copy(fontFamily = font), bodySmall = base.bodySmall.copy(fontFamily = font),
        titleLarge = base.titleLarge.copy(fontFamily = font, fontWeight = FontWeight.Bold), titleMedium = base.titleMedium.copy(fontFamily = font, fontWeight = FontWeight.Bold), titleSmall = base.titleSmall.copy(fontFamily = font),
        headlineLarge = base.headlineLarge.copy(fontFamily = font, fontWeight = FontWeight.Bold), headlineMedium = base.headlineMedium.copy(fontFamily = font, fontWeight = FontWeight.Bold),
        labelLarge = base.labelLarge.copy(fontFamily = font), labelMedium = base.labelMedium.copy(fontFamily = font), labelSmall = base.labelSmall.copy(fontFamily = font)
    ), content = content)
}
