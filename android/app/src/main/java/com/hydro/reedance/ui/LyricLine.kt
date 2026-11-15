package com.hydro.reedance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import com.hydro.reedance.data.*

/** A play action at the start of the original line; translation sits below the text, not the button. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun LyricLine(line: JsonObject, mode: Int, active: Boolean, onPlay: () -> Unit, onToken: (JsonObject) -> Unit, playing: Boolean = false, single: Boolean = false) {
    val fa = LearningLanguage.persian
    val interfaceDirection = LayoutDirection.Rtl
    Panel(tone = if(active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if(!single) PressIconButton(onPlay, Modifier.size(48.dp)) {
                    AppIcon(if(playing) UiIcons.PauseFilled else UiIcons.PlayFilled,
                        if(playing) tr("مکث خط: ${line.s("text")}", "Pause line: ${line.s("text")}") else tr("پخش خط: ${line.s("text")}", "Play line: ${line.s("text")}"), Modifier.size(18.dp),
                        tint = if(active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = if(single) Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally) else Arrangement.spacedBy(2.dp)) {
                        line.a("tokens").forEach { token ->
                            Column(Modifier.clickable { onToken(token) }.padding(horizontal = 3.dp, vertical = 8.dp), horizontalAlignment = if(single) Alignment.CenterHorizontally else Alignment.Start) {
                                Text(token.s("surface"), style = MaterialTheme.typography.titleMedium)
                                if(mode == 2) Text(token.tr("translation", fa), style = MaterialTheme.typography.labelSmall.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Rtl), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if(mode == 1) CompositionLocalProvider(LocalLayoutDirection provides interfaceDirection) {
                        Text(line.tr("translation", fa), Modifier.fillMaxWidth(), style = LocalTextStyle.current.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Rtl), textAlign = if(single) TextAlign.Center else if(fa) TextAlign.Right else TextAlign.Left)
                    }
                }
            }
        }
    }
}
