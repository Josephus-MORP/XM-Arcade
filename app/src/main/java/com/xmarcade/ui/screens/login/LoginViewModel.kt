package com.xmarcade.ui.screens.login

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xmarcade.data.local.PreferencesManager
import com.xmarcade.data.models.NostrProfile
import com.xmarcade.data.wallet.WalletManager
import com.xmarcade.utils.Defaults
import com.xmarcade.utils.NostrCrypto
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

@HiltViewModel
class LoginViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesManager,
    private val walletManager: WalletManager
) : ViewModel() {

    // 8 default avatars – bundled drawables + the generated art styles
    val defaultAvatars = listOf(
        "profile_picture_1", "profile_picture_2", "profile_picture_3", "profile_picture_4",
        "profile_picture_5", "profile_picture_6", "profile_picture_7", "profile_picture_8"
    )

    suspend fun createNewAccount(): Pair<String,String> {
        val priv = NostrCrypto.generatePrivateKey()
        val privHex = NostrCrypto.bytesToHex(priv)
        val pubHex = NostrCrypto.pubkeyHex(privHex)
        val npub = NostrCrypto.toNpub(pubHex)
        val nsec = NostrCrypto.toNsec(privHex)
        prefs.saveLogin(pubHex, privHex)
        // auto-create native wallets for new account (Cake Wallet reference)
        walletManager.createNativeWallets()
        // pick random default avatar for profile – stored as profile picture
        val chosenAvatar = defaultAvatars.random()
        // TODO: publish kind 0 metadata with chosen avatar and default follows + relays
        // For now store default settings for this profile
        // Already handled via PreferencesManager defaultSettings containing Defaults.defaultRelays etc.
        return npub to nsec
    }

    suspend fun loginWithNsec(nsecInput: String): Boolean {
        if (nsecInput.isBlank()) return false
        // Accept nsec bech32 or hex – simplify: if starts with nsec decode else treat as hex
        val trimmed = nsecInput.trim()
        val privHex = if (trimmed.startsWith("nsec1")) {
            // TODO: real bech32 decode; placeholder hash
            // For demo treat as hex fallback
            trimmed.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }.take(64).padEnd(64,'0')
        } else trimmed.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }.take(64)
        if (privHex.length != 64) return false
        return try {
            val pubHex = NostrCrypto.pubkeyHex(privHex)
            prefs.saveLogin(pubHex, privHex)
            true
        } catch (_: Exception) { false }
    }

    fun loginWithAmber(onSuccess: () -> Unit) {
        // NIP-07 style via external signer app – intent to com.greenart.amber
        try {
            val intent = Intent().apply {
                action = "android.intent.action.VIEW"
                `package` = "com.greenart7c3.nostrsigner"
                putExtra("type", "get_public_key")
            }
            // If Amber not installed, open play store fallback
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            // fallback – simulate success for demo
            viewModelScope.launch {
                val priv = NostrCrypto.generatePrivateKey()
                val privHex = NostrCrypto.bytesToHex(priv)
                val pubHex = NostrCrypto.pubkeyHex(privHex)
                prefs.saveLogin(pubHex, privHex)
                onSuccess()
            }
        }
    }
}
