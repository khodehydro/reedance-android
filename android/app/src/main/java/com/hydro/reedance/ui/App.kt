package com.hydro.reedance.ui

import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

@Composable fun ReedanceApp(model: AppModel) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val ready by model.ready.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val context = LocalContext.current
    val systemReduced = AndroidSettings.Global.getFloat(context.contentResolver, AndroidSettings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val fa = settings.language != "en"
    CompositionLocalProvider(LocalFa provides fa, LocalReducedMotion provides (settings.reduceMotion || systemReduced), LocalLayoutDirection provides if(fa) LayoutDirection.Rtl else LayoutDirection.Ltr,
        LocalDensity provides Density(density.density, density.fontScale * listOf(.9f, 1f, 1.15f)[settings.textSize.coerceIn(0,2)])) {
        ReedanceTheme(settings) {
            val lightBars = MaterialTheme.colorScheme.background.luminance() > .5f
            SideEffect {
                (context as? android.app.Activity)?.window?.let { window ->
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = lightBars
                        isAppearanceLightNavigationBars = lightBars
                    }
                }
            }
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when {
                    !ready -> BrandSplash()
                    settings.language.isEmpty() -> Setup(model)
                    else -> MainShell(model)
                }
            }
        }
    }
}
@Composable private fun BrandSplash() {
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Image(painterResource(R.drawable.reedance_logo), "Reedance", Modifier.align(Alignment.Center).size(144.dp))
        Text("HYDRO team", Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp), color = Blue)
    }
}
@Composable private fun Setup(model: AppModel) {
    var language by rememberSaveable { mutableStateOf("en") }
    CompositionLocalProvider(LocalFa provides (language == "fa"), LocalLayoutDirection provides if(language == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(30.dp))
            Image(painterResource(R.drawable.reedance_logo), "Reedance", Modifier.size(110.dp))
            Text(tr("به ری‌دنس خوش آمدی", "Welcome to Reedance"), style = MaterialTheme.typography.headlineMedium)
            Text(tr("با موسیقی، زبان یاد بگیر.", "Find your rhythm. Learn a language."))
            Panel {
                Text(tr("زبان برنامه را انتخاب کن", "Choose your app language"), style = MaterialTheme.typography.titleLarge)
                listOf("en" to "English", "fa" to "فارسی").forEach { (code, label) ->
                    Row(Modifier.fillMaxWidth().clickable { language = code }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(language == code, { language = code }); Text(label)
                    }
                }
                Text(tr("زبان آموزش را بعداً جداگانه انتخاب می‌کنی.", "Your learning language is chosen separately."), style = MaterialTheme.typography.bodySmall)
            }
            Action(tr("شروع کنیم", "Let's get started"), Modifier.fillMaxWidth()) { model.language(language) }
            Text("HYDRO team", color = MaterialTheme.colorScheme.primary)
        }
    }
}
@Composable private fun MainShell(model: AppModel) {
    var page by rememberSaveable { mutableStateOf("home") }
    var songId by rememberSaveable { mutableStateOf("midnight-avenue") }
    var songTab by rememberSaveable { mutableIntStateOf(0) }
    var reviewQuiz by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val message by model.message.collectAsStateWithLifecycle()
    val started by model.started.collectAsStateWithLifecycle()
    val playing by model.playing.collectAsStateWithLifecycle()
    val reduced = LocalReducedMotion.current
    LaunchedEffect(message) { if(message.isNotEmpty()) { snackbar.showSnackbar(message); model.message.value = "" } }
    fun navigate(to: String) { if(to == "quiz") model.pause(); page = to }
    val independent = page in listOf("song", "quiz")
    BackHandler(page != "home") { page = if(page == "quiz") if(reviewQuiz) "review" else "song" else "home" }
    Scaffold(containerColor = Color.Transparent, snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        if(page != "song") Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if(page == "quiz") {
                IconButton({ page = if(reviewQuiz) "review" else "song" }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("بازگشت", "Back")) }
                Text(tr("آزمون", "Practice"), style = MaterialTheme.typography.titleLarge)
            } else {
                Image(painterResource(R.drawable.reedance_logo), "Reedance", Modifier.size(40.dp))
                Text("Reedance", Modifier.padding(start = 8.dp).weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton({ model.language(if(LocalFaValue(model)) "en" else "fa") }) { Text(tr("EN", "فا")) }
                IconButton({ navigate("profile") }) { Icon(Icons.Rounded.PersonOutline, tr("فضای من", "My space")) }
            }
        }
    }, bottomBar = {
        if(!independent) Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp)) {
            if(started) Surface(shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ songId = "midnight-avenue"; navigate("song") }, Modifier.weight(1f)) { Text("Midnight Avenue") }
                    IconButton(model::togglePlay) { Icon(if(playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, tr("پخش یا مکث", "Play or pause")) }
                }
            }
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
                Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    val nav = listOf(Triple("home", tr("خانه", "Home"), Icons.Rounded.Home), Triple("explore", tr("کشف", "Explore"), Icons.Rounded.Search), Triple("review", tr("لایتنر", "Review"), Icons.Rounded.Style), Triple("library", tr("کتابخانه", "Library"), Icons.Rounded.LibraryMusic))
                    nav.forEach { (id, title, icon) ->
                        Column(Modifier.weight(1f).clickable { navigate(id) }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(icon, title, tint = if(page == id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }
        }
    }) { padding ->
        AnimatedContent(page, Modifier.padding(padding), transitionSpec = {
            (if(reduced) EnterTransition.None else fadeIn(tween(320)) + slideInVertically(tween(320)) { 12 }) togetherWith (if(reduced) ExitTransition.None else fadeOut(tween(140)))
        }, label = "page") { current ->
            when(current) {
                "home", "explore" -> CatalogScreen(model, current == "home", { songId = it; navigate("song") }, { navigate("starter") })
                "starter" -> StarterScreen(model)
                "song" -> SongScreen(model, songId, songTab, { songTab = it }, { navigate("home") }, { reviewQuiz = false; navigate("quiz") })
                "quiz" -> QuizScreen(model, reviewQuiz, { navigate(if(reviewQuiz) "review" else "song") }, { navigate("review") })
                "review" -> ReviewScreen(model) { reviewQuiz = true; navigate("quiz") }
                "library" -> LibraryScreen(model) { songId = "midnight-avenue"; navigate("song") }
                "profile" -> ProfileScreen(model) { navigate("library") }
            }
        }
    }
}
private fun LocalFaValue(model: AppModel) = model.settings.value.language != "en"
