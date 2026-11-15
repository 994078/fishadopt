package com.fishadopt.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.fishadopt.app.di.AppContainer
import com.fishadopt.app.ui.about.AboutScreen
import com.fishadopt.app.ui.chat.ChatScreen
import com.fishadopt.app.ui.home.HomeScreen
import com.fishadopt.app.ui.logs.LogsScreen
import com.fishadopt.app.ui.onboarding.OnboardingScreen
import com.fishadopt.app.ui.settings.SettingsScreen
import com.fishadopt.app.ui.welcome.WelcomeScreen

/** 页面路由（PRD 表6 的 7 个页面）。 */
object Routes {
    const val WELCOME = "welcome"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CHAT = "chat"
    const val LOGS = "logs"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
}

@Composable
fun FishNavHost(
    navController: NavHostController,
    container: AppContainer,
    startDestination: String,
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.WELCOME) {
            WelcomeScreen(onDone = {
                container.settings.onboardingDone = true
                navController.navigate(Routes.ONBOARDING) {
                    popUpTo(Routes.WELCOME) { inclusive = true }
                }
            })
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                container = container,
                onDone = {
                    container.settings.onboardingDone = true
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                container = container,
                onOpenChat = { navController.navigate(Routes.CHAT) },
                onOpenLogs = { navController.navigate(Routes.LOGS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.CHAT) {
            ChatScreen(
                container = container,
                onBackToHome = { navController.popBackStack(Routes.HOME, false) },
            )
        }
        composable(Routes.LOGS) {
            LogsScreen(container = container, onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                container = container,
                onOpenLogs = { navController.navigate(Routes.LOGS) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
                onBack = { navController.popBackStack() },
                onRerunOnboarding = { navController.navigate(Routes.ONBOARDING) },
            )
        }
        composable(Routes.ABOUT) {
            AboutScreen(container = container, onBack = { navController.popBackStack() })
        }
    }
}
