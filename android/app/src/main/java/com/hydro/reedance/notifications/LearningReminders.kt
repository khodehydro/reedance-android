package com.hydro.reedance.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.*
import com.hydro.reedance.domain.ReminderPolicy
import com.hydro.reedance.MainActivity
import com.hydro.reedance.R
import com.hydro.reedance.data.LearningDatabase
import com.hydro.reedance.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object LearningReminders {
    const val CHANNEL = "learning_reminders"
    const val NOTIFICATION = 2401
    fun permitted(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE)

    fun schedule(context: Context, enabled: Boolean) {
        val work = WorkManager.getInstance(context)
        if(enabled) {
            val request = PeriodicWorkRequestBuilder<LearningReminderWorker>(12, TimeUnit.HOURS, 1, TimeUnit.HOURS)
                .setInitialDelay(12, TimeUnit.HOURS).build()
            work.enqueueUniquePeriodicWork("learning-reminders", ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            work.cancelUniqueWork("learning-reminders")
            NotificationManagerCompat.from(context).cancel(NOTIFICATION)
        }
    }
}

class LearningReminderWorker(context: Context, parameters: WorkerParameters): CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val context = applicationContext
        val preferences = SettingsRepository(context)
        val settings = preferences.flow.first()
        val now = System.currentTimeMillis()
        if(!settings.reminders || !settings.permissionsSetup || settings.language.isEmpty() ||
            !ReminderPolicy.eligible(now, settings.lastVisit, settings.lastReminder) || !LearningReminders.permitted(context)) {
            Result.success()
        } else {
            val dao = LearningDatabase.get(context).learning()
            val due = dao.dueCount(now)
            val steps = dao.stepCount()
            val fa = settings.language == "fa"
            val text = when {
                due > 0 -> if(fa) "$due واژه منتظر مرور تو هستند؛ با یک مرور کوتاه برگرد." else "$due words are ready for review. Make a little time for them."
                steps in 1..14 -> if(fa) "قدم‌های خوبی برداشتی؛ برای قدم بعدی استارتر برگرد." else "You've made a start. Come back for your next Starter step."
                else -> if(fa) "چند ساعتیه بهمون سر نزدی؛ وقت یک آهنگ و چند کلمهٔ تازه نیست؟" else "It's been a while. How about a song and a few new words?"
            }
            // Recheck opt-in and foreground state immediately before posting.
            withContext(Dispatchers.Main) {
                if(preferences.flow.first().reminders && LearningReminders.permitted(context) &&
                    !ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    val manager = context.getSystemService(NotificationManager::class.java)
                    manager.createNotificationChannel(NotificationChannel(LearningReminders.CHANNEL,
                        if(fa) "یادآوری یادگیری" else "Learning reminders", NotificationManager.IMPORTANCE_DEFAULT))
                    val intent = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    val notification = NotificationCompat.Builder(context, LearningReminders.CHANNEL)
                        .setSmallIcon(R.drawable.ic_learning_reminder).setContentTitle("Reedance")
                        .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
                        .setContentIntent(intent).setAutoCancel(true).setOnlyAlertOnce(true).build()
                    try {
                        NotificationManagerCompat.from(context).notify(LearningReminders.NOTIFICATION, notification)
                        preferences.reminded(now)
                    } catch(_: SecurityException) { /* Permission may be revoked between check and post. */ }
                }
            }
            Result.success()
        }
    } catch(cancelled: CancellationException) { throw cancelled } catch(_: Exception) { Result.retry() }
}
