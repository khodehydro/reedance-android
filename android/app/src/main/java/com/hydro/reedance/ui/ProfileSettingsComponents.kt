package com.hydro.reedance.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun profileSettingsBackground(): Color {
    val background = MaterialTheme.colorScheme.background
    return if(background.luminance() < .5f) Color(0xFF0B0B0B) else background
}

@Composable internal fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), tonalElevation = 0.dp) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable internal fun SettingsDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
}

@Composable internal fun SettingsAction(icon: Int, title: String, value: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = 60.dp)
        .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIcon(icon, null, Modifier.size(21.dp))
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if(value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if(LocalLayoutDirection.current == LayoutDirection.Rtl) "‹" else "›", fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.sizeIn(minWidth = 10.dp))
    }
}

@Composable internal fun SettingsChoiceDialog(title: String, choices: List<String>, selected: Int, dismiss: () -> Unit, change: (Int) -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = {
        Column(Modifier.selectableGroup()) {
            choices.forEachIndexed { index, label ->
                Row(Modifier.fillMaxWidth().selectable(index == selected, role = Role.RadioButton, onClick = { change(index) })
                    .heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RadioButton(selected = index == selected, onClick = null)
                    Text(label)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text(tr("بستن", "Close")) } })
}

@Composable internal fun AppearancePicker(theme: String, onTheme: (String) -> Unit) {
    val choices = listOf("system" to tr("سیستم", "System"), "light" to tr("روشن", "Light"), "dark" to tr("تاریک", "Dark"))
    val accent = MaterialTheme.colorScheme.primary
    val frame = RoundedCornerShape(17.dp)
    Row(Modifier.fillMaxWidth().padding(14.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        choices.forEach { (id, label) ->
            val selected = theme == id
            Column(Modifier.weight(1f).clip(frame).selectable(selected, role = Role.RadioButton, onClick = { onTheme(id) }),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(frame)
                    .then(if(selected) Modifier.border(2.dp, accent, frame) else Modifier)) {
                    ThemeMiniature(id, Modifier.fillMaxSize())
                    if(selected) Surface(Modifier.align(Alignment.BottomStart).padding(7.dp).size(22.dp),
                        shape = androidx.compose.foundation.shape.CircleShape, color = accent,
                        contentColor = MaterialTheme.colorScheme.onPrimary) {
                        Box(contentAlignment = Alignment.Center) { AppIcon(UiIcons.Check, null, Modifier.size(13.dp)) }
                    }
                }
                Text(label, Modifier.padding(bottom = 4.dp), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Lightweight previews drawn in Compose, not screenshot assets. */
@Composable private fun ThemeMiniature(theme: String, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun paint(dark: Boolean) {
            drawRect(if(dark) Color(0xFF353535) else Color(0xFFE8E8EA))
            val phone = if(dark) Color(0xFF171717) else Color(0xFFFFFFFF)
            val block = if(dark) Color(0xFF282828) else Color(0xFFEEEEF0)
            drawRoundRect(phone, Offset(.14f * w, .19f * h), Size(.72f * w, .87f * h), CornerRadius(.13f * w))
            drawRoundRect(Color(0xFF101010), Offset(.39f * w, .23f * h), Size(.22f * w, .07f * h), CornerRadius(.04f * w))
            drawRoundRect(block, Offset(.29f * w, .42f * h), Size(.42f * w, .11f * h), CornerRadius(.06f * w))
            drawRoundRect(block, Offset(.22f * w, .64f * h), Size(.34f * w, .045f * h), CornerRadius(.025f * w))
            drawRoundRect(block, Offset(.22f * w, .73f * h), Size(.56f * w, .32f * h), CornerRadius(.07f * w))
        }
        if(theme == "system") {
            clipRect(right = w / 2) { paint(false) }
            clipRect(left = w / 2) { paint(true) }
        } else paint(theme == "dark")
    }
}
