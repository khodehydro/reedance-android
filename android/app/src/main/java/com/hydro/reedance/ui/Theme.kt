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
val BrandFont = FontFamily(Font(R.font.vazirmatn_black, FontWeight.Black))
val Deep = Color(0xFF102D5C)
val Paper = Color(0xFFF6F9FE)
val LocalOverlayBottomInset = staticCompositionLocalOf { 0.dp }
val LocalFa = staticCompositionLocalOf { true }
val LocalReducedMotion = staticCompositionLocalOf { false }
@Stable internal class BackgroundMotion {
    var overlays by mutableIntStateOf(0)
    val paused: Boolean get() = overlays > 0
}
internal val LocalBackgroundMotion = staticCompositionLocalOf { BackgroundMotion() }
/** Neutral charcoal surfaces, independent of the deliberately blue brand accents. */
internal val CharcoalDarkColors = darkColorScheme(
    primary = Color(0xFF9BC4FF), onPrimary = Color(0xFF121212),
    primaryContainer = Color(0xFF303030), onPrimaryContainer = Color(0xFFF5F5F5),
    secondary = Color(0xFFCBCBCB), onSecondary = Color(0xFF121212),
    secondaryContainer = Color(0xFF2A2A2A), onSecondaryContainer = Color(0xFFF5F5F5),
    tertiary = Color(0xFFD6D6D6), onTertiary = Color(0xFF121212),
    tertiaryContainer = Color(0xFF303030), onTertiaryContainer = Color(0xFFF5F5F5),
    background = Color(0xFF121212), onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF1C1C1C), onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF282828), onSurfaceVariant = Color(0xFFB3B3B3),
    surfaceDim = Color(0xFF101010), surfaceBright = Color(0xFF363636),
    surfaceContainerLowest = Color(0xFF0B0B0B), surfaceContainerLow = Color(0xFF181818),
    surfaceContainer = Color(0xFF1E1E1E), surfaceContainerHigh = Color(0xFF282828),
    surfaceContainerHighest = Color(0xFF333333),
    // Tonal elevation must not reintroduce a blue/purple wash on neutral cards/dialogs.
    surfaceTint = Color.Transparent,
    outline = Color(0xFF737373), outlineVariant = Color(0xFF393939),
    inverseSurface = Color(0xFFE6E6E6), inverseOnSurface = Color(0xFF1C1C1C),
    inversePrimary = Color(0xFF215AB0), scrim = Color.Black,
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6)
)

@Composable fun tr(fa: String, en: String) = if(LocalFa.current) fa else en
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ReedanceTheme(settings: Settings, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = settings.theme == "dark" || (settings.theme == "system" && systemDark)
    val colors = if(dark) CharcoalDarkColors
        else lightColorScheme(primary = Blue, background = Paper, surface = Color.White, onSurface = Color(0xFF172945), secondaryContainer = Color(0xFFEDF4FF))
    val backgroundMotion = remember { BackgroundMotion() }
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
        CompositionLocalProvider(LocalIndication provides rememberPressFeedback(), LocalRippleConfiguration provides null, LocalBackgroundMotion provides backgroundMotion, content = content)
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
