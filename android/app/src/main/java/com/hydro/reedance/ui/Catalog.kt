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
import com.hydro.reedance.domain.latestSongs
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
    val settings by model.settings.collectAsStateWithLifecycle()
    val targetLanguage = settings.targetLanguage
    var access by rememberSaveable { mutableIntStateOf(0) }
    val grid = rememberLazyGridState()
    val lesson by model.lesson.collectAsStateWithLifecycle()
    val installedIds = remember(lesson) { listOfNotNull(lesson?.s("songId")?.takeIf { it.isNotBlank() }).toSet() }
    val reduced = LocalReducedMotion.current
    val filtered = remember(catalog, targetLanguage, home, access, installedIds) { catalog.filter { s ->
        (s.s("language") == targetLanguage) && when(if(home) 0 else access) {
            1 -> s.s("access") == "free"; 2 -> s.s("access") == "paid"; 3 -> s.s("id") in installedIds; else -> true
        }
    }.let { if(home) latestSongs(it) else it } }
    LazyVerticalGrid(columns = GridCells.Fixed(2), state = grid, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp + LocalOverlayBottomInset.current), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item(key = "heading", span = { GridItemSpan(maxLineSpan) }) {
            Heading(if(home) tr("آخرین آهنگ‌ها", "Latest songs") else tr("کشف موسیقی", "Discover music"), tr("موسیقی را بشنو، کلمه‌ها را کشف کن.", "Good music. Words that stay with you."))
        }
        if(!home) item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PillTabs(listOf(
                    PillOption(tr("همه", "All"), UiIcons.All), PillOption(tr("رایگان", "Free"), UiIcons.Play),
                    PillOption(tr("پریمیوم", "Premium"), UiIcons.Crown), PillOption(tr("دانلودشده‌ها", "Downloaded"), UiIcons.Check)
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
            Panel { Text(if(home) tr("هنوز آهنگی برای این زبان منتشر نشده است.", "No songs published for this language yet.") else tr("آهنگی پیدا نشد؛ فیلترها را تغییر بده.", "No songs found. Try another filter.")) }
        }
        if(!home) {
            item(key = "starter", span = { GridItemSpan(maxLineSpan) }) {
                Panel(tone = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(tr("اولین قدمت را بردار", "A little start. A whole new language."), style = MaterialTheme.typography.titleLarge)
                    Text(tr("از حروف و صداها تا اولین سطر؛ انگلیسی، اسپانیایی و کره‌ای.", "Letters, sounds and your very first line. English, Spanish and Korean."))
                    PressTextButton(starter) { Text(tr("ورود به استارتر", "Open Starter")) }
                }
            }
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
            if(song.s("access") == "paid") Surface(Modifier.align(Alignment.TopEnd).padding(10.dp), shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = .9f)) {
                AppIcon(UiIcons.Crown, tr("پریمیوم", "Premium"), Modifier.padding(6.dp).size(18.dp), tint = Color(0xFFE6A800))
            }
        }
        Foreign(song.s("title"))
        Text(song.s("artist"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${when(song.s("language")) { "es" -> "🇪🇸"; "ko" -> "🇰🇷"; else -> "🇬🇧" }}  ${song.s("level")}  ·  ${song.s("duration")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}
