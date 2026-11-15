package com.fishadopt.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.fishadopt.app.di.AppContainer
import com.fishadopt.app.ui.navigation.FishNavHost
import com.fishadopt.app.ui.navigation.Routes
import com.fishadopt.app.ui.theme.FishAdoptTheme

class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = (application as FishApp).container
        setContent {
            FishAdoptTheme {
                val navController = rememberNavController()
                // 首次启动进引导，后续直接进主控制页（权限每次启动仍会实时校验）
                val start = if (container.settings.onboardingDone) Routes.HOME else Routes.WELCOME
                FishNavHost(
                    navController = navController,
                    container = container,
                    startDestination = start,
                )
            }
        }
    }
}
