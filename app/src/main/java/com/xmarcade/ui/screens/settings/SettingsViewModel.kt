package com.xmarcade.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xmarcade.data.local.PreferencesManager
import com.xmarcade.data.models.AppSettings
import com.xmarcade.data.wallet.WalletManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PreferencesManager,
    private val wallet: WalletManager
) : ViewModel() {
    val settings: StateFlow<AppSettings> = prefs.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val profilesCount: StateFlow<Int> = prefs.profilesFlow.map{ it.size + 1 }.stateIn(viewModelScope, SharingStarted.Eagerly, 1)

    private val _showNsec = MutableStateFlow(false)
    val showNsec: StateFlow<Boolean> = _showNsec

    val nsecPreview: String = "nsec1qqqq...demo-backup-never-share-7x8k9l" // decrypted from DataStore in prod

    fun toggleShowNsec() { _showNsec.value = !_showNsec.value }

    fun updateZap(v: Long) { viewModelScope.launch{ prefs.saveSettings(settings.value.copy(zapPresetSats=v)) } }
    fun updateXap(v: Long) { viewModelScope.launch{ prefs.saveSettings(settings.value.copy(xapPresetAtomic=v)) } }
    fun toggleDarkMode() { viewModelScope.launch{ prefs.saveSettings(settings.value.copy(isDarkMode = !settings.value.isDarkMode)) } }
    fun updateHue(h: Float) { viewModelScope.launch{ prefs.saveSettings(settings.value.copy(secondaryColorHue=h)) } }

    fun addTag(tag: String) { viewModelScope.launch{ prefs.saveSettings(settings.value.copy(followedTags = settings.value.followedTags + tag)) } }
    fun removeTag(tag: String) { viewModelScope.launch{ prefs.saveSettings(settings.value.copy(followedTags = settings.value.followedTags - tag)) } }

    fun addRelay(url: String) { viewModelScope.launch{ prefs.saveSettings(settings.value.copy(relays = settings.value.relays + url)) } }
    fun removeRelay(url: String) { viewModelScope.launch{ prefs.saveSettings(settings.value.copy(relays = settings.value.relays - url)) } }
    fun addBlossom(url: String) { viewModelScope.launch{ prefs.saveSettings(settings.value.copy(blossomServers = settings.value.blossomServers + url)) } }
    fun removeBlossom(url: String) { viewModelScope.launch{ prefs.saveSettings(settings.value.copy(blossomServers = settings.value.blossomServers - url)) } }

    fun loginSecondAccount() { viewModelScope.launch{ prefs.addSecondaryProfile("secondPubkeyDemo", null) } }
    fun logout() { viewModelScope.launch{ prefs.clearLogin() } }
    fun deleteAccount() { viewModelScope.launch{ prefs.clearLogin() } }
    fun deleteSatsWallet() { wallet.deleteSatsWallet() }
    fun deleteXmrWallet() { wallet.deleteXmrWallet() }
    fun deleteBothWallets() { wallet.deleteSatsWallet(); wallet.deleteXmrWallet() }
}
