package com.hydro.reedance.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.annotation.DrawableRes
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import com.hydro.reedance.BuildConfig
import com.hydro.reedance.R
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydro.reedance.data.*

@Composable fun ProfileScreen(model: AppModel, library: () -> Unit, premium: () -> Unit) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val lesson by model.lesson.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var picker by rememberSaveable { mutableStateOf("") }
    val languages = listOf("English", "فارسی")
    val sizes = listOf(tr("کوچک", "Small"), tr("معمولی", "Default"), tr("بزرگ", "Large"))
    val languageIndex = if(settings.language == "en") 0 else 1
    val sizeIndex = settings.textSize.coerceIn(sizes.indices)
    val unavailable = tr("لینک تلگرام باز نشد.", "Could not open the Telegram link.")
    val scheme = MaterialTheme.colorScheme
    val background = scheme.background
    Column(Modifier.fillMaxSize().background(background).verticalScroll(rememberScrollState())
        .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 28.dp + LocalOverlayBottomInset.current),
        verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Text(tr("تنظیمات", "Settings"), style = MaterialTheme.typography.headlineLarge,
            color = scheme.onBackground)
        SettingsSection(tr("ظاهر", "Appearance")) {
            AppearancePicker(settings.theme, model::theme)
        }
        SettingsSection(tr("تنظیمات برنامه", "App settings")) {
            SettingsAction(UiIcons.Globe, tr("زبان رابط", "App language"), languages[languageIndex]) { picker = "language" }
            SettingsDivider()
            SettingsAction(UiIcons.WordMode, tr("اندازهٔ نوشته", "Text size"), sizes[sizeIndex]) { picker = "size" }
        }
        SettingsSection(tr("پخش و یادآوری", "Playback & reminders")) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) { ReminderControls(model) }
            SettingsDivider()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) { LyricsOverlayControls(model, premium) }
        }
        SettingsSection(tr("فضای من", "My space")) {
            SettingsAction(UiIcons.Library, tr("کتابخانه", "Library"), onClick = library)
            SettingsDivider()
            SettingsAction(UiIcons.CrownFilled, tr("عضویت پریمیوم", "Premium membership"), onClick = premium)
            SettingsDivider()
            SettingsAction(UiIcons.Explore, tr("ورود به استارتر", "Open Starter")) {
                context.startActivity(Intent(context, com.hydro.reedance.StarterActivity::class.java))
            }
            SettingsDivider()
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tr("فضای آفلاین", "Offline storage"), style = MaterialTheme.typography.titleMedium)
                Text(lesson?.s("title") ?: tr("بسته‌ای نصب نیست.", "No package installed."))
                Text(tr("با حذف آهنگ، نشان‌ها و پیشرفت پاک نمی‌شوند.", "Removing a song keeps your bookmarks and progress."),
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
        SettingsSection(tr("اعضای ری‌دنس", "Reedance team")) {
            ReedanceMember("حمید رضا سلیم وند", "Hamidreza Salimvand", "HS",
                tr("بنیان‌گذار ری‌دنس", "Reedance founder"), portrait = R.drawable.reedance_founder)
            SettingsDivider()
            ReedanceMember("سجاد علی وزیری", "Sajjad Alivaziri", "SA",
                tr("مدیر بخش محتوا", "Content lead"))
        }
        SettingsSection(tr("پشتیبانی و دربارهٔ برنامه", "Support & about")) {
            SettingsAction(UiIcons.ShareFilled, tr("اشتراک‌گذاری برنامه", "Share Reedance")) {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "Reedance — learn languages through music. https://github.com/khodehydro/reedance-android"), "Reedance"))
            }
            SettingsDivider()
            SettingsAction(UiIcons.Headphones, tr("پشتیبانی در تلگرام", "Telegram support")) {
                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/reedance"))) }
                catch(_: ActivityNotFoundException) { Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show() }
            }
            SettingsDivider()
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tr("به تجربه‌ات امتیاز بده", "How does it feel?"), style = MaterialTheme.typography.titleMedium)
                Row { (1..5).forEach { rating -> PressIconButton({ model.rating(rating) }, Modifier.weight(1f)) {
                    AppIcon(UiIcons.Star, "$rating", tint = if(settings.rating >= rating) scheme.primary else scheme.outlineVariant)
                } } }
                Text(tr("این امتیاز فقط محلی است و به فروشگاه ارسال نمی‌شود.", "This rating is local only. It is not sent to a store."), style = MaterialTheme.typography.bodySmall)
            }
            SettingsDivider()
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tr("نسخه: ${BuildConfig.VERSION_NAME}", "Version: ${BuildConfig.VERSION_NAME}"))
                Text(tr("تاریخ ساخت: ${BuildConfig.BUILD_DATE}", "Build date: ${BuildConfig.BUILD_DATE}"))
                DeveloperCredit()
            }
        }
    }
    if(picker.isNotEmpty()) {
        val language = picker == "language"
        SettingsChoiceDialog(if(language) tr("زبان رابط", "App language") else tr("اندازهٔ نوشته", "Text size"),
            if(language) languages else sizes, if(language) languageIndex else sizeIndex, { picker = "" }) {
            if(language) model.language(if(it == 0) "en" else "fa") else model.textSize(it)
            picker = ""
        }
    }
}

@Composable private fun ReedanceMember(nameFa: String, nameEn: String, initials: String, role: String,
    @DrawableRes portrait: Int? = null) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(Modifier.size(52.dp), shape = CircleShape, color = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer) {
            if(portrait != null) {
                Image(painterResource(portrait), contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(initials, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(tr(nameFa, nameEn), style = MaterialTheme.typography.titleMedium)
            Text(tr(nameEn, nameFa), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Text(role, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
    }
}
