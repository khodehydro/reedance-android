package com.hydro.reedance.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.tween
import com.hydro.reedance.R
import com.hydro.reedance.data.*
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun cover(id: String) = when(id) {
    "despacito" -> R.drawable.despacito; "spring-day" -> R.drawable.spring_day; "flowers" -> R.drawable.flowers
    "someone-like-you" -> R.drawable.someone_like_you; "blinding-lights" -> R.drawable.blinding_lights; else -> R.drawable.midnight_avenue
}
// Approx. 6MB maximum, shared across Home/Explore; no image decode on the UI thread.
private val coverCache = object : LruCache<Int, Bitmap>(6 * 1024 * 1024) {
    override fun sizeOf(key: Int, value: Bitmap) = value.allocationByteCount
}
@Composable fun CoverImage(id: String, description: String, modifier: Modifier = Modifier) {
    val resource = cover(id)
    val resources = LocalContext.current.resources
    val bitmap by produceState<Bitmap?>(coverCache.get(resource), resource) {
        if(value == null) value = withContext(Dispatchers.IO) {
            coverCache.get(resource) ?: BitmapFactory.decodeResource(resources, resource,
                BitmapFactory.Options().apply { inSampleSize = 2; inScaled = false })?.also { coverCache.put(resource, it) }
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.secondaryContainer)) {
        bitmap?.let { Image(it.asImageBitmap(), description, Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
    }
}
@Composable fun CatalogScreen(model: AppModel, home: Boolean, openSong: (String) -> Unit, starter: () -> Unit) {
    val catalog by model.catalog.collectAsStateWithLifecycle()
    var language by rememberSaveable { mutableIntStateOf(0) }
    var access by rememberSaveable { mutableIntStateOf(0) }
    val grid = rememberLazyGridState()
    val lesson by model.lesson.collectAsStateWithLifecycle()
    val installedIds = remember(lesson) { listOfNotNull(lesson?.s("songId")?.takeIf { it.isNotBlank() }).toSet() }
    val reduced = LocalReducedMotion.current
    val filtered = remember(catalog, language, access, installedIds) { catalog.filter { s ->
        (language == 0 || s.s("language") == listOf("", "en", "es", "ko")[language]) && when(access) {
            1 -> s.s("access") == "free"; 2 -> s.s("access") == "paid"; 3 -> s.s("id") in installedIds; else -> true
        }
    } }
    LazyVerticalGrid(columns = GridCells.Fixed(2), state = grid, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if(home) {
            item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Blue, Deep))).padding(26.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(tr("یادگیری با ریتم تو", "YOUR RHYTHM. YOUR WORDS."), color = Color(0xFFCADFFF), style = MaterialTheme.typography.labelMedium)
                        Text(tr("هر آهنگ،\nیک قدم جلوتر.", "Every song.\nOne step further."), color = Color.White, style = MaterialTheme.typography.headlineLarge)
                        Text(tr("گوش بده، کشف کن و زبان را زندگی کن.", "Listen closer. Discover more. Make the words yours."), color = Color(0xFFE1ECFF))
                        Button({ openSong("midnight-avenue") }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Blue)) { Text(tr("شروع یادگیری", "Start learning")); AppIcon(UiIcons.Play, null) }
                    }
                }
            }
            item(key = "starter", span = { GridItemSpan(maxLineSpan) }) {
                Panel(tone = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(tr("اولین قدمت را بردار", "A little start. A whole new language."), style = MaterialTheme.typography.titleLarge)
                    Text(tr("از حروف و صداها تا اولین سطر؛ انگلیسی، اسپانیایی و کره‌ای.", "Letters, sounds and your very first line. English, Spanish and Korean."))
                    TextButton(starter) { Text(tr("ورود به استارتر", "Open Starter")) }
                }
            }
        }
        item(key = "heading", span = { GridItemSpan(maxLineSpan) }) {
            Heading(if(home) tr("آهنگ بعدی تو", "Find your next song") else tr("کشف موسیقی", "Discover music"), tr("موسیقی را بشنو، کلمه‌ها را کشف کن.", "Good music. Words that stay with you."))
        }
        item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PillTabs(listOf(
                    PillOption(tr("همه", "All"), UiIcons.Globe, tr("همهٔ زبان‌ها", "All languages")),
                    PillOption("", description = tr("انگلیسی", "English"), flag = R.drawable.flag_gb_circle),
                    PillOption("", description = tr("اسپانیایی", "Spanish"), flag = R.drawable.flag_es_circle),
                    PillOption("", description = tr("کره‌ای", "Korean"), flag = R.drawable.flag_kr_circle)
                ), language) { language = it }
                PillTabs(listOf(
                    PillOption(tr("همه", "All"), UiIcons.All), PillOption(tr("رایگان", "Free"), UiIcons.LockOpen),
                    PillOption(tr("پولی", "Premium"), UiIcons.Crown), PillOption(tr("دانلودشده‌ها", "Downloaded"), UiIcons.Check)
                ), access) { access = it }
            }
        }
        items(filtered, key = { it.s("id") }, contentType = { "song" }) { song ->
            SongTile(song, Modifier.fillMaxWidth().animateItem(
                fadeInSpec = tween(if(reduced) 0 else 180),
                placementSpec = tween(if(reduced) 0 else 220),
                fadeOutSpec = tween(if(reduced) 0 else 140)
            )) { openSong(song.s("id")) }
        }
        if(filtered.isEmpty()) item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
            Panel { Text(tr("آهنگی پیدا نشد؛ فیلترها را تغییر بده.", "No songs found. Try another filter.")) }
        }
        item(key = "notice", span = { GridItemSpan(maxLineSpan) }) {
            Text(tr("فقط Midnight Avenue دموی محلی دارد. بقیهٔ عنوان‌ها پیش‌نمایش کاتالوگ‌اند، نه محتوای قابل خرید یا دانلود.", "Only Midnight Avenue has a local demo. Other titles are catalog previews, not purchasable or downloadable content."), style = MaterialTheme.typography.bodySmall)
        }
        item(key = "credits", span = { GridItemSpan(maxLineSpan) }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("developed with ❤️ by HYDRO team", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
@Composable private fun SongTile(song: JsonObject, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box {
            CoverImage(song.s("id"), song.s("title"), Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)))
            Surface(Modifier.align(Alignment.TopEnd).padding(10.dp), shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = .9f)) {
                AppIcon(if(song.s("access") == "free") UiIcons.LockOpen else UiIcons.Lock, tr("ردهٔ دسترسی", "Access tier"), Modifier.padding(6.dp).size(18.dp), tint = Deep)
            }
        }
        Foreign(song.s("title"))
        Text(song.s("artist"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${when(song.s("language")) { "es" -> "🇪🇸"; "ko" -> "🇰🇷"; else -> "🇬🇧" }}  ${song.s("level")}  ·  ${song.s("duration")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}
