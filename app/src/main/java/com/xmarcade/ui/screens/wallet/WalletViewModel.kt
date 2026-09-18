package com.xmarcade.ui.screens.wallet

import androidx.lifecycle.ViewModel
import com.xmarcade.data.wallet.WalletManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val wallet: WalletManager
) : ViewModel() {
    val walletState = wallet.state
    fun linkNwc(uri: String) { if(uri.isNotBlank()) wallet.linkNwc(uri) }
    fun linkXmr(info: String) { if(info.isNotBlank()) wallet.linkExternalXmr(info) }
    fun createNativeSats() { wallet.createNativeWallets() }
    fun createNativeXmr() { wallet.createNativeWallets() }
    fun pay() {}
    fun receive() {}
}
