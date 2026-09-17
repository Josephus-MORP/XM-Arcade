package com.xmarcade.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xmarcade.app.core.Nwc
import com.xmarcade.app.core.Xmr
import com.xmarcade.app.core.displayName
import com.xmarcade.app.core.money
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WalletScreen() {
  val scope = rememberCoroutineScope()
  var satsBal by remember { mutableStateOf<Long?>(null) }
  var satsState by remember { mutableStateOf("idle") }
  var xmrAddr by remember { mutableStateOf("") }
  var xmrBal by remember { mutableStateOf<Long?>(null) }
  var xmrState by remember { mutableStateOf("idle") }
  var hideXmr by remember { mutableStateOf(false) }

  fun load() {
    satsState = "checking"; xmrState = "checking"
    scope.launch(Dispatchers.IO) {
      if (Nwc.connected()) {
        try {
          val ms = Nwc.getBalanceMsats()
          satsBal = ms / 1000
          withContext(Dispatchers.Main) { satsState = "ok" }
        } catch (_: Exception) {
          withContext(Dispatchers.Main) { satsState = "down" }
        }
      } else {
        withContext(Dispatchers.Main) { satsState = "idle" }
      }
      if (Xmr.mode == "external") {
        withContext(Dispatchers.Main) { xmrState = "external" }
      } else {
        try {
          Xmr.status()
          xmrAddr = Xmr.address().optString("address", "")
          xmrBal = Xmr.balance().optLong("balance", 0L)
          withContext(Dispatchers.Main) { xmrState = "ok" }
        } catch (_: Exception) {
          withContext(Dispatchers.Main) { xmrState = "down" }
        }
      }
    }
  }
  LaunchedEffect(Unit) { load() }

  Column(Modifier.fillMaxSize()) {
    TopBar("Wallet", displayName(Me.profile), Me.profile.picture, onDd = { Nav.ddOpen = true })
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Hot wallet", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.width(8.dp))
        ModeBadge(xmrState == "ok" || satsState == "ok" || xmrState == "external")
        Spacer(Modifier.weight(1f))
        BareIconBtn("refresh", "Refresh", { load() }, size = 38.dp)
      }
      Spacer(Modifier.height(8.dp))
      Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
        .background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          CoinMark("xmr", 40.dp)
          Spacer(Modifier.width(12.dp))
          Column(Modifier.weight(1f)) {
            Text("Monero", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(when (xmrState) {
              "ok" -> "connected · ${Xmr.cfg.url}"
              "external" -> "external wallet mode"
              "checking" -> "checking…"
              else -> "not connected"
            }, fontSize = 12.sp, color = LocalXM.current.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          if (xmrState != "ok" && xmrState != "external") {
            XMButton("Connect", { Nav.openSheet(Sheet("xmr")) }, small = true)
          }
        }
        Spacer(Modifier.height(12.dp))
        Text(if (hideXmr) "••••••" else String.format(Locale.US, "%.4f XMR", Xmr.piconeroToXmr(xmrBal ?: 0L)),
          style = MaterialTheme.typography.displaySmall)
        if (xmrAddr.isNotEmpty()) {
          Spacer(Modifier.height(4.dp))
          Text(xmrAddr.take(18) + "…" + xmrAddr.takeLast(8), fontFamily = Mono, fontSize = 12.sp,
            color = LocalXM.current.text2)
        }
        Spacer(Modifier.height(12.dp))
        Row {
          XMButton("Receive", { Nav.openSheet(Sheet("receive", mapOf("kind" to "xmr"))) },
            Modifier.weight(1f), kind = BtnKind.Line, small = true, icon = "qr")
          Spacer(Modifier.width(8.dp))
          XMButton("Send", { Nav.openSheet(Sheet("pay", mapOf("kind" to "xmr"))) },
            Modifier.weight(1f), kind = BtnKind.Line, small = true, icon = "send")
          Spacer(Modifier.width(8.dp))
          XMButton(if (hideXmr) "Show" else "Hide", { hideXmr = !hideXmr },
            Modifier.weight(1f), kind = BtnKind.Ghost, small = true, icon = "eye")
        }
      }
      Spacer(Modifier.height(12.dp))
      Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
        .background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          CoinMark("sats", 40.dp)
          Spacer(Modifier.width(12.dp))
          Column(Modifier.weight(1f)) {
            Text("Bitcoin sats", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(when (satsState) {
              "ok" -> "NWC · " + (Nwc.conn?.relay?.removePrefix("wss://")?.substringBefore("/") ?: "wallet")
              "checking" -> "checking…"
              "down" -> "wallet unreachable"
              else -> "no wallet connected"
            }, fontSize = 12.sp, color = LocalXM.current.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          if (satsState != "ok") {
            XMButton("Connect", { Nav.openSheet(Sheet("nwc")) }, small = true)
          }
        }
        Spacer(Modifier.height(12.dp))
        Text(if (satsBal == null) "—" else money(satsBal!!) + " sats",
          style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(12.dp))
        Row {
          XMButton("Receive", { Nav.openSheet(Sheet("receive", mapOf("kind" to "sats"))) },
            Modifier.weight(1f), kind = BtnKind.Line, small = true, icon = "qr")
          Spacer(Modifier.width(8.dp))
          XMButton("Pay", { Nav.openSheet(Sheet("pay", mapOf("kind" to "sats"))) },
            Modifier.weight(1f), kind = BtnKind.Line, small = true, icon = "zap")
        }
        if (Nwc.connected()) {
          Spacer(Modifier.height(8.dp))
          XMButton("Disconnect NWC", {
            Nwc.disconnect(); satsBal = null; satsState = "idle"
            Nav.toast("NWC disconnected")
          }, Modifier.fillMaxWidth(), kind = BtnKind.Ghost, small = true)
        }
      }
      Spacer(Modifier.height(14.dp))
      Text("XM Arcade never holds your keys. Sats move through your NWC wallet; XMR through your node or external wallet.",
        fontSize = 12.5.sp, color = LocalXM.current.text3, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 30.dp))
    }
  }
}
