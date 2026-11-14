package com.hydro.reedance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import com.hydro.reedance.data.AppModel
import com.hydro.reedance.ui.ReedanceApp
class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val model = ViewModelProvider(this)[AppModel::class.java]
        splash.setKeepOnScreenCondition { !model.ready.value }
        setContent { ReedanceApp(model) }
    }
}
