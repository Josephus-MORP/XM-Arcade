package com.xmarcade.ui.screens.shorts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xmarcade.data.blossom.BlossomManager
import com.xmarcade.data.local.PreferencesManager
import com.xmarcade.data.models.ShortFeedTab
import com.xmarcade.data.models.ShortVideo
import com.xmarcade.data.wallet.WalletManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ShortsViewModel @Inject constructor(
    private val blossom: BlossomManager,
    private val wallet: WalletManager,
    private val prefs: PreferencesManager
) : ViewModel() {
    private val _feedTab = MutableStateFlow(ShortFeedTab.GLOBAL)
    val feedTab: StateFlow<ShortFeedTab> = _feedTab

    private val _shorts = MutableStateFlow(sampleShorts())
    val shorts: StateFlow<List<ShortVideo>> = _shorts

    private val _onlineNowCount = MutableStateFlow(12)
    val onlineNowCount: StateFlow<Int> = _onlineNowCount

    fun setTab(tab: ShortFeedTab) { _feedTab.value = tab
        // filter logic per tab – demo just shuffle
        _shorts.value = sampleShorts().shuffled()
    }

    fun refreshOnlineNow() { _onlineNowCount.value = (5..42).random() }

    fun react(id: String) { /* NIP-25 reaction */ }
    fun zap(id: String) { viewModelScope.launch { wallet.zapSats("demoPubkey", 21) } }
    fun xap(id: String) { viewModelScope.launch { wallet.xapXmr("demoPubkey", 100000) } }
    fun share(id: String) {}
    fun comment(id: String) {}
    fun followTag(tag: String) {
        viewModelScope.launch {
            val settings = prefs.settingsFlow // would update
        }
    }

    fun uploadShort(desc: String, tags: String, fileRef: String) {
        viewModelScope.launch {
            // create temp file placeholder and upload via BlossomManager to all servers
            try {
                val tmp = File.createTempFile("short", ".mp4")
                tmp.writeText("fake video $desc")
                val results = blossom.uploadToAll(tmp, "demoPubkey", "video/mp4")
                // publish kind 30078 or 22 video event with blossom urls
            } catch (_: Exception) {}
        }
    }

    private fun sampleShorts(): List<ShortVideo> = listOf(
        ShortVideo("1","pub1","SatoshiCat","", "https://blossom.ditto.pub/video1.mp4", hashtags=listOf("music","fps","funny"), description="When the beat drops...", createdAt=System.currentTimeMillis(), zapCount=42, xapCount=7, commentCount=18, reactionCount=133, authorName="SatoshiCat"),
        ShortVideo("2","pub2","Wisp Girl","", "https://blossom.primal.net/video2.mp4", hashtags=listOf("nature","alternative","platformer"), description="Mountain biking POV", createdAt=System.currentTimeMillis(), zapCount=11, xapCount=2, commentCount=5, authorName="Wisp Girl"),
        ShortVideo("3","pub3","MoneroCowboy","", "https://blossom.data.haus/video3.mp4", hashtags=listOf("country","rock","family"), description="Sunset jam session", createdAt=System.currentTimeMillis(), zapCount=89, xapCount=12, commentCount=27, authorName="MoneroCowboy"),
    )
}
