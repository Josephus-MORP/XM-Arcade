package com.xmarcade.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.xmarcade.data.models.AppSettings
import com.xmarcade.utils.Defaults
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "xm_arcade_prefs")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val PUBKEY_HEX = stringPreferencesKey("pubkey_hex")
        val NSEC_HEX = stringPreferencesKey("nsec_hex_encrypted")
        val APP_SETTINGS = stringPreferencesKey("app_settings_json")
        val CURRENT_PROFILE_INDEX = intPreferencesKey("current_profile_index")
        val PROFILES_JSON = stringPreferencesKey("profiles_json")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    val pubkeyFlow: Flow<String?> = context.dataStore.data.map { it[Keys.PUBKEY_HEX] }
    val nsecFlow: Flow<String?> = context.dataStore.data.map { it[Keys.NSEC_HEX] }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        prefs[Keys.APP_SETTINGS]?.let {
            try { Json.decodeFromString<AppSettings>(it) } catch (_: Exception) { defaultSettings() }
        } ?: defaultSettings()
    }

    private fun defaultSettings() = AppSettings(
        zapPresetSats = Defaults.defaultZapSats,
        xapPresetAtomic = Defaults.defaultXapAtomic,
        isDarkMode = true,
        followedTags = Defaults.defaultFollowTags,
        relays = Defaults.defaultRelays,
        blossomServers = Defaults.defaultBlossomServers
    )

    suspend fun saveLogin(pubkeyHex: String, nsecHex: String?) {
        context.dataStore.edit { e ->
            e[Keys.PUBKEY_HEX] = pubkeyHex
            if (nsecHex != null) e[Keys.NSEC_HEX] = nsecHex else e.remove(Keys.NSEC_HEX)
            e[Keys.ONBOARDING_DONE] = true
        }
    }

    suspend fun saveSettings(settings: AppSettings) {
        context.dataStore.edit { e ->
            e[Keys.APP_SETTINGS] = Json.encodeToString(settings)
        }
    }

    suspend fun clearLogin() {
        context.dataStore.edit { e ->
            e.remove(Keys.PUBKEY_HEX)
            e.remove(Keys.NSEC_HEX)
            e[Keys.ONBOARDING_DONE] = false
        }
    }

    suspend fun addSecondaryProfile(pubkeyHex: String, nsecHex: String?) {
        // Keep list of profiles in PROFILES_JSON as set of pubkeys; simplified
        context.dataStore.edit { e ->
            val existing = e[Keys.PROFILES_JSON]?.let {
                try { Json.decodeFromString<Set<String>>(it) } catch (_: Exception) { emptySet() }
            } ?: emptySet()
            e[Keys.PROFILES_JSON] = Json.encodeToString(existing + pubkeyHex)
        }
        // also save if this is additional login: Store nsec per pubkey in separate key if needed
    }

    val profilesFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.PROFILES_JSON]?.let {
            try { Json.decodeFromString<Set<String>>(it) } catch (_: Exception) { emptySet() }
        } ?: emptySet()
    }
}
