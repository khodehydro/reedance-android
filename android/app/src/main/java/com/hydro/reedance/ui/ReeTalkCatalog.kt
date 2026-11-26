package com.hydro.reedance.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class ReeTalkTopic(val id: String, val fa: String, val en: String,
    val coverUrl: String, val premium: Boolean, val scenarios: List<ReeTalkScenario>)
internal data class ReeTalkScenario(val id: String, val fa: String, val en: String, val level: String,
    val coverUrl: String, val downloadUrl: String, val premium: Boolean)

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
                    entry.s("level"), entry.s("coverUrl"), entry.s("downloadUrl"), entry.s("access") == "paid")
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
    DialoguePalette(enabled = false, paperIndex = 0, dim = false) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
            HeaderSurface {
                Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PressIconButton(::back) { AppIcon(UiIcons.Back, tr("بازگشت", "Back")) }
                    Text(when {
                        scenario != null -> tr("دانلود گفت‌وگو", "Download dialogue")
                        category != null -> tr(category.fa, category.en)
                        else -> ReeTalkName
                    }, style = MaterialTheme.typography.titleLarge)
                }
            }
            key(category?.id, scenario?.id) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when {
                        scenario != null -> item {
                            ReeTalkDownloadDetails(scenario, category?.premium == true || scenario.premium, openPremium)
                        }
                        category != null -> itemsIndexed(category.scenarios, key = { _, entry -> entry.id }) { index, entry ->
                            ReeTalkCatalogCard(tr(entry.fa, entry.en), entry.coverUrl, entry.premium || category.premium,
                                entry.level.ifBlank { if(entry.downloadUrl.isBlank()) tr("به‌زودی", "Coming soon") else "" }) {
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

@Composable private fun ReeTalkDownloadDetails(scenario: ReeTalkScenario, premium: Boolean, openPremium: () -> Unit) {
    val context = LocalContext.current
    val failed = tr("لینک دانلود باز نشد.", "Could not open the download link.")
    Panel {
        ReeTalkCatalogCover(scenario.coverUrl, Modifier.fillMaxWidth().aspectRatio(5f / 3f).clip(InsetControlShape))
        SongTitle(tr(scenario.fa, scenario.en), premium, MaterialTheme.typography.headlineSmall)
        if(scenario.level.isNotBlank()) Text(scenario.level, style = MaterialTheme.typography.labelLarge)
        if(scenario.downloadUrl.isNotBlank()) {
            Text(tr("لینک، فایل را خارج از برنامه باز می‌کند. نصب بستهٔ ری‌تاک هنوز در اپ فعال نیست.",
                "This link opens the file outside the app. In-app installation is not available yet."))
            Action(tr("دانلود پکیج", "Download package"), Modifier.fillMaxWidth()) {
                if(premium && !PremiumEntitlement.active.value) openPremium()
                else try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(scenario.downloadUrl))) }
                catch(_: ActivityNotFoundException) { Toast.makeText(context, failed, Toast.LENGTH_SHORT).show() }
            }
        } else Text(tr("لینک دانلود هنوز اضافه نشده است.", "The download link has not been published yet."))
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
