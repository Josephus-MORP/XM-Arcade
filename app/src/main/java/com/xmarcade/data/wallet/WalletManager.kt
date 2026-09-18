package com.xmarcade.data.wallet

import com.xmarcade.data.models.WalletState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wallet abstractions:
 * - Sats: NWC (Nostr Wallet Connect) referencing Ditto/Amethyst flow OR native wallet via Cake Wallet construction
 * - XMR: external wallet linking referencing Amethyst payment targets OR native Cake Wallet
 * If no external wallets chosen, create wallets automatically for new accounts (mirrors CakeWallet SDK).
 */
@Singleton
class WalletManager @Inject constructor() {
    private val _state = MutableStateFlow(WalletState(
        satsBalance = 27000,
        satsBalanceLocal = "$0.42",
        xmrAtomic = 20000000000L,
        xmrBalance = 0.02,
        xmrBalanceLocal = "$2.84",
        hasNativeSatsWallet = true,
        hasNativeXmrWallet = true
    ))
    val state: StateFlow<WalletState> = _state

    fun linkNwc(uri: String) {
        // Validate NWC URI: nostr+walletconnect://...
        require(uri.startsWith("nostr+walletconnect://") || uri.startsWith("nostrwalletconnect://")) {
            "Invalid NWC URI"
        }
        _state.value = _state.value.copy(nwcUri = uri, hasNativeSatsWallet = false)
    }

    fun linkExternalXmr(walletInfo: String) {
        // walletInfo may be seed, view key, or address per cake-tech flow
        _state.value = _state.value.copy(xmrWalletInfo = walletInfo, hasNativeXmrWallet = false)
    }

    fun createNativeWallets() {
        _state.value = _state.value.copy(hasNativeSatsWallet = true, hasNativeXmrWallet = true)
    }

    fun deleteSatsWallet() { _state.value = _state.value.copy(hasNativeSatsWallet = false, nwcUri = null, satsBalance = 0) }
    fun deleteXmrWallet() { _state.value = _state.value.copy(hasNativeXmrWallet = false, xmrWalletInfo = null, xmrAtomic = 0) }

    suspend fun zapSats(pubkeyHex: String, amountSats: Long, comment: String = ""): Boolean {
        // TODO: implement NIP-57 zap via NWC or native Lightning (LDK) using stored wallet
        return true
    }

    suspend fun xapXmr(pubkeyHex: String, amountAtomic: Long, comment: String = ""): Boolean {
        // TODO: implement XMR transfer via monero-j / cake wallet SDK or external wallet RPC
        return true
    }
}
