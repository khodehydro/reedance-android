package com.hydro.reedance.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
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
    "pending-gulum-ii" -> R.drawable.gulum_ii
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
        value = coverCache.get(resource) ?: withContext(Dispatchers.IO) {
            coverCache.get(resource) ?: BitmapFactory.decodeResource(resources, resource,
                BitmapFactory.Options().apply { inSampleSize = 2; inScaled = false })?.also { coverCache.put(resource, it) }
        }
    }
    // Draw-time clipping is independent of lazy animateItem/retained graphics layers.
    // Apply it to both placeholder and decoded image on every draw.
    Box(modifier.drawWithCache {
        val radius = 20.dp.toPx()
        val outline = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(radius))) }
        onDrawWithContent { clipPath(outline) { this@onDrawWithContent.drawContent() } }
    }.background(MaterialTheme.colorScheme.secondaryContainer)) {
        bitmap?.let { Image(it.asImageBitmap(), description, Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
    }
}
@Composable fun CatalogScreen(model: AppModel, home: Boolean, openSong: (String) -> Unit, starter: () -> Unit, explore: () -> Unit) {
    val catalog by model.catalog.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val targetLanguage = settings.targetLanguage
    var query by rememberSaveable { mutableStateOf("") }
    var access by rememberSaveable { mutableIntStateOf(0) }
    val grid = rememberLazyGridState()
    val lesson by model.lesson.collectAsStateWithLifecycle()
    val installedIds by model.installedIds.collectAsStateWithLifecycle()
    val searchFocus = LocalSearchFocus.current
    DisposableEffect(searchFocus) { onDispose { searchFocus?.focused = false } }
    val reduced = LocalReducedMotion.current
    val filtered = remember(catalog, targetLanguage, home, access, installedIds, query) { catalog.filter { s ->
        (s.s("language") == targetLanguage) &&
        (home || query.isBlank() || listOf(s.s("title"), s.s("artist")).any { it.contains(query.trim(), ignoreCase = true) }) && when(if(home) 0 else access) {
            1 -> s.s("access") == "free"; 2 -> s.s("access") == "paid"; 3 -> s.s("id") in installedIds; else -> true
        }
    }.let { if(home) latestSongs(it) else it } }
    LazyVerticalGrid(columns = GridCells.Fixed(2), state = grid, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp + LocalOverlayBottomInset.current), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if(home) {
            item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
                HomePager(model, explore)
            }
            item(key = "starter", span = { GridItemSpan(maxLineSpan) }) {
                Panel(tone = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(tr("اولین قدمت را بردار", "A little start. A whole new language."), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        StarterArtwork(Modifier.size(104.dp))
                    }
                    Text(tr("از حروف و صداها تا اولین سطر؛ انگلیسی، اسپانیایی و کره‌ای.", "Letters, sounds and your very first line. English, Spanish and Korean."))
                    PressButton(starter, Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                        Text(tr("ورود به استارتر", "Open Starter"), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        AppIcon(UiIcons.Back, null, Modifier.size(20.dp).graphicsLayer { rotationZ = 180f })
                    }
                }
            }
        }
        item(key = "heading", span = { GridItemSpan(maxLineSpan) }) {
            Heading(if(home) tr("آخرین آهنگ‌ها", "Latest songs") else tr("کشف موسیقی", "Discover music"), tr("موسیقی را بشنو، کلمه‌ها را کشف کن.", "Good music. Words that stay with you."))
        }
        if(!home) item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth()
                    .onFocusChanged { searchFocus?.focused = it.isFocused }
                    .onGloballyPositioned { searchFocus?.bounds = it.boundsInRoot() },
                    shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { searchFocus?.dismiss?.invoke() }),
                    leadingIcon = { AppIcon(UiIcons.Search, null, Modifier.size(20.dp)) },
                    singleLine = true, label = { Text(tr("جست‌وجوی آهنگ یا خواننده", "Search songs or artists")) },
                    trailingIcon = { if(query.isNotEmpty()) PressTextButton({ query = "" }) { Text(tr("پاک کردن", "Clear")) } })
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
        item(key = "notice", span = { GridItemSpan(maxLineSpan) }) {
            Text(tr("فقط Midnight Avenue دموی محلی دارد. بقیهٔ عنوان‌ها پیش‌نمایش کاتالوگ‌اند، نه محتوای قابل خرید یا دانلود.", "Only Midnight Avenue has a local demo. Other titles are catalog previews, not purchasable or downloadable content."), style = MaterialTheme.typography.bodySmall)
        }
        item(key = "credits", span = { GridItemSpan(maxLineSpan) }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DeveloperCredit()
            }
        }
    }
}
@Composable private fun SongTile(song: JsonObject, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box {
            CoverImage(if(song.s("distributionRef") == "gulum-ii") "pending-gulum-ii" else song.s("id"), song.s("title"), Modifier.fillMaxWidth().aspectRatio(1f))
        }
        SongTitle(song.s("title"), premium = song.s("access") == "paid")
        Text(song.s("artist"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${when(song.s("language")) { "tr" -> "🇹🇷"; "es" -> "🇪🇸"; "ko" -> "🇰🇷"; else -> "🇬🇧" }}  ${song.s("level")}  ·  ${song.s("duration")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}
