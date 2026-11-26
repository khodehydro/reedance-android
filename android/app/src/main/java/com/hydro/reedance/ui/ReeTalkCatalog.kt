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
    val coverUrl: String, val pack: ReeTalkPackSpec?, val premium: Boolean, val downloadListed: Boolean,
    val language: String, val stats: ReeTalkLearningStats)

internal data class ReeTalkLearningStats(val words: Long, val grammar: Long, val phrases: Long, val turns: Long, val questions: Long)

internal suspend fun loadReeTalkTopics(context: Context, language: String): List<ReeTalkTopic> = withContext(Dispatchers.IO) {
    val catalog = context.assets.open("reetalk_catalog.json").bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
    if(catalog.getAsJsonArray("languages").none { it.asString == language }) return@withContext emptyList()
    val metadata = catalog.obj("scenarioMetadata")
    catalog.a("topics").map { topic ->
        val topicEntry = catalog.obj("topicMetadata").obj("$language-${topic.s("code")}")
        ReeTalkTopic(topic.s("id"), topic.s("titleFa"), topic.s("titleEn"),
            topicEntry.s("coverUrl").ifBlank { topic.s("coverUrl") }, topic.s("access") == "paid",
            topic.a("scenarios").map { scenario ->
                val id = "$language-${topic.s("code")}-${scenario.s("code")}"
                val entry = metadata.obj(id)
                val stats = entry.obj("learningStats")
                val titleFa = entry.s("titleFa").ifBlank { scenario.s("titleFa") }
                // A URL alone is not a verified pack. Keep the old pinned pilot available until
                // the CDN object has its own verified size, hash and content version.
                ReeTalkScenario(id, titleFa, entry.s("titleEn").ifBlank { titleFa },
                    entry.s("level"), entry.s("coverUrl"), entry.s("pinnedDownloadUrl").ifBlank { entry.s("downloadUrl") }.takeIf { it.isNotBlank() }?.let { url ->
                        val version = entry.n("contentVersionCode")
                        val bytes = entry.n("downloadBytes")
                        val sha = entry.s("downloadSha256")
                        if(version in 1..Int.MAX_VALUE.toLong() && bytes > 0 && sha.matches(Regex("[a-f0-9]{64}")))
                            ReeTalkPackSpec(id, topic.s("id"), language, url, bytes, sha, version.toInt(),
                                studioQa = entry.s("studioQa") == "true") else null
                    }, entry.s("access") == "paid", entry.s("downloadUrl").isNotBlank(), language,
                    ReeTalkLearningStats(stats.n("words", -1), stats.n("grammar", -1), stats.n("phrases", -1),
                        stats.n("turns", -1), stats.n("questions", -1)))
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
    val demo = remember(installed) { installed?.toReader() }
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
                            ReeTalkDownloadDetails(scenario, category?.let { tr(it.fa, it.en) }.orEmpty(), category?.premium == true || scenario.premium, installer,
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
                                ReeTalkTopicCard(tr(topic.fa, topic.en), topic.coverUrl, topic.premium,
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

@Composable private fun ReeTalkTopicCard(title: String, coverUrl: String, premium: Boolean, detail: String, open: () -> Unit) {
    Surface(onClick = open, modifier = Modifier.fillMaxWidth(), shape = GroupedCardShape) {
        Column {
            ReeTalkCatalogCover(coverUrl, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SongTitle(title, premium)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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

@Composable private fun ReeTalkDownloadDetails(scenario: ReeTalkScenario, topic: String, premium: Boolean, installer: ReeTalkInstaller,
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
    val stats = scenario.stats
    val countItems = listOf(
        Triple(UiIcons.WordsFilled, tr("لغت‌ها", "Words"), stats.words),
        Triple(UiIcons.NotesFilled, tr("نکات گرامری", "Grammar notes"), stats.grammar),
        Triple(UiIcons.PhrasesFilled, tr("عبارت‌ها و اصطلاحات", "Expressions and phrases"), stats.phrases),
        Triple(UiIcons.Lines, tr("خطوط گفت‌وگو", "Dialogue lines"), stats.turns),
        Triple(UiIcons.QuizFilled, tr("سؤال‌ها", "Questions"), stats.questions)
    )
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.size(228.dp), contentAlignment = Alignment.Center) {
            if(busy) CircularWaveProgress(fraction, Modifier.fillMaxSize())
            ReeTalkCatalogCover(scenario.coverUrl, Modifier.size(184.dp).clip(clipShape))
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SongTitle(tr(scenario.fa, scenario.en), premium, MaterialTheme.typography.headlineMedium)
            if(topic.isNotBlank()) Text(topic, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if(busy) {
                val status = when(phase) {
                    "decrypt" -> tr("در حال آماده‌سازی گفت‌وگو", "Preparing dialogue")
                    "validate" -> tr("در حال بررسی پکیج", "Checking package")
                    else -> tr("در حال دانلود گفت‌وگو", "Downloading dialogue")
                }
                Text(fraction?.let { "${(it * 100f).roundToInt()}% · $status" } ?: status,
                    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DownloadMetaChip(tr("زبان", "Language"), languageDisplayName(scenario.language), Modifier.weight(1f))
            if(scenario.level.isNotBlank()) DownloadMetaChip(tr("سطح", "Level"), scenario.level, Modifier.weight(1f))
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("محتوای آموزشی", "Included learning content"), Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(Modifier.fillMaxWidth(), shape = GroupedCardShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column {
                    countItems.forEachIndexed { index, (icon, label, count) ->
                        if(index > 0) DownloadContentDivider()
                        DownloadContentRow(icon, label) {
                            Text(if(count >= 0) count.toString() else "—", style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if(scenario.pack != null && !busy) Text(
                tr("برای دانلود بهتر، VPN را خاموش کنید.", "For a smoother download, turn off your VPN."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(busy) {
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
        } else if(scenario.downloadListed) {
            Text(tr("لینک پکیج ثبت شده، اما دانلود هنوز تأیید نشده است.",
                "The package link is listed, but the download has not been verified yet."))
        } else Text(tr("پکیج دانلود هنوز منتشر نشده است.", "The downloadable package has not been published yet."))
    }
}

@Composable internal fun ReeTalkCatalogCover(url: String, modifier: Modifier, fallback: Int? = null) {
    val context = LocalContext.current.applicationContext
    val image by produceState<Pair<String, android.graphics.Bitmap>?>(null, url) {
        value = remoteCover(context, url)?.let { url to it }
    }
    Surface(modifier, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Box(contentAlignment = Alignment.Center) {
            val bitmap = image?.takeIf { it.first == url }?.second
            if(bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            else if(fallback != null) DialogueCover(fallback, Modifier.matchParentSize())
            else AppIcon(UiIcons.Phrases, null, Modifier.size(24.dp))
        }
    }
}
