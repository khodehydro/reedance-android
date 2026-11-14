package com.hydro.reedance.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydro.reedance.data.AppModel
import com.hydro.reedance.notifications.LearningReminders

@Composable fun ReminderControls(model: AppModel) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permitted by remember { mutableStateOf(LearningReminders.permitted(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permitted = LearningReminders.permitted(context) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = LearningReminders.permitted(context)
        model.reminders(granted && permitted)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(tr("یادآور هوشمند یادگیری", "Smart learning reminders"), Modifier.weight(1f))
            PressSwitch(settings.reminders, { enabled ->
                when {
                    !enabled -> model.reminders(false)
                    permitted -> model.reminders(true)
                    Build.VERSION.SDK_INT >= 33 -> permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            })
        }
        Text(tr("اختیاری و کاملاً محلی؛ پس از حداقل ۱۲ ساعت دوری، بر اساس واژه‌های آمادهٔ مرور و قدم‌های استارتر. زمان ارسال به محدودیت باتری دستگاه بستگی دارد.", "Optional and entirely on-device. After at least 12 hours away, reminders use due words and Starter progress. Delivery time depends on Android battery limits."), style = MaterialTheme.typography.bodySmall)
        if(!permitted) {
            Text(tr("اعلان‌ها در اندروید مجاز نیستند؛ بدون فعال‌کردن آن‌ها هم می‌توانی از برنامه استفاده کنی.", "Android notifications are blocked. You can still use the whole app without them."), style = MaterialTheme.typography.bodySmall)
            PressTextButton({ context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) {
                Text(tr("تنظیمات اعلان اندروید", "Android notification settings"))
            }
        }
    }
}

@Composable fun PermissionSetup(model: AppModel) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Heading(tr("آمادهٔ شروع؟", "Ready to begin?"), tr("فقط دسترسی‌های ضروری، با انتخاب خودت", "Only the access you choose"))
        Panel { ReminderControls(model) }
        Panel {
            Text(tr("دانلود و فضای آفلاین", "Downloads and offline storage"), style = MaterialTheme.typography.titleMedium)
            Text(tr("دریافت محتوا از اینترنت و ذخیره در فضای خصوصی برنامه، به مجوز فایل، عکس یا دسترسی کامل حافظه نیاز ندارد. هیچ‌کدام درخواست نمی‌شوند.", "Internet downloads stored in private app storage do not need file, photo or all-files permissions. None will be requested."))
        }
        Action(tr("ادامه به برنامه", "Continue to the app"), Modifier.fillMaxWidth()) { model.completePermissionSetup() }
        Text(tr("فعال‌کردن اعلان اجباری نیست. هر زمان از فضای من و تنظیمات می‌توانی نظرت را عوض کنی.", "Notifications are not required. Change your choice anytime in My space and settings."), style = MaterialTheme.typography.bodySmall)
    }
}
