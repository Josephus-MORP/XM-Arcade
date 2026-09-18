package com.xmarcade.ui.screens.miniapps

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xmarcade.data.models.MiniApp
import com.xmarcade.data.wallet.WalletManager
import com.xmarcade.utils.Defaults
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MiniAppsViewModel @Inject constructor(
    private val wallet: WalletManager
) : ViewModel() {
    val categories = Defaults.miniAppCategories
    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag
    var showUploadDialog by mutableStateOf(false)

    private val _apps = MutableStateFlow(sampleApps())
    val apps: StateFlow<List<MiniApp>> = _apps

    fun selectTag(tag: String) {
        _selectedTag.value = if (_selectedTag.value==tag) null else tag
        // filter
        _apps.value = if (_selectedTag.value==null) sampleApps() else sampleApps().filter{ it.tags.contains(tag) }
    }
    fun playApp(app: MiniApp) { /* launch webxdc viewer – Armada reference */ }
    fun react(id: String) {}
    fun zap(id: String) { viewModelScope.launch{ wallet.zapSats("pub",21) } }
    fun xap(id: String) { viewModelScope.launch{ wallet.xapXmr("pub",100000) } }
    fun shareToConcord(id: String) { /* include feed button sends to concord chat group */ }

    fun uploadApp(name: String, desc: String, tags: String) {
        val tagList = tags.split(" ", "#").filter{ it.isNotBlank() }
        _apps.value = _apps.value + MiniApp(id=System.currentTimeMillis().toString(), pubkey="me", uploaderName="You", uploaderPicture="", name=name, description=desc, tags=tagList)
    }

    private fun sampleApps() = listOf(
        MiniApp("1","pub1","SatoshiDev","", "Pixel Runner","", "Endless runner – jump, dash, zap!", listOf("platformer","arcade"), "", zapCount=5),
        MiniApp("2","pub2","Nostr Gamer","", "Lightning Poker","", "Texas hold’em with sats. #poker #funny", listOf("action","funny"), ""),
        MiniApp("3","pub3","Retro Labs","", "Space FPS","", "Multiplayer FPS in webxdc – #fps #action", listOf("fps","action"), ""),
        MiniApp("4","pub4","Monero Arcade","", "Family Farm","", "Cozy farming sim #family #nature", listOf("family","nature"), ""),
    )
}
