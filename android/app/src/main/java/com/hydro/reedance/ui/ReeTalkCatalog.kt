package com.hydro.reedance.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.JsonParser
import com.hydro.reedance.data.*
import com.hydro.reedance.domain.PremiumEntitlement
import com.hydro.reedance.install.InstalledReeTalk
import com.hydro.reedance.install.ReeTalkInstaller
import com.hydro.reedance.install.ReeTalkPackSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class ReeTalkTopic(val id: String, val fa: String, val en: String,
    val coverUrl: String, val premium: Boolean, val scenarios: List<ReeTalkScenario>)
internal data class ReeTalkScenario(val id: String, val fa: String, val en: String, val level: String,
    val coverUrl: String, val pack: ReeTalkPackSpec?, val premium: Boolean)

internal suspend fun loadReeTalkTopics(context: Context, language: String): List<ReeTalkTopic> = withContext(Dispatchers.IO) {
    val catalog = context.assets.open("reetalk_catalog.json").bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
    if(catalog.getAsJsonArray("languages").none { it.asString == language }) return@withContext emptyList()
    val metadata = catalog.obj("scenarioMetadata")
    catalog.a("topics").map { topic ->
        ReeTalkTopic(topic.s("id"), topic.s("titleFa"), topic.s("titleEn"), topic.s("coverUrl"), topic.s("access") == "paid",
            topic.a("scenarios").map { scenario ->
                val id = "$language-${topic.s("code")}-${scenario.s("code")}"
                val entry = metadata.obj(id)
                ReeTalkScenario(id, scenario.s("titleFa"), entry.s("titleEn").ifBlank { scenario.s("titleFa") },
                    entry.s("level"), entry.s("coverUrl"), entry.s("downloadUrl").takeIf { it.isNotBlank() }?.let { url ->
                        val version = entry.n("contentVersionCode")
                        val bytes = entry.n("downloadBytes")
                        val sha = entry.s("downloadSha256")
                        if(version in 1..Int.MAX_VALUE.toLong() && bytes > 0 && sha.matches(Regex("[a-f0-9]{64}")))
                            ReeTalkPackSpec(id, topic.s("id"), language, url, bytes, sha, version.toInt()) else null
                    }, entry.s("access") == "paid")
            })
    }
}

@Composable internal fun ReeTalkCatalogScreen(topics: List<ReeTalkTopic>, initialCategory: Int, exit: () -> Unit,
    openPremium: () -> Unit, pauseSong: () -> Unit, showDemoEntry: Boolean) {
    var categoryIndex by rememberSaveable { mutableIntStateOf(initialCategory) }
    var scenarioIndex by rememberSaveable { mutableIntStateOf(-1) }
    var showDemo by rememberSaveable { mutableStateOf(false) }
    val subscribed by PremiumEntitlement.active.collectAsStateWithLifecycle()
    val category = topics.getOrNull(categoryIndex)
    val scenario = category?.scenarios?.getOrNull(scenarioIndex)
    val lockedCategory = category?.premium == true && !subscribed
    val lockedScenario = scenario?.premium == true && !subscribed
    if(lockedCategory || lockedScenario) {
        LaunchedEffect(lockedCategory, lockedScenario) {
            scenarioIndex = -1
            if(lockedCategory) categoryIndex = -1
            openPremium()
        }
        return
    }
    if(showDemo) {
        DialoguesScreen(pauseSong, -1, { showDemo = false }, openPremium)
        return
    }
    fun back() {
        when {
            scenarioIndex >= 0 -> scenarioIndex = -1
            categoryIndex >= 0 -> categoryIndex = -1
            else -> exit()
        }
    }
    BackHandler { back() }
    val context = LocalContext.current.applicationContext
    val installer = remember(context) { ReeTalkInstaller(context) }
    val pack = scenario?.pack
    var installed by remember(pack) { mutableStateOf<InstalledReeTalk?>(null) }
    var checking by remember(pack) { mutableStateOf(pack != null) }
    LaunchedEffect(pack) {
        installed = if(pack == null) null else {
            try {
                installer.installed(pack)
            } catch(cancelled: CancellationException) {
                throw cancelled
            } catch(ignored: Exception) {
                null
            }
        }
        checking = false
    }
    val fa = LocalFa.current
    val demo = remember(installed, fa) { installed?.toReader(fa) }
    LaunchedEffect(demo?.id) { if(demo != null) pauseSong() }
    var paperIndex by rememberDialoguePreference("paper", 0, DialoguePaper.entries.indices)
    var dim by rememberDialogueToggle("dim", false)
    DialoguePalette(enabled = demo != null, paperIndex = paperIndex, dim = dim) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
            HeaderSurface {
                Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PressIconButton(::back) { AppIcon(UiIcons.Back, tr("بازگشت", "Back")) }
                    Text(when {
                        demo != null -> tr(demo.titleFa, demo.titleEn)
                        checking && scenario != null -> tr(scenario.fa, scenario.en)
                        scenario != null -> tr("دانلود گفت‌وگو", "Download dialogue")
                        category != null -> tr(category.fa, category.en)
                        else -> ReeTalkName
                    }, style = MaterialTheme.typography.titleLarge)
                }
            }
            if(demo != null) key(demo.id) {
                DialogueReader(demo, paperIndex, dim, onPaperChange = { paperIndex = (paperIndex + 1) % DialoguePaper.entries.size },
                    onDimChange = { dim = !dim })
            } else if(checking) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else key(category?.id, scenario?.id) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when {
                        scenario != null -> item {
                            ReeTalkDownloadDetails(scenario, category?.premium == true || scenario.premium, installer,
                                openPremium, onInstalled = { installed = it })
                        }
                        category != null -> itemsIndexed(category.scenarios, key = { _, entry -> entry.id }) { index, entry ->
                            ReeTalkCatalogCard(tr(entry.fa, entry.en), entry.coverUrl, entry.premium || category.premium,
                                entry.level.ifBlank { if(entry.pack == null) tr("به‌زودی", "Coming soon") else "" }) {
                                if((entry.premium || category.premium) && !PremiumEntitlement.active.value) openPremium() else scenarioIndex = index
                            }
                        }
                        else -> {
                            if(topics.isEmpty()) item { Text(tr("هنوز موضوعی برای این زبان منتشر نشده است.", "No topics have been published for this language yet.")) }
                            itemsIndexed(topics, key = { _, topic -> topic.id }) { index, topic ->
                                ReeTalkCatalogCard(tr(topic.fa, topic.en), topic.coverUrl, topic.premium,
                                    tr("${topic.scenarios.size} سناریو", "${topic.scenarios.size} scenarios")) {
                                    if(topic.premium && !PremiumEntitlement.active.value) openPremium() else categoryIndex = index
                                }
                            }
                            if(showDemoEntry) item(key = "local-demo") {
                                PressTextButton({ showDemo = true }, Modifier.fillMaxWidth()) { Text(tr("دموهای محلی", "Local demos")) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun ReeTalkCatalogCard(title: String, coverUrl: String, premium: Boolean, detail: String, open: () -> Unit) {
    Surface(onClick = open, modifier = Modifier.fillMaxWidth(), shape = GroupedCardShape) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ReeTalkCatalogCover(coverUrl, Modifier.size(58.dp).clip(InsetControlShape))
            Column(Modifier.weight(1f)) {
                SongTitle(title, premium)
                if(detail.isNotEmpty()) Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable private fun ReeTalkDownloadDetails(scenario: ReeTalkScenario, premium: Boolean, installer: ReeTalkInstaller,
    openPremium: () -> Unit, onInstalled: (InstalledReeTalk) -> Unit) {
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    var phase by remember { mutableStateOf("") }
    var fraction by remember { mutableStateOf<Float?>(null) }
    var failed by remember { mutableStateOf(false) }
    val busy = job != null
    val coverMorph by animateFloatAsState(if(busy) 1f else 0f, tween(if(LocalReducedMotion.current) 0 else 420),
        label = "reetalk-download-cover")
    val clipShape = androidx.compose.foundation.shape.RoundedCornerShape((20f + 72f * coverMorph).dp)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(228.dp), contentAlignment = Alignment.Center) {
            if(busy) CircularWaveProgress(fraction, Modifier.fillMaxSize())
            ReeTalkCatalogCover(scenario.coverUrl, Modifier.size(184.dp).clip(clipShape))
        }
        SongTitle(tr(scenario.fa, scenario.en), premium, MaterialTheme.typography.headlineMedium)
        if(scenario.level.isNotBlank()) Text(scenario.level, style = MaterialTheme.typography.labelLarge)
        if(busy) {
            val status = when(phase) {
                "decrypt" -> tr("در حال آماده‌سازی گفت‌وگو", "Preparing dialogue")
                "validate" -> tr("در حال بررسی پکیج", "Checking package")
                else -> tr("در حال دانلود گفت‌وگو", "Downloading dialogue")
            }
            Text(fraction?.let { "${(it * 100f).roundToInt()}% · $status" } ?: status,
                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            PressOutlinedButton({ job?.cancel() }, Modifier.fillMaxWidth()) { Text(tr("لغو دانلود", "Cancel download")) }
        } else if(scenario.pack != null) {
            val spec = scenario.pack
            Action(if(failed) tr("تلاش دوباره", "Try again") else tr("دانلود گفت‌وگو", "Download dialogue"), Modifier.fillMaxWidth()) {
                if(premium && !PremiumEntitlement.active.value) openPremium() else if(job == null) {
                    failed = false
                    fraction = null
                    phase = "download"
                    job = scope.launch {
                        try {
                            onInstalled(installer.install(spec, allowed = { !premium || PremiumEntitlement.active.value }) { step, bytes, total ->
                                phase = step
                                fraction = if(step == "download" && total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else null
                            })
                        } catch(cancelled: CancellationException) {
                            throw cancelled
                        } catch(ignored: Exception) {
                            failed = true
                        } finally {
                            job = null
                        }
                    }
                }
            }
            if(failed) Text(tr("دانلود یا بررسی پکیج ناموفق بود. اتصال اینترنت و نسخهٔ برنامه را بررسی کنید.",
                "Could not download or verify the package. Check your connection and app version."), color = MaterialTheme.colorScheme.error)
        } else Text(tr("پکیج دانلود هنوز منتشر نشده است.", "The downloadable package has not been published yet."))
    }
}

@Composable internal fun ReeTalkCatalogCover(url: String, modifier: Modifier, fallback: Int? = null) {
    val image by produceState<android.graphics.Bitmap?>(null, url) {
        value = if(url.isBlank()) null else remoteCover(url)
    }
    Surface(modifier, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Box(contentAlignment = Alignment.Center) {
            val bitmap = image
            if(bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            else if(fallback != null) DialogueCover(fallback, Modifier.matchParentSize())
            else AppIcon(UiIcons.Phrases, null, Modifier.size(24.dp))
        }
    }
}
