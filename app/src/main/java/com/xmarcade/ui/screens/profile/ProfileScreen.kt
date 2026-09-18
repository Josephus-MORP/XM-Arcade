package com.xmarcade.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.xmarcade.ui.components.XmAvatar
import com.xmarcade.ui.theme.*

@Composable
fun ProfileScreen(navController: NavController, vm: ProfileViewModel = hiltViewModel()) {
    val profile by vm.profile.collectAsState()
    val notes by vm.notes.collectAsState()
    val isOwnProfile by vm.isOwnProfile.collectAsState()
    var showEdit by remember { mutableStateOf(false) }
    var showZapXap by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().background(XmNavy)) {
        item {
            // Banner
            Box(
                modifier=Modifier.fillMaxWidth().height(140.dp).background(XmCard),
                contentAlignment=Alignment.BottomStart
            ) {
                Box(Modifier.fillMaxSize().background(XmCardLight))
                // Edit banner hint
                if (isOwnProfile) {
                    Surface(shape=RoundedCornerShape(8.dp), color=Color.Black.copy(alpha=0.45f), modifier=Modifier.align(Alignment.TopEnd).padding(10.dp).clickable{ showEdit=true }) {
                        Row(Modifier.padding(horizontal=8.dp, vertical=4.dp), verticalAlignment=Alignment.CenterVertically) {
                            Icon(Icons.Default.Edit, contentDescription=null, tint=Color.White, modifier=Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Edit banner", fontSize=10.sp, color=Color.White)
                        }
                    }
                }
            }
            // Avatar overlap banner
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp).offset(y=(-30).dp), verticalAlignment=Alignment.Bottom) {
                Box(
                    modifier=Modifier.size(86.dp).clip(CircleShape).background(XmSurface).border(3.dp, MoneroOrange, CircleShape).clickable{
                        if (isOwnProfile) showEdit=true else showZapXap=true
                    },
                    contentAlignment=Alignment.Center
                ) {
                    // Load profile picture or random default
                    XmAvatar(imageUrl=profile.picture, size=86, onClick={ if (!isOwnProfile) showZapXap=true })
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).padding(bottom=6.dp)) {
                    Text(profile.displayName.ifBlank{profile.name.ifBlank{"anon"}}, fontWeight=FontWeight.Bold, color=TextPrimary, fontSize=16.sp)
                    Text(profile.nip05.ifBlank{"npub1...demo"}, fontSize=11.sp, color=TextMuted)
                }
                if (isOwnProfile) {
                    Button(onClick={ showEdit=true }, colors=ButtonDefaults.buttonColors(containerColor=MoneroOrange), shape=RoundedCornerShape(20.dp), contentPadding=PaddingValues(horizontal=14.dp, vertical=6.dp)) {
                        Text("Edit", fontSize=12.sp, fontWeight=FontWeight.Bold, color=Color.White)
                    }
                } else {
                    // Chips for zap sats or xap xmr to that user via your wallet
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        Button(onClick={ showZapXap=true }, colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFFFC107)), shape=RoundedCornerShape(20.dp), contentPadding=PaddingValues(horizontal=12.dp, vertical=4.dp)) {
                            Icon(Icons.Default.Bolt, contentDescription=null, tint=XmNavy, modifier=Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Zap", fontSize=11.sp, color=XmNavy, fontWeight=FontWeight.Bold)
                        }
                        Button(onClick={ showZapXap=true }, colors=ButtonDefaults.buttonColors(containerColor=MoneroOrange), shape=RoundedCornerShape(20.dp), contentPadding=PaddingValues(horizontal=12.dp, vertical=4.dp)) {
                            Text("M", fontWeight=FontWeight.Black, fontSize=12.sp, color=Color.White)
                            Spacer(Modifier.width(4.dp))
                            Text("XAP", fontSize=11.sp, color=Color.White)
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal=16.dp).offset(y=(-14).dp)) {
                Text(profile.about.ifBlank{"Nostr explorer • Building on XM Arcade. #music #fps #nature"}, color=TextSecondary, fontSize=13.sp, lineHeight=16.sp)
                Spacer(Modifier.height(10.dp))
                if (isOwnProfile) {
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Button(onClick={ showNoteDialog=true }, colors=ButtonDefaults.buttonColors(containerColor=XmCard), shape=RoundedCornerShape(10.dp), border=androidx.compose.foundation.BorderStroke(1.dp, DividerDark)) {
                            Icon(Icons.Default.Edit, contentDescription=null, tint=TextPrimary, modifier=Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Post a new note", fontSize=12.sp, color=TextPrimary)
                        }
                    }
                }
                Divider(color=DividerDark, modifier=Modifier.padding(vertical=12.dp))
                Text("Notes", fontWeight=FontWeight.Bold, color=TextPrimary, fontSize=14.sp)
                Text("Tap to respond or repost your own notes (and comments thereon) or others", fontSize=11.sp, color=TextMuted)
            }
        }
        items(notes) { note ->
            Card(colors=CardDefaults.cardColors(containerColor=XmCard), shape=RoundedCornerShape(12.dp), modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp, vertical=6.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        XmAvatar(imageUrl=profile.picture, size=28)
                        Spacer(Modifier.width(8.dp))
                        Text(profile.displayName.ifBlank{"You"}, fontWeight=FontWeight.Bold, fontSize=12.sp, color=TextPrimary)
                        Spacer(Modifier.width(6.dp))
                        Text("· ${note.createdAt}", fontSize=10.sp, color=TextMuted)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(note.content, color=TextPrimary, fontSize=13.sp, lineHeight=16.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(16.dp), verticalAlignment=Alignment.CenterVertically) {
                        Row(Modifier.clickable{ vm.replyTo(note.id) }, verticalAlignment=Alignment.CenterVertically) {
                            Icon(Icons.Default.ChatBubbleOutline, contentDescription="respond", tint=TextMuted, modifier=Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reply", fontSize=11.sp, color=TextMuted)
                        }
                        Row(Modifier.clickable{ vm.repost(note.id) }, verticalAlignment=Alignment.CenterVertically) {
                            Icon(Icons.Default.Repeat, contentDescription="repost", tint=TextMuted, modifier=Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Repost", fontSize=11.sp, color=TextMuted)
                        }
                        Row(Modifier.clickable{}, verticalAlignment=Alignment.CenterVertically) {
                            Icon(Icons.Default.FavoriteBorder, contentDescription=null, tint=TextMuted, modifier=Modifier.size(16.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.Bolt, contentDescription="zap", tint=Color(0xFFFFC107), modifier=Modifier.size(16.dp))
                        Text("M", color=MoneroOrange, fontWeight=FontWeight.Black, fontSize=12.sp)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }

    if (showEdit) {
        EditProfileDialog(
            currentName = profile.displayName,
            currentAbout = profile.about,
            onDismiss = { showEdit=false },
            onSave = { name, about, picChoice -> vm.updateProfile(name, about, picChoice); showEdit=false }
        )
    }
    if (showZapXap) {
        AlertDialog(
            onDismissRequest={ showZapXap=false },
            title={ Text("Zap / XAP", color=TextPrimary) },
            text={
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Send sats or XMR to this user via your wallet.", fontSize=12.sp, color=TextSecondary)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()) {
                        Button(onClick={ vm.zapUser(); showZapXap=false }, colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFFFC107)), modifier=Modifier.weight(1f)) { Text("Zap ⚡", color=XmNavy, fontWeight=FontWeight.Bold) }
                        Button(onClick={ vm.xapUser(); showZapXap=false }, colors=ButtonDefaults.buttonColors(containerColor=MoneroOrange), modifier=Modifier.weight(1f)) { Text("XAP M", color=Color.White, fontWeight=FontWeight.Bold) }
                    }
                }
            },
            confirmButton={ TextButton(onClick={ showZapXap=false }){ Text("Close", color=MoneroOrange) } },
            containerColor=XmSurface
        )
    }
    if (showNoteDialog) {
        var content by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest={ showNoteDialog=false },
            title={ Text("New note", color=TextPrimary) },
            text={
                OutlinedTextField(value=content, onValueChange={content=it}, placeholder={Text("What's on your mind?")}, modifier=Modifier.fillMaxWidth(), minLines=3, shape=RoundedCornerShape(12.dp))
            },
            confirmButton={ Button(onClick={ vm.postNote(content); showNoteDialog=false }, colors=ButtonDefaults.buttonColors(containerColor=MoneroOrange)) { Text("Post") } },
            dismissButton={ TextButton(onClick={ showNoteDialog=false }){ Text("Cancel") } },
            containerColor=XmSurface
        )
    }
}

@Composable
private fun EditProfileDialog(currentName: String, currentAbout: String, onDismiss:()->Unit, onSave:(String,String,String)->Unit) {
    var name by remember { mutableStateOf(currentName) }
    var about by remember { mutableStateOf(currentAbout) }
    var selectedPic by remember { mutableStateOf("default_1") }
    AlertDialog(
        onDismissRequest=onDismiss,
        title={ Text("Edit profile", color=TextPrimary) },
        text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value=name, onValueChange={name=it}, label={Text("Display name")}, modifier=Modifier.fillMaxWidth(), shape=RoundedCornerShape(10.dp))
                OutlinedTextField(value=about, onValueChange={about=it}, label={Text("Biography")}, modifier=Modifier.fillMaxWidth(), minLines=2, shape=RoundedCornerShape(10.dp))
                Text("Profile photo – upload new or choose from defaults", fontSize=11.sp, color=TextMuted, fontWeight=FontWeight.Bold)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()) {
                    repeat(4) { idx ->
                        Box(
                            modifier=Modifier.size(56.dp).clip(CircleShape).background(if(selectedPic=="default_$idx") MoneroOrange else DividerDark).border(2.dp, if(selectedPic=="default_$idx") MoneroOrange else DividerDark, CircleShape).clickable{ selectedPic="default_$idx" },
                            contentAlignment=Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription=null, tint=Color.White.copy(alpha=0.7f))
                        }
                    }
                }
                OutlinedButton(onClick={}, modifier=Modifier.fillMaxWidth(), shape=RoundedCornerShape(10.dp)) {
                    Icon(Icons.Default.PhotoCamera, contentDescription=null, tint=MoneroOrange)
                    Spacer(Modifier.width(8.dp))
                    Text("Upload new photo")
                }
                OutlinedButton(onClick={}, modifier=Modifier.fillMaxWidth(), shape=RoundedCornerShape(10.dp)) {
                    Icon(Icons.Default.Panorama, contentDescription=null, tint=XmCyan)
                    Spacer(Modifier.width(8.dp))
                    Text("Upload banner")
                }
            }
        },
        confirmButton={ Button(onClick={ onSave(name, about, selectedPic) }, colors=ButtonDefaults.buttonColors(containerColor=MoneroOrange)) { Text("Save") } },
        dismissButton={ TextButton(onClick=onDismiss){ Text("Cancel") } },
        containerColor=XmSurface
    )
}
