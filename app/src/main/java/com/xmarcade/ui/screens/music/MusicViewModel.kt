package com.xmarcade.ui.screens.music

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.xmarcade.data.models.MusicTrack
import com.xmarcade.data.wallet.WalletManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val app: Application,
    private val wallet: WalletManager
) : AndroidViewModel(app) {
    var showUpload by mutableStateOf(false)
    private var exo: ExoPlayer? = null

    private val _tracks = MutableStateFlow(sampleTracks())
    val tracks: StateFlow<List<MusicTrack>> = _tracks

    private val _queue = MutableStateFlow<List<MusicTrack>>(emptyList())
    val queue: StateFlow<List<MusicTrack>> = _queue

    private val _currentTrack = MutableStateFlow<MusicTrack?>(null)
    val currentTrack: StateFlow<MusicTrack?> = _currentTrack

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag

    private fun ensurePlayer(): ExoPlayer {
        if (exo==null) exo = ExoPlayer.Builder(app).build()
        return exo!!
    }

    fun play(track: MusicTrack) {
        _currentTrack.value = track
        val player = ensurePlayer()
        player.setMediaItem(MediaItem.fromUri(track.audioUrl))
        player.prepare()
        player.play()
        _isPlaying.value = true
    }

    fun togglePlayPause() {
        val p = exo ?: return
        if (p.isPlaying) { p.pause(); _isPlaying.value=false } else { p.play(); _isPlaying.value=true }
    }

    fun addToQueue(track: MusicTrack) { _queue.value = _queue.value + track }

    fun filterByTag(tag: String) {
        _selectedTag.value = if (_selectedTag.value==tag) null else tag
        _tracks.value = if (_selectedTag.value==null) sampleTracks() else sampleTracks().filter{ it.hashtags.contains(tag.removePrefix("#")) }
    }

    fun toggleQueueView() { /* switch between list vs queue */ }

    fun uploadMusic(title: String, artist: String, tags: String) {
        val tagList = tags.split(" ", "#", ",").filter{ it.isNotBlank() }
        val newTrack = MusicTrack(id=System.currentTimeMillis().toString(), pubkey="me", artist=artist.ifBlank{"Unknown"}, album="Single", title=title.ifBlank{"Untitled"}, audioUrl="https://relay.fountain.fm/audio_demo.mp3", hashtags=tagList)
        _tracks.value = listOf(newTrack) + _tracks.value
    }

    fun zap(id: String) { viewModelScope.launch{ wallet.zapSats("pub",21) } }
    fun xap(id: String) { viewModelScope.launch{ wallet.xapXmr("pub",100000) } }

    private fun sampleTracks() = listOf(
        MusicTrack("1","p1","Synth Rider","Neon Nights","Midnight Drive","", "https://relay.fountain.fm/track1.mp3", 187, listOf("alternative","music")),
        MusicTrack("2","p2","Dusty Strings","Country Roads","Prairie Wind","", "https://relay.fountain.fm/track2.mp3", 203, listOf("country","family")),
        MusicTrack("3","p3","Rock Forge","Stone Age","Thunder","", "https://relay.fountain.fm/track3.mp3", 211, listOf("rock")),
        MusicTrack("4","p4","LoFi Cat","Chillhop","Purring","", "https://relay.fountain.fm/track4.mp3", 155, listOf("music","nature")),
    )

    override fun onCleared() { exo?.release(); exo=null; super.onCleared() }
}
