package com.xmarcade.app.core

import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Sticky music player (in-process MediaPlayer). Call from the main thread. */
object Player {
  private var mp: MediaPlayer? = null
  private val _current = MutableStateFlow<Track?>(null)
  val current: StateFlow<Track?> = _current
  private val _queue = MutableStateFlow<List<Track>>(emptyList())
  val queue: StateFlow<List<Track>> = _queue
  private val _playing = MutableStateFlow(false)
  val playing: StateFlow<Boolean> = _playing

  fun enqueue(t: Track) { _queue.value = _queue.value + t }

  fun play(t: Track) {
    try { mp?.release() } catch (_: Exception) {}
    mp = null
    _current.value = t
    _playing.value = false
    try {
      val p = MediaPlayer()
      p.setDataSource(t.url)
      p.setOnPreparedListener { it.start(); _playing.value = true }
      p.setOnCompletionListener {
        _playing.value = false
        val q = _queue.value
        val i = q.indexOfFirst { it.id == t.id }
        if (i >= 0 && i + 1 < q.size) play(q[i + 1])
      }
      p.setOnErrorListener { _, _, _ -> _playing.value = false; true }
      p.prepareAsync()
      mp = p
    } catch (_: Exception) { _playing.value = false }
  }

  fun toggle() {
    val p = mp ?: return
    try {
      if (p.isPlaying) { p.pause(); _playing.value = false }
      else { p.start(); _playing.value = true }
    } catch (_: Exception) {}
  }

  fun stop() {
    try { mp?.stop(); mp?.release() } catch (_: Exception) {}
    mp = null
    _current.value = null
    _playing.value = false
  }
}
