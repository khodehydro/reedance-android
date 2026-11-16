package com.hydro.reedance.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.*
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
@Composable private fun Setup(model: AppModel) {
    var language by rememberSaveable { mutableStateOf("en") }
    CompositionLocalProvider(LocalFa provides (language == "fa"), LocalLayoutDirection provides if(language == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Box(Modifier.fillMaxSize()) {
            PremiumBackdrop(Modifier.matchParentSize())
            Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(30.dp))
            Image(painterResource(R.drawable.reedance_header), "Reedance", Modifier.size(120.dp))
            Text(tr("به ری‌دنس خوش آمدی", "Welcome to Reedance"), style = MaterialTheme.typography.headlineMedium)
            Text(tr("با موسیقی، زبان یاد بگیر.", "Find your rhythm. Learn a language."))
            Panel {
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
    val pageStates = rememberSaveableStateHolder()
    var compactNavigation by remember(page) { mutableStateOf(false) }
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
    fun navigate(to: String) { if(to == "quiz") model.pause(); page = to }
    val independent = page in listOf("song", "quiz", "premium")
    BackHandler(page != "home") { page = when(page) { "quiz" -> if(reviewQuiz) "review" else "song"; "premium" -> premiumReturnPage; else -> "home" } }
    SearchDismissHost {
     Box(Modifier.fillMaxSize()) {
      if(page == "premium") PremiumBackdrop(Modifier.matchParentSize())
      Scaffold(Modifier.fillMaxSize(), containerColor = Color.Transparent, snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        if(page != "song") Surface(color = if(page == "premium") Color.Transparent else MaterialTheme.colorScheme.background, contentColor = if(page == "premium") Color(0xFF493300) else MaterialTheme.colorScheme.onSurface) { Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if(page == "quiz" || page == "premium") {
                PressIconButton({ page = if(page == "quiz") { if(reviewQuiz) "review" else "song" } else premiumReturnPage }) { AppIcon(UiIcons.Back, tr("بازگشت", "Back")) }
                Text(if(page == "quiz") tr("آزمون", "Practice") else tr("پریمیوم", "Premium"), style = MaterialTheme.typography.titleLarge)
            } else {
                MainHeader(settings.targetLanguage, model::targetLanguage, { navigate("profile") })
            }
        }
        }
    }, bottomBar = {
        if(!independent) Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if(started) activePlayback?.let { active ->
                MiniPlayer(active, playing, model::toggleActivePlayback, { songId = active.songId; navigate("song") }, compact = compactNavigation)
            }
            FloatingNavigation(page, compactNavigation, ::navigate)
        }
    }) { padding ->
        // Isolate destination identity so Home effects/state are not reused in another page.
        // The bottom bar floats over the scroll viewport. Its measured height is
        // trailing *content* space, not viewport padding, so rows pass underneath.
        Box(Modifier.padding(start = padding.calculateStartPadding(LocalLayoutDirection.current), end = padding.calculateEndPadding(LocalLayoutDirection.current), top = padding.calculateTopPadding(), bottom = if(independent) padding.calculateBottomPadding() else 0.dp).nestedScroll(scrollConnection)) {
         CompositionLocalProvider(LocalOverlayBottomInset provides if(independent) 0.dp else padding.calculateBottomPadding()) {
         key(page) {
          pageStates.SaveableStateProvider(page) {
           PageEntrance(if(page == "song") "$page:$songId" else page) {
            when(page) {
                "home", "explore" -> CatalogScreen(model, page == "home", { songId = it; navigate("song") }, { context.startActivity(android.content.Intent(context, com.hydro.reedance.StarterActivity::class.java)) }, { navigate("explore") })
                "song" -> SongScreen(model, songId, songTab, { songTab = it }, { navigate("home") }, { reviewQuiz = false; navigate("quiz") }) { premiumReturnPage = "song"; navigate("premium") }
                "premium" -> PremiumScreen()
                "quiz" -> QuizScreen(model, reviewQuiz, { navigate(if(reviewQuiz) "review" else "song") }, { navigate("review") })
                "review" -> ReviewScreen(model) { reviewQuiz = true; navigate("quiz") }
                "library" -> LibraryScreen(model) { songId = it; navigate("song") }
                "profile" -> ProfileScreen(model, { navigate("library") }) { premiumReturnPage = "profile"; navigate("premium") }
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
