package com.hydro.reedance.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydro.reedance.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest

@Composable fun ReviewScreen(model: AppModel, quiz: () -> Unit) {
    val reviews by model.reviews.collectAsStateWithLifecycle()
    val pageActive = LocalPageActive.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(pageActive) {
        if(pageActive) while(true) { delay(1000); now = System.currentTimeMillis() }
    }
    val due = reviews.filter { it.dueAt <= now }
    val card = due.firstOrNull()
    val reviewLesson by produceState<com.google.gson.JsonObject?>(null, card?.songId) {
        value = null
        value = card?.let { try { model.lessonForReview(it.songId) } catch(cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch(_: Exception) { null } }
    }
    val word = reviewLesson?.takeIf { it.s("songId") == card?.songId }?.a("vocabulary")?.find { it.s("lexemeId") == card?.lexemeId }
    var reveal by remember(card?.songId, card?.lexemeId) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current
    val fa = LearningLanguage.persian
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp + LocalOverlayBottomInset.current), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Heading(tr("جعبهٔ لایتنر", "Words that stay"), tr("یادگیری، یک مرور کوتاه در هر روز.", "A little practice. A lasting memory."))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { (1..5).forEach { stage -> Column {
            Text("$stage", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text("${reviews.count { it.stage == stage }}", style = MaterialTheme.typography.bodySmall)
        } } }
        Text(tr("${due.size} واژه آمادهٔ مرور", "${due.size} words ready to review"))
        if(card != null && word != null) {
            Deck("${card.songId}:${card.lexemeId}") {
                Panel(tone = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(tr("کارت واژه · خانهٔ ${card.stage}", "WORD CARD · STAGE ${card.stage}"), style = MaterialTheme.typography.labelMedium)
                    Foreign(word.s("lemma"), large = true)
                    WrittenPronunciation(word)
                    RevealButton(reveal, { reveal = !reveal }, meaning = true)
                    if(reveal) {
                        PersianText(word.tr("translation", fa), style = MaterialTheme.typography.titleLarge)
                        Foreign(word.obj("example").s("text")); PersianText(word.obj("example").tr("translation", fa))
                        ReviewAnswerButtons(enabled = !busy) { remembered ->
                            if(!busy) {
                                busy = true
                                scope.launch {
                                    model.review(card, remembered)
                                    if(!reduced) delay(220)
                                    busy = false
                                }
                            }
                        }
                    }
                }
            }
            PressOutlinedButton({ card?.let { scope.launch { model.openInstalled(it.songId); quiz() } } }, Modifier.fillMaxWidth()) { Text(tr("مرور با سؤال", "Practice with questions")) }
        } else Panel {
            Text(if(reviews.isEmpty()) tr("در تب لغت‌های آهنگ، واژه‌ای را نشان کن تا به جعبه‌ات بیاید.", "Save a word in the song lesson to start your collection.") else if(card != null) tr("برای نمایش کارت‌ها، بستهٔ آهنگ را دوباره نصب کن. پیشرفتت حفظ شده است.", "Reinstall the song to view these cards. Your progress is preserved.") else tr("عالی بود! واژه‌ها در موعد بعدی برمی‌گردند.", "All caught up! Your words will return when they are due."))
        }
        Text(tr("پاسخ درست: خانهٔ بعد با فاصلهٔ ۲، ۴، ۸ و ۱۶ روز. فراموشی: خانهٔ اول، یک دقیقهٔ دیگر.", "Remembered: next stage in 2, 4, 8 or 16 days. Forgotten: stage one, due in one minute."), style = MaterialTheme.typography.bodySmall)
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable fun LibraryScreen(model: AppModel, song: (String) -> Unit) {
    val bookmarks by model.bookmarks.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val catalog by model.catalog.collectAsStateWithLifecycle()
    val types = listOf("song", "vocabulary", "grammar", "phrases")
    val pagerState = rememberPagerState(pageCount = { types.size })
    val scope = rememberCoroutineScope()
    val catalogById = remember(catalog) { catalog.associateBy { it.s("id") } }
    val compactPages = remember(types.size) {
        mutableStateListOf<Boolean>().apply { repeat(types.size) { add(false) } }
    }
    val selectedPage = pagerState.currentPage.coerceAtMost(types.lastIndex)
    var query by rememberSaveable(settings.targetLanguage) { mutableStateOf("") }
    var sourceFilter by rememberSaveable(settings.targetLanguage) { mutableStateOf("") }
    var order by rememberSaveable { mutableStateOf(LibraryOrder.NEWEST) }
    val sources = remember(bookmarks, catalogById, selectedPage) {
        bookmarks.asSequence().filter { matchesLibraryTab(it, types[selectedPage]) }.map { it.songId }.distinct()
            .map { id -> id to catalogById[id]?.s("title").orEmpty().ifBlank { id } }
            .sortedBy { it.second.lowercase() }.toList()
    }
    val compact = compactPages[selectedPage]

    Column(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = !compact,
            enter = fadeIn(tween(170)) + expandVertically(tween(190)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(190))
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Heading(tr("کتابخانهٔ تو", "Your little collection"), tr("آهنگ‌ها و نکته‌هایی که ذخیره کرده‌ای.", "Songs and learning items you’ve saved."))
                val selectedCount = bookmarks.count { matchesLibraryTab(it, types[selectedPage]) }
                Text(
                    tr("$selectedCount مورد نشان‌شده", "$selectedCount saved items"),
                    style = MaterialTheme.typography.labelMedium
                )
                LibraryControls(query, { query = it }, sources, sourceFilter, { sourceFilter = it }, order, { order = it }, visible = !compact)
            }
        }
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                CollapsingPillTabs(listOf(
                    PillOption(tr("آهنگ‌ها", "Songs"), UiIcons.Lyrics, filledIcon = UiIcons.LyricsFilled),
                    PillOption(tr("لغت‌ها", "Words"), UiIcons.Words, filledIcon = UiIcons.WordsFilled),
                    PillOption(tr("نکته‌ها", "Notes"), UiIcons.Notes, filledIcon = UiIcons.NotesFilled),
                    PillOption(tr("جمله‌ها و اصطلاحات", "Sentences & expressions"), UiIcons.Phrases, filledIcon = UiIcons.PhrasesFilled)
                ), selectedPage, compact) { target ->
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
            key = { types[it] }
        ) { page ->
            val requestKey = LibraryRequest(settings.targetLanguage, page, query.trim(), sourceFilter, order)
            val threshold = with(LocalDensity.current) { 40.dp.toPx() }
            val scrollConnection = remember(settings.targetLanguage, page, threshold) {
                LibraryScrollConnection(threshold) { compactPages[page] = it }
            }
            val preparedEntries by produceState<PreparedLibraryEntries?>(null, bookmarks, requestKey, catalogById) {
                if(requestKey.query.isNotBlank()) delay(280)
                val rows = prepareLibraryRows(bookmarks, types[page], requestKey, catalogById, model::libraryItem)
                val initialWindow = libraryInitialWindow(rows, model::libraryItem)
                value = PreparedLibraryEntries(requestKey, rows, initialWindow)
            }
            val prepared = preparedEntries?.takeIf { it.key == requestKey }
            val preparingEntries = prepared == null
            val entries = prepared?.entries.orEmpty()

            androidx.compose.runtime.key(settings.targetLanguage, page) {
                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                LaunchedEffect(requestKey) { listState.scrollToItem(0) }
                LaunchedEffect(listState, scrollConnection) {
                    snapshotFlow { listState.canScrollForward || listState.canScrollBackward }
                        .distinctUntilChanged().collectLatest { scrollable ->
                            if(!scrollable) scrollConnection.reset()
                        }
                }
                LaunchedEffect(listState, entries) {
                    snapshotFlow {
                        val visible = listState.layoutInfo.visibleItemsInfo
                        val start = visible.firstOrNull()?.index ?: 0
                        val end = visible.lastOrNull()?.index ?: 0
                        start to end
                    }.distinctUntilChanged().collectLatest { (start, end) ->
                        delay(100) // Coalesce range changes during a fling instead of chasing each row.
                        // A small rolling window, never the complete saved library.
                        val from = (start - 2).coerceIn(0, entries.size)
                        val until = (end + 9).coerceIn(from, minOf(entries.size, from + 24))
                        entries.subList(from, until)
                            .filter { it.itemType != "song" }.distinctBy { it.songId }.take(4).forEach { mark ->
                                try { if(model.cachedLibraryItem(mark) == null) model.libraryItem(mark) }
                                catch(cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch(_: Exception) { /* The visible card supplies the unavailable state. */ }
                            }
                    }
                }
                CompositionLocalProvider(LocalItemEntranceEnabled provides false) {
                    androidx.compose.foundation.lazy.LazyColumn(
                        Modifier.fillMaxSize().nestedScroll(scrollConnection),
                        state = listState,
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp + LocalOverlayBottomInset.current),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if(preparingEntries) item(key = "preparing") {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        else if(entries.isEmpty()) item(key = "empty") {
                            Panel {
                                if(query.isNotBlank() || sourceFilter.isNotEmpty()) {
                                    Text(tr("نتیجه‌ای پیدا نشد.", "No matching saved items."))
                                    PressTextButton({ query = ""; sourceFilter = "" }) { Text(tr("پاک‌کردن جستجو و فیلتر", "Clear search and filters")) }
                                } else Text(tr("در این بخش هنوز موردی نشان نکرده‌ای.", "No saved items in this tab yet."))
                            }
                        }
                        items(entries, key = { "${it.songId}:${it.itemType}:${it.itemId}" }, contentType = { it.itemType }) { mark ->
                            LibraryBookmarkCard(model, mark, catalogById[mark.songId], song,
                                preloaded = prepared?.initialWindow?.containsKey(mark) == true,
                                preloadedContent = prepared?.initialWindow?.get(mark))
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun LibraryControls(query: String, onQuery: (String) -> Unit, sources: List<Pair<String, String>>,
    source: String, onSource: (String) -> Unit, order: LibraryOrder, onOrder: (LibraryOrder) -> Unit, visible: Boolean) {
    var filtersOpen by remember { mutableStateOf(false) }
    var sortingOpen by remember { mutableStateOf(false) }
    val allSongs = tr("همهٔ آهنگ‌ها", "All songs")
    val sourceTitle = sources.firstOrNull { it.first == source }?.second ?: source
    val sortLabels = listOf(tr("جدیدترین", "Newest first"), tr("قدیمی‌ترین", "Oldest first"), tr("نام آهنگ", "Song title"))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CatalogSearchField(query, onQuery, tr("جست‌وجو در این تب", "Search this tab"), visible = visible)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                FilterChip(shape = CircleShape, selected = source.isNotEmpty(), onClick = { filtersOpen = true }, label = {
                    Text(if(source.isEmpty()) tr("فیلتر آهنگ", "Filter by song") else sourceTitle,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }, leadingIcon = { AppIcon(UiIcons.All, null, Modifier.size(16.dp)) })
                DropdownMenu(expanded = filtersOpen, onDismissRequest = { filtersOpen = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                    (listOf("" to allSongs) + sources + (if(source.isNotEmpty() && sources.none { it.first == source }) listOf(source to sourceTitle) else emptyList())).forEach { (id, title) ->
                        DropdownMenuItem(text = { Text(title) }, onClick = { onSource(id); filtersOpen = false },
                            trailingIcon = if(id == source) ({ AppIcon(UiIcons.Check, null) }) else null)
                    }
                }
            }
            Box {
                FilterChip(shape = CircleShape, selected = order != LibraryOrder.NEWEST, onClick = { sortingOpen = true }, label = {
                    Text(sortLabels[order.ordinal], maxLines = 1)
                }, trailingIcon = { Text("▾") })
                DropdownMenu(expanded = sortingOpen, onDismissRequest = { sortingOpen = false }) {
                    LibraryOrder.entries.forEach { option ->
                        DropdownMenuItem(text = { Text(sortLabels[option.ordinal]) }, onClick = { onOrder(option); sortingOpen = false },
                            trailingIcon = if(option == order) ({ AppIcon(UiIcons.Check, null) }) else null)
                    }
                }
            }
        }
    }
}
