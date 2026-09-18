package com.xmarcade.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xmarcade.ui.theme.*

@Composable
fun XmAvatar(
    imageUrl: String?,
    fallbackRes: Int? = null,
    size: Int = 40,
    onClick: (() -> Unit)? = null,
    borderColor: Color = MoneroOrange
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(XmCard)
            .border(1.5.dp, borderColor, CircleShape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary, modifier = Modifier.size((size*0.6).dp))
        }
    }
}

@Composable
fun HashtagChip(text: String, following: Boolean = false, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (following) MoneroOrange.copy(alpha = 0.2f) else XmCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (following) MoneroOrange else DividerDark),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = "#$text",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (following) MoneroOrange else XmCyan
        )
    }
}

@Composable
fun ActionIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = TextPrimary
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }.padding(4.dp)) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 10.sp, color = TextSecondary)
    }
}

@Composable
fun XmTopBar(
    onAvatarClick: () -> Unit,
    onOnlineNowClick: () -> Unit = {},
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    avatarUrl: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(XmNavy)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        XmAvatar(imageUrl = avatarUrl, size = 36, onClick = onAvatarClick)
        Spacer(Modifier.width(10.dp))
        Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
        // Online now chip – reference wisp presence
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = WispOnline.copy(alpha = 0.15f),
            border = androidx.compose.foundation.BorderStroke(1.dp, WispOnline),
            modifier = Modifier.clickable { onOnlineNowClick() }
        ) {
            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(WispOnline, CircleShape))
                Spacer(Modifier.width(4.dp))
                Text("online now", fontSize = 11.sp, color = WispOnline, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

@Composable
fun CategoryTagRow(
    tags: List<String>,
    selected: String? = null,
    onTagClick: (String)->Unit = {},
    onUploadClick: (() -> Unit)? = null
) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        tags.forEach { tag ->
            HashtagChip(tag, following = tag == selected) { onTagClick(tag) }
            Spacer(Modifier.width(6.dp))
        }
        Spacer(Modifier.weight(1f))
        if (onUploadClick != null) {
            IconButton(onClick = onUploadClick, modifier = Modifier.size(32.dp).background(MoneroOrange, RoundedCornerShape(8.dp))) {
                Icon(Icons.Default.FileUpload, contentDescription = "Upload", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun NowPlayingBar(
    trackTitle: String,
    artist: String,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onExpand: () -> Unit
) {
    Surface(
        color = XmCard,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExpand() }
            .border(1.dp, DividerDark)
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(DividerDark, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = MoneroOrange)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Now Playing", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                Text("$artist — $trackTitle", fontSize = 13.sp, color = TextPrimary, maxLines = 1)
            }
            IconButton(onClick = onPlayPause) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = MoneroOrange)
            }
        }
    }
}
