package com.xmarcade.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xmarcade.app.core.Nip19
import com.xmarcade.app.core.Signer
import com.xmarcade.app.core.displayName

@Composable
fun SettingsScreen() {
  Column(Modifier.fillMaxSize()) {
    TopBar("Settings", displayName(Me.profile), Me.profile.picture, onDd = { Nav.ddOpen = true })
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
      GroupLabel("Account")
      if (Signer.isSignedIn()) {
        val npub = try { Nip19.npubEncode(Signer.pubkey ?: "") } catch (_: Exception) { "" }
        RowItem("user", MaterialTheme.colorScheme.primary, "Signed in",
          if (npub.length > 28) npub.take(28) + "…" else npub,
          onClick = { Acts.copyText(npub, "npub copied") })
        Spacer(Modifier.height(8.dp))
        RowItem("edit", null, "Edit profile", "Name, bio, picture, banner",
          onClick = { Nav.openSheet(Sheet("editProfile")) })
        Spacer(Modifier.height(8.dp))
        RowItem("key", LocalXM.current.ok, "My keys", "npub / nsec backup",
          onClick = { Nav.openSheet(Sheet("keys")) })
        Spacer(Modifier.height(8.dp))
        RowItem("logout", LocalXM.current.live, "Log out", "Clears the key from this device",
          onClick = {
            Signer.logout(); Me.refresh()
            Nav.reset("welcome")
          })
      } else {
        RowItem("key", MaterialTheme.colorScheme.primary, "Sign in", "Amber, bunker or nsec",
          onClick = { Nav.reset("welcome") })
      }
      GroupLabel("Appearance")
      RowItem("eye", null, "Theme", if (S.theme == "dark") "Dark" else "Light", chev = false,
        tail = {
          Row {
            Pill("Dark", S.theme == "dark") { S.theme = "dark"; S.save() }
            Spacer(Modifier.width(8.dp))
            Pill("Light", S.theme == "light") { S.theme = "light"; S.save() }
          }
        }, onClick = {})
      Spacer(Modifier.height(8.dp))
      RowItem("spark", null, "Accent hue", S.accentHue.toString() + "°", chev = false, onClick = {})
      HueSlider(S.accentHue) { S.accentHue = it; S.save() }
      Spacer(Modifier.height(4.dp))
      Text("Starting pane", fontSize = 14.sp, color = LocalXM.current.text2,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 6.dp))
      SECTIONS.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
          row.forEach { (t, _) ->
            Pill(t.second, S.startingPane == t.first) { S.startingPane = t.first; S.save() }
            Spacer(Modifier.width(8.dp))
          }
        }
      }
      GroupLabel("Network")
      RowItem("relay", null, "Relays", "${com.xmarcade.app.core.RelayPool.relays.size} configured",
        onClick = { Nav.openSheet(Sheet("relays")) })
      Spacer(Modifier.height(8.dp))
      RowItem("image", null, "Blossom servers", "Media uploads",
        onClick = { Nav.openSheet(Sheet("blossom")) })
      GroupLabel("Tags")
      RowItem("hash", null, "Followed tags", S.followTags.joinToString(", ").ifEmpty { "none" },
        onClick = { Nav.openSheet(Sheet("tags")) })
      GroupLabel("About")
      RowItem("info", null, "About XM Arcade", "Version 1.0.4 · native Kotlin",
        onClick = { Nav.openSheet(Sheet("about")) })
      Spacer(Modifier.height(14.dp))
      Text("Shorts · mini apps · music · Concord · zaps · xaps",
        fontSize = 12.5.sp, color = LocalXM.current.text3, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 30.dp))
    }
  }
}
