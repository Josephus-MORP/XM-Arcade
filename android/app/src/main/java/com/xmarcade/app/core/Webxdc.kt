package com.xmarcade.app.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.ServerSocket
import java.util.Collections
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread

/** Localhost static server so multi-file webxdc apps resolve relative assets. */
object LocalServer {
  private var server: ServerSocket? = null
  var port: Int = 0
    private set
  private val mounts = Collections.synchronizedMap(mutableMapOf<String, Map<String, ByteArray>>())

  @Synchronized
  fun start(): Int {
    if (server != null) return port
    val s = ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1"))
    server = s; port = s.localPort
    thread(name = "xm-local", isDaemon = true) {
      while (!s.isClosed) {
        try {
          val c = s.accept()
          thread(isDaemon = true) { serve(c) }
        } catch (_: Exception) { break }
      }
    }
    return port
  }

  fun mount(files: Map<String, ByteArray>): String {
    val token = uid()
    mounts[token] = files
    if (mounts.size > 12) mounts.keys.firstOrNull()?.let { mounts.remove(it) }
    return token
  }

  private fun serve(c: java.net.Socket) {
    try {
      c.use { sock ->
        val br = sock.getInputStream().bufferedReader()
        val line = br.readLine() ?: return
        val path = line.split(" ").getOrNull(1) ?: "/"
        var len = 0
        while (true) { val h = br.readLine() ?: break; if (h.isEmpty()) break }
        val parts = path.trimStart('/').split("/", limit = 3)
        var code = 404; var body = ByteArray(0); var mime = "text/plain"
        if (parts.size >= 2 && parts[0] == "t") {
          val files = mounts[parts[1]]
          val name = if (parts.size == 3) parts[2].substringBefore("?") else "index.html"
          val hit = files?.get(name) ?: files?.get(name.substringAfterLast("/"))
          if (hit != null) { code = 200; body = hit; mime = Webxdc.mimeFor(name) }
        }
        len = body.size
        val out = sock.getOutputStream()
        out.write("HTTP/1.1 $code OK\r\nContent-Type: $mime\r\nContent-Length: $len\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(body)
        out.flush()
      }
    } catch (_: Exception) {}
  }
}

/** webxdc catalog: builtins + uploads + Nostr discovery. Mirrors webxdc.js. */
object Webxdc {
  private lateinit var appCtx: Context
  fun init(ctx: Context) { appCtx = ctx.applicationContext }

  val SHIM_JS: String = """
;(function(){
  var listeners=[], updates=[], serial=0, selfAddr='device-'+Math.random().toString(36).slice(2,8), selfName='you';
  try{
    var q=new URLSearchParams(location.search);
    if(q.get('selfAddr')) selfAddr=q.get('selfAddr');
    if(q.get('selfName')) selfName=q.get('selfName');
  }catch(e){}
  function flush(){ listeners.forEach(function(l){ try{ l.cb(l.last===0?updates.filter(function(u){return u.serial>l.last}):updates.filter(function(u){return u.serial>l.last})); l.last=serial; }catch(e){} }); }
  window.webxdc = {
    selfAddr: selfAddr, selfName: selfName,
    setUpdateListener: function(cb, s){ listeners.push({cb:cb,last:s||0}); flush(); return Promise.resolve(); },
    getAllUpdates: function(){ return Promise.resolve(updates.slice()); },
    sendUpdate: function(update, descr){
      serial++; var u={payload:update, summary:descr||'', serial:serial, max_serial:serial};
      updates.push(u);
      try{ parent.postMessage({__xm:'webxdc-update', update:u}, '*'); }catch(e){}
      flush(); return Promise.resolve();
    },
    sendToChat: function(msg){ try{ parent.postMessage({__xm:'webxdc-chat', text:(msg&&msg.text)||String(msg||'')}, '*'); }catch(e){} return Promise.resolve(); }
  };
  window.addEventListener('message', function(ev){
    var m=ev.data||{};
    if(m && m.__xm==='webxdc-inject' && m.update){ serial=Math.max(serial,m.update.serial||0); updates.push(m.update); flush(); }
    if(m && m.__xm==='webxdc-hello'){ if(m.selfAddr) { selfAddr=m.selfAddr; window.webxdc.selfAddr=m.selfAddr; } if(m.selfName){ selfName=m.selfName; window.webxdc.selfName=m.selfName; } }
  });
  try{ parent.postMessage({__xm:'webxdc-ready'}, '*'); }catch(e){}
})();
"""

  /** Forwards shim postMessages to the native bridge (top frame messages itself). */
  const val BRIDGE_FWD: String = """
;(function(){
  window.addEventListener('message', function(ev){
    var m=ev.data||{};
    if(m && m.__xm && m.__xm!=='webxdc-inject' && m.__xm!=='webxdc-hello'){
      try{ __xmNative.post(m.__xm, JSON.stringify(m)); }catch(_){}
    }
  });
})();
"""

  val BUILTINS = listOf(
    MiniApp("builtin-hello", "builtin", "Hello Arcade", "The classic first webxdc — counters sync between players.", listOf("starter"), author = "XM Arcade", file = "webxdc/hello/index.html"),
    MiniApp("builtin-taprace", "builtin", "Tap Race", "20-second tap sprint. Feed your score to a channel.", listOf("arcade"), author = "XM Arcade", file = "webxdc/taprace/index.html"),
  )

  fun mimeFor(n: String): String = when {
    Regex("\\.html?$").containsMatchIn(n) -> "text/html"
    n.endsWith(".js") -> "text/javascript"
    n.endsWith(".css") -> "text/css"
    n.endsWith(".json") -> "application/json"
    n.endsWith(".svg") -> "image/svg+xml"
    n.endsWith(".png") -> "image/png"
    Regex("\\.jpe?g$").containsMatchIn(n) -> "image/jpeg"
    n.endsWith(".gif") -> "image/gif"
    n.endsWith(".webp") -> "image/webp"
    n.endsWith(".mp3") -> "audio/mpeg"
    n.endsWith(".ogg") -> "audio/ogg"
    n.endsWith(".wav") -> "audio/wav"
    else -> "application/octet-stream"
  }

  // ---------- local storage ----------
  private fun dir(): File = File(appCtx.filesDir, "webxdc").also { it.mkdirs() }

  fun localApps(): List<MiniApp> {
    val a = Store.getArr("xm.webxdc.meta.v1") ?: return emptyList()
    return (0 until a.length()).mapNotNull { i ->
      val o = a.optJSONObject(i) ?: return@mapNotNull null
      MiniApp(o.optString("id"), "local", o.optString("title"), o.optString("desc"),
        (0 until (o.optJSONArray("tags")?.length() ?: 0)).map { o.getJSONArray("tags").getString(it) },
        author = "", fileName = o.optString("fileName"), size = o.optLong("size"))
    }
  }

  private fun saveLocalApps(list: List<MiniApp>) {
    Store.setArr("xm.webxdc.meta.v1", JSONArray(list.map {
      JSONObject().put("id", it.id).put("title", it.title).put("desc", it.desc)
        .put("tags", JSONArray(it.tags)).put("fileName", it.fileName).put("size", it.size)
    }))
  }

  suspend fun catalog(nostr: Boolean = true): List<MiniApp> = withContext(Dispatchers.IO) {
    val list = BUILTINS.map { it.copy() }.toMutableList()
    list.addAll(localApps())
    if (nostr) {
      try {
        for (f in Repo.fetchDiscoveredApps(20)) {
          list.add(MiniApp("nostr:" + f.id, "nostr", f.title,
            "Shared on Nostr · " + f.url.take(60), if (f.tags.isEmpty()) listOf("webxdc") else f.tags,
            url = f.url, author = f.pubkey.take(8) + "…"))
        }
      } catch (_: Exception) {}
    }
    list
  }

  fun unzipAll(bytes: ByteArray): Map<String, ByteArray> {
    val out = mutableMapOf<String, ByteArray>()
    ZipInputStream(bytes.inputStream()).use { zip ->
      while (true) {
        val e = try { zip.nextEntry } catch (_: Exception) { break } ?: break
        try {
          var name = e.name.removePrefix("./")
          if (e.isDirectory || name.isEmpty() || ".." in name) continue
          out[name] = zip.readBytes()
        } finally { try { zip.closeEntry() } catch (_: Exception) {} }
      }
    }
    return out
  }

  suspend fun addUpload(bytes: ByteArray, fileName: String, title: String, desc: String, tags: List<String>): MiniApp =
    withContext(Dispatchers.IO) {
      if (bytes.isEmpty()) throw IllegalArgumentException("Choose a file first.")
      if (bytes.size > 20 * 1024 * 1024) throw IllegalArgumentException("20 MB limit for local apps.")
      val name = fileName.lowercase()
      if (!Regex("\\.(xdc|webxdc|zip|html)$").containsMatchIn(name))
        throw IllegalArgumentException("Need a .xdc / .webxdc / .zip (or single-file .html).")
      if (title.trim().isEmpty()) throw IllegalArgumentException("Give the app a title.")
      val all = BUILTINS + localApps()
      if (all.any { it.title.lowercase() == title.trim().lowercase() })
        throw IllegalArgumentException("An app with that title already exists.")
      var finalTitle = title.trim()
      if (Regex("\\.(xdc|webxdc|zip)$").containsMatchIn(name)) {
        val files = unzipAll(bytes)
        files["manifest.toml"]?.toString(Charsets.UTF_8)?.let { man ->
          Regex("name\\s*=\\s*\"([^\"]+)\"").find(man)?.let { finalTitle = it.groupValues[1] }
        }
        if (!files.containsKey("index.html") && files.keys.none { it.lowercase().endsWith(".html") })
          throw IllegalArgumentException("Archive has no index.html.")
      }
      val id = "local-" + uid()
      File(dir(), id).writeBytes(bytes)
      val meta = MiniApp(id, "local", finalTitle, desc.trim(),
        tags.map { it.removePrefix("#") }.filter { it.isNotEmpty() }, fileName = fileName, size = bytes.size.toLong())
      saveLocalApps(listOf(meta) + localApps())
      meta
    }

  fun removeUpload(id: String) {
    try { File(dir(), id).delete() } catch (_: Exception) {}
    saveLocalApps(localApps().filter { it.id != id })
  }

  // ---------- resolve + serve ----------
  private suspend fun entryFiles(entry: MiniApp): Map<String, ByteArray> = withContext(Dispatchers.IO) {
    when (entry.source) {
      "builtin" -> {
        val asset = "games/" + entry.file.substringAfter("webxdc/")
        mapOf("index.html" to appCtx.assets.open(asset).readBytes())
      }
      "local" -> {
        val f = File(dir(), entry.id)
        if (!f.exists()) throw IllegalStateException("App file is gone from this device.")
        val bytes = f.readBytes()
        if (entry.fileName.lowercase().endsWith(".html")) mapOf("index.html" to bytes)
        else unzipAll(bytes)
      }
      else -> {
        val req = Request.Builder().url(entry.url).get().build()
        Net.client.newCall(req).execute().use { r ->
          if (!r.isSuccessful) throw IllegalStateException("Couldn't download the shared app.")
          val bytes = r.body?.bytes() ?: throw IllegalStateException("Couldn't download the shared app.")
          val ct = (r.header("Content-Type") ?: "").lowercase()
          if ("text/html" in ct || Regex("\\.html(\\?|#|$)").containsMatchIn(entry.url)) mapOf("index.html" to bytes)
          else unzipAll(bytes)
        }
      }
    }
  }

  /** Mounts the app on the localhost server; returns the index URL. */
  suspend fun mount(entry: MiniApp): String = withContext(Dispatchers.IO) {
    val files = entryFiles(entry).toMutableMap()
    val idxName = if (files.containsKey("index.html")) "index.html"
      else files.keys.firstOrNull { it.lowercase().endsWith(".html") }
      ?: throw IllegalStateException("App has no HTML entry point.")
    var html = files[idxName]!!.toString(Charsets.UTF_8)
    val shimTag = "<script>$SHIM_JS</script><script>$BRIDGE_FWD</script>"
    val headRe = Regex("<head[^>]*>", RegexOption.IGNORE_CASE)
    val headHit = headRe.find(html)
    html = if (headHit != null) html.replaceRange(headHit.range, headHit.value + shimTag) else shimTag + html
    files[idxName] = html.toByteArray(Charsets.UTF_8)
    // Flatten nested paths for the simple router.
    val flat = mutableMapOf<String, ByteArray>()
    files.forEach { (k, v) -> flat[k] = v; flat[k.substringAfterLast("/")] = v }
    flat["index.html"] = files[idxName]!!
    val port = LocalServer.start()
    val token = LocalServer.mount(flat)
    "http://127.0.0.1:$port/t/$token/${idxName.substringAfterLast("/")}"
  }

  fun pushUpdate(appId: String, json: String) {
    try {
      val k = "xm.webxdc.updates.$appId"
      val cur = Store.getArr(k)
      val list = mutableListOf<String>()
      if (cur != null) for (i in 0 until cur.length()) list.add(cur.optString(i))
      list.add(json)
      Store.setArr(k, JSONArray(list.takeLast(200)))
    } catch (_: Exception) {}
  }

  // ---------- feed an app to a Concord channel ----------
  suspend fun feedAppToChannel(entry: MiniApp, group: Group, channel: String): Triple<NostrEvent, PublishRes, String> =
    withContext(Dispatchers.IO) {
      var appUrl = entry.url
      val fileLine = entry.fileName.ifEmpty { entry.file }
      if (entry.source == "local") {
        val f = File(dir(), entry.id)
        if (!f.exists()) throw IllegalStateException("App file is gone from this device.")
        val up = Repo.blossomUpload(f.readBytes(), entry.fileName.ifEmpty { "app.webxdc" }, "application/octet-stream")
        appUrl = up.url
      } else if (entry.source == "builtin") {
        val asset = "games/" + entry.file.substringAfter("webxdc/")
        val up = Repo.blossomUpload(appCtx.assets.open(asset).readBytes(), entry.id + ".html", "text/html")
        appUrl = up.url
      }
      if (appUrl.isEmpty()) throw IllegalStateException("This app has no shareable file.")
      val body = "🎮 ${entry.title}\n${entry.desc.ifEmpty { "Play it inside XM Arcade." }}\n$appUrl"
      val tags = mutableListOf(listOf("r", appUrl), listOf("t", "webxdc")) +
        entry.tags.take(4).map { listOf("t", it.removePrefix("#")) } + listOf(listOf("title", entry.title))
      val note = Signer.signEvent(1, body, tags)
      val pub = RelayPool.publish(note, RelayPool.relays)
      val card = JSONObject().put("app", "xm-arcade").put("type", "webxdc")
        .put("title", entry.title).put("url", appUrl).put("file", fileLine).put("note", note.id).toString()
      Repo.sendGroupMessage(group, channel, "🎮 ${entry.title} — tap Play in XM Arcade\n$card")
      Triple(note, pub, appUrl)
    }
}
