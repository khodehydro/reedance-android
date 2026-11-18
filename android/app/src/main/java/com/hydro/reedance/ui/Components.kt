package com.hydro.reedance.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.delay
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.*
import com.google.gson.*
import com.hydro.reedance.data.*

@Composable fun Panel(modifier: Modifier = Modifier, tone: Color = MaterialTheme.colorScheme.surface, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth().then(if(LocalItemEntranceEnabled.current) Modifier.itemEntrance() else Modifier), shape = RoundedCornerShape(22.dp), color = tone, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable fun Heading(title: String, subtitle: String = "") { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(title, style = MaterialTheme.typography.headlineMedium)
    if(subtitle.isNotEmpty()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
} }
@Composable fun Foreign(text: String, modifier: Modifier = Modifier, large: Boolean = false) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(text, modifier.fillMaxWidth(), style = (if(large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge).copy(textDirection = TextDirection.Ltr, textAlign = androidx.compose.ui.text.style.TextAlign.Left))
    }
}
@Composable fun Action(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    PressButton(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(14.dp)) { Text(text) }
}
data class PillOption(val label: String, val icon: Int? = null, val description: String = label, val flag: Int? = null, val filledIcon: Int? = null)
@Composable fun ChoiceRow(items: List<String>, selected: Int, onSelect: (Int) -> Unit) =
    PillTabs(items.map { PillOption(it) }, selected, onSelect)

@Composable fun ChoiceRow(items: List<String>, selected: Int, icons: List<Pair<Int, Int>>, onSelect: (Int) -> Unit) {
    require(items.size == icons.size)
    PillTabs(items.mapIndexed { index, label -> PillOption(label, icons[index].first, filledIcon = icons[index].second) }, selected, onSelect)
}

@Composable fun PillTabs(items: List<PillOption>, selected: Int, onSelect: (Int) -> Unit) =
    PillTabRow(items, selected, false, onSelect)

@Composable fun ExpandedPillTabs(items: List<PillOption>, selected: Int, onSelect: (Int) -> Unit) =
    PillTabRow(items, selected, true, onSelect)

@Composable private fun PillTabRow(items: List<PillOption>, selected: Int, expanded: Boolean, onSelect: (Int) -> Unit) {
    val reduced = LocalReducedMotion.current
    val duration = if(reduced) 0 else 150
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
    Row(Modifier.then(if(expanded) Modifier.fillMaxWidth() else Modifier.horizontalScroll(rememberScrollState())).selectableGroup().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { index, option ->
            val active = selected == index
            val background by animateColorAsState(if(active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, tween(duration), label = "pill-color")
            val foreground by animateColorAsState(if(active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, tween(duration), label = "pill-ink")
            val scale by animateFloatAsState(if(active) 1f else .97f, tween(duration), label = "pill-scale")
            Surface(shape = if(option.flag != null) CircleShape else RoundedCornerShape(50), color = background, contentColor = foreground,
                shadowElevation = if(active) 3.dp else 0.dp,
                border = BorderStroke(1.dp, if(active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)),
                modifier = (if(expanded) Modifier.weight(1f) else Modifier).graphicsLayer { scaleX = if(expanded) 1f else scale; scaleY = scale }
                    .selectable(active, role = Role.Tab, onClick = { if(!active) onSelect(index) })
                    .semantics { contentDescription = option.description }) {
                if(option.flag != null) {
                    Image(painterResource(option.flag), null, Modifier.size(48.dp).padding(if(active) 3.dp else 1.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                } else if(expanded) Column(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)) {
                    (if(active) option.filledIcon ?: option.icon else option.icon)?.let { AppIcon(it, null, Modifier.size(17.dp)) }
                    Text(option.label, style = MaterialTheme.typography.labelLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontWeight = if(active) FontWeight.Bold else FontWeight.Normal)
                } else Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    (if(active) option.filledIcon ?: option.icon else option.icon)?.let { AppIcon(it, null, Modifier.size(17.dp)) }
                    Text(option.label, style = MaterialTheme.typography.labelLarge, fontWeight = if(active) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
    }
}
/** Reference CSS: enter 390ms (-15,10), exit 220ms (30,0), mirrored in RTL. */
@Composable fun Deck(cardKey: Any, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val sign = if(rtl) -1 else 1
    val sheets = remember { mutableMapOf<Any, @Composable () -> Unit>() }
    sheets[cardKey] = content
    LaunchedEffect(cardKey) {
        if(!reduced) delay(650)
        sheets.keys.retainAll(setOf(cardKey))
    }
    Box(modifier.fillMaxWidth().padding(top = 21.dp)) {
        Box(Modifier.matchParentSize().padding(horizontal = 15.dp).graphicsLayer { translationY = -21.dp.toPx(); rotationZ = 2.3f; scaleX = .973f }.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .22f)))
        Box(Modifier.matchParentSize().padding(horizontal = 8.dp).graphicsLayer { translationY = -13.dp.toPx(); rotationZ = -1.6f; scaleX = .99f }.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)))
        AnimatedContent(cardKey, transitionSpec = {
            if(reduced) EnterTransition.None togetherWith ExitTransition.None
            else (fadeIn(tween(390, 220)) + slideIn(tween(390, 220, easing = CubicBezierEasing(.19f,.85f,.3f,1f))) { IntOffset(-15 * sign, 10) } + scaleIn(tween(390, 220), initialScale = .975f)) togetherWith
                (fadeOut(tween(220)) + slideOut(tween(220, easing = FastOutLinearInEasing)) { IntOffset(30 * sign, 0) } + scaleOut(tween(220), targetScale = .96f))
        }, label = "card-deck") { key ->
            // Preserve the outgoing visual sheet, but remove its semantics and input.
            val inactive = if(key == cardKey) Modifier else Modifier.clearAndSetSemantics { }.pointerInput(key) {
                awaitPointerEventScope {
                    while(true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
            Box(inactive) { sheets[key]?.invoke() }
        }
    }
}
@Composable fun LearningTable(table: JsonObject, fitToWidth: Boolean = false) {
    val fa = LearningLanguage.persian
    val columns = remember(table) { table.getAsJsonArray("columns")?.toList().orEmpty() }
    val rows = remember(table) { table.getAsJsonArray("rows")?.map { it.asJsonArray.toList() }.orEmpty() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PersianText(table.tr("title", fa), style = MaterialTheme.typography.titleSmall)
        table.tr("intro", fa).takeIf { it.isNotEmpty() }?.let { PersianText(it) }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            if(fitToWidth) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    // Keep wide tables readable without introducing a horizontal gesture inside the pager.
                    val columnsPerBlock = (maxWidth / 104.dp).toInt().coerceAtLeast(1)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        columns.indices.chunked(columnsPerBlock).forEach { indices ->
                            LearningTableGrid(columns, rows, indices, fa, fitToWidth = true)
                        }
                    }
                }
            } else {
                LearningTableGrid(columns, rows, columns.indices.toList(), fa, fitToWidth = false)
            }
        }
        table.tr("footer", fa).takeIf { it.isNotEmpty() }?.let { PersianText(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable private fun LearningTableGrid(columns: List<JsonElement>, rows: List<List<JsonElement>>,
    indices: List<Int>, fa: Boolean, fitToWidth: Boolean) {
    val shape = RoundedCornerShape(12.dp)
    val scroll = if(fitToWidth) Modifier else Modifier.horizontalScroll(rememberScrollState())
    Column(Modifier.fillMaxWidth().then(scroll).clip(shape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)) {
        Row(Modifier.background(MaterialTheme.colorScheme.secondaryContainer)) {
            indices.forEach { index ->
                LearningCell(columns[index].cell(fa), "", MaterialTheme.typography.titleSmall,
                    if(fitToWidth) Modifier.weight(1f) else Modifier.width(152.dp))
            }
        }
        rows.forEach { row ->
            HorizontalDivider()
            Row {
                indices.forEach { index ->
                    val cell = row.getOrNull(index)
                    LearningCell(cell?.cell(fa).orEmpty(), if(cell?.isJsonObject == true) cell.asJsonObject.s("dir") else "",
                        MaterialTheme.typography.bodyMedium, if(fitToWidth) Modifier.weight(1f) else Modifier.width(152.dp))
                }
            }
        }
    }
}

@Composable fun LearningNote(note: JsonObject, fitTablesToWidth: Boolean = false, save: (@Composable () -> Unit)? = null) {
    val fa = LearningLanguage.persian
    var answer by remember(note) { mutableStateOf(false) }
    val tables = remember(note) { note.a("tables") }
    val color = when(note.s("tone")) {
        "mint" -> Color(0xFFDEEFE7); "amber" -> Color(0xFFFFEBCB); "coral" -> Color(0xFFFFE0D8); "rose" -> Color(0xFFF8DFE8); "apricot" -> Color(0xFFFFE6CF); else -> Color(0xFFE3EEFF)
    }
    Panel(tone = androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surface, color, .18f)) {
        PersianText(note.tr("tag", fa), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        PersianText(note.tr("title", fa), style = MaterialTheme.typography.titleLarge)
        note.tr("body", fa).ifEmpty { note.tr("tip", fa) }.takeIf { it.isNotBlank() }?.let { body ->
            LearningSection("توضیح") { PersianText(body) }
        }
        WrittenPronunciation(note)
        val ex = note.get("example")
        val text = if(ex?.isJsonObject == true) ex.asJsonObject.s("text") else if(ex?.isJsonPrimitive == true) ex.asString else ""
        if(text.isNotEmpty()) {
            LearningSection("مثال در جمله") {
                Foreign(text)
                val translation = if(ex?.isJsonObject == true) ex.asJsonObject.tr("translation", fa) else note.tr("translation", fa)
                if(translation.isNotBlank()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                    PersianText(translation)
                }
            }
        }
        tables.forEach { LearningTable(it, fitToWidth = fitTablesToWidth) }
        note.tr("takeaway", fa).takeIf { it.isNotEmpty() }?.let { PersianText(it, style = MaterialTheme.typography.titleSmall) }
        if(note.has("check")) {
            PersianText(note.obj("check").tr("prompt", fa))
            RevealButton(answer, { answer = !answer })
            AnimatedVisibility(answer, enter = if(LocalReducedMotion.current) EnterTransition.None else fadeIn(tween(260)) + expandVertically()) { PersianText(note.obj("check").tr("answer", fa)) }
        }
        save?.invoke()
    }

}

// Match the title font scale, keep the crown adjacent to short titles, and reserve
// room for it on long titles. Song names remain LTR in both interface languages.
@Composable fun SongTitle(title: String, premium: Boolean, style: TextStyle = MaterialTheme.typography.bodyLarge) {
    val crownSize = with(LocalDensity.current) { style.fontSize.toDp() * .9f }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if(premium) AppIcon(UiIcons.CrownFilled, tr("پریمیوم", "Premium"), Modifier.size(crownSize), tint = Color(0xFFE6A800))
            Text(title, Modifier.weight(1f, fill = false), style = style, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
