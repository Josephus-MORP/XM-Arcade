package com.xmarcade.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.xmarcade.ui.screens.chats.ChatsScreen
import com.xmarcade.ui.screens.login.LoginScreen
import com.xmarcade.ui.screens.miniapps.MiniAppsScreen
import com.xmarcade.ui.screens.music.MusicScreen
import com.xmarcade.ui.screens.profile.ProfileScreen
import com.xmarcade.ui.screens.settings.SettingsScreen
import com.xmarcade.ui.screens.shorts.ShortsScreen
import com.xmarcade.ui.screens.wallet.WalletScreen

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Shorts : Screen("shorts")
    data object MiniApps : Screen("miniapps")
    data object Music : Screen("music")
    data object Chats : Screen("chats")
    data object Wallet : Screen("wallet")
    data object Profile : Screen("profile")
    data object Settings : Screen("settings")
}

@Composable
fun XMNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Login.route,
    isLoggedIn: Boolean = false,
    onLoginSuccess: () -> Unit = {},
    musicViewModel: com.xmarcade.ui.screens.music.MusicViewModel? = null
) {
    NavHost(navController = navController, startDestination = if (isLoggedIn) Screen.Shorts.route else startDestination) {
        composable(Screen.Login.route) { LoginScreen(onLoginSuccess = {
            onLoginSuccess()
            navController.navigate(Screen.Shorts.route) { popUpTo(Screen.Login.route){ inclusive=true } }
        }, navController = navController) }
        composable(Screen.Shorts.route) { ShortsScreen(navController) }
        composable(Screen.MiniApps.route) { MiniAppsScreen(navController) }
        composable(Screen.Music.route) { MusicScreen(navController) }
        composable(Screen.Chats.route) { ChatsScreen(navController) }
        composable(Screen.Wallet.route) { WalletScreen(navController) }
        composable(Screen.Profile.route) { ProfileScreen(navController) }
        composable(Screen.Settings.route) { SettingsScreen(navController) }
    }
}
