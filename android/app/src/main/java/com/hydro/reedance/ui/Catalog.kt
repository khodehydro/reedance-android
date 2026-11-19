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
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydro.reedance.R
import com.hydro.reedance.data.*
import com.google.gson.JsonObject
import com.hydro.reedance.domain.latestSongs
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun cover(id: String): Int? = when(id) {
    "pending-gulum-ii", "gulum-2" -> R.drawable.gulum_ii
    else -> null
}
// Approx. 6MB maximum, shared across Home/Explore; no image decode on the UI thread.
private val coverCache = object : LruCache<Int, Bitmap>(6 * 1024 * 1024) {
    override fun sizeOf(key: Int, value: Bitmap) = value.allocationByteCount
}
@Composable fun CoverImage(id: String, description: String, modifier: Modifier = Modifier, shapeProgress: Float = 0f) {
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
        val baseRadius = 20.dp.toPx()
        val circleRadius = minOf(size.width, size.height) / 2f
        val radius = baseRadius + (circleRadius - baseRadius) * shapeProgress.coerceIn(0f, 1f)
        val outline = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(radius))) }
        onDrawWithContent { clipPath(outline) { this@onDrawWithContent.drawContent() } }
    }.background(MaterialTheme.colorScheme.secondaryContainer)) {
        bitmap?.let { Image(it.asImageBitmap(), description, Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
    }
}
@Composable fun CatalogScreen(model: AppModel, home: Boolean, openSong: (String) -> Unit, starter: () -> Unit, openLibrary: () -> Unit,
    artistKey: String?, onArtist: (String?) -> Unit, explore: () -> Unit) {
    if(home) CatalogFeed(model, true, openSong, starter, openLibrary, explore)
    else ExploreCatalogScreen(model, openSong, artistKey, onArtist)
}

@Composable private fun CatalogFeed(model: AppModel, home: Boolean, openSong: (String) -> Unit, starter: () -> Unit, openLibrary: () -> Unit, explore: () -> Unit) {
    val catalog by model.catalog.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val targetLanguage = settings.targetLanguage
    var query by rememberSaveable { mutableStateOf("") }
    var access by rememberSaveable { mutableIntStateOf(0) }
    val grid = rememberLazyGridState()
    val lesson by model.lesson.collectAsStateWithLifecycle()
    val installedIds by model.installedIds.collectAsStateWithLifecycle()
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
                CatalogSearchField(query, { query = it }, tr("جست‌وجوی آهنگ یا خواننده", "Search songs or artists"))
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
        if(home) item(key = "saved-preview", span = { GridItemSpan(maxLineSpan) }) {
            HomeSavedPreview(model, catalog, openSong, openLibrary)
        }
        item(key = "credits", span = { GridItemSpan(maxLineSpan) }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DeveloperCredit()
            }
        }
    }
}
internal data class ExploreArtist(val key: String, val name: String, val songs: List<JsonObject>)

internal fun exploreArtists(songs: List<JsonObject>): List<ExploreArtist> = songs
    .filter { it.s("artist").isNotBlank() }
    .groupBy { it.s("artist").trim().replace(artistSpace, " ").lowercase(Locale.ROOT) }
    .map { (key, songs) -> ExploreArtist(key, songs.first().s("artist").trim(), songs) }
    .sortedBy { it.key }

private val artistSpace = Regex("\\s+")

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun ExploreCatalogScreen(model: AppModel, openSong: (String) -> Unit, artistKey: String?, onArtist: (String?) -> Unit) {
    val catalog by model.catalog.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val targetLanguage = settings.targetLanguage
    val songs = remember(catalog, targetLanguage) { catalog.filter { it.s("language") == targetLanguage } }
    val artists = remember(songs) { exploreArtists(songs) }
    var query by rememberSaveable { mutableStateOf("") }
    val selectedArtist = artists.firstOrNull { it.key == artistKey }
    val tabOptions = listOf(
        PillOption(tr("آهنگ‌ها", "Songs"), UiIcons.Lyrics, filledIcon = UiIcons.LyricsFilled),
        PillOption(tr("آرتیست‌ها", "Artists"), UiIcons.User)
    )
    val pagerState = rememberPagerState { tabOptions.size }
    val scope = rememberCoroutineScope()
    var compact by rememberSaveable(targetLanguage) { mutableStateOf(false) }
    val songGrid = rememberLazyGridState()
    val artistGrid = rememberLazyGridState()
    if(artistKey != null) {
        key(artistKey) {
            ArtistSongs(selectedArtist, { onArtist(null) }, openSong)
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = !compact,
            enter = fadeIn(tween(170)) + expandVertically(tween(190)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(190))
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Heading(tr("کشف موسیقی", "Discover music"), tr("موسیقی را بشنو، کلمه‌ها را کشف کن.", "Good music. Words that stay with you."))
                CatalogSearchField(query, { query = it },
                    if(pagerState.settledPage == 1) tr("جست‌وجوی آرتیست", "Search artists")
                    else tr("جست‌وجوی آهنگ یا خواننده", "Search songs or artists"), visible = !compact)
            }
        }
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                CollapsingPillTabs(tabOptions, pagerState.currentPage, compact, stretchExpanded = true) { target ->
                    scope.launch { pagerState.animateScrollToPage(target) }
                }
            }
        }
        val term = query.trim()
        val visibleSongs = remember(songs, term) {
            songs.filter { term.isEmpty() || it.s("title").contains(term, true) || it.s("artist").contains(term, true) }
        }
        val visibleArtists = remember(artists, term) { artists.filter { it.name.contains(term, true) } }
        LaunchedEffect(term, targetLanguage) {
            if(songGrid.firstVisibleItemIndex != 0 || songGrid.firstVisibleItemScrollOffset != 0) songGrid.requestScrollToItem(0)
            if(artistGrid.firstVisibleItemIndex != 0 || artistGrid.firstVisibleItemScrollOffset != 0) artistGrid.requestScrollToItem(0)
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            val threshold = with(LocalDensity.current) { 40.dp.toPx() }
            val scrollConnection = remember(page, threshold) {
                LibraryScrollConnection(threshold) { collapsed ->
                    if(pagerState.settledPage == page && !pagerState.isScrollInProgress) compact = collapsed
                }
            }
            val gridState = if(page == 0) songGrid else artistGrid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2), state = gridState,
                modifier = Modifier.fillMaxSize().nestedScroll(scrollConnection),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp + LocalOverlayBottomInset.current),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if(page == 0) {
                    items(visibleSongs, key = { it.s("id") }, contentType = { "song" }) { song ->
                        SongTile(song, Modifier.fillMaxWidth()) { openSong(song.s("id")) }
                    }
                } else {
                    items(visibleArtists, key = { it.key }, contentType = { "artist" }) { artist ->
                        ArtistTile(artist) { onArtist(artist.key) }
                    }
                }
                if(if(page == 0) visibleSongs.isEmpty() else visibleArtists.isEmpty()) item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    Panel { Text(if(page == 0) tr("آهنگی پیدا نشد.", "No songs found.") else tr("آرتیستی پیدا نشد.", "No artists found.")) }
                }
            }
        }
    }
}

@Composable private fun ArtistTile(artist: ExploreArtist, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ArtistCover(artist, Modifier.fillMaxWidth(.86f).aspectRatio(1f))
        Text(artist.name, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium)
        Text(artistSongCount(artist.songs.size), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun ArtistCover(artist: ExploreArtist, modifier: Modifier) {
    val coverId = artist.songs.asSequence().map { songCoverId(it) }.firstOrNull { cover(it) != null }
    if(coverId != null) CoverImage(coverId, "", modifier, shapeProgress = 1f)
    else Box(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        Text(artist.name.split(artistSpace).take(2).joinToString("") { it.take(1).uppercase(Locale.ROOT) },
            color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable private fun artistSongCount(count: Int) = tr("$count آهنگ", if(count == 1) "1 song" else "$count songs")

@Composable private fun ArtistSongs(artist: ExploreArtist?, back: () -> Unit, openSong: (String) -> Unit) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
        HeaderSurface {
            Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp)) {
                if(artist != null) {
                    Column(Modifier.align(Alignment.TopCenter).padding(horizontal = 52.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ArtistCover(artist, Modifier.size(48.dp))
                        Text(artist.name, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(artistSongCount(artist.songs.size), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                PressIconButton(back, Modifier.align(Alignment.TopStart)) {
                    AppIcon(UiIcons.Back, tr("بازگشت به آرتیست‌ها", "Back to artists"))
                }
            }
        }
        if(artist == null) return@Column
        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f).navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            items(artist.songs, key = { it.s("id") }, contentType = { "song" }) { song ->
                SongTile(song, Modifier.fillMaxWidth()) { openSong(song.s("id")) }
            }
        }
    }
}

private fun songCoverId(song: JsonObject): String =
    if(song.s("distributionRef") == "gulum-ii") "pending-gulum-ii" else song.s("id")

@Composable private fun SongTile(song: JsonObject, modifier: Modifier, onClick: () -> Unit) {
    val entrance = if(LocalItemEntranceEnabled.current) modifier.itemEntrance() else modifier
    Column(entrance.clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box {
            CoverImage(songCoverId(song), song.s("title"), Modifier.fillMaxWidth().aspectRatio(1f))
        }
        SongTitle(song.s("title"), premium = song.s("access") == "paid")
        Text(song.s("artist"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${when(song.s("language")) { "tr" -> "🇹🇷"; "es" -> "🇪🇸"; "ko" -> "🇰🇷"; else -> "🇬🇧" }}  ${song.s("level")}  ·  ${song.s("duration")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}
