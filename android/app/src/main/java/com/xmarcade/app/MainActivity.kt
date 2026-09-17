package com.xmarcade.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.xmarcade.app.core.Webxdc
import com.xmarcade.app.ui.Nav
import com.xmarcade.app.ui.XMApp

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Platform.bind(this)
    setContent { XMApp() }
  }
}

/** Native bridge: clipboard, links, file picker, Amber. Called from composables. */
object Platform {
  private var activity: ComponentActivity? = null
  private var picker: ActivityResultLauncher<String>? = null
  private var pickCb: ((String, ByteArray) -> Unit)? = null

  fun bind(a: ComponentActivity) {
    activity = a
    picker = a.registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
      val cb = pickCb
      pickCb = null
      if (uri == null || cb == null) return@registerForActivityResult
      try {
        val cr = a.contentResolver
        var name = "file"
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
          if (c.moveToFirst()) name = c.getString(0) ?: name
        }
        val bytes = cr.openInputStream(uri)?.use { it.readBytes() }
          ?: run { Nav.toast("Couldn't read that file"); return@registerForActivityResult }
        if (bytes.size > 25 * 1024 * 1024) { Nav.toast("File too big (25 MB cap)"); return@registerForActivityResult }
        cb(name, bytes)
      } catch (_: Exception) {
        Nav.toast("Couldn't read that file")
      }
    }
  }

  fun pickFile(mime: String, cb: (String, ByteArray) -> Unit) {
    pickCb = cb
    try {
      picker?.launch(mime) ?: run { pickCb = null; Nav.toast("Picker unavailable") }
    } catch (_: Exception) {
      pickCb = null
      Nav.toast("Picker unavailable")
    }
  }

  fun copy(text: String) {
    val a = activity ?: return
    try {
      val cm = a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      cm.setPrimaryClip(ClipData.newPlainText("xm", text))
    } catch (_: Exception) {}
  }

  fun paste(): String {
    val a = activity ?: return ""
    return try {
      val cm = a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      cm.primaryClip?.getItemAt(0)?.coerceToText(a)?.toString() ?: ""
    } catch (_: Exception) { "" }
  }

  fun openUri(u: String) {
    val a = activity ?: return
    try {
      a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
    } catch (_: Exception) {
      Nav.toast("No app can open that link")
    }
  }

  fun openAmber(uri: String): Boolean {
    val a = activity ?: return false
    return try {
      a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage("com.greenart7c3.nostrsigner"))
      true
    } catch (_: Exception) {
      try {
        a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        true
      } catch (_: Exception) {
        false
      }
    }
  }

  fun mimeFor(name: String): String {
    val n = name.lowercase()
    return when {
      n.endsWith(".mp4") || n.endsWith(".m4v") -> "video/mp4"
      n.endsWith(".mov") -> "video/quicktime"
      n.endsWith(".webm") -> "video/webm"
      n.endsWith(".mkv") -> "video/x-matroska"
      n.endsWith(".m4a") -> "audio/mp4"
      n.endsWith(".opus") || n.endsWith(".oga") -> "audio/ogg"
      n.endsWith(".zip") -> "application/zip"
      n.endsWith(".html") || n.endsWith(".htm") -> "text/html"
      else -> Webxdc.mimeFor(name)
    }
  }
}
