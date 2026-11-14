package com.hydro.reedance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.hydro.reedance.data.AppModel
import com.hydro.reedance.ui.ReedanceApp
import com.hydro.reedance.ui.StarterDestination

/** Separate task-stack entry; never composes MainShell or its floating menu. */
class StarterActivity: ComponentActivity() {
    private lateinit var model: AppModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        model = ViewModelProvider(this)[AppModel::class.java]
        setContent { ReedanceApp(model) { StarterDestination(model, ::finish) } }
    }
    override fun onResume() {
        super.onResume()
        model.foregroundActivity(true)
    }
    override fun onPause() {
        model.foregroundActivity(false)
        super.onPause()
    }
    override fun onStart() {
        super.onStart()
        model.recordVisit()
        androidx.core.app.NotificationManagerCompat.from(this).cancel(com.hydro.reedance.notifications.LearningReminders.NOTIFICATION)
    }
    override fun onStop() {
        model.recordVisit()
        super.onStop()
    }
}
