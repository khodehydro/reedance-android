package com.hydro.reedance.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.layout.layout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydro.reedance.data.*

@Composable fun ReedanceApp(model: AppModel, destination: (@Composable () -> Unit)? = null) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val ready by model.ready.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val context = LocalContext.current
    val fa = settings.language != "en"
    CompositionLocalProvider(LocalFa provides fa, LocalReducedMotion provides false, LocalLayoutDirection provides if(fa) LayoutDirection.Rtl else LayoutDirection.Ltr,
        LocalDensity provides Density(density.density, density.fontScale * listOf(.9f, 1f, 1.15f)[settings.textSize.coerceIn(0,2)])) {
        ReedanceTheme(settings) {
            val lightBars = settings.permissionsSetup && MaterialTheme.colorScheme.background.luminance() > .5f
            SideEffect {
                (context as? android.app.Activity)?.window?.let { window ->
                    @Suppress("DEPRECATION")
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                    @Suppress("DEPRECATION")
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT
                    if(android.os.Build.VERSION.SDK_INT >= 29) {
                        window.isStatusBarContrastEnforced = false
                        window.isNavigationBarContrastEnforced = false
                    }
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = lightBars
                        isAppearanceLightNavigationBars = lightBars
                    }
                }
            }
            HeartOverlay { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when {
                    !ready -> Box(Modifier.fillMaxSize()) // Covered by the single system splash.
                    !settings.permissionsSetup -> Setup(model)
                    destination != null -> destination()
                    else -> MainShell(model)
                }
            } }
        }
    }
}
@Composable internal fun MainShell(model: AppModel) {
    var page by rememberSaveable { mutableStateOf("home") }
    var storyReturnPage by rememberSaveable { mutableStateOf("song-playback") }
    var songReturnPage by rememberSaveable { mutableStateOf("home") }
    var songId by rememberSaveable { mutableStateOf("") }
    var songTab by rememberSaveable { mutableIntStateOf(0) }
    var premiumReturnPage by rememberSaveable { mutableStateOf("profile") }
    var profileReturnPage by rememberSaveable { mutableStateOf("home") }
    var studyReturnPage by rememberSaveable { mutableStateOf(StudyHubRoute) }
    val currentPage by rememberUpdatedState(page)
    val snackbar = remember { SnackbarHostState() }
    val message by model.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val settings by model.settings.collectAsStateWithLifecycle()
    var exploreArtistKey by rememberSaveable(settings.targetLanguage) { mutableStateOf<String?>(null) }
    val lightSystemBars = page != "premium" && MaterialTheme.colorScheme.background.luminance() > .5f
    SideEffect {
        if(page != "song-story") (context as? android.app.Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = lightSystemBars
                isAppearanceLightNavigationBars = lightSystemBars
            }
        }
    }
    val installedIds by model.installedIds.collectAsStateWithLifecycle()
    val explorePager = rememberPagerState { 2 }
    val shellScope = rememberCoroutineScope()
    val pageStates = rememberSaveableStateHolder()
    val mainPageTypes = remember { setOf("home", "explore", StudyHubRoute, "profile", "quiz", "premium") + studySectionRoutes }
    val songPageTypes = remember { setOf("song-download", "song-playback", "song-story") }
    val mountedPages = remember {
        mutableStateListOf<String>().apply { if(page in mainPageTypes) add(page) }
    }
    val mountedSongPages = remember {
        mutableStateListOf<Pair<String, String>>().apply {
            if(page in songPageTypes && songId.isNotBlank()) add(page to songId)
            if((page == "quiz" || page == "song-story" && storyReturnPage == "song-playback") && songId.isNotBlank()) add("song-playback" to songId)
        }
    }
    LaunchedEffect(message) { if(message.isNotEmpty()) { snackbar.showSnackbar(message); model.message.value = "" } }
    fun navigate(to: String) {
        if(to in studySectionRoutes && page in setOf(StudyHubRoute, "home", "profile", "quiz")) studyReturnPage = page
        if(to == "song-story" && page != to) storyReturnPage = page
        if(to == "quiz" || to == "song-story") model.pause()
        if(to in mainPageTypes && to !in mountedPages) mountedPages += to
        if(to in songPageTypes) {
            val route = to to songId
            mountedSongPages.removeAll { it.first == to && it.second != songId }
            if(route !in mountedSongPages) mountedSongPages += route
        }
        page = to
    }
    LaunchedEffect(model) {
        model.premiumRequired.collect {
            premiumReturnPage = if(currentPage == "song-playback") "song-playback" else "profile"
            navigate("premium")
        }
    }
    fun openSong(id: String) {
        if(page !in songPageTypes) songReturnPage = page
        songId = id
        navigate(if(id in installedIds) "song-playback" else "song-download")
    }
    val navigateLatest = rememberUpdatedState<(String) -> Unit>({ destination -> navigate(destination) })
    val navigateAction = remember { { destination: String -> navigateLatest.value(destination) } }
    val swipeNavigateLatest = rememberUpdatedState<(String) -> Unit>({ destination ->
        val exploreTarget = explorePageForMainSwipe(page, destination, explorePager.settledPage)
        if(page == "explore" && exploreTarget != null) {
            shellScope.launch { explorePager.animateScrollToPage(exploreTarget) }
        } else {
            if(exploreTarget != null) explorePager.requestScrollToPage(exploreTarget)
            navigate(destination)
        }
    })
    val swipeNavigate = remember { { destination: String -> swipeNavigateLatest.value(destination) } }
    val openSongLatest = rememberUpdatedState<(String) -> Unit>({ id -> openSong(id) })
    val openSongAction = remember { { id: String -> openSongLatest.value(id) } }
    val openStoryLatest = rememberUpdatedState<(String) -> Unit>({ id ->
        songId = id
        navigate("song-story")
    })
    val openStoryAction = remember { { id: String -> openStoryLatest.value(id) } }
    val setSongTabLatest = rememberUpdatedState<(Int) -> Unit>({ songTab = it })
    val setSongTabAction = remember { { tab: Int -> setSongTabLatest.value(tab) } }
    val setPremiumReturnLatest = rememberUpdatedState<(String) -> Unit>({ premiumReturnPage = it })
    val setPremiumReturnAction = remember { { destination: String -> setPremiumReturnLatest.value(destination) } }
    val starterAction = remember(context) { { context.startActivity(android.content.Intent(context, com.hydro.reedance.StarterActivity::class.java)) } }
    val songPage = page in songPageTypes
    val artistDetail = page == "explore" && exploreArtistKey != null
    val studySection = page in studySectionRoutes
    val independent = artistDetail || songPage || studySection || page in listOf("quiz", "premium", "profile")
    fun goBack() {
        if(artistDetail) exploreArtistKey = null
        else navigate(when(page) {
            "song-story" -> storyReturnPage
            "song-download", "song-playback" -> songReturnPage
            "quiz" -> "song-playback"
            "premium" -> premiumReturnPage
            "profile" -> profileReturnPage
            in studySectionRoutes -> studyReturnPage
            else -> "home"
        })
    }
    BackHandler(page != "home") { goBack() }
    SearchDismissHost {
     Box(Modifier.fillMaxSize().mainTabSwipe(page, page in mainTabRoutes && !independent,
         LocalLayoutDirection.current, swipeNavigate)) {
      val scaffoldInsets = if(artistDetail || page == "song-story" || page == "premium") WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets
      Scaffold(Modifier.fillMaxSize(), contentWindowInsets = scaffoldInsets, containerColor = Color.Transparent, snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        if(!artistDetail && !songPage && page != "premium" && page != DialoguesRoute) Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onSurface) { Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if(page == "quiz" || page == "profile" || studySection) {
                PressIconButton(::goBack) { AppIcon(UiIcons.Back, tr("بازگشت", "Back")) }
                if(page == "quiz") Text(tr("آزمون", "Practice"), style = MaterialTheme.typography.titleLarge)
                if(studySection) Text(studySectionTitle(page), style = MaterialTheme.typography.titleLarge)
            } else {
                MainHeader(settings.targetLanguage, model::targetLanguage, {
                    profileReturnPage = page
                    navigate("profile")
                })
            }
        }
        }
    }, bottomBar = {
        if(!independent) {
            val started by model.started.collectAsStateWithLifecycle()
            val playing by model.playing.collectAsStateWithLifecycle()
            val activePlayback by model.activePlayback.collectAsStateWithLifecycle()
            Column(
                Modifier.fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if(started) activePlayback?.let { active ->
                    MiniPlayer(active, playing, model::toggleActivePlayback, { openSongAction(active.songId) })
                }
                FloatingNavigation(page, compact = false, onNavigate = navigateAction)
            }
        }
    }) { padding ->
        // Keep visited tabs composed for return-state, but do not measure or place hidden pages.
        // The bottom bar floats over the scroll viewport; its height is trailing content space.
        Box(
            Modifier
                .padding(
                    start = padding.calculateStartPadding(LocalLayoutDirection.current),
                    end = padding.calculateEndPadding(LocalLayoutDirection.current),
                    top = padding.calculateTopPadding(),
                    bottom = 0.dp
                )
                .clipToBounds()
        ) {
            CompositionLocalProvider(LocalOverlayBottomInset provides padding.calculateBottomPadding()) {
                mountedPages.forEach { mountedPage ->
                    val active = page == mountedPage
                    key(mountedPage) {
                        pageStates.SaveableStateProvider(mountedPage) {
                            CompositionLocalProvider(LocalPageActive provides active) {
                                Box(Modifier.visiblePageLayout(active)) {
                                    if(mountedPage == "premium") {
                                        if(active) PremiumScreen { navigateAction(premiumReturnPage) }
                                    } else {
                                        ShellPageContent(
                                            page = mountedPage,
                                            model = model,
                                            songId = if(mountedPage == "song-playback" || mountedPage == "song-download") songId else "",
                                            songTab = if(mountedPage == "song-playback") songTab else 0,
                                            setSongTab = setSongTabAction,
                                            navigate = navigateAction,
                                            openSong = openSongAction,
                                            openStory = openStoryAction,
                                            starter = starterAction,
                                            setPremiumReturnPage = setPremiumReturnAction,
                                            songReturnPage = songReturnPage,
                                            explorePager = explorePager,
                                            exploreArtistKey = exploreArtistKey,
                                            setExploreArtist = { exploreArtistKey = it }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                mountedSongPages.forEach { (route, routeSongId) ->
                    val active = page == route && songId == routeSongId
                    key(route, routeSongId) {
                        pageStates.SaveableStateProvider(route) {
                            CompositionLocalProvider(LocalPageActive provides active) {
                                Box(Modifier.visiblePageLayout(active)) {
                                    if(route == "song-story") {
                                        if(active) SongStoryScreen(model, routeSongId) { navigateAction(storyReturnPage) }
                                    } else {
                                        ShellPageContent(
                                            page = route,
                                            model = model,
                                            songId = routeSongId,
                                            songTab = songTab,
                                            setSongTab = setSongTabAction,
                                            navigate = navigateAction,
                                            openSong = openSongAction,
                                            openStory = openStoryAction,
                                            starter = starterAction,
                                            setPremiumReturnPage = setPremiumReturnAction,
                                            songReturnPage = songReturnPage,
                                            explorePager = explorePager,
                                            exploreArtistKey = exploreArtistKey,
                                            setExploreArtist = { exploreArtistKey = it }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
      }
    }
    }
}

// Retain visited compositions and remember state, without sending Scaffold constraint
// changes through every hidden lazy list or placing it outside the viewport.
internal fun Modifier.visiblePageLayout(active: Boolean): Modifier = if(active) fillMaxSize() else
    clearAndSetSemantics {}.layout { _, constraints ->
        layout(constraints.minWidth, constraints.minHeight) {}
    }

@Composable private fun ShellPageContent(
    page: String,
    model: AppModel,
    songId: String,
    songTab: Int,
    setSongTab: (Int) -> Unit,
    navigate: (String) -> Unit,
    openSong: (String) -> Unit,
    openStory: (String) -> Unit,
    starter: () -> Unit,
    setPremiumReturnPage: (String) -> Unit,
    songReturnPage: String,
    explorePager: PagerState,
    exploreArtistKey: String?,
    setExploreArtist: (String?) -> Unit
) {
    when(page) {
        "home", "explore" -> CatalogScreen(model, page == "home", openSong, starter, { navigate("library") }, exploreArtistKey, setExploreArtist, explorePager) { navigate("explore") }
        "song-download" -> SongDownloadScreen(model, songId, { navigate(songReturnPage) }) { navigate("song-playback") }
        "song-playback" -> SongPlaybackScreen(
            model, songId, songTab, setSongTab, { navigate(songReturnPage) }, { navigate("song-download") },
            { navigate("quiz") }, { navigate("song-story") }
        ) { setPremiumReturnPage("song-playback"); navigate("premium") }
        StudyHubRoute -> StudyHubScreen(navigate)
        StoriesRoute -> StoriesScreen(model, openStory)
        DialoguesRoute -> if(LocalPageActive.current) DialoguesScreen(model::pause) { navigate(StudyHubRoute) }
        "game" -> GameScreen(model) { setPremiumReturnPage("game"); navigate("premium") }
        "quiz" -> QuizScreen(model, { navigate("song-playback") }, { navigate("review") })
        "review" -> ReviewScreen(model)
        "gym" -> GymScreen(model) { navigate("explore") }
        "library" -> LibraryScreen(model, openSong)
        "profile" -> ProfileScreen(model, { navigate("library") }) { setPremiumReturnPage("profile"); navigate("premium") }
    }
}
