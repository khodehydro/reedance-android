package com.hydro.reedance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import com.hydro.reedance.data.AppModel
import com.hydro.reedance.ui.ReedanceApp
class MainActivity: ComponentActivity() {
    private lateinit var appModel: AppModel
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        if(android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        val model = ViewModelProvider(this)[AppModel::class.java]
        appModel = model
        splash.setKeepOnScreenCondition { !model.ready.value }
        setContent { ReedanceApp(model) }
    }
    override fun onStop() {
        if(::appModel.isInitialized) appModel.onBackground()
        super.onStop()
    }

}
