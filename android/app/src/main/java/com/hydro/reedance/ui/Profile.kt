package com.hydro.reedance.ui

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.hydro.reedance.BuildConfig
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydro.reedance.data.*

@Composable fun ProfileScreen(model: AppModel, library: () -> Unit, premium: () -> Unit) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val lesson by model.lesson.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp + LocalOverlayBottomInset.current), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Heading(tr("فضای من", "My space"), tr("ریتم یادگیری خودت را پیدا کن.", "Make this little space your own."))
        ProfileMembershipCard(premium)
        ProfileStarterCard { context.startActivity(Intent(context, com.hydro.reedance.StarterActivity::class.java)) }
        Panel {
            Text(tr("تنظیمات برنامه", "Make yourself at home"), style = MaterialTheme.typography.titleLarge)
            Text(tr("زبان رابط", "App language"))
            ChoiceRow(listOf("English", "فارسی"), if(settings.language == "en") 0 else 1, List(2) { UiIcons.Globe to UiIcons.GlobeFilled }) { model.language(if(it == 0) "en" else "fa") }
            ReminderControls(model)
            Text(tr("ظاهر", "Appearance"))
            ChoiceRow(listOf(tr("روشن", "Light"), tr("تاریک", "Dark")), if(settings.theme == "dark") 1 else 0, listOf(UiIcons.Light to UiIcons.LightFilled, UiIcons.Dark to UiIcons.DarkFilled)) { model.theme(if(it == 1) "dark" else "light") }
            Text(tr("اندازهٔ نوشته", "Text size"))
            ChoiceRow(listOf(tr("کوچک", "Small"), tr("معمولی", "Default"), tr("بزرگ", "Large")), settings.textSize, List(3) { UiIcons.WordMode to UiIcons.WordModeFilled }, model::textSize)

        }
        Panel { PackControls(model) }
        Panel {
            Text(tr("فضای آفلاین", "Offline storage"), style = MaterialTheme.typography.titleLarge)
            Text(if(lesson == null) tr("بسته‌ای نصب نیست.", "No package installed.") else lesson!!.s("title"))
            Text(tr("با حذف آهنگ، نشان‌ها و پیشرفت پاک نمی‌شوند.", "Removing a song keeps your bookmarks and progress."), style = MaterialTheme.typography.bodySmall)
        }
        Panel {
            Text(tr("به تجربه‌ات امتیاز بده", "How does it feel?"), style = MaterialTheme.typography.titleLarge)
            Row { (1..5).forEach { rating -> PressIconButton({ model.rating(rating) }) {
                AppIcon(UiIcons.Star, "$rating", tint = if(settings.rating >= rating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
            } } }
            Text(tr("این امتیاز فقط محلی است و به فروشگاه ارسال نمی‌شود.", "This rating is local only. It is not sent to a store."), style = MaterialTheme.typography.bodySmall)
            PressOutlinedButton({
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Reedance — learn languages through music. https://github.com/khodehydro/reedance-android"), "Reedance"))
            }) { Text(tr("اشتراک‌گذاری برنامه", "Share Reedance")) }
        }
        Panel {
            Text(tr("دربارهٔ ری‌دنس", "About Reedance"), style = MaterialTheme.typography.titleLarge)
            Text(tr("نسخه: ${BuildConfig.VERSION_NAME}", "Version: ${BuildConfig.VERSION_NAME}"))
            Text(tr("تاریخ ساخت: ${BuildConfig.BUILD_DATE}", "Build date: ${BuildConfig.BUILD_DATE}"))
            DeveloperCredit()
        }
    }
}
