package com.hydro.reedance.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydro.reedance.R
import com.hydro.reedance.data.*
import com.google.gson.JsonObject
import com.hydro.reedance.domain.latestSongs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

fun cover(id: String): Int? = when(id) {
    "pending-gulum-ii", "gulum-2" -> R.drawable.gulum_ii
    else -> null
}
// Approx. 6MB maximum, shared across Home/Explore; no image decode on the UI thread.
private val coverCache = object : LruCache<Int, Bitmap>(6 * 1024 * 1024) {
    override fun sizeOf(key: Int, value: Bitmap) = value.allocationByteCount
}
@Composable fun CoverImage(id: String, description: String, modifier: Modifier = Modifier) {
    val resource = cover(id)
    val resources = LocalContext.current.resources
    val bitmap by produceState<Bitmap?>(resource?.let { coverCache.get(it) }, resource) {
        value = resource?.let { coverCache.get(it) } ?: if(resource == null) null else withContext(Dispatchers.IO) {
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
    if(home) CatalogFeed(model, true, openSong, starter, explore)
    else ExploreCatalogScreen(model, openSong)
}

@Composable private fun CatalogFeed(model: AppModel, home: Boolean, openSong: (String) -> Unit, starter: () -> Unit, explore: () -> Unit) {
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
                    PillOption(tr("همه", "All"), UiIcons.All, filledIcon = UiIcons.AllFilled), PillOption(tr("رایگان", "Free"), UiIcons.Play, filledIcon = UiIcons.PlayFilled),
                    PillOption(tr("پریمیوم", "Premium"), UiIcons.Crown, filledIcon = UiIcons.CrownFilled), PillOption(tr("دانلودشده‌ها", "Downloaded"), UiIcons.Check, filledIcon = UiIcons.CheckFilled)
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
        item(key = "credits", span = { GridItemSpan(maxLineSpan) }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DeveloperCredit()
            }
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun ExploreCatalogScreen(model: AppModel, openSong: (String) -> Unit) {
    val catalog by model.catalog.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val installedIds by model.installedIds.collectAsStateWithLifecycle()
    val query = rememberSaveable { mutableStateOf("") }
    val targetLanguage = settings.targetLanguage
    val filters = listOf(
        PillOption(tr("همه", "All"), UiIcons.All, filledIcon = UiIcons.AllFilled),
        PillOption(tr("رایگان", "Free"), UiIcons.Play, filledIcon = UiIcons.PlayFilled),
        PillOption(tr("پریمیوم", "Premium"), UiIcons.Crown, filledIcon = UiIcons.CrownFilled),
        PillOption(tr("دانلودشده‌ها", "Downloaded"), UiIcons.Check, filledIcon = UiIcons.CheckFilled)
    )
    val pagerState = rememberPagerState(pageCount = { filters.size })
    val scope = rememberCoroutineScope()
    val searchFocus = LocalSearchFocus.current
    val compactPages = remember(filters.size) {
        mutableStateListOf<Boolean>().apply { repeat(filters.size) { add(false) } }
    }
    val compact = compactPages[pagerState.currentPage]
    DisposableEffect(searchFocus) { onDispose { searchFocus?.focused = false } }
    LaunchedEffect(compact) { if(compact) searchFocus?.dismiss?.invoke() }

    Column(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = !compact,
            enter = fadeIn(tween(170)) + expandVertically(tween(190)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(190))
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Heading(tr("کشف موسیقی", "Discover music"), tr("موسیقی را بشنو، کلمه‌ها را کشف کن.", "Good music. Words that stay with you."))
                OutlinedTextField(
                    value = query.value,
                    onValueChange = { query.value = it },
                    modifier = Modifier.fillMaxWidth()
                        .onFocusChanged { searchFocus?.focused = it.isFocused }
                        .onGloballyPositioned { searchFocus?.bounds = it.boundsInRoot() },
                    shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { searchFocus?.dismiss?.invoke() }),
                    leadingIcon = { AppIcon(UiIcons.Search, null, Modifier.size(20.dp)) },
                    singleLine = true,
                    label = { Text(tr("جست‌وجوی آهنگ یا خواننده", "Search songs or artists")) },
                    trailingIcon = {
                        if(query.value.isNotEmpty()) PressTextButton({ query.value = "" }) { Text(tr("پاک کردن", "Clear")) }
                    }
                )
            }
        }
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                CollapsingPillTabs(filters, pagerState.currentPage, compact) { target ->
                    scope.launch {
                        if(kotlin.math.abs(target - pagerState.currentPage) > 1) pagerState.scrollToPage(target)
                        else pagerState.animateScrollToPage(target)
                    }
                }
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            key = { filters[it].label }
        ) { page ->
            val gridState = rememberLazyGridState()
            val threshold = with(LocalDensity.current) { 40.dp.toPx() }
            val scrollConnection = remember(page, threshold) {
                LibraryScrollConnection(threshold) { compactPages[page] = it }
            }
            val filtered = remember(catalog, targetLanguage, page, installedIds, query.value) {
                catalog.filter { song ->
                    song.s("language") == targetLanguage &&
                        (query.value.isBlank() || listOf(song.s("title"), song.s("artist")).any { it.contains(query.value.trim(), ignoreCase = true) }) &&
                        when(page) {
                            1 -> song.s("access") == "free"
                            2 -> song.s("access") == "paid"
                            3 -> song.s("id") in installedIds
                            else -> true
                        }
                }
            }
            LaunchedEffect(gridState, scrollConnection) {
                snapshotFlow { gridState.canScrollForward || gridState.canScrollBackward }
                    .distinctUntilChanged().collectLatest { scrollable ->
                        if(!scrollable) scrollConnection.reset()
                    }
            }
            LaunchedEffect(query.value, targetLanguage, gridState) { gridState.scrollToItem(0) }
            val reduced = LocalReducedMotion.current
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier.fillMaxSize().nestedScroll(scrollConnection),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp + LocalOverlayBottomInset.current),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(filtered, key = { it.s("id") }, contentType = { "song" }) { song ->
                    SongTile(song, Modifier.fillMaxWidth().animateItem(
                        fadeInSpec = tween(if(reduced) 0 else 180),
                        placementSpec = tween(if(reduced) 0 else 220),
                        fadeOutSpec = tween(if(reduced) 0 else 140)
                    )) { openSong(song.s("id")) }
                }
                if(filtered.isEmpty()) item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    Panel { Text(tr("آهنگی پیدا نشد؛ جست‌وجو یا فیلترها را تغییر بده.", "No songs found. Try another search or filter.")) }
                }
                item(key = "credits", span = { GridItemSpan(maxLineSpan) }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { DeveloperCredit() }
                }
            }
        }
    }
}

@Composable private fun SongTile(song: JsonObject, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.itemEntrance().clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box {
            CoverImage(if(song.s("distributionRef") == "gulum-ii") "pending-gulum-ii" else song.s("id"), song.s("title"), Modifier.fillMaxWidth().aspectRatio(1f))
        }
        SongTitle(song.s("title"), premium = song.s("access") == "paid")
        Text(song.s("artist"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${when(song.s("language")) { "tr" -> "🇹🇷"; "es" -> "🇪🇸"; "ko" -> "🇰🇷"; else -> "🇬🇧" }}  ${song.s("level")}  ·  ${song.s("duration")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}
