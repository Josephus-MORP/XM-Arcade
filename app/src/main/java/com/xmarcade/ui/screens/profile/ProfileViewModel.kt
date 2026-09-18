package com.xmarcade.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xmarcade.data.local.PreferencesManager
import com.xmarcade.data.models.NostrProfile
import com.xmarcade.data.models.Note
import com.xmarcade.data.wallet.WalletManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val prefs: PreferencesManager,
    private val wallet: WalletManager
) : ViewModel() {
    private val _profile = MutableStateFlow(NostrProfile(
        pubkeyHex="demoPubkey",
        name="arcade_player",
        displayName="Arcade Player",
        about="Explorer of nostr mini-apps, music & shorts. #music #fps",
        picture="",
        banner="",
        nip05="player@xm.arcade"
    ))
    val profile: StateFlow<NostrProfile> = _profile

    private val _notes = MutableStateFlow(sampleNotes())
    val notes: StateFlow<List<Note>> = _notes

    private val _isOwnProfile = MutableStateFlow(true)
    val isOwnProfile: StateFlow<Boolean> = _isOwnProfile

    fun updateProfile(name: String, about: String, picChoice: String) {
        _profile.value = _profile.value.copy(displayName=name, about=about, picture=picChoice)
        // TODO: publish kind 0 metadata event signed with nsec, upload blossom if needed
    }

    fun postNote(content: String) {
        if(content.isBlank()) return
        val note = Note(id=System.currentTimeMillis().toString(), pubkey="me", content=content, createdAt=System.currentTimeMillis(), authorName=_profile.value.displayName, authorPicture=_profile.value.picture)
        _notes.value = listOf(note) + _notes.value
        // publish kind 1 via RelayManager
    }
    fun replyTo(id: String) {}
    fun repost(id: String) {}
    fun zapUser() { viewModelScope.launch{ wallet.zapSats(_profile.value.pubkeyHex, 21) } }
    fun xapUser() { viewModelScope.launch{ wallet.xapXmr(_profile.value.pubkeyHex, 100000) } }

    private fun sampleNotes() = listOf(
        Note("1","me","Just discovered XM Arcade mini-apps are actually fun 🎮⚡ No doomscrolling!", System.currentTimeMillis(), authorName="Arcade Player"),
        Note("2","me","Uploaded my first short — encoded via blossom redundancy. Watch it propagate!", System.currentTimeMillis()-3600000),
        Note("3","me","Listening to #rock on fountain.fm relay – music never stops even when browsing chats.", System.currentTimeMillis()-7200000),
    )
}
