package com.xmarcade.app.ui

import com.xmarcade.app.Platform
import com.xmarcade.app.core.AppScope
import com.xmarcade.app.core.NostrEvent
import com.xmarcade.app.core.Nwc
import com.xmarcade.app.core.RelayPool
import com.xmarcade.app.core.Repo
import com.xmarcade.app.core.Signer
import com.xmarcade.app.core.Xmr
import com.xmarcade.app.core.nostrFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shared action handlers (mirrors app.js action cases). */
object Acts {
  private fun guard(block: suspend () -> Unit) {
    AppScope.launch {
      try { block() } catch (e: Exception) { Nav.toast(e.message ?: "Something failed") }
    }
  }

  fun toastOnMain(msg: String) = Nav.toast(msg)

  fun toggleTag(tag: String) = guard {
    val t = tag.lowercase()
    if (S.followTags.contains(t)) {
      S.followTags.remove(t); S.save()
      withContext(Dispatchers.Main) { Nav.toast("Unfollowed #$t") }
    } else {
      S.followTags.add(t); S.save()
      withContext(Dispatchers.Main) { Nav.toast("Following #$t") }
    }
  }

  fun openAuthor(pk: String) {
    if (pk.isEmpty() || pk.startsWith("demo")) { Nav.toast("Demo placeholder — no key"); return }
    guard {
      Repo.fetchProfiles(listOf(pk))
      withContext(Dispatchers.Main) { Nav.openSheet(Sheet("author", mapOf("pk" to pk))) }
    }
  }

  fun visitProfile(pk: String) {
    if (pk.isEmpty() || pk.startsWith("demo")) { Nav.toast("Demo placeholder — no key"); return }
    Nav.closeSheet()
    Nav.go("profile", pk)
  }

  fun doFollow(pk: String) = guard {
    if (!Signer.isSignedIn()) throw IllegalStateException("Sign in to follow")
    val on = S.followUsers.contains(pk)
    Repo.setFollow(pk, !on)
    if (on) S.followUsers.remove(pk) else S.followUsers.add(pk)
    S.save()
    withContext(Dispatchers.Main) {
      Nav.closeSheet()
      Nav.toast(if (on) "Unfollowed" else "Following")
    }
  }

  suspend fun fetchTarget(eid: String): NostrEvent? {
    if (eid.isEmpty() || eid.startsWith("demo") || eid.startsWith("dn")) return null
    ViewCache.notesById[eid]?.let { return it }
    return RelayPool.queryOne(nostrFilter(ids = listOf(eid), limit = 1), RelayPool.relays, 8000).firstOrNull()
  }

  fun doReplyCompose(eid: String) = guard {
    if (!Signer.isSignedIn()) throw IllegalStateException("Sign in to reply")
    if (eid.isEmpty() || eid.startsWith("demo") || eid.startsWith("dn"))
      throw IllegalStateException("Demo post — replies are local only")
    val target = fetchTarget(eid) ?: throw IllegalStateException("Original not found on relays")
    withContext(Dispatchers.Main) { Nav.go("compose", ReplyTo(target.id, target.pubkey, target.kind)) }
  }

  fun doReplyInline(eid: String, body: String, onDone: () -> Unit) = guard {
    if (body.trim().isEmpty()) throw IllegalStateException("Write something first")
    val target = fetchTarget(eid) ?: throw IllegalStateException("Not found on relays")
    val (_, res) = Repo.publishNote(body.trim(), listOf(
      listOf("e", target.id, "", "reply"), listOf("p", target.pubkey), listOf("k", target.kind.toString())))
    withContext(Dispatchers.Main) {
      onDone()
      Nav.toast(if (res.ok) "Replied on ${res.oks}/${res.total} relays" else "No relay accepted it")
    }
  }

  fun doRepost(eid: String) = guard {
    if (!Signer.isSignedIn()) throw IllegalStateException("Sign in first")
    if (eid.isEmpty() || eid.startsWith("demo")) throw IllegalStateException("Demo post — nothing to sign")
    val target = fetchTarget(eid) ?: throw IllegalStateException("Not found on relays")
    val r = Repo.repost(target)
    withContext(Dispatchers.Main) { Nav.toast(if (r.ok) "Reposted" else "No relay accepted it") }
  }

  fun doReactOpen(eid: String) = guard {
    if (!Signer.isSignedIn()) throw IllegalStateException("Sign in first")
    if (eid.isEmpty() || eid.startsWith("demo")) throw IllegalStateException("Demo post — nothing to sign")
    val target = fetchTarget(eid) ?: throw IllegalStateException("Not found on relays")
    ViewCache.notesById[target.id] = target
    withContext(Dispatchers.Main) {
      Nav.openSheet(Sheet("react", mapOf("eid" to target.id, "kind" to target.kind.toString(), "pk" to target.pubkey)))
    }
  }

  fun doReactSend(eid: String, emoji: String, eurl: String = "") = guard {
    if (emoji.isEmpty()) throw IllegalStateException("Pick or type an emoji")
    val target = fetchTarget(eid) ?: throw IllegalStateException("Not found on relays")
    Repo.pushRecentEmoji(Repo.RecentEmoji(emoji, eurl))
    val tag = if (eurl.isNotEmpty()) listOf("emoji", emoji.replace(":", ""), eurl) else null
    val r = Repo.react(target, target.kind, emoji, tag)
    withContext(Dispatchers.Main) {
      Nav.closeSheet()
      Nav.toast(if (r.ok) "Reacted" else "No relay accepted it")
    }
  }

  fun doZapOpen(eid: String, pk: String) {
    if (!Nwc.connected()) { Nav.openSheet(Sheet("needWallet", mapOf("kind" to "sats"))); return }
    Nav.openSheet(Sheet("zap", mapOf("eid" to eid, "pk" to pk)))
  }

  fun doXapOpen(eid: String, pk: String) = guard {
    if (Xmr.mode != "external") {
      try {
        Xmr.status()
        Xmr.mode = "rpc"; Xmr.saveMode()
      } catch (_: Exception) {
        withContext(Dispatchers.Main) { Nav.openSheet(Sheet("needWallet", mapOf("kind" to "xmr"))) }
        return@guard
      }
    }
    withContext(Dispatchers.Main) { Nav.openSheet(Sheet("xap", mapOf("eid" to eid, "pk" to pk))) }
  }

  fun copyText(text: String, doneMsg: String = "Copied") {
    Platform.copy(text)
    Nav.toast(doneMsg)
  }
}

data class ReplyTo(val id: String, val pubkey: String, val kind: Int)
