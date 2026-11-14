package com.hydro.reedance.ui

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydro.reedance.data.*

@Composable fun ProfileScreen(model: AppModel, library: () -> Unit) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val lesson by model.lesson.collectAsStateWithLifecycle()
    val results by model.results.collectAsStateWithLifecycle()
    val reviews by model.reviews.collectAsStateWithLifecycle()
    val steps by model.steps.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp + LocalOverlayBottomInset.current), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Heading(tr("فضای من", "My space"), tr("ریتم یادگیری خودت را پیدا کن.", "Make this little space your own."))
        Panel {
            AppIcon(UiIcons.User, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Text(tr("سلام، همراه مهمان", "Hello, guest"), style = MaterialTheme.typography.titleLarge)
            Text(tr("حساب یا همگام‌سازی ابری فعال نیست. پیشرفت فقط روی این دستگاه ذخیره می‌شود.", "No account or cloud sync. Your progress stays on this device."))
            Text(tr("${reviews.size} واژه · ${steps.size} قدم استارتر · ${results.size} پاسخ ثبت‌شده", "${reviews.size} words · ${steps.size} starter steps · ${results.size} answers"))
            PressTextButton(library) { Text(tr("کتابخانهٔ من", "My library")) }
        }
        Panel {
            Text(tr("تنظیمات برنامه", "Make yourself at home"), style = MaterialTheme.typography.titleLarge)
            Text(tr("زبان رابط", "App language"))
            ChoiceRow(listOf("English", "فارسی"), if(settings.language == "en") 0 else 1) { model.language(if(it == 0) "en" else "fa") }
            Text(tr("ظاهر", "Appearance"))
            ChoiceRow(listOf(tr("دستگاه", "System"), tr("روشن", "Light"), tr("تاریک", "Dark")), listOf("system", "light", "dark").indexOf(settings.theme)) { model.theme(listOf("system", "light", "dark")[it]) }
            Text(tr("اندازهٔ نوشته", "Text size"))
            ChoiceRow(listOf(tr("کوچک", "Small"), tr("معمولی", "Default"), tr("بزرگ", "Large")), settings.textSize, model::textSize)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(tr("حرکت کمتر", "Reduce motion"), Modifier.weight(1f))
                PressSwitch(settings.reduceMotion, model::motion)
            }
            Text(tr("تنظیم حرکت دستگاه نیز رعایت می‌شود.", "Your device animation setting is also respected."), style = MaterialTheme.typography.bodySmall)
        }
        Panel {
            Text(tr("فضای آفلاین", "Offline storage"), style = MaterialTheme.typography.titleLarge)
            Text(if(lesson == null) tr("بسته‌ای نصب نیست.", "No package installed.") else tr("دموی Midnight Avenue نصب شده است.", "Midnight Avenue demo is installed."))
            if(lesson != null) PressOutlinedButton({ confirmDelete = true }) { Text(tr("پاک کردن فایل‌های آهنگ", "Remove song files")) }
            Text(tr("با حذف آهنگ، نشان‌ها و پیشرفت پاک نمی‌شوند.", "Removing a song keeps your bookmarks and progress."), style = MaterialTheme.typography.bodySmall)
        }
        Panel {
            Text(tr("طرح اشتراک", "Membership"), style = MaterialTheme.typography.titleLarge)
            Text(tr("هنوز فعال نیست؛ خرید یا پرداختی انجام نمی‌شود.", "Not available yet. No purchase or payment is offered."))
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
            Text(tr("نسخهٔ بومی آزمایشی · Kotlin و Jetpack Compose\nمحتوای دمو مستقل و موسیقی بی‌کلام است.", "Native debug edition · Kotlin & Jetpack Compose\nOriginal demo material with instrumental music."))
            Text("developed with ❤️ by HYDRO team")
            PressTextButton({ uri.openUri("https://github.com/khodehydro/reedance-android") }) { Text(tr("کد منبع", "Source code")) }
        }
    }
    if(confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(tr("فایل‌های آهنگ حذف شوند؟", "Remove song files?")) }, text = { Text(tr("پیشرفت و نشان‌ها محفوظ می‌مانند.", "Your progress and bookmarks will be kept.")) }, confirmButton = { PressTextButton({ model.deleteDemo(); confirmDelete = false }) { Text(tr("حذف", "Remove")) } }, dismissButton = { PressTextButton({ confirmDelete = false }) { Text(tr("انصراف", "Cancel")) } })
}
