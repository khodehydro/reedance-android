package com.hydro.reedance.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.JsonObject
import com.hydro.reedance.data.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

@Composable fun SongDownloadScreen(model: AppModel, songId: String, back: () -> Unit, onDownloaded: () -> Unit) {
    val catalog by model.catalog.collectAsStateWithLifecycle()
    val installedIds by model.installedIds.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val song = catalog.firstOrNull { it.s("id") == songId } ?: JsonObject()
    LaunchedEffect(songId, installedIds) { if(songId in installedIds) onDownloaded() }

    Column(Modifier.fillMaxSize()) {
        HeaderSurface {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                PressIconButton(back) { AppIcon(UiIcons.Back, tr("بازگشت", "Back")) }
                Text(song.s("title").ifEmpty { tr("دانلود آهنگ", "Download song") }, Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(48.dp))
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if(song.size() == 0) item(key = "download-loading") {
                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else item(key = "download-details") {
                val bundled = song.get("bundledPack")?.asBoolean == true
                val bundledReady = song.get("bundledPackReady")?.asBoolean == true
                SongDownloadDetails(
                    model = model,
                    song = song,
                    songId = songId,
                    busy = busy,
                    available = if(bundled) bundledReady else song.get("remotePackReady")?.asBoolean == true,
                    bundled = bundled,
                    bundledReady = bundledReady,
                    onDownload = { if(bundled) model.installBundledPackage(songId) else model.installPackage(songId) },
                    onCancel = model::cancelInstall
                )
            }
        }
    }
}

@Composable private fun SongDownloadDetails(
    model: AppModel,
    song: JsonObject,
    songId: String,
    busy: Boolean,
    available: Boolean,
    bundled: Boolean,
    bundledReady: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit
) {
    val transfer by model.installTransfer.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val reduced = LocalReducedMotion.current
    val coverMorph by animateFloatAsState(
        targetValue = if(busy) 1f else 0f,
        animationSpec = tween(if(reduced) 0 else 420),
        label = "download-cover-morph"
    )
    val transferFraction = transfer?.takeIf { it.phase in listOf("download", "copy") }?.fraction
    val downloadProgress = transfer?.takeIf { it.phase == "download" }?.fraction
    val downloadSize = song.n("packageSizeBytes")
    val countItems = listOf(
        Triple(UiIcons.WordsFilled, tr("لغت", "Words"), "vocabularyCount"),
        Triple(UiIcons.NotesFilled, tr("نکته", "Notes"), "grammarCount"),
        Triple(UiIcons.PhrasesFilled, tr("اصطلاح", "Expressions"), "idiomCount"),
        Triple(UiIcons.LinesFilled, tr("جمله", "Sentences"), "sentenceCount")
    )
    val hasMissingCounts = countItems.any { !song.has(it.third) }
    val phaseText = when(transfer?.phase) {
        "manifest" -> tr("در حال آماده‌سازی دانلود", "Preparing download")
        "download" -> tr("در حال دانلود آهنگ", "Downloading song")
        "copy" -> tr("در حال آماده‌سازی آهنگ", "Preparing song")
        "decrypt" -> tr("در حال آماده‌سازی آهنگ", "Preparing song")
        "validate" -> tr("در حال بررسی فایل آهنگ", "Checking song file")
        "installed" -> tr("دانلود کامل شد", "Download complete")
        else -> tr("در حال آماده‌سازی", "Preparing")
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(tr("اطلاعات آهنگ", "SONG INFO"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Box(Modifier.size(228.dp), contentAlignment = Alignment.Center) {
            if(busy) CircularWaveProgress(transferFraction, Modifier.fillMaxSize())
            CoverImage(
                if(song.s("distributionRef") == "gulum-ii") "pending-gulum-ii" else songId,
                song.s("title"),
                Modifier.size(184.dp),
                shapeProgress = coverMorph
            )
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(song.s("artist"), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if(busy) {
                Text(
                    downloadProgress?.let { "${(it * 100f).roundToInt()}% · $phaseText" } ?: phaseText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if(song.s("language").isNotBlank()) DownloadMetaChip(tr("زبان", "Language"), languageDisplayName(song.s("language")), Modifier.weight(1f))
            if(song.s("level").isNotBlank()) DownloadMetaChip(tr("سطح", "Level"), song.s("level"), Modifier.weight(1f))
            if(song.s("duration").isNotBlank()) DownloadMetaChip(tr("زمان", "Duration"), song.s("duration"), Modifier.weight(1f))
        }

        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .78f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(UiIcons.Check, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(tr("حجم دانلود", "Download size"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(
                    if(downloadSize > 0) android.text.format.Formatter.formatFileSize(context, downloadSize)
                    else tr("هنوز مشخص نیست", "Not available yet"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("محتوای این آهنگ", "Included learning content"), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                countItems.forEach { (icon, label, key) ->
                    val count = if(song.has(key)) song.n(key).toString() else "—"
                    Surface(
                        Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = .72f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                    ) {
                        Column(Modifier.padding(vertical = 12.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AppIcon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(count, style = MaterialTheme.typography.titleMedium)
                            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if(hasMissingCounts) Text(
                tr("تعداد دقیق محتوا تا زمانی که آمار معتبر منتشر نشود نمایش داده نمی‌شود.", "Exact counts will appear when verified content data is available."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if(available && busy) {
            PressOutlinedButton(onCancel, Modifier.fillMaxWidth()) { Text(tr("لغو دانلود", "Cancel download")) }
        } else if(available) {
            Action(tr("دانلود آهنگ", "Download song"), Modifier.fillMaxWidth()) { onDownload() }
        } else {
            Action(tr("این آهنگ هنوز قابل دانلود نیست", "This song isn’t available to download yet"), Modifier.fillMaxWidth(), enabled = false) {}
            Text(
                if(bundled && !bundledReady) tr("نسخهٔ همراه برنامه با سازوکار رمزنگاری قدیمی ساخته شده و باید از Windows Studio دوباره صادر شود.", "The bundled copy uses the retired encryption setup and must be re-exported from Windows Studio.")
                else tr("برای این پیش‌نمایش هنوز فایل صوتی و محتوای آموزشی منتشر نشده است.", "Audio and learning content have not been published for this preview yet."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if(bundled && bundledReady) Text(
            tr("این آهنگ همراه برنامه ارائه شده است. امضای مستقل ناشر برای فایل همراه برنامه تأیید نشده است.", "This song is included with the app. The bundled file does not have an independently verified publisher signature."),
            Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if(song.get("directTestPack")?.asBoolean == true) Text(
            tr("نسخهٔ آزمایشی از GitHub دانلود می‌شود؛ اندازه و SHA-256 پیش از رمزگشایی بررسی می‌شوند. این مسیر فقط برای debug است و امضای ناشر ندارد.", "Debug test: downloaded from GitHub; size and SHA-256 are checked before decryption. This path is not publisher-signed and is disabled in release builds."),
            Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable private fun DownloadMetaChip(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .68f)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable private fun CircularWaveProgress(progress: Float?, modifier: Modifier = Modifier) {
    val motion = rememberInfiniteTransition(label = "download-ring-wave")
    val phase by motion.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "download-ring-phase"
    )
    val active = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f)
    Canvas(modifier) {
        val stroke = 4.dp.toPx()
        val radius = minOf(size.width, size.height) / 2f - stroke
        drawCircle(track, radius, style = Stroke(stroke))
        val sweep = if(progress == null) 94f else 360f * progress.coerceIn(0f, 1f)
        val start = if(progress == null) -90f + phase * 180f / PI.toFloat() else -90f
        val steps = (sweep / 2f).roundToInt().coerceAtLeast(2)
        val waveAmplitude = 1.25.dp.toPx()
        val path = Path()
        repeat(steps + 1) { index ->
            val fraction = index.toFloat() / steps
            val angle = (start + sweep * fraction) * PI.toFloat() / 180f
            val radiusOffset = sin((fraction * 12f * PI.toFloat() - phase).toDouble()).toFloat() * waveAmplitude
            val r = radius + radiusOffset
            val point = Offset(
                size.width / 2f + cos(angle.toDouble()).toFloat() * r,
                size.height / 2f + sin(angle.toDouble()).toFloat() * r
            )
            if(index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        if(progress == null || progress > 0f) drawPath(path, active, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable private fun languageDisplayName(code: String) = when(code) {
    "es" -> tr("اسپانیایی", "Spanish")
    "ko" -> tr("کره‌ای", "Korean")
    "tr" -> tr("ترکی", "Turkish")
    else -> tr("انگلیسی", "English")
}
