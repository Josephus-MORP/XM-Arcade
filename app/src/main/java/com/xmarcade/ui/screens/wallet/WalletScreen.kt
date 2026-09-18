package com.xmarcade.ui.screens.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.xmarcade.ui.components.XmAvatar
import com.xmarcade.ui.theme.*

@Composable
fun WalletScreen(navController: NavController, vm: WalletViewModel = hiltViewModel()) {
    val state by vm.walletState.collectAsState()
    var showNwcDialog by remember { mutableStateOf(false) }
    var showXmrDialog by remember { mutableStateOf(false) }
    var showTxLog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(XmNavy)) {
        // Header: O Wallet | pay ↑ | receive ↓ | + add/create wallet
        Row(
            modifier=Modifier.fillMaxWidth().background(XmNavy).padding(8.dp),
            verticalAlignment=Alignment.CenterVertically
        ) {
            XmAvatar(imageUrl=null, size=34, onClick={ navController.navigate("profile") })
            Spacer(Modifier.width(8.dp))
            Text("Wallet", fontWeight=FontWeight.Bold, color=TextPrimary, fontSize=16.sp)
            Spacer(Modifier.weight(1f))
            Surface(shape=RoundedCornerShape(8.dp), color=MoneroOrange, modifier=Modifier.clickable{ vm.pay() }) {
                Row(Modifier.padding(horizontal=10.dp, vertical=6.dp), verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Default.ArrowUpward, contentDescription="pay", tint=Color.White, modifier=Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("pay", color=Color.White, fontSize=12.sp, fontWeight=FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(6.dp))
            Surface(shape=RoundedCornerShape(8.dp), color=XmCyan.copy(alpha=0.9f), modifier=Modifier.clickable{ vm.receive() }) {
                Row(Modifier.padding(horizontal=10.dp, vertical=6.dp), verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Default.ArrowDownward, contentDescription="receive", tint=XmNavy, modifier=Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("receive", color=XmNavy, fontSize=12.sp, fontWeight=FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(6.dp))
            IconButton(onClick={ showNwcDialog=true }, modifier=Modifier.size(34.dp).background(MoneroOrange.copy(alpha=0.2f), RoundedCornerShape(8.dp))) {
                Icon(Icons.Default.Add, contentDescription="add/create wallet", tint=MoneroOrange, modifier=Modifier.size(18.dp))
            }
        }
        Divider(color=DividerDark)
        // XMR card – M 0.02  $X.XX  etc.
        WalletCard(
            icon = "M",
            iconColor = MoneroOrange,
            amount = "0.02",
            subAmount = "$${String.format("%.2f", state.xmrBalance*142.0)}",
            label = "XMR",
            onClick = { showTxLog=true },
            onLongClick = { showXmrDialog=true }
        )
        Divider(color=DividerDark)
        // Sats card – bolt 27,000  $X.XX
        WalletCard(
            icon = "⚡",
            iconColor = Color(0xFFFFC107),
            amount = "%,d".format(state.satsBalance),
            subAmount = state.satsBalanceLocal,
            label = "sats",
            onClick = { showTxLog=true },
            onLongClick = { showNwcDialog=true }
        )
        Spacer(Modifier.height(16.dp))
        // Details
        Card(colors=CardDefaults.cardColors(containerColor=XmCard), shape=RoundedCornerShape(14.dp), modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("How wallets work", fontWeight=FontWeight.Bold, color=TextPrimary, fontSize=13.sp)
                Text("• Sats: NWC connection (like Ditto/Amethyst) OR native wallet via Cake Wallet SDK\n• XMR: External wallet linking (Amethyst payment targets) OR native Cake Wallet\n• New accounts: native wallets created automatically\n• Tap cards to see most recent transactions log", fontSize=11.sp, color=TextSecondary, lineHeight=14.sp)
                if (state.nwcUri!=null) Text("NWC: ${state.nwcUri?.take(28)}...", fontSize=10.sp, color=XmCyan)
                if (state.xmrWalletInfo!=null) Text("XMR ext: ${state.xmrWalletInfo?.take(20)}...", fontSize=10.sp, color=MoneroOrange)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={ showNwcDialog=true }, shape=RoundedCornerShape(8.dp)) { Text("Link NWC", fontSize=11.sp) }
                    OutlinedButton(onClick={ showXmrDialog=true }, shape=RoundedCornerShape(8.dp)) { Text("Link XMR", fontSize=11.sp) }
                }
            }
        }
    }

    if (showNwcDialog) {
        var uri by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest={ showNwcDialog=false },
            title={ Text("Add Nostr Wallet Connect", color=TextPrimary) },
            text={
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Paste NWC URI (nostr+walletconnect://...). Reference Ditto/Amethyst.", fontSize=12.sp, color=TextSecondary)
                    OutlinedTextField(value=uri, onValueChange={uri=it}, placeholder={Text("nostr+walletconnect://")}, modifier=Modifier.fillMaxWidth(), shape=RoundedCornerShape(10.dp))
                    Text("Or choose 'Use native wallet' – Cake Wallet construction.", fontSize=11.sp, color=TextMuted)
                }
            },
            confirmButton={
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick={ vm.linkNwc(uri); showNwcDialog=false }, colors=ButtonDefaults.buttonColors(containerColor=MoneroOrange)) { Text("Link") }
                    OutlinedButton(onClick={ vm.createNativeSats(); showNwcDialog=false }) { Text("Use native") }
                }
            },
            dismissButton={ TextButton(onClick={ showNwcDialog=false }){ Text("Cancel") } },
            containerColor=XmSurface
        )
    }
    if (showXmrDialog) {
        var info by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest={ showXmrDialog=false },
            title={ Text("Link External XMR Wallet", color=TextPrimary) },
            text={
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Reference Amethyst payment targets / 'Use external wallet'. Paste view key, address, or seed. Leave blank to use native Cake XMR wallet.", fontSize=12.sp, color=TextSecondary)
                    OutlinedTextField(value=info, onValueChange={info=it}, placeholder={Text("44A... or seed words")}, modifier=Modifier.fillMaxWidth(), shape=RoundedCornerShape(10.dp))
                }
            },
            confirmButton={
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick={ if(info.isBlank()) vm.createNativeXmr() else vm.linkXmr(info); showXmrDialog=false }, colors=ButtonDefaults.buttonColors(containerColor=MoneroOrange)) { Text(if(info.isBlank()) "Use native" else "Link") }
                }
            },
            dismissButton={ TextButton(onClick={ showXmrDialog=false }){ Text("Cancel") } },
            containerColor=XmSurface
        )
    }
    if (showTxLog) {
        AlertDialog(
            onDismissRequest={ showTxLog=false },
            title={ Text("Recent transactions", color=TextPrimary) },
            text={
                Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf("Zap ⚡ 21 sats to nostr:p1 – today", "XAP M 0.001 XMR to nostr:p2 – yesterday", "Received ⚡ 100 sats – 2d ago").forEach {
                        Text("• $it", fontSize=12.sp, color=TextSecondary)
                    }
                }
            },
            confirmButton={ TextButton(onClick={ showTxLog=false }){ Text("Close", color=MoneroOrange) } },
            containerColor=XmSurface
        )
    }
}

@Composable
private fun WalletCard(icon: String, iconColor: Color, amount: String, subAmount: String, label: String, onClick:()->Unit, onLongClick:()->Unit) {
    Row(
        modifier=Modifier.fillMaxWidth().background(XmNavy).clickable{ onClick() }.padding(horizontal=16.dp, vertical=18.dp),
        verticalAlignment=Alignment.CenterVertically
    ) {
        Text(icon, color=iconColor, fontSize=36.sp, fontWeight=FontWeight.Black, modifier=Modifier.width(50.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(amount, color=TextPrimary, fontSize=26.sp, fontWeight=FontWeight.Bold, lineHeight=26.sp)
            Text(subAmount, color=XmCyan, fontSize=13.sp, fontWeight=FontWeight.Medium)
            Text("$label • amount $label / amount local currency", fontSize=10.sp, color=TextMuted)
        }
        Icon(Icons.Default.ChevronRight, contentDescription="to see most recent transactions log", tint=MoneroOrange, modifier=Modifier.size(20.dp))
    }
}
