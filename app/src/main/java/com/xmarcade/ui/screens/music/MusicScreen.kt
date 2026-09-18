package com.xmarcade.ui.screens.music

import androidx.compose.foundation.background
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
fun MusicScreen(navController: NavController, vm: MusicViewModel = hiltViewModel()) {
    val tracks by vm.tracks.collectAsState()
    val queue by vm.queue.collectAsState()
    val currentTrack by vm.currentTrack.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val selectedTag by vm.selectedTag.collectAsState()

    Column(Modifier.fillMaxSize().background(XmNavy)) {
        // Header: your pfp | Music | To view and add to queue | online now | upload new
        Row(
            modifier=Modifier.fillMaxWidth().background(XmNavy).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            XmAvatar(imageUrl=null, size=34, onClick={ navController.navigate("profile") })
            Spacer(Modifier.width(8.dp))
            Text("Music", fontWeight=FontWeight.Bold, color=TextPrimary, fontSize=16.sp)
            Spacer(Modifier.width(8.dp))
            Surface(
                shape=RoundedCornerShape(8.dp),
                color = XmCard,
                modifier=Modifier.clickable{ vm.toggleQueueView() }.padding(2.dp)
            ) { Text("View queue", fontSize=10.sp, color=TextSecondary, modifier=Modifier.padding(horizontal=8.dp, vertical=4.dp)) }
            Spacer(Modifier.weight(1f))
            Surface(shape=RoundedCornerShape(16.dp), color=WispOnline.copy(alpha=0.15f)) {
                Row(Modifier.padding(horizontal=8.dp, vertical=4.dp), verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(WispOnline, CircleShape))
                    Spacer(Modifier.width(4.dp))
                    Text("online now", fontSize=10.sp, color=WispOnline, fontWeight=FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick={ vm.showUpload=true }, modifier=Modifier.size(36.dp).background(MoneroOrange, RoundedCornerShape(8.dp))) {
                Icon(Icons.Default.FileUpload, contentDescription="upload new", tint=Color.White, modifier=Modifier.size(18.dp))
            }
        }
        // Category squares + add to queue
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp), horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("#rock","#country","#alternative","#indie").forEach { t ->
                val selected = selectedTag==t
                Surface(
                    shape=RoundedCornerShape(6.dp),
                    color= if(selected) MoneroOrange else XmCard,
                    modifier=Modifier.clickable{ vm.filterByTag(t) }.size(36.dp),
                    border= androidx.compose.foundation.BorderStroke(1.dp, if(selected) MoneroOrange else DividerDark)
                ) { Box(contentAlignment=Alignment.Center){ Text(t.take(2), fontSize=10.sp, color= if(selected) Color.White else TextSecondary, fontWeight=FontWeight.Bold) } }
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.QueueMusic, contentDescription="add to queue", tint=XmCyan, modifier=Modifier.size(22.dp))
        }
        Divider(color=DividerDark, modifier=Modifier.padding(vertical=8.dp))
        // Tracks – vertically scroll
        LazyColumn(modifier=Modifier.weight(1f).padding(horizontal=8.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
            items(tracks) { track ->
                MusicTrackRow(
                    track = track,
                    isCurrent = currentTrack?.id==track.id,
                    isPlaying = isPlaying && currentTrack?.id==track.id,
                    onPlay = { vm.play(track) },
                    onAddToQueue = { vm.addToQueue(track) },
                    onZap = { vm.zap(track.id) },
                    onXap = { vm.xap(track.id) }
                )
                Divider(color=DividerDark.copy(alpha=0.5f))
            }
        }
        // Queue bar / Now Playing (music keeps playing even as you are in any other panes except shorts; keep banner)
        if (currentTrack!=null) {
            Surface(color=XmCard, modifier=Modifier.fillMaxWidth().clickable{}) {
                Row(Modifier.padding(12.dp), verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Default.MusicNote, contentDescription=null, tint=MoneroOrange)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Now Playing", fontSize=10.sp, color=TextMuted, fontWeight=FontWeight.Bold)
                        Text("${currentTrack!!.artist} — ${currentTrack!!.title}", fontSize=12.sp, color=TextPrimary, maxLines=1)
                    }
                    IconButton(onClick={ vm.togglePlayPause() }) {
                        Icon(if(isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription=null, tint=MoneroOrange, modifier=Modifier.size(28.dp))
                    }
                }
            }
        }
        if (queue.isNotEmpty()) {
            Text("Queue: ${queue.size} tracks", fontSize=11.sp, color=TextMuted, modifier=Modifier.padding(8.dp))
        }
    }

    if (vm.showUpload) {
        UploadMusicDialog(onDismiss={ vm.showUpload=false }, onUpload={ title, artist, tags -> vm.uploadMusic(title, artist, tags); vm.showUpload=false })
    }
}

@Composable
private fun MusicTrackRow(
    track: com.xmarcade.data.models.MusicTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: ()->Unit,
    onAddToQueue: ()->Unit,
    onZap: ()->Unit,
    onXap: ()->Unit
) {
    Row(modifier=Modifier.fillMaxWidth().background(if(isCurrent) XmCard.copy(alpha=0.6f) else Color.Transparent, RoundedCornerShape(10.dp)).padding(8.dp), verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(DividerDark), contentAlignment=Alignment.Center) {
            Icon(Icons.Default.Album, contentDescription=null, tint=TextSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(track.artist, fontWeight=FontWeight.Bold, color=TextPrimary, fontSize=13.sp)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.Bolt, contentDescription="zap", tint=Color(0xFFFFC107), modifier=Modifier.size(16.dp).clickable{ onZap() })
                Spacer(Modifier.width(6.dp))
                Text("M", color=MoneroOrange, fontWeight=FontWeight.Black, fontSize=12.sp, modifier=Modifier.clickable{ onXap() })
            }
            Text(track.album, fontSize=11.sp, color=TextMuted)
            Text(track.title, fontSize=13.sp, color=XmCyan, fontWeight=FontWeight.SemiBold)
            // seek bar placeholder
            Row(verticalAlignment=Alignment.CenterVertically, modifier=Modifier.padding(top=4.dp)) {
                Icon(if(isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription=null, tint=MoneroOrange, modifier=Modifier.size(18.dp).clickable{ onPlay() })
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f).height(4.dp).background(DividerDark, CircleShape)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(0.35f).background(MoneroOrange, CircleShape))
                }
            }
        }
        Column(horizontalAlignment=Alignment.CenterHorizontally) {
            IconButton(onClick=onAddToQueue, modifier=Modifier.size(32.dp).background(XmCard, RoundedCornerShape(8.dp))) {
                Icon(Icons.Default.Add, contentDescription="add to queue", tint=TextPrimary, modifier=Modifier.size(18.dp))
            }
            Text("queue", fontSize=9.sp, color=TextMuted)
        }
    }
}

@Composable
private fun UploadMusicDialog(onDismiss:()->Unit, onUpload:(String,String,String)->Unit) {
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest=onDismiss,
        title={ Text("Upload Music", color=TextPrimary) },
        text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Reference nostria for handling and upload of new music.", fontSize=11.sp, color=TextMuted)
                OutlinedTextField(value=artist, onValueChange={artist=it}, label={Text("Artist")}, modifier=Modifier.fillMaxWidth())
                OutlinedTextField(value=title, onValueChange={title=it}, label={Text("Track Name")}, modifier=Modifier.fillMaxWidth())
                OutlinedTextField(value=tags, onValueChange={tags=it}, label={Text("Tags (#rock #country ...)")}, modifier=Modifier.fillMaxWidth())
                OutlinedButton(onClick={}, modifier=Modifier.fillMaxWidth()){ Text("Pick audio file") }
            }
        },
        confirmButton={ Button(onClick={ onUpload(title, artist, tags) }, colors=ButtonDefaults.buttonColors(containerColor=MoneroOrange)) { Text("Upload") } },
        dismissButton={ TextButton(onClick=onDismiss){ Text("Cancel") } },
        containerColor=XmSurface
    )
}
