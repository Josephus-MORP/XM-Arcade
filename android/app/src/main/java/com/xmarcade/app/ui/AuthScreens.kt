package com.xmarcade.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.xmarcade.app.R
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xmarcade.app.Platform
import com.xmarcade.app.core.AppScope
import com.xmarcade.app.core.RelayPool
import com.xmarcade.app.core.Repo
import com.xmarcade.app.core.Signer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WelcomeScreen() {
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 26.dp),
    horizontalAlignment = Alignment.CenterHorizontally) {
    Spacer(Modifier.height(52.dp))
    Image(painterResource(R.drawable.xm_logo), "XM Arcade logo",
      Modifier.size(96.dp).clip(RoundedCornerShape(26.dp)), contentScale = ContentScale.Crop)
    Spacer(Modifier.height(20.dp))
    Text(buildAnnotatedString {
      append("Welcome to ")
      pushStyle(SpanStyle(color = LocalXM.current.xmr))
      append("XM Arcade")
      pop()
    }, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
    Spacer(Modifier.height(10.dp))
    Text("Shorts · mini apps · music · Concord chats — with zaps and Monero tips built in.",
      fontSize = 15.sp, color = LocalXM.current.text2, textAlign = TextAlign.Center)
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      StaticChip("real Nostr client", "check", green = true)
      Spacer(Modifier.width(8.dp))
      StaticChip("Amber-ready", "key", green = true)
    }
    Spacer(Modifier.height(22.dp))
    XMButton("Create account", { Nav.go("create") }, Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
    XMButton("Log in with Amber", { Nav.go("loginSigner") }, Modifier.fillMaxWidth(), kind = BtnKind.Line)
    Spacer(Modifier.height(10.dp))
    XMButton("I already have a key", { Nav.go("loginNsec") }, Modifier.fillMaxWidth(), kind = BtnKind.Ghost)
    Spacer(Modifier.height(22.dp))
    Text("Curious first? Browse the demo, then decide.", fontSize = 13.sp, color = LocalXM.current.text3,
      textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 30.dp))
  }
}

@Composable
fun CreateAccountScreen() {
  val scope = rememberCoroutineScope()
  var name by remember { mutableStateOf("you") }
  var about by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
    BackBar("New account", onBack = { Nav.back() })
    Column(Modifier.padding(horizontal = 22.dp)) {
      Spacer(Modifier.height(10.dp))
      Avatar(name.ifEmpty { "you" }, 92.dp)
      Spacer(Modifier.height(8.dp))
      Text(if (name.isEmpty()) "your avatar" else name, fontSize = 13.sp, color = LocalXM.current.text3)
      Spacer(Modifier.height(16.dp))
      GroupLabel("Display name")
      XMField(name, { name = it }, "you", maxChars = 60)
      Spacer(Modifier.height(12.dp))
      GroupLabel("About (optional)")
      XMField(about, { about = it }, "gamer · nostrich · xmr maxi", minLines = 3, singleLine = false, maxChars = 280)
      Spacer(Modifier.height(14.dp))
      Text("Your profile will be published to:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(8.dp))
      RelayPool.relays.forEach { r ->
        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
          AppIcon("check", 15.dp, tint = LocalXM.current.ok)
          Spacer(Modifier.width(8.dp))
          Text(r, fontFamily = Mono, fontSize = 13.sp, color = LocalXM.current.text2)
        }
      }
      Spacer(Modifier.height(18.dp))
      XMButton("Generate my key", {
        busy = true
        scope.launch(Dispatchers.IO) {
          try {
            Signer.createLocal()
            try { Repo.publishProfile(mapOf("name" to name.ifEmpty { "you" }, "about" to about)) } catch (_: Exception) {}
            try { Repo.fetchContacts() } catch (_: Exception) {}
            withContext(Dispatchers.Main) {
              busy = false
              Me.refresh()
              Nav.reset(S.startingPane); S.section = S.startingPane; S.save()
              Nav.toast("Account created — welcome to XM Arcade")
            }
          } catch (e: Exception) {
            withContext(Dispatchers.Main) { busy = false; Nav.toast("Create failed: ${e.message}") }
          }
        }
      }, Modifier.fillMaxWidth(), enabled = !busy)
      Spacer(Modifier.height(10.dp))
      Text("A fresh key is generated on this device — XM Arcade never sees it.",
        fontSize = 13.sp, color = LocalXM.current.text3, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 30.dp))
    }
  }
}

@Composable
fun LoginNsecScreen() {
  val scope = rememberCoroutineScope()
  var key by remember { mutableStateOf("") }
  var show by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
    BackBar("Log in", onBack = { Nav.back() })
    Column(Modifier.padding(horizontal = 22.dp)) {
      Text("Paste your key", style = MaterialTheme.typography.headlineLarge)
      Spacer(Modifier.height(4.dp))
      Text("nsec, hex secret or bunker:// — XM Arcade stores it on this device only.",
        fontSize = 14.sp, color = LocalXM.current.text2)
      Spacer(Modifier.height(14.dp))
      XMField(key, { key = it }, "nsec1…", mono = true, password = !show)
      Spacer(Modifier.height(10.dp))
      Row(Modifier.fillMaxWidth()) {
        XMButton(if (show) "Hide" else "Show", { show = !show }, kind = BtnKind.Line, small = true)
        Spacer(Modifier.width(10.dp))
        XMButton("Paste", {
          val p = Platform.paste()
          if (p.isNotEmpty()) { key = p; Nav.toast("Pasted") }
        }, kind = BtnKind.Line, small = true)
        Spacer(Modifier.weight(1f))
        XMButton("Log in", {
          val v = key.trim()
          if (v.isEmpty()) { Nav.toast("Paste a key first"); return@XMButton }
          busy = true
          scope.launch(Dispatchers.IO) {
            try {
              if (v.startsWith("bunker://")) Signer.loginBunkerUri(v)
              else Signer.loginLocalInput(v)
              try { Repo.fetchContacts() } catch (_: Exception) {}
              withContext(Dispatchers.Main) {
                busy = false
                Me.refresh()
                Nav.reset(S.startingPane); S.section = S.startingPane; S.save()
                Nav.toast("Signed in")
              }
            } catch (e: Exception) {
              withContext(Dispatchers.Main) { busy = false; Nav.toast("Key rejected: ${e.message}") }
            }
          }
        }, kind = BtnKind.Primary, small = true, enabled = !busy)
      }
      Spacer(Modifier.height(18.dp))
      RowItem("key", LocalXM.current.ok, "Safer alternative", "Let Amber or another signer hold your key",
        onClick = { Nav.go("loginSigner") })
      Spacer(Modifier.height(10.dp))
      Text("Paste into a fake app and your sats + identity are gone. Double-check you trust XM Arcade first.",
        fontSize = 13.sp, color = LocalXM.current.text3, modifier = Modifier.padding(bottom = 30.dp))
    }
  }
}

@Composable
fun LoginSignerScreen() {
  var pane by remember { mutableStateOf("") } // "", "bunker", "pair"
  var bunker by remember { mutableStateOf("") }
  var pairObj by remember { mutableStateOf<Signer.Pairing?>(null) }
  var pairStatus by remember { mutableStateOf("") } // waiting|approved|paired|error:..
  var busy by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
    BackBar("External signer", onBack = { Nav.back() })
    Column(Modifier.padding(horizontal = 22.dp)) {
      Text("Log in with Amber", style = MaterialTheme.typography.headlineLarge)
      Spacer(Modifier.height(4.dp))
      Text("Your key stays in the signer app — XM Arcade only asks it to approve each event.",
        fontSize = 14.sp, color = LocalXM.current.text2)
      Spacer(Modifier.height(14.dp))
      RowItem("key", LocalXM.current.ok, "Paste bunker URL",
        "Already paired in Amber? Paste the bunker:// connection string",
        onClick = { pane = if (pane == "bunker") "" else "bunker" })
      Spacer(Modifier.height(10.dp))
      RowItem("qr", MaterialTheme.colorScheme.primary, "Pair a new signer",
        "Generate a pairing link, approve it in Amber, done",
        onClick = { pane = if (pane == "pair") "" else "pair" })
      if (pane == "bunker") {
        Spacer(Modifier.height(12.dp))
        XMField(bunker, { bunker = it }, "bunker://…", mono = true)
        Spacer(Modifier.height(10.dp))
        XMButton("Connect bunker", {
          if (bunker.trim().isEmpty()) { Nav.toast("Paste a bunker URL first"); return@XMButton }
          busy = true
          scope.launch(Dispatchers.IO) {
            try {
              Signer.loginBunkerUri(bunker.trim())
              try { Repo.fetchContacts() } catch (_: Exception) {}
              withContext(Dispatchers.Main) {
                busy = false
                Me.refresh()
                Nav.reset(S.startingPane); S.section = S.startingPane; S.save()
                Nav.toast("Signer connected — welcome back")
              }
            } catch (e: Exception) {
              withContext(Dispatchers.Main) { busy = false; Nav.toast("Bunker failed: ${e.message}") }
            }
          }
        }, Modifier.fillMaxWidth(), enabled = !busy)
      }
      if (pane == "pair") {
        Spacer(Modifier.height(12.dp))
        if (pairObj == null) {
          XMButton("Generate pairing link", {
            val p = Signer.pairingState()
            pairObj = p
            pairStatus = "waiting"
            scope.launch(Dispatchers.IO) {
              try {
                Signer.pairNewSigner(p) { st ->
                  AppScope.launch(Dispatchers.Main) {
                    if (pairStatus != "paired") pairStatus = if (st == "approved") "approved" else "waiting"
                  }
                }
                withContext(Dispatchers.Main) { pairStatus = "paired" }
              } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                  if (pairStatus != "paired") pairStatus = "error:" + (e.message ?: "failed")
                }
              }
            }
          }, Modifier.fillMaxWidth())
        } else {
          KeyBox(pairObj!!.uri)
          Spacer(Modifier.height(10.dp))
          Row(Modifier.fillMaxWidth()) {
            XMButton("Copy link", { Acts.copyText(pairObj!!.uri, "Pairing link copied") }, kind = BtnKind.Line, small = true)
            Spacer(Modifier.width(8.dp))
            XMButton("Open in Amber", {
              val ok = Platform.openAmber(pairObj!!.uri)
              if (!ok) Nav.toast("Amber not installed — copy the link instead")
            }, kind = BtnKind.Line, small = true)
            Spacer(Modifier.width(8.dp))
            XMButton("Cancel", { pane = ""; pairObj = null; pairStatus = "" }, kind = BtnKind.Ghost, small = true)
          }
          Spacer(Modifier.height(10.dp))
          Text(when {
            pairStatus == "paired" -> "Paired ✓"
            pairStatus == "approved" -> "Approved — finishing…"
            pairStatus.startsWith("error:") -> pairStatus.removePrefix("error:")
            else -> "Waiting for approval in your signer app…"
          }, fontSize = 13.sp, color = if (pairStatus.startsWith("error:")) LocalXM.current.live else LocalXM.current.text2)
          if (pairStatus == "paired") {
            Spacer(Modifier.height(8.dp))
            XMButton("Enter XM Arcade", {
              Me.refresh()
              try { scope.launch(Dispatchers.IO) { try { Repo.fetchContacts() } catch (_: Exception) {} } } catch (_: Exception) {}
              Nav.reset(S.startingPane); S.section = S.startingPane; S.save()
              Nav.toast("Paired with Amber")
            }, Modifier.fillMaxWidth())
          }
        }
      }
      Spacer(Modifier.height(18.dp))
      Text("How pairing works", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(6.dp))
      Text("1. XM Arcade creates a one-time pairing link.\n2. Approve it in Amber (or any NIP-46 signer).\n3. This device can request signatures — the key never leaves the signer.",
        fontSize = 13.5.sp, color = LocalXM.current.text2, modifier = Modifier.padding(bottom = 30.dp))
    }
  }
}
