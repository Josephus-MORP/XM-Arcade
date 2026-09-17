package com.xmarcade.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** App icon set — same 24x24 stroke vocabulary as the web app (stroke 1.75, round). */
private val PATHS = mapOf(
  "home" to "M4 11.2 12 4l8 7.2V20a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z",
  "compass" to "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18M15.4 8.6l-2 4.8-4.8 2 2-4.8z",
  "game" to "M8 12h2m-1-1v2m7-1h.01M17.5 13h.01M17 6H7a5 5 0 0 0-4.9 6l1 4.4A2.6 2.6 0 0 0 6.6 18c1 0 1.6-.5 2.2-1.2L10 15.4h4l1.2 1.4c.6.7 1.2 1.2 2.2 1.2a2.6 2.6 0 0 0 2.5-1.6l1-4.4A5 5 0 0 0 17 6z",
  "users" to "M9 12.5a4 4 0 1 0 0-8 4 4 0 0 0 0 8m7.5-.5a3.2 3.2 0 1 0 0-6.4M3 20a6 6 0 0 1 12 0m2.2-5.2A5.6 5.6 0 0 1 21 20",
  "user" to "M12 12a4.2 4.2 0 1 0 0-8.4A4.2 4.2 0 0 0 12 12M4.5 20.5a7.5 7.5 0 0 1 15 0",
  "chevR" to "M9.5 5.5 16 12l-6.5 6.5",
  "chevL" to "M14.5 5.5 8 12l6.5 6.5",
  "chevD" to "M6 9.5 12 16l6-6.5",
  "close" to "M6 6l12 12M18 6 6 18",
  "plus" to "M12 5v14M5 12h14",
  "bell" to "M18 8.5a6 6 0 1 0-12 0c0 5.2-2 6.5-2 6.5h16s-2-1.3-2-6.5M10.3 19a2 2 0 0 0 3.4 0",
  "msg" to "M20.5 11.6c0 4.2-3.8 7.6-8.5 7.6a9.7 9.7 0 0 1-2.7-.4L4.5 20.5l1.3-3.6A7.3 7.3 0 0 1 3.5 11.6C3.5 7.4 7.3 4 12 4s8.5 3.4 8.5 7.6z",
  "play" to "M8 5.2 19 12 8 18.8z",
  "pause" to "M9 5h2.2v14H9zM12.8 5H15v14h-2.2z",
  "mic" to "M12 15.5a3.5 3.5 0 0 0 3.5-3.5V6a3.5 3.5 0 0 0-7 0v6a3.5 3.5 0 0 0 3.5 3.5M5.5 11.5a6.5 6.5 0 0 0 13 0M12 18.5V21",
  "camera" to "M4 8.5A1.5 1.5 0 0 1 5.5 7h1.9l1.2-2h6.8l1.2 2h1.9A1.5 1.5 0 0 1 20 8.5v9A1.5 1.5 0 0 1 18.5 19h-13A1.5 1.5 0 0 1 4 17.5zM12 16a3.4 3.4 0 1 0 0-6.8 3.4 3.4 0 0 0 0 6.8",
  "zap" to "M13.5 2 4 14h6.5L10 22l9.5-12H13z",
  "xmr" to "M4.5 17.5v-11L12 14l7.5-7.5v11",
  "reply" to "M9 6 4 10.5 9 15M4.5 10.5H14a5.5 5.5 0 0 1 5.5 5.5v3",
  "share" to "M12 16V4m0 0L8 8m4-4 4 4M5 13v5.5A1.5 1.5 0 0 0 6.5 20h11a1.5 1.5 0 0 0 1.5-1.5V13",
  "search" to "M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14m5.5 1.5L21 24",
  "lock" to "M6.5 10.5h11v9h-11zM8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5",
  "key" to "M15.5 3a5.5 5.5 0 1 0-4.2 9 5.6 5.6 0 0 0 1.9.3H14v2.2h2.2V17H18v2.4h3.2V14a5.5 5.5 0 0 0-5.7-11z",
  "relay" to "M12 3v3.5M4.5 8.5A9 9 0 0 0 12 21a9 9 0 0 0 7.5-12.5M12 12m-3 0a3 3 0 1 0 6 0 3 3 0 1 0-6 0",
  "gear" to "M12 15.2a3.2 3.2 0 1 0 0-6.4 3.2 3.2 0 0 0 0 6.4M19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-2.7 1.1v.3a2 2 0 1 1-4 0v-.2a1.6 1.6 0 0 0-2.8-1.1l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1A1.6 1.6 0 0 0 3.6 14H3.3a2 2 0 1 1 0-4h.2a1.6 1.6 0 0 0 1.1-2.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.6 1.6 0 0 0 2.7-1.1V3a2 2 0 1 1 4 0v.2a1.6 1.6 0 0 0 2.8 1.1l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0 1.1 2.7h.3a2 2 0 1 1 0 4h-.2a1.6 1.6 0 0 0-1.1 1.1z",
  "hash" to "M5 9h14M5 15h14M10 4 8.5 20M15.5 4 14 20",
  "eye" to "M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6",
  "eyeOff" to "M4 4l16 16M9.9 5.9A9.6 9.6 0 0 1 12 5.5c6 0 9.5 6.5 9.5 6.5a17 17 0 0 1-2.6 3.4M6.4 7.9A16.7 16.7 0 0 0 2.5 12S6 18.5 12 18.5a9.7 9.7 0 0 0 3.3-.6M9.9 10.2a3 3 0 0 0 4 4",
  "image" to "M4 5.5A1.5 1.5 0 0 1 5.5 4h13A1.5 1.5 0 0 1 20 5.5v13a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18.5zM8.8 11a1.6 1.6 0 1 0 0-3.2 1.6 1.6 0 0 0 0 3.2M4.5 17.5 9.8 12l3.2 3 2.8-2.4 3.7 4.9",
  "video" to "M3.5 7.5A1.5 1.5 0 0 1 5 6h8a1.5 1.5 0 0 1 1.5 1.5v9A1.5 1.5 0 0 1 13 18H5a1.5 1.5 0 0 1-1.5-1.5zM15.5 11l5-3v8l-5-3z",
  "check" to "M4.5 12.5 9.5 17.5 20 6.5",
  "shield" to "M12 3 5 6v6c0 4.4 3 7.6 7 9 4-1.4 7-4.6 7-9V6zM9 12l2.2 2.2L15.5 10",
  "send" to "M4.5 12 20 4.5 15 20l-3.4-6.1z",
  "copy" to "M9 9h9.5v11H9zM5.5 15H4V4h11v1.6",
  "edit" to "M4 20h4L19.5 8.5a2.1 2.1 0 0 0-3-3L5 17zM14.5 6.5l3 3",
  "qr" to "M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h2.5v2.5H14zM17.5 17.5H20V20h-2.5z",
  "trash" to "M5 7h14M9.5 7V4.5h5V7M6.5 7l1 13h9l1-13M10.5 11v5.5M13.5 11v5.5",
  "vol" to "M5 9.5h3L12 6v12l-4-3.5H5zM16 9.5a3.5 3.5 0 0 1 0 5M18.5 7a7 7 0 0 1 0 10",
  "globe" to "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18M3.5 9h17M3.5 15h17M12 3a14 14 0 0 1 0 18 14 14 0 0 1 0-18",
  "info" to "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18M12 11v5.5M12 7.6h.01",
  "refresh" to "M20 12a8 8 0 1 1-2.6-5.9M20 4v4.5h-4.5",
  "bolt" to "M11 3 5 13h5l-1 8 6-10h-5z",
  "heart" to "M12 20s-7-4.4-7-9.4A4.1 4.1 0 0 1 12 8a4.1 4.1 0 0 1 7 2.6C19 15.6 12 20 12 20",
  "grid" to "M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z",
  "list" to "M4 7h16M4 12h16M4 17h16",
  "dots" to "M12 6h.01M12 12h.01M12 18h.01",
  "spark" to "M12 3.5 13.9 9l5.6 1.9-5.6 1.9L12 18.4l-1.9-5.6L4.5 11l5.6-1.9z",
  "more" to "M6 12h.01M12 12h.01M18 12h.01",
  "wallet" to "M4 7.5A1.5 1.5 0 0 1 5.5 6H18v3M4 7.5v10A1.5 1.5 0 0 0 5.5 19h14v-4.5M19.5 9.5H21v5h-1.5a2.5 2.5 0 0 1 0-5",
  "smile" to "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18M9 9.8h.01M15 9.8h.01M8.3 13.6a4.6 4.6 0 0 0 7.4 0",
  "note" to "M9 17.5V6l10-2v11M9 17.5a2.5 2.5 0 1 1-5 0 2.5 2.5 0 0 1 5 0m10-2.5a2.5 2.5 0 1 1-5 0 2.5 2.5 0 0 1 5 0",
  "prev" to "M17 6l-8 6 8 6zM7 6v12",
  "next" to "M7 6l8 6-8 6zM17 6v12",
  "upload" to "M12 16V5m0 0-4.5 4.5M12 5l4.5 4.5M5 19h14",
  "filter" to "M4 6h16M7 12h10M10 18h4",
  "flag" to "M6 21V4h11l-2 4 2 4H6",
  "wave" to "M3 12h2l2-5 3 11 3-8 2 4h6",
  "nwcLink" to "M10 13a5 5 0 0 0 7.1 0l3-3a5 5 0 0 0-7.1-7.1l-1.7 1.7M14 11a5 5 0 0 0-7.1 0l-3 3a5 5 0 0 0 7.1 7.1l1.7-1.7",
  "logout" to "M9 4H5.5A1.5 1.5 0 0 0 4 5.5v13A1.5 1.5 0 0 0 5.5 20H9M15 8l4 4-4 4M19 12H10",
)

private val vectorCache = mutableMapOf<String, ImageVector>()

fun iconVector(name: String): ImageVector = vectorCache.getOrPut(name) {
  val data = PATHS[name] ?: PATHS["info"]!!
  ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).run {
    addPath(
      PathParser().parsePathString(data).toNodes(),
      fill = null, stroke = SolidColor(Color.White),
      strokeLineWidth = 1.75f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    )
    build()
  }
}

@Composable
fun AppIcon(name: String, size: Dp = 22.dp, tint: Color = LocalContentColor.current) {
  val v = remember(name) { iconVector(name) }
  Icon(v, contentDescription = null, modifier = Modifier.size(size), tint = tint)
}
