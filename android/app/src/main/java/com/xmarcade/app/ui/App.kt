package com.xmarcade.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xmarcade.app.core.AppScope
import com.xmarcade.app.core.MiniApp
import com.xmarcade.app.core.Net
import com.xmarcade.app.core.Nwc
import com.xmarcade.app.core.RelayPool
import com.xmarcade.app.core.Repo
import com.xmarcade.app.core.Signer
import com.xmarcade.app.core.Store
import com.xmarcade.app.core.Webxdc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XMApp() {
  val ctx = LocalContext.current
  var ready by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    Store.init(ctx)
    Net.init(ctx)
    Webxdc.init(ctx)
    RelayPool.init()
    Repo.init()
    Nwc.init()
    S.init()
    if (Nwc.connected()) { try { Nwc.listen() } catch (_: Exception) {} }
    RelayPool.refreshProbes()
    AppScope.launch(Dispatchers.Main) {
      RelayPool.status.collect { m ->
        RelayLive.count = m.values.count { it == "live" }
        RelayLive.total = RelayPool.relays.size
      }
    }
    val ok = try { Signer.restoreSession() } catch (_: Exception) { false }
    if (ok && Signer.isSignedIn()) {
      Me.refresh()
      AppScope.launch(Dispatchers.IO) {
        try { Repo.fetchContacts() } catch (_: Exception) {}
        try { Repo.collectKeyShares() } catch (_: Exception) {}
      }
      Nav.reset(S.startingPane)
      S.section = S.startingPane
    } else {
      Nav.reset("welcome")
    }
    ready = true
  }

  XMTheme(S.theme, S.accentHue) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      if (!ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            XLogo(72.dp)
            Spacer(Modifier.height(12.dp))
            Text("XM Arcade", color = LocalXM.current.text2, fontSize = 15.sp)
          }
        }
      } else {
        val canPop = Nav.ddOpen || Nav.stack.size > 1
        // When a sheet is open the bottom sheet owns back-presses; at the root
        // the press falls through to the system (exits the app).
        BackHandler(enabled = Nav.sheet == null && canPop) { Nav.back() }
        Scaffold(snackbarHost = { SnackbarHost(Nav.snacks) }) { pad ->
          Box(Modifier.fillMaxSize().padding(pad)) {
            val r = Nav.stack.lastOrNull() ?: Route("welcome")
            when (r.id) {
              "welcome" -> WelcomeScreen()
              "create" -> CreateAccountScreen()
              "loginNsec" -> LoginNsecScreen()
              "loginSigner" -> LoginSignerScreen()
              "profile" -> ProfileScreen(r.data as? String)
              "shorts" -> ShortsScreen()
              "miniapps" -> MiniAppsScreen()
              "music" -> MusicScreen()
              "chats" -> ChatsScreen()
              "wallet" -> WalletScreen()
              "settings" -> SettingsScreen()
              "channel" -> (r.data as? ChannelData)?.let { ChannelScreen(it) } ?: ChatsScreen()
              "gamerun" -> (r.data as? MiniApp)?.let { GameRunScreen(it) } ?: MiniAppsScreen()
              "compose" -> ComposeScreen(r.data as? ReplyTo)
              "studio" -> StudioScreen(r.data as? String)
              else -> ShortsScreen()
            }
            if (Nav.ddOpen) {
              DdPanel(Nav.stack.firstOrNull()?.id ?: S.section,
                onSection = { Nav.setSection(it) }, onDismiss = { Nav.ddOpen = false })
            }
          }
        }
        val sh = Nav.sheet
        if (sh != null) {
          ModalBottomSheet(
            onDismissRequest = { Nav.closeSheet() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
          ) {
            SheetHost(sh)
          }
        }
      }
    }
  }
}
