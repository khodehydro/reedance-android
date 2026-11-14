package com.hydro.reedance.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.hydro.reedance.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private data class SongLanguage(val code: String, val flag: Int)
private val songLanguages = listOf(
    SongLanguage("en", R.drawable.flag_gb_circle),
    SongLanguage("es", R.drawable.flag_es_circle),
    SongLanguage("ko", R.drawable.flag_kr_circle),
    SongLanguage("tr", R.drawable.flag_tr_circle)
)

@Composable private fun languageName(code: String): String = when(code) {
    "es" -> tr("اسپانیایی", "Spanish")
    "tr" -> tr("ترکی", "Turkish")
    "ko" -> tr("کره‌ای", "Korean")
    else -> tr("انگلیسی", "English")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MainHeader(targetLanguage: String, onLanguage: (String) -> Unit, profile: () -> Unit) {
    var choosingLanguage by rememberSaveable { mutableStateOf(false) }
    val selected = songLanguages.firstOrNull { it.code == targetLanguage } ?: songLanguages.first()
    val selectedName = languageName(selected.code)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PressIconButton({ choosingLanguage = true }, Modifier.size(48.dp)) {
            Image(painterResource(selected.flag), tr("انتخاب زبان آهنگ: $selectedName", "Choose song language: $selectedName"), Modifier.size(32.dp).clip(CircleShape))
        }
        CyclingBrand(Modifier.weight(1f))
        PressIconButton(profile, Modifier.size(48.dp)) {
            AppIcon(UiIcons.User, tr("فضای من و تنظیمات", "My space and settings"))
        }
    }
    if(choosingLanguage) ReedanceBottomSheet(onDismissRequest = { choosingLanguage = false }) {
        val dismissSheet = LocalSheetDismiss.current
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("زبان آهنگ‌ها", "Song language"), style = MaterialTheme.typography.titleLarge)
            Text(tr("می‌خواهی با آهنگ‌های کدام زبان یاد بگیری؟", "Which language would you like to learn through music?"), style = MaterialTheme.typography.bodyMedium)
            songLanguages.forEach { language ->
                val name = languageName(language.code)
                Row(Modifier.fillMaxWidth().clip(CircleShape).selectable(
                    selected = language.code == selected.code,
                    role = Role.RadioButton,
                    onClick = { dismissSheet { onLanguage(language.code) } }
                ).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Image(painterResource(language.flag), null, Modifier.size(36.dp).clip(CircleShape))
                    Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    PressRadioButton(language.code == selected.code, null)
                }
            }
        }
    }
}

// The timer and animation state are isolated here: no periodic catalog recomposition.
@Composable private fun CyclingBrand(modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val paused = LocalBackgroundMotion.current.paused
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var showLogo by remember { mutableStateOf(false) }
    LaunchedEffect(reduced, paused, lifecycle) {
        if(reduced) showLogo = false
        if(!reduced && !paused) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(isActive) {
                delay(3_000)
                showLogo = !showLogo
            }
        }
    }
    Box(modifier.height(48.dp).clipToBounds().clearAndSetSemantics { contentDescription = "Reedance" }, contentAlignment = Alignment.Center) {
        AnimatedContent(targetState = showLogo && !reduced, contentAlignment = Alignment.Center,
            transitionSpec = {
                (slideInVertically(tween(450)) { it } + fadeIn(tween(450))) togetherWith
                    (slideOutVertically(tween(450)) { -it } + fadeOut(tween(450))) using SizeTransform(clip = true)
            }, label = "header-brand") { logo ->
            if(logo) Image(painterResource(R.drawable.reedance_header), null, Modifier.size(40.dp))
            else Text("Reedance", maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = BrandFont, fontWeight = FontWeight.ExtraBold))
        }
    }
}
