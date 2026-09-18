package com.xmarcade.ui.components

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.xmarcade.ui.navigation.Screen
import com.xmarcade.ui.theme.*

data class NavItem(val label: String, val icon: ImageVector, val route: String)

val bottomNavItems = listOf(
    NavItem("Shorts", Icons.Default.PlayCircle, Screen.Shorts.route),
    NavItem("Mini Apps", Icons.Default.Apps, Screen.MiniApps.route),
    NavItem("Music", Icons.Default.MusicNote, Screen.Music.route),
    NavItem("Chats", Icons.Default.Chat, Screen.Chats.route),
    NavItem("Wallet", Icons.Default.AccountBalanceWallet, Screen.Wallet.route),
)

@Composable
fun AppScaffold(
    navController: NavController,
    currentRoute: String,
    onNavigate: (String)->Unit,
    showNowPlaying: Boolean = false,
    nowPlayingContent: @Composable ()->Unit = {},
    content: @Composable ()->Unit
) {
    var drawerOpen by remember { mutableStateOf(false) }
    // Drawer state
    Box(Modifier.fillMaxSize().background(XmNavy)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { content() }
            if (showNowPlaying) { nowPlayingContent() }
            BottomBar(currentRoute, onNavigate)
        }
        if (drawerOpen) {
            // Scrim
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.45f)).clickable { drawerOpen=false })
            XmDrawer(
                onClose = { drawerOpen=false },
                onNavigate = { route -> drawerOpen=false; onNavigate(route) },
                currentRoute = currentRoute
            )
        }
    }
}

@Composable
fun BottomBar(currentRoute: String, onNavigate: (String)->Unit) {
    NavigationBar(containerColor = XmSurface, tonalElevation = 8.dp) {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                icon = { Icon(item.icon, contentDescription = item.label, tint = if (selected) MoneroOrange else TextSecondary) },
                label = { Text(item.label, fontSize = 10.sp, color = if (selected) MoneroOrange else TextSecondary) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = MoneroOrange.copy(alpha=0.15f))
            )
        }
    }
}

@Composable
fun XmDrawer(onClose: ()->Unit, onNavigate: (String)->Unit, currentRoute: String) {
    // Mirrors reference sketch dropdown: Profile | shorts | mini apps | music | chats | wallet | settings
    // Includes switch profile button if >1 profile
    Column(
        modifier = Modifier
            .width(260.dp)
            .fillMaxHeight()
            .background(XmSurface)
            .padding(vertical = 20.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).background(XmCard), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Profile", fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("npub1...demo", fontSize=11.sp, color = TextMuted)
            }
            // Switch profiles button – appears if >1 profile signed in
            // Logic handled via PreferencesManager profilesFlow; show conditionally
            // For demo always show subtle hint
            // TextButton(onClick={}) { Text("switch", fontSize=10.sp, color=MoneroOrange) }
        }
        Divider(color = DividerDark, modifier = Modifier.padding(vertical=16.dp))
        DrawerItem(Icons.Default.Person, "Profile", currentRoute==Screen.Profile.route) { onNavigate(Screen.Profile.route) }
        DrawerItem(Icons.Default.PlayCircle, "Shorts", currentRoute==Screen.Shorts.route) { onNavigate(Screen.Shorts.route) }
        DrawerItem(Icons.Default.Apps, "Mini Apps", currentRoute==Screen.MiniApps.route) { onNavigate(Screen.MiniApps.route) }
        DrawerItem(Icons.Default.MusicNote, "Music", currentRoute==Screen.Music.route) { onNavigate(Screen.Music.route) }
        DrawerItem(Icons.Default.Chat, "Chats", currentRoute==Screen.Chats.route) { onNavigate(Screen.Chats.route) }
        DrawerItem(Icons.Default.AccountBalanceWallet, "Wallet", currentRoute==Screen.Wallet.route) { onNavigate(Screen.Wallet.route) }
        DrawerItem(Icons.Default.Settings, "Settings", currentRoute==Screen.Settings.route) { onNavigate(Screen.Settings.route) }

        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(WispOnline, androidx.compose.foundation.shape.CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("3 friends online now", fontSize=12.sp, color=WispOnline, modifier=Modifier.clickable{ /* show online list */ })
        }
        // Online now detail: click to see people you follow who are online right now – wisp protocol
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, selected: Boolean, onClick:()->Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable{ onClick() }
            .background(if (selected) MoneroOrange.copy(alpha=0.12f) else Color.Transparent)
            .padding(horizontal=16.dp, vertical=14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription=null, tint= if(selected) MoneroOrange else TextSecondary, modifier=Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color= if(selected) MoneroOrange else TextPrimary, fontWeight= if(selected) FontWeight.Bold else FontWeight.Normal)
        Spacer(Modifier.weight(1f))
        if (selected) Box(Modifier.size(6.dp).background(MoneroOrange, androidx.compose.foundation.shape.CircleShape))
    }
}
