package com.xmarcade.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.xmarcade.ui.theme.*

@Composable
fun SettingsScreen(navController: NavController, vm: SettingsViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsState()
    val profilesCount by vm.profilesCount.collectAsState()
    val showNsec by vm.showNsec.collectAsState()

    LazyColumn(
        modifier=Modifier.fillMaxSize().background(XmNavy).padding(horizontal=16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp),
        contentPadding=PaddingValues(vertical=16.dp)
    ) {
        item {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(MoneroOrange), contentAlignment=Alignment.Center) {
                    Icon(Icons.Default.Settings, contentDescription=null, tint=Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Settings", fontWeight=FontWeight.Black, color=TextPrimary, fontSize=22.sp)
                    Text("Personalize XM Arcade", fontSize=11.sp, color=TextMuted)
                }
            }
        }
        item {
            SettingsSection("Presets") {
                PresetRow("Zap preset", "${settings.zapPresetSats} sats", Icons.Default.Bolt, Color(0xFFFFC107)) {
                    var tmp by remember { mutableStateOf(settings.zapPresetSats.toString()) }
                    AlertDialog(onDismissRequest={}, title={Text("Zap preset")}, text={ OutlinedTextField(value=tmp, onValueChange={tmp=it}, label={Text("sats")}) }, confirmButton={ Button(onClick={ vm.updateZap(tmp.toLongOrNull()?:21) }){ Text("Save") } }, dismissButton={}, containerColor=XmSurface)
                }
                PresetRow("XAP preset", "${settings.xapPresetAtomic/1_000_000_000.0} XMR", Icons.Default.CurrencyExchange, MoneroOrange) {
                    // simplified
                    vm.updateXap(settings.xapPresetAtomic*2)
                }
            }
        }
        item {
            SettingsSection("Appearance") {
                Row(verticalAlignment=Alignment.CenterVertically, modifier=Modifier.fillMaxWidth()) {
                    Text("Dark mode", color=TextPrimary, fontSize=14.sp, modifier=Modifier.weight(1f))
                    Switch(checked=settings.isDarkMode, onCheckedChange={ vm.toggleDarkMode() }, colors=SwitchDefaults.colors(checkedThumbColor=MoneroOrange, checkedTrackColor=MoneroOrange.copy(alpha=0.35f)))
                }
                Spacer(Modifier.height(12.dp))
                Text("Secondary color", color=TextSecondary, fontSize=12.sp, fontWeight=FontWeight.Bold)
                Slider(
                    value=settings.secondaryColorHue,
                    onValueChange={ vm.updateHue(it) },
                    valueRange=0f..360f,
                    colors=SliderDefaults.colors(activeTrackColor=Color.hsv(settings.secondaryColorHue,0.85f,1f), thumbColor=Color.hsv(settings.secondaryColorHue,0.85f,1f))
                )
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf(24f to "Orange", 190f to "Cyan", 140f to "Green", 270f to "Purple").forEach { (h,l) ->
                        Box(Modifier.size(36.dp).clip(CircleShape).background(Color.hsv(h,0.85f,1f)).clickable{ vm.updateHue(h) }.then(if(settings.secondaryColorHue==h) Modifier else Modifier))
                    }
                }
            }
        }
        item {
            SettingsSection("Hashtag follows") {
                Text("Tap to add/remove. Defaults can be changed.", fontSize=11.sp, color=TextMuted)
                Spacer(Modifier.height(8.dp))
                // chips wrap – using FlowRow via simple Column+Rows
                val tags = settings.followedTags
                // chunked
                tags.chunked(3).forEach { row ->
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth().padding(vertical=4.dp)) {
                        row.forEach { tag ->
                            Surface(shape=RoundedCornerShape(16.dp), color=MoneroOrange.copy(alpha=0.18f), border=androidx.compose.foundation.BorderStroke(1.dp, MoneroOrange), modifier=Modifier.clickable{ vm.removeTag(tag) }) {
                                Row(Modifier.padding(horizontal=10.dp, vertical=6.dp), verticalAlignment=Alignment.CenterVertically) {
                                    Text("#$tag", fontSize=12.sp, color=TextPrimary)
                                    Spacer(Modifier.width(6.dp))
                                    Icon(Icons.Default.Close, contentDescription="remove", tint=TextMuted, modifier=Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                var newTag by remember { mutableStateOf("") }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    OutlinedTextField(value=newTag, onValueChange={newTag=it}, placeholder={Text("#tag")}, modifier=Modifier.weight(1f), shape=RoundedCornerShape(10.dp))
                    Spacer(Modifier.width(8.dp))
                    Button(onClick={ if(newTag.isNotBlank()){ vm.addTag(newTag.trim().removePrefix("#")); newTag="" } }, colors=ButtonDefaults.buttonColors(containerColor=MoneroOrange), shape=RoundedCornerShape(10.dp)) { Text("Add") }
                }
            }
        }
        item {
            SettingsSection("Relays & Blossom") {
                EditableList(title="Relays", items=settings.relays, onAdd={ vm.addRelay(it) }, onRemove={ vm.removeRelay(it) }, hint="wss://...")
                Spacer(Modifier.height(10.dp))
                EditableList(title="Blossom servers", items=settings.blossomServers, onAdd={ vm.addBlossom(it) }, onRemove={ vm.removeBlossom(it) }, hint="https://...")
                Text("All defaults can be removed. No relay or blossom server is forced.", fontSize=11.sp, color=TextMuted, modifier=Modifier.padding(top=8.dp))
            }
        }
        item {
            SettingsSection("Accounts & Security") {
                Button(onClick={ vm.toggleShowNsec() }, colors=ButtonDefaults.buttonColors(containerColor=XmCard), shape=RoundedCornerShape(10.dp), border=androidx.compose.foundation.BorderStroke(1.dp, DividerDark), modifier=Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Key, contentDescription=null, tint=MoneroOrange)
                    Spacer(Modifier.width(8.dp))
                    Text(if(showNsec) "Hide nsec" else "Backup nsec (show)", color=TextPrimary, fontWeight=FontWeight.Bold)
                }
                if (showNsec) {
                    Surface(color=XmNavy, shape=RoundedCornerShape(10.dp), modifier=Modifier.fillMaxWidth().padding(top=8.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(vm.nsecPreview, fontSize=11.sp, color=MoneroOrange, fontWeight=FontWeight.Bold)
                            Text("Never share this. Anyone with it controls your identity.", fontSize=11.sp, color=Color(0xFFFF5252), modifier=Modifier.padding(top=6.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick={ vm.loginSecondAccount() }, colors=ButtonDefaults.buttonColors(containerColor=XmCard), shape=RoundedCornerShape(10.dp), border=androidx.compose.foundation.BorderStroke(1.dp, XmCyan), modifier=Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.SwitchAccount, contentDescription=null, tint=XmCyan)
                    Spacer(Modifier.width(8.dp))
                    Text("Log in with second account", color=XmCyan, fontSize=13.sp)
                }
                if (profilesCount>1) {
                    Text("Switch profiles button appears to the right of 'Profile' in drawer ( ${profilesCount} signed in )", fontSize=11.sp, color=WispOnline, modifier=Modifier.padding(top=6.dp))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick={ vm.logout() }, modifier=Modifier.fillMaxWidth(), shape=RoundedCornerShape(10.dp)) { Text("Log out", color=TextPrimary) }
                TextButton(onClick={ vm.deleteAccount() }, modifier=Modifier.fillMaxWidth()) { Text("Delete account", color=Color(0xFFFF5252)) }
            }
        }
        item {
            SettingsSection("Wallets") {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()) {
                    Button(onClick={ vm.deleteSatsWallet() }, colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFFFC107)), modifier=Modifier.weight(1f)) { Text("Delete sats wallet", fontSize=11.sp, color=XmNavy) }
                    Button(onClick={ vm.deleteXmrWallet() }, colors=ButtonDefaults.buttonColors(containerColor=MoneroOrange), modifier=Modifier.weight(1f)) { Text("Delete XMR wallet", fontSize=11.sp, color=Color.White) }
                }
                OutlinedButton(onClick={ vm.deleteBothWallets() }, modifier=Modifier.fillMaxWidth(), shape=RoundedCornerShape(10.dp)) { Text("Delete both wallets", color=Color(0xFFFF5252)) }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.()->Unit) {
    Surface(shape=RoundedCornerShape(14.dp), color=XmCard, modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight=FontWeight.Bold, color=TextPrimary, fontSize=13.sp)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun PresetRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable{ onClick() }.padding(vertical=6.dp), verticalAlignment=Alignment.CenterVertically) {
        Icon(icon, contentDescription=null, tint=tint, modifier=Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color=TextPrimary, fontSize=13.sp, modifier=Modifier.weight(1f))
        Text(value, color=TextSecondary, fontSize=13.sp, fontWeight=FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.ChevronRight, contentDescription=null, tint=TextMuted, modifier=Modifier.size(16.dp))
    }
}

@Composable
private fun EditableList(title: String, items: List<String>, onAdd:(String)->Unit, onRemove:(String)->Unit, hint:String) {
    Text(title, fontSize=12.sp, color=TextSecondary, fontWeight=FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            Row(Modifier.fillMaxWidth().background(XmNavy, RoundedCornerShape(8.dp)).padding(horizontal=10.dp, vertical=8.dp), verticalAlignment=Alignment.CenterVertically) {
                Text(item, fontSize=12.sp, color=TextPrimary, modifier=Modifier.weight(1f))
                Icon(Icons.Default.Close, contentDescription="remove", tint=Color(0xFFFF5252), modifier=Modifier.size(16.dp).clickable{ onRemove(item) })
            }
        }
        var input by remember { mutableStateOf("") }
        Row(verticalAlignment=Alignment.CenterVertically) {
            OutlinedTextField(value=input, onValueChange={input=it}, placeholder={Text(hint, fontSize=12.sp)}, modifier=Modifier.weight(1f), shape=RoundedCornerShape(10.dp))
            Spacer(Modifier.width(8.dp))
            IconButton(onClick={ if(input.isNotBlank()){ onAdd(input.trim()); input="" } }, modifier=Modifier.size(40.dp).background(MoneroOrange, CircleShape)) { Icon(Icons.Default.Add, contentDescription="add", tint=Color.White, modifier=Modifier.size(18.dp)) }
        }
    }
}
