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
import com.hydro.reedance.BuildConfig
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

    // One-time work supports the temporary ten-second test; periodic work has
    // a fifteen-minute minimum. Production milestones use the same persisted chain.
    suspend fun schedule(context: Context, enabled: Boolean, append: Boolean = false) {
        val work = WorkManager.getInstance(context)
        work.cancelUniqueWork("learning-reminders") // Retire the old periodic schedule.
        val name = "learning-reminder-stages"
        if(enabled) {
            val settings = SettingsRepository(context).flow.first()
            if(!settings.reminders || settings.lastVisit == 0L) return
            val minimumDelay = if(!permitted(context) || !settings.permissionsSetup) ReminderPolicy.unitMillis(BuildConfig.DEBUG)
                else if(BuildConfig.DEBUG) 10_000L else 60_000L
            val delay = (ReminderPolicy.nextAt(settings.lastVisit, settings.lastReminder, BuildConfig.DEBUG) - System.currentTimeMillis())
                .coerceAtLeast(minimumDelay)
            val request = OneTimeWorkRequestBuilder<LearningReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
            work.enqueueUniqueWork(name, if(append) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE, request)
        } else {
            work.cancelUniqueWork(name)
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
        val result = if(!settings.reminders || !settings.permissionsSetup || settings.language.isEmpty() ||
            !ReminderPolicy.eligible(now, settings.lastVisit, settings.lastReminder, BuildConfig.DEBUG) || !LearningReminders.permitted(context)) {
            Result.success()
        } else {
            val dao = LearningDatabase.get(context).learning()
            val due = dao.dueCount(now)
            val steps = dao.stepCount()
            val fa = settings.language == "fa"
            val stage = ReminderPolicy.stage(now - settings.lastVisit, BuildConfig.DEBUG)
            val text = ReminderPolicy.message(stage, due, steps, fa)
            // Recheck opt-in and foreground state immediately before posting.
            withContext(Dispatchers.Main) {
                val latest = preferences.flow.first()
                if(latest.reminders && latest.lastVisit == settings.lastVisit && LearningReminders.permitted(context) &&
                    !ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    val manager = context.getSystemService(NotificationManager::class.java)
                    manager.createNotificationChannel(NotificationChannel(LearningReminders.CHANNEL,
                        if(fa) "یادآوری یادگیری" else "Learning reminders", NotificationManager.IMPORTANCE_DEFAULT))
                    val intent = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    val notification = NotificationCompat.Builder(context, LearningReminders.CHANNEL)
                        .setSmallIcon(R.drawable.ic_learning_reminder).setContentTitle("Reedance")
                        .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
                        .setContentIntent(intent).setAutoCancel(true).build()
                    try {
                        NotificationManagerCompat.from(context).notify(LearningReminders.NOTIFICATION, notification)
                        preferences.reminded(now)
                    } catch(_: SecurityException) { /* Permission may be revoked between check and post. */ }
                }
            }
            Result.success()
        }
        LearningReminders.schedule(context, preferences.flow.first().reminders, append = true)
        result
    } catch(cancelled: CancellationException) { throw cancelled } catch(_: Exception) { Result.retry() }
}
