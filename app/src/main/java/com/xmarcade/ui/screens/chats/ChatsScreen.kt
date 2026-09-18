package com.xmarcade.ui.screens.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.xmarcade.data.models.ChatGroup
import com.xmarcade.ui.components.XmAvatar
import com.xmarcade.ui.theme.*

@Composable
fun ChatsScreen(navController: NavController, vm: ChatsViewModel = hiltViewModel()) {
    val groups by vm.groups.collectAsState()
    val showUnreadOnly by vm.showUnreadOnly.collectAsState()
    val isBlockView by vm.isBlockView.collectAsState()

    Column(Modifier.fillMaxSize().background(XmNavy)) {
        // Header: O Chats | Unread | discover new groups? | + to create new channel | grid toggle
        Row(
            modifier=Modifier.fillMaxWidth().background(XmNavy).padding(8.dp),
            verticalAlignment=Alignment.CenterVertically
        ) {
            XmAvatar(imageUrl=null, size=34, onClick={ navController.navigate("profile") })
            Spacer(Modifier.width(8.dp))
            Text("Chats", fontWeight=FontWeight.Bold, color=TextPrimary, fontSize=16.sp)
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected=showUnreadOnly,
                onClick={ vm.toggleUnreadFilter() },
                label={ Text("Unread", fontSize=11.sp) },
                colors=FilterChipDefaults.filterChipColors(selectedContainerColor=MoneroOrange, selectedLabelColor=Color.White)
            )
            Spacer(Modifier.width(6.dp))
            Surface(shape=RoundedCornerShape(8.dp), color=XmCard, modifier=Modifier.clickable{ vm.discoverGroups() }) {
                Row(Modifier.padding(horizontal=8.dp, vertical=6.dp), verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Default.Explore, contentDescription="discover new groups", tint=TextSecondary, modifier=Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Discover", fontSize=10.sp, color=TextSecondary)
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick={ vm.toggleView() }, modifier=Modifier.size(32.dp).background(XmCard, RoundedCornerShape(8.dp))) {
                Icon(if(isBlockView) Icons.Default.ViewList else Icons.Default.GridView, contentDescription="toggle between list vs block view", tint=TextSecondary, modifier=Modifier.size(16.dp))
            }
            Spacer(Modifier.width(6.dp))
            IconButton(onClick={ vm.createGroup() }, modifier=Modifier.size(36.dp).background(MoneroOrange, CircleShape)) {
                Icon(Icons.Default.Add, contentDescription="to create new channel", tint=Color.White)
            }
        }
        Divider(color=DividerDark)
        LazyColumn(modifier=Modifier.fillMaxSize(), verticalArrangement=Arrangement.spacedBy(0.dp)) {
            itemsIndexed(groups) { index, group ->
                ChatGroupRow(
                    group = group,
                    index = index,
                    onGroupClick = { vm.enterChat(group.id) },
                    onChannelClick = { ch -> vm.enterChannel(group.id, ch.id) },
                    onReorder = { from, to -> vm.reorder(from,to) }
                )
                Divider(color=DividerDark)
            }
        }
    }
}

@Composable
private fun ChatGroupRow(
    group: ChatGroup,
    index: Int,
    onGroupClick: ()->Unit,
    onChannelClick: (com.xmarcade.data.models.ChatChannel)->Unit,
    onReorder: (Int,Int)->Unit
) {
    Row(modifier=Modifier.fillMaxWidth().background(if(group.hasMention) XmCard.copy(alpha=0.35f) else Color.Transparent).clickable{ onGroupClick() }.padding(12.dp), verticalAlignment=Alignment.Top) {
        // grab to rearrange list order
        Column(modifier=Modifier.padding(end=8.dp), verticalArrangement=Arrangement.spacedBy(2.dp)) {
            repeat(3){ Row(horizontalArrangement=Arrangement.spacedBy(2.dp)){ repeat(2){ Box(Modifier.size(3.dp).background(TextMuted, CircleShape)) } } }
        }
        XmAvatar(imageUrl=group.avatarUrl, size=40, onClick=onGroupClick)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(group.name, color= if(group.unreadCount>0) MoneroOrange else TextPrimary, fontWeight=FontWeight.Bold, fontSize=14.sp, modifier=Modifier.clickable{ onGroupClick() })
                Spacer(Modifier.width(6.dp))
                if (group.hasMention) {
                    Surface(shape=CircleShape, color=MoneroOrange) { Text("@", color=Color.White, fontSize=10.sp, modifier=Modifier.padding(horizontal=5.dp, vertical=1.dp), fontWeight=FontWeight.Bold) }
                    Spacer(Modifier.width(4.dp))
                    Text("only appears if you have been mentioned", fontSize=9.sp, color=TextMuted)
                }
                Spacer(Modifier.weight(1f))
                if (group.unreadCount>0) {
                    Box(Modifier.size(22.dp).background(MoneroOrange, CircleShape), contentAlignment=Alignment.Center) {
                        Text("${group.unreadCount}", color=Color.White, fontSize=11.sp, fontWeight=FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()) {
                group.channels.forEach { ch ->
                    Text(
                        text="#${ch.name.removePrefix("#")}",
                        color= if(ch.unread>0) XmCyan else TextSecondary,
                        fontSize=12.sp,
                        fontWeight= if(ch.unread>0) FontWeight.Bold else FontWeight.Normal,
                        modifier=Modifier.clickable{ onChannelClick(ch) }.background(if(ch.unread>0) XmCard else Color.Transparent, RoundedCornerShape(6.dp)).padding(horizontal=6.dp, vertical=2.dp)
                    )
                    if (ch.unread>0) {
                        Box(Modifier.size(16.dp).background(MoneroOrange, CircleShape), contentAlignment=Alignment.Center){ Text("${ch.unread}", fontSize=9.sp, color=Color.White) }
                    }
                }
            }
            Text("concord groups • webxdc + call supported", fontSize=9.sp, color=TextMuted, modifier=Modifier.padding(top=4.dp))
        }
        Icon(Icons.Default.MoreVert, contentDescription=null, tint=TextMuted, modifier=Modifier.size(16.dp))
    }
}
