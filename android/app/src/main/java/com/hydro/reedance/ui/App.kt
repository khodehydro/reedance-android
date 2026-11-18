package com.hydro.reedance.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.delay
import kotlin.math.abs
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydro.reedance.R
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
            val lightBars = MaterialTheme.colorScheme.background.luminance() > .5f
            SideEffect {
                (context as? android.app.Activity)?.window?.let { window ->
                    @Suppress("DEPRECATION")
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT
                    if(android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = lightBars
                        isAppearanceLightNavigationBars = lightBars
                    }
                }
            }
            HeartOverlay { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when {
                    !ready -> Box(Modifier.fillMaxSize()) // Covered by the single system splash.
                    settings.language.isEmpty() -> PageEntrance("setup") { Setup(model) }
                    !settings.permissionsSetup -> PageEntrance("permissions") { PermissionSetup(model) }
                    destination != null -> destination()
                    else -> MainShell(model)
                }
            } }
        }
    }
}
@Composable private fun SetupBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.background(Brush.linearGradient(listOf(
        Color(0xFFEAF5FF), Color(0xFF9BC8FF), Color(0xFFF8FCFF), Color(0xFFB6D6FF), Color(0xFFEFF7FF)
    )))) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(Brush.radialGradient(
                listOf(Color.White.copy(alpha = .62f), Color.Transparent),
                center = Offset(size.width * .18f, size.height * .2f), radius = size.width * .82f
            ))
            drawRect(Brush.radialGradient(
                listOf(Color(0xFF6FA7F7).copy(alpha = .24f), Color.Transparent),
                center = Offset(size.width * .92f, size.height * .86f), radius = size.width * .92f
            ))
        }
    }
}

@Composable private fun Setup(model: AppModel) {
    var language by rememberSaveable { mutableStateOf("en") }
    CompositionLocalProvider(LocalFa provides (language == "fa"), LocalLayoutDirection provides if(language == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Box(Modifier.fillMaxSize()) {
            SetupBackdrop(Modifier.matchParentSize())
            Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(30.dp))
            Image(painterResource(R.drawable.reedance_header), "Reedance", Modifier.size(120.dp))
            Text(tr("به ری‌دنس خوش آمدی", "Welcome to Reedance"), style = MaterialTheme.typography.headlineMedium)
            Text(tr("با موسیقی، زبان یاد بگیر.", "Find your rhythm. Learn a language."))
            Panel(tone = Color.White.copy(alpha = .58f)) {
                Text(tr("زبان برنامه را انتخاب کن", "Choose your app language"), style = MaterialTheme.typography.titleLarge)
                listOf("en" to "English", "fa" to "فارسی").forEach { (code, label) ->
                    Row(Modifier.fillMaxWidth().clickable { language = code }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PressRadioButton(language == code, { language = code }); Text(label)
                    }
                }
                Text(tr("زبان آموزش را بعداً جداگانه انتخاب می‌کنی.", "Your learning language is chosen separately."), style = MaterialTheme.typography.bodySmall)
            }
            Action(tr("شروع کنیم", "Let's get started"), Modifier.fillMaxWidth()) { model.language(language) }
            Text("HYDRO team", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
@Composable internal fun MainShell(model: AppModel) {
    var page by rememberSaveable { mutableStateOf("home") }
    var songId by rememberSaveable { mutableStateOf("") }
    var songTab by rememberSaveable { mutableIntStateOf(0) }
    var reviewQuiz by rememberSaveable { mutableStateOf(false) }
    var premiumReturnPage by rememberSaveable { mutableStateOf("profile") }
    var profileReturnPage by rememberSaveable { mutableStateOf("home") }
    val currentPage by rememberUpdatedState(page)
    val snackbar = remember { SnackbarHostState() }
    val message by model.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val settings by model.settings.collectAsStateWithLifecycle()
    val lightSystemBars = page == "premium" || MaterialTheme.colorScheme.background.luminance() > .5f
    SideEffect {
        (context as? android.app.Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = lightSystemBars
                isAppearanceLightNavigationBars = lightSystemBars
            }
        }
    }
    val started by model.started.collectAsStateWithLifecycle()
    val playing by model.playing.collectAsStateWithLifecycle()
    val activePlayback by model.activePlayback.collectAsStateWithLifecycle()
    val installedIds by model.installedIds.collectAsStateWithLifecycle()
    val pageStates = rememberSaveableStateHolder()
    val preloadablePages = remember { setOf("home", "explore", "game", "review", "library", "profile", "quiz", "premium") }
    val songPageTypes = remember { setOf("song-download", "song-playback") }
    val mountedPages = remember { mutableStateListOf("home") }
    val mountedSongPages = remember { mutableStateListOf<Pair<String, String>>() }
    var pendingRoute by remember { mutableStateOf<Pair<String, String>?>(null) }
    var compactNavigation by remember(page) { mutableStateOf(false) }
    val navigationWidth by animateDpAsState(
        targetValue = navigationMenuWidth(compactNavigation),
        animationSpec = tween(if(LocalReducedMotion.current) 0 else 150, easing = FastOutSlowInEasing),
        label = "navigation-menu-width"
    )
    val threshold = with(LocalDensity.current) { 36.dp.toPx() }
    val scrollConnection = remember(page, threshold) {
        object : NestedScrollConnection {
            private var direction = 0
            private var distance = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if(source != NestedScrollSource.UserInput) return Offset.Zero
                val dy = consumed.y
                if(dy != 0f) {
                    val next = if(dy < 0) -1 else 1
                    if(next != direction) { direction = next; distance = 0f }
                    distance += abs(dy)
                    if(distance >= threshold) { compactNavigation = next < 0; distance = 0f }
                } else if(available.y > 0) compactNavigation = false
                return Offset.Zero // Observe scrolling; never steal it from the page.
            }
        }
    }
    LaunchedEffect(message) { if(message.isNotEmpty()) { snackbar.showSnackbar(message); model.message.value = "" } }
    LaunchedEffect(Unit) {
        // Warm the common navigation destinations gradually after the first screen settles.
        delay(1_000)
        listOf("explore", "game", "review", "library", "profile", "quiz", "premium").forEach { destination ->
            if(destination !in mountedPages) mountedPages += destination
            delay(700)
        }
    }
    fun navigate(to: String) {
        if(to == "quiz") model.pause()
        if(to in preloadablePages) {
            if(to !in mountedPages) {
                pendingRoute = to to ""
                mountedPages += to
                return
            }
            pendingRoute = null
            page = to
            return
        }
        if(to in songPageTypes) {
            val route = to to songId
            mountedSongPages.removeAll { it.first == to && it.second != songId }
            if(route !in mountedSongPages) {
                pendingRoute = route
                mountedSongPages += route
                return
            }
        }
        pendingRoute = null
        page = to
    }
    LaunchedEffect(model) {
        model.premiumRequired.collect {
            premiumReturnPage = if(currentPage == "song-playback") "song-playback" else "profile"
            navigate("premium")
        }
    }
    fun openSong(id: String) {
        songId = id
        navigate(if(id in installedIds) "song-playback" else "song-download")
    }
    val navigateLatest = rememberUpdatedState<(String) -> Unit>({ destination -> navigate(destination) })
    val navigateAction = remember { { destination: String -> navigateLatest.value(destination) } }
    val openSongLatest = rememberUpdatedState<(String) -> Unit>({ id -> openSong(id) })
    val openSongAction = remember { { id: String -> openSongLatest.value(id) } }
    val setSongTabLatest = rememberUpdatedState<(Int) -> Unit>({ songTab = it })
    val setSongTabAction = remember { { tab: Int -> setSongTabLatest.value(tab) } }
    val setReviewQuizLatest = rememberUpdatedState<(Boolean) -> Unit>({ reviewQuiz = it })
    val setReviewQuizAction = remember { { review: Boolean -> setReviewQuizLatest.value(review) } }
    val setPremiumReturnLatest = rememberUpdatedState<(String) -> Unit>({ premiumReturnPage = it })
    val setPremiumReturnAction = remember { { destination: String -> setPremiumReturnLatest.value(destination) } }
    val starterAction = remember(context) { { context.startActivity(android.content.Intent(context, com.hydro.reedance.StarterActivity::class.java)) } }
    val songPage = page == "song-download" || page == "song-playback"
    val independent = songPage || page in listOf("quiz", "premium", "profile")
    BackHandler(page != "home") {
        navigate(when(page) {
            "quiz" -> if(reviewQuiz) "review" else "song-playback"
            "premium" -> premiumReturnPage
            "profile" -> profileReturnPage
            else -> "home"
        })
    }
    SearchDismissHost {
     Box(Modifier.fillMaxSize()) {
      if(page == "premium") PremiumBackdrop(Modifier.matchParentSize())
      Scaffold(Modifier.fillMaxSize(), containerColor = Color.Transparent, snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        if(!songPage) Surface(color = if(page == "premium") Color.Transparent else MaterialTheme.colorScheme.background, contentColor = if(page == "premium") Color(0xFF493300) else MaterialTheme.colorScheme.onSurface) { Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if(page in listOf("quiz", "premium", "profile")) {
                PressIconButton({
                    navigate(when(page) {
                        "quiz" -> if(reviewQuiz) "review" else "song-playback"
                        "premium" -> premiumReturnPage
                        else -> profileReturnPage
                    })
                }) { AppIcon(UiIcons.Back, tr("بازگشت", "Back")) }
                Text(when(page) {
                    "quiz" -> tr("آزمون", "Practice")
                    "premium" -> tr("پریمیوم", "Premium")
                    else -> tr("پروفایل", "Profile")
                }, style = MaterialTheme.typography.titleLarge)
            } else {
                MainHeader(settings.targetLanguage, model::targetLanguage, {
                    profileReturnPage = page
                    navigate("profile")
                })
            }
        }
        }
    }, bottomBar = {
        if(!independent) Column(
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .navigationBarsPadding()
                .padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if(started) activePlayback?.let { active ->
                MiniPlayer(active, playing, model::toggleActivePlayback, { openSong(active.songId) }, compact = compactNavigation, menuWidth = navigationWidth)
            }
            FloatingNavigation(page, compactNavigation, ::navigate, navigationWidth)
        }
    }) { padding ->
        // Keep common tabs composed off-screen so their content and scroll state are ready on selection.
        // The bottom bar floats over the scroll viewport; its height is trailing content space.
        Box(
            Modifier
                .padding(
                    start = padding.calculateStartPadding(LocalLayoutDirection.current),
                    end = padding.calculateEndPadding(LocalLayoutDirection.current),
                    top = padding.calculateTopPadding(),
                    bottom = if(independent) padding.calculateBottomPadding() else 0.dp
                )
                .nestedScroll(scrollConnection)
                .clipToBounds()
        ) {
            CompositionLocalProvider(LocalOverlayBottomInset provides if(independent) 0.dp else padding.calculateBottomPadding()) {
                mountedPages.forEach { mountedPage ->
                        val active = page == mountedPage
                        key(mountedPage) {
                            LaunchedEffect(mountedPage, pendingRoute) {
                                if(pendingRoute == (mountedPage to "")) {
                                    withFrameNanos { }
                                    if(pendingRoute == (mountedPage to "")) {
                                        page = mountedPage
                                        pendingRoute = null
                                    }
                                }
                            }
                            pageStates.SaveableStateProvider(mountedPage) {
                                CompositionLocalProvider(
                                    LocalPageActive provides active,
                                    LocalItemEntranceEnabled provides active
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .then(if(active) Modifier else Modifier.offset(x = 10_000.dp).clearAndSetSemantics {})
                                    ) {
                                        PageEntrance(mountedPage) {
                                            ShellPageContent(
                                                page = mountedPage,
                                                model = model,
                                                songId = if(mountedPage == "song-playback" || mountedPage == "song-download") songId else "",
                                                songTab = if(mountedPage == "song-playback") songTab else 0,
                                                setSongTab = setSongTabAction,
                                                navigate = navigateAction,
                                                openSong = openSongAction,
                                                starter = starterAction,
                                                reviewQuiz = if(mountedPage == "quiz") reviewQuiz else false,
                                                setReviewQuiz = setReviewQuizAction,
                                                setPremiumReturnPage = setPremiumReturnAction
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
                        LaunchedEffect(route, routeSongId, pendingRoute) {
                            if(pendingRoute == (route to routeSongId)) {
                                withFrameNanos { }
                                if(pendingRoute == (route to routeSongId)) {
                                    page = route
                                    songId = routeSongId
                                    pendingRoute = null
                                }
                            }
                        }
                        pageStates.SaveableStateProvider(route) {
                            CompositionLocalProvider(
                                LocalPageActive provides active,
                                LocalItemEntranceEnabled provides active
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .then(if(active) Modifier else Modifier.offset(x = 10_000.dp).clearAndSetSemantics {})
                                ) {
                                    PageEntrance("$route:$routeSongId") {
                                        ShellPageContent(
                                            page = route,
                                            model = model,
                                            songId = routeSongId,
                                            songTab = songTab,
                                            setSongTab = setSongTabAction,
                                            navigate = navigateAction,
                                            openSong = openSongAction,
                                            starter = starterAction,
                                            reviewQuiz = reviewQuiz,
                                            setReviewQuiz = setReviewQuizAction,
                                            setPremiumReturnPage = setPremiumReturnAction
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

@Composable private fun ShellPageContent(
    page: String,
    model: AppModel,
    songId: String,
    songTab: Int,
    setSongTab: (Int) -> Unit,
    navigate: (String) -> Unit,
    openSong: (String) -> Unit,
    starter: () -> Unit,
    reviewQuiz: Boolean,
    setReviewQuiz: (Boolean) -> Unit,
    setPremiumReturnPage: (String) -> Unit
) {
    when(page) {
        "home", "explore" -> CatalogScreen(model, page == "home", openSong, starter) { navigate("explore") }
        "song-download" -> SongDownloadScreen(model, songId, { navigate("home") }) { navigate("song-playback") }
        "song-playback" -> SongPlaybackScreen(
            model, songId, songTab, setSongTab, { navigate("home") }, { navigate("song-download") },
            { setReviewQuiz(false); navigate("quiz") }
        ) { setPremiumReturnPage("song-playback"); navigate("premium") }
        "premium" -> PremiumScreen()
        "game" -> GameScreen(model) { setPremiumReturnPage("game"); navigate("premium") }
        "quiz" -> QuizScreen(model, reviewQuiz, { navigate(if(reviewQuiz) "review" else "song-playback") }, { navigate("review") })
        "review" -> ReviewScreen(model) { setReviewQuiz(true); navigate("quiz") }
        "library" -> LibraryScreen(model, openSong)
        "profile" -> ProfileScreen(model, { navigate("library") }) { setPremiumReturnPage("profile"); navigate("premium") }
    }
}
