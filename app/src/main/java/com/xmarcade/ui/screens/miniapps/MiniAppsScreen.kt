package com.xmarcade.ui.screens.miniapps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.xmarcade.ui.components.CategoryTagRow
import com.xmarcade.ui.components.XmAvatar
import com.xmarcade.ui.theme.*

@Composable
fun MiniAppsScreen(navController: NavController, vm: MiniAppsViewModel = hiltViewModel()) {
    val apps by vm.apps.collectAsState()
    val selectedTag by vm.selectedTag.collectAsState()
    var expandedAppId by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(XmNavy)) {
        // Top bar mirrors reference: your pfp | Mini App + category tags (fps etc) + upload
        Row(
            modifier = Modifier.fillMaxWidth().background(XmNavy).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            XmAvatar(imageUrl=null, size=34, onClick={ navController.navigate("profile") })
            Spacer(Modifier.width(8.dp))
            Text("Mini Apps", fontWeight=FontWeight.Bold, color=TextPrimary, fontSize=16.sp)
            Spacer(Modifier.weight(1f))
            // wisp online now
            Surface(shape=RoundedCornerShape(16.dp), color=WispOnline.copy(alpha=0.15f)) {
                Row(Modifier.padding(horizontal=8.dp, vertical=4.dp), verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(WispOnline, androidx.compose.foundation.shape.CircleShape))
                    Spacer(Modifier.width(4.dp))
                    Text("online now", fontSize=10.sp, color=WispOnline, fontWeight=FontWeight.Bold)
                }
            }
        }
        CategoryTagRow(
            tags = vm.categories,
            selected = selectedTag,
            onTagClick = { vm.selectTag(it) },
            onUploadClick = { vm.showUploadDialog = true }
        )
        Spacer(Modifier.height(8.dp))
        Divider(color=DividerDark)
        LazyColumn(modifier=Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(apps) { app ->
                val expanded = expandedAppId == app.id
                MiniAppCard(
                    app = app,
                    expanded = expanded,
                    onToggleExpand = { expandedAppId = if (expanded) null else app.id },
                    onPlay = { vm.playApp(app) },
                    onReact = { vm.react(app.id) },
                    onZap = { vm.zap(app.id) },
                    onXap = { vm.xap(app.id) },
                    onShare = { vm.shareToConcord(app.id) },
                    onProfileClick = { /* open uploader profile + chips zap/xap */ }
                )
            }
        }
    }

    if (vm.showUploadDialog) {
        UploadMiniAppDialog(onDismiss={ vm.showUploadDialog=false }, onUpload={ name, desc, tags -> vm.uploadApp(name, desc, tags); vm.showUploadDialog=false })
    }
}

@Composable
private fun MiniAppCard(
    app: com.xmarcade.data.models.MiniApp,
    expanded: Boolean,
    onToggleExpand: ()->Unit,
    onPlay: ()->Unit,
    onReact: ()->Unit,
    onZap: ()->Unit,
    onXap: ()->Unit,
    onShare: ()->Unit,
    onProfileClick: ()->Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = XmCard,
        modifier = Modifier.fillMaxWidth().clickable{ onToggleExpand() },
        shadowElevation = 4.dp
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(DividerDark),
                    contentAlignment = Alignment.Center
                ) {
                    if (app.logoUrl.isNotBlank()) {
                        Text("APP", color=MoneroOrange, fontWeight=FontWeight.Black, fontSize=10.sp)
                    } else {
                        Icon(Icons.Default.Extension, contentDescription=null, tint=MoneroOrange)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(app.name, color=TextPrimary, fontWeight=FontWeight.Bold, fontSize=15.sp)
                        Spacer(Modifier.width(8.dp))
                        app.tags.take(2).forEach { t ->
                            Text("#$t", color=XmCyan, fontSize=10.sp, modifier=Modifier.padding(end=4.dp))
                        }
                    }
                    Text(if (expanded) app.description else app.description.take(60)+"...", color=TextSecondary, fontSize=12.sp, maxLines= if(expanded) 10 else 1)
                }
                Button(onClick=onPlay, colors=ButtonDefaults.buttonColors(containerColor=MoneroOrange), shape=RoundedCornerShape(8.dp), contentPadding=PaddingValues(horizontal=14.dp, vertical=6.dp)) {
                    Text("Play", fontWeight=FontWeight.Bold, fontSize=12.sp, color=Color.White)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier=Modifier.fillMaxWidth()) {
                XmAvatar(imageUrl=app.uploaderPicture, size=28, onClick=onProfileClick)
                Spacer(Modifier.width(6.dp))
                Text(app.uploaderName, color=TextMuted, fontSize=11.sp)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.SentimentSatisfiedAlt, contentDescription="react", tint=TextSecondary, modifier=Modifier.size(20.dp).clickable{ onReact() })
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Default.Bolt, contentDescription="zap", tint=Color(0xFFFFC107), modifier=Modifier.size(20.dp).clickable{ onZap() })
                Spacer(Modifier.width(12.dp))
                Text("M", color=MoneroOrange, fontWeight=FontWeight.Black, fontSize=14.sp, modifier=Modifier.clickable{ onXap() })
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Default.Share, contentDescription="forward / share – sends to concord chat group", tint=TextSecondary, modifier=Modifier.size(18.dp).clickable{ onShare() })
                Spacer(Modifier.width(8.dp))
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription="collapse or open description box", tint=TextSecondary, modifier=Modifier.size(18.dp))
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                Divider(color=DividerDark)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick={ onShare() }, label={ Text("Send to Concord", fontSize=11.sp) }, leadingIcon={ Icon(Icons.Default.Chat, contentDescription=null, modifier=Modifier.size(14.dp)) })
                    AssistChip(onClick={}, label={ Text("Details", fontSize=11.sp) })
                }
            }
        }
    }
}

@Composable
private fun UploadMiniAppDialog(onDismiss:()->Unit, onUpload:(String,String,String)->Unit) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest=onDismiss,
        title={ Text("Upload Mini App (webxdc)", color=TextPrimary) },
        text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value=name, onValueChange={name=it}, label={ Text("App Name") }, modifier=Modifier.fillMaxWidth())
                OutlinedTextField(value=desc, onValueChange={desc=it}, label={ Text("Description") }, modifier=Modifier.fillMaxWidth())
                OutlinedTextField(value=tags, onValueChange={tags=it}, label={ Text("Tags (space separated)") }, placeholder={ Text("#fps #arcade") }, modifier=Modifier.fillMaxWidth())
                Text("webxdc handling references Armada & Ditto. Include .xdc file picker in production.", fontSize=11.sp, color=TextMuted)
                OutlinedButton(onClick={}, modifier=Modifier.fillMaxWidth()) { Text("Pick .xdc file") }
            }
        },
        confirmButton={ Button(onClick={ onUpload(name, desc, tags) }, colors=ButtonDefaults.buttonColors(containerColor=MoneroOrange)) { Text("Upload") } },
        dismissButton={ TextButton(onClick=onDismiss){ Text("Cancel") } },
        containerColor=XmSurface
    )
}
