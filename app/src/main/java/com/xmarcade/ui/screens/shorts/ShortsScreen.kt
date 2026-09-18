package com.xmarcade.ui.screens.shorts

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
import com.xmarcade.data.models.ShortFeedTab
import com.xmarcade.ui.components.HashtagChip
import com.xmarcade.ui.components.XmAvatar
import com.xmarcade.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortsScreen(navController: NavController, vm: ShortsViewModel = hiltViewModel()) {
    val feedTab by vm.feedTab.collectAsState()
    val shorts by vm.shorts.collectAsState()
    val onlineCount by vm.onlineNowCount.collectAsState()
    var showUpload by remember { mutableStateOf(false) }
    var showFollowTagDialog by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(XmNavy)) {
        // Header: your pfp | Global | Tags | Following | online now | upload
        Row(
            modifier = Modifier.fillMaxWidth().background(XmNavy).padding(horizontal=8.dp, vertical=8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            XmAvatar(imageUrl = null, size = 34, onClick = { navController.navigate("profile") })
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = feedTab== ShortFeedTab.GLOBAL,
                onClick = { vm.setTab(ShortFeedTab.GLOBAL) },
                label = { Text("Global", fontSize=12.sp, fontWeight=FontWeight.Bold) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MoneroOrange, selectedLabelColor = Color.White)
            )
            Spacer(Modifier.width(6.dp))
            FilterChip(
                selected = feedTab== ShortFeedTab.TAGS,
                onClick = { vm.setTab(ShortFeedTab.TAGS) },
                label = { Text("Tags", fontSize=12.sp) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MoneroOrange.copy(alpha=0.2f), selectedLabelColor=MoneroOrange)
            )
            Spacer(Modifier.width(6.dp))
            FilterChip(
                selected = feedTab== ShortFeedTab.FOLLOWING,
                onClick = { vm.setTab(ShortFeedTab.FOLLOWING) },
                label = { Text("Following", fontSize=12.sp) }
            )
            Spacer(Modifier.weight(1f))
            // wisp online now
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = WispOnline.copy(alpha=0.15f),
                modifier = Modifier.clickable { vm.refreshOnlineNow() }
            ) {
                Row(Modifier.padding(horizontal=8.dp, vertical=4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(WispOnline, CircleShape))
                    Spacer(Modifier.width(4.dp))
                    Text("${onlineCount} online now", fontSize=10.sp, color=WispOnline, fontWeight=FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { showUpload = true }, modifier = Modifier.size(36.dp).background(MoneroOrange, RoundedCornerShape(8.dp))) {
                Icon(Icons.Default.FileUpload, contentDescription="Upload", tint=Color.White)
            }
        }

        // Vertical feed – all panes scroll vertically to keep showing more options
        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            items(shorts) { video ->
                ShortVideoCard(
                    video = video,
                    onTagClick = { tag -> showFollowTagDialog = tag },
                    onAvatarClick = { /* navigate to other profile */ },
                    onReact = { vm.react(video.id) },
                    onZap = { vm.zap(video.id) },
                    onXap = { vm.xap(video.id) },
                    onShare = { vm.share(video.id) },
                    onComment = { vm.comment(video.id) }
                )
                Divider(color = DividerDark, thickness = 1.dp)
            }
        }
    }

    if (showUpload) {
        UploadShortDialog(onDismiss = { showUpload=false }, onUpload = { desc, tags, file ->
            vm.uploadShort(desc, tags, file)
            showUpload=false
        })
    }

    showFollowTagDialog?.let { tag ->
        AlertDialog(
            onDismissRequest = { showFollowTagDialog=null },
            title = { Text("Follow #$tag ?", color=TextPrimary) },
            text = { Text("Add #$tag to your followed tags? You'll see more of this in Tags feed.", color=TextSecondary, fontSize=13.sp) },
            confirmButton = {
                Button(onClick = { vm.followTag(tag); showFollowTagDialog=null }, colors=ButtonDefaults.buttonColors(containerColor=MoneroOrange)) { Text("Yes") }
            },
            dismissButton = { TextButton(onClick={ showFollowTagDialog=null }){ Text("No", color=TextMuted)} },
            containerColor = XmSurface
        )
    }
}

@Composable
private fun ShortVideoCard(
    video: com.xmarcade.data.models.ShortVideo,
    onTagClick: (String)->Unit,
    onAvatarClick: ()->Unit,
    onReact: ()->Unit,
    onZap: ()->Unit,
    onXap: ()->Unit,
    onShare: ()->Unit,
    onComment: ()->Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(520.dp)
            .background(Color.Black)
    ) {
        // Video placeholder – in prod: ExoPlayer with androidx.media3
        Box(Modifier.fillMaxSize().background(Color(0xFF0F0F0F)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PlayCircle, contentDescription=null, tint=Color.White.copy(alpha=0.7f), modifier=Modifier.size(64.dp))
            Text("VIDEO", color=Color.White.copy(alpha=0.2f), fontSize=48.sp, fontWeight=FontWeight.Black, modifier=Modifier.align(Alignment.Center))
        }
        // Hashtags over the video – clickable to follow right from video screen
        Column(Modifier.align(Alignment.TopStart).padding(12.dp)) {
            // no overlay needed
        }
        // Bottom overlay: hashtags + action row
        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Color.Black.copy(alpha=0.55f)).padding(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                video.hashtags.forEach { tag ->
                    Text(
                        text = "#$tag",
                        color = XmCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onTagClick(tag) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                XmAvatar(imageUrl = video.authorPicture, size = 36, onClick = onAvatarClick)
                Spacer(Modifier.width(8.dp))
                Text(video.authorName, color=Color.White, fontWeight=FontWeight.SemiBold, fontSize=13.sp, modifier=Modifier.clickable{ onAvatarClick() })
                Spacer(Modifier.weight(1f))
                IconButton(onClick=onComment, modifier=Modifier.size(36.dp)) { Icon(Icons.Default.SentimentSatisfiedAlt, contentDescription="React", tint=Color.White) }
                IconButton(onClick=onZap) { Icon(Icons.Default.Bolt, contentDescription="Zap", tint=Color(0xFFFFC107)) }
                // Monero M for XAP – custom
                Text("M", color=MoneroOrange, fontWeight=FontWeight.Black, fontSize=18.sp, modifier=Modifier.clickable{ onXap() }.padding(8.dp))
                IconButton(onClick=onShare) { Icon(Icons.Default.Share, contentDescription="Share", tint=Color.White) }
                Icon(Icons.Default.MoreVert, contentDescription="details", tint=Color.White.copy(alpha=0.7f), modifier=Modifier.size(18.dp).clickable{})
            }
            // Quick stats row for demo
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier=Modifier.padding(top=4.dp)) {
                Text("♡ ${video.reactionCount}", color=TextSecondary, fontSize=11.sp)
                Text("⚡ ${video.zapCount}", color=TextSecondary, fontSize=11.sp)
                Text("M ${video.xapCount}", color=MoneroOrange, fontSize=11.sp)
                Text("💬 ${video.commentCount}", color=TextSecondary, fontSize=11.sp)
            }
        }
    }
}

@Composable
private fun UploadShortDialog(onDismiss: ()->Unit, onUpload: (String, String, String)->Unit) {
    var desc by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("no file chosen") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Upload Short", color=TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick={ fileName="video_2024.mp4 (demo)" }, modifier=Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.VideoLibrary, contentDescription=null, tint=MoneroOrange)
                    Spacer(Modifier.width(8.dp))
                    Text(fileName, fontSize=12.sp)
                }
                OutlinedTextField(value=desc, onValueChange={desc=it}, label={Text("Description")}, modifier=Modifier.fillMaxWidth(), maxLines=2)
                OutlinedTextField(value=tags, onValueChange={tags=it}, label={Text("Hashtags (space-separated)")}, placeholder={Text("#music #fps #funny")}, modifier=Modifier.fillMaxWidth())
                Text("Video will upload to all your blossom servers simultaneously for redundancy (blossom.ditto.pub, primal.net, data.haus).", fontSize=11.sp, color=TextMuted, lineHeight=13.sp)
            }
        },
        confirmButton = {
            Button(onClick={ onUpload(desc, tags, fileName) }, colors=ButtonDefaults.buttonColors(containerColor=MoneroOrange)) { Text("Upload") }
        },
        dismissButton = { TextButton(onClick=onDismiss){ Text("Cancel") } },
        containerColor = XmSurface
    )
}
