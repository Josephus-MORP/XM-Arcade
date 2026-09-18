package com.xmarcade.data.models

import kotlinx.serialization.Serializable

@Serializable
data class NostrProfile(
    val pubkeyHex: String,
    val npub: String = "",
    val name: String = "",
    val displayName: String = "",
    val about: String = "",
    val picture: String = "",
    val banner: String = "",
    val nip05: String = "",
    val lud16: String = "",
    val followsTags: List<String> = emptyList(),
    val relays: List<String> = emptyList(),
    val blossomServers: List<String> = emptyList()
)

@Serializable
data class Note(
    val id: String,
    val pubkey: String,
    val content: String,
    val createdAt: Long,
    val tags: List<List<String>> = emptyList(),
    val authorName: String = "",
    val authorPicture: String = ""
)

@Serializable
data class ShortVideo(
    val id: String,
    val pubkey: String,
    val authorName: String,
    val authorPicture: String,
    val videoUrl: String,
    val thumbUrl: String = "",
    val description: String = "",
    val hashtags: List<String> = emptyList(),
    val createdAt: Long,
    val zapCount: Int = 0,
    val xapCount: Int = 0,
    val commentCount: Int = 0,
    val reactionCount: Int = 0
)

@Serializable
data class MusicTrack(
    val id: String,
    val pubkey: String,
    val artist: String,
    val album: String,
    val title: String,
    val artUrl: String = "",
    val audioUrl: String,
    val durationSec: Int = 0,
    val hashtags: List<String> = emptyList()
)

@Serializable
data class MiniApp(
    val id: String,
    val pubkey: String,
    val uploaderName: String,
    val uploaderPicture: String,
    val name: String,
    val logoUrl: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val webxdcUrl: String = "",
    val zapCount: Int = 0,
    val xapCount: Int = 0
)

@Serializable
data class ChatGroup(
    val id: String,
    val name: String,
    val avatarUrl: String = "",
    val channels: List<ChatChannel> = emptyList(),
    val unreadCount: Int = 0,
    val hasMention: Boolean = false,
    val isConcordGroup: Boolean = true
)

@Serializable
data class ChatChannel(
    val id: String,
    val groupId: String,
    val name: String, // e.g. "#general"
    val unread: Int = 0
)

@Serializable
data class WalletState(
    val satsBalance: Long = 0L, // sats
    val satsBalanceLocal: String = "$0.00",
    val xmrAtomic: Long = 0L, // piconero
    val xmrBalance: Double = 0.0,
    val xmrBalanceLocal: String = "$0.00",
    val nwcUri: String? = null,
    val xmrWalletInfo: String? = null,
    val hasNativeSatsWallet: Boolean = false,
    val hasNativeXmrWallet: Boolean = false
)

@Serializable
data class AppSettings(
    val zapPresetSats: Long = 21,
    val xapPresetAtomic: Long = 100000L,
    val isDarkMode: Boolean = true,
    val secondaryColorHue: Float = 24f, // orange hue for Monero
    val followedTags: List<String> = emptyList(),
    val relays: List<String> = emptyList(),
    val blossomServers: List<String> = emptyList()
)

enum class ShortFeedTab { GLOBAL, TAGS, FOLLOWING }
enum class MusicQueueMode { VIEW_QUEUE, ONLINE_NOW }
