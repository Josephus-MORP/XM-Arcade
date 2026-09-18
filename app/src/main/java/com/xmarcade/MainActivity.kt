package com.xmarcade

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.xmarcade.data.local.PreferencesManager
import com.xmarcade.ui.components.AppScaffold
import com.xmarcade.ui.components.NowPlayingBar
import com.xmarcade.ui.navigation.Screen
import com.xmarcade.ui.navigation.XMNavGraph
import com.xmarcade.ui.screens.music.MusicViewModel
import com.xmarcade.ui.theme.XMArcadeTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var preferences: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by preferences.settingsFlow.collectAsState(initial = com.xmarcade.data.models.AppSettings())
            val pubkey by preferences.pubkeyFlow.collectAsState(initial = null)
            XMArcadeTheme(darkTheme = settings.isDarkMode, secondaryHue = settings.secondaryColorHue) {
                Surface(modifier = Modifier.fillMaxSize(), color = com.xmarcade.ui.theme.XmNavy) {
                    val navController = rememberNavController()
                    val backStack by navController.currentBackStackEntryAsState()
                    val currentRoute = backStack?.destination?.route ?: Screen.Login.route
                    val isLoggedIn = pubkey != null
                    // Shared MusicViewModel for persistent playback (except shorts)
                    val musicVm: MusicViewModel = hiltViewModel()
                    val isPlaying by musicVm.isPlaying.collectAsState()
                    val currentTrack by musicVm.currentTrack.collectAsState()

                    val showScaffold = currentRoute != Screen.Login.route
                    if (showScaffold) {
                        AppScaffold(
                            navController = navController,
                            currentRoute = currentRoute,
                            onNavigate = { route ->
                                navController.navigate(route) {
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            showNowPlaying = currentTrack != null && currentRoute != Screen.Shorts.route,
                            nowPlayingContent = {
                                currentTrack?.let { track ->
                                    NowPlayingBar(
                                        trackTitle = track.title,
                                        artist = track.artist,
                                        isPlaying = isPlaying,
                                        onPlayPause = { musicVm.togglePlayPause() },
                                        onExpand = { navController.navigate(Screen.Music.route) }
                                    )
                                }
                            }
                        ) {
                            XMNavGraph(
                                navController = navController,
                                isLoggedIn = isLoggedIn,
                                onLoginSuccess = {},
                                musicViewModel = musicVm
                            )
                        }
                    } else {
                        XMNavGraph(navController = navController, isLoggedIn = isLoggedIn, musicViewModel = musicVm)
                    }
                }
            }
        }
    }
}
