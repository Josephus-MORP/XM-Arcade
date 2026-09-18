package com.xmarcade.utils

/**
 * Global defaults for XM Arcade.
 * All defaults are removable / editable in Settings — never hard-coded without escape hatch.
 */
object Defaults {
    // Default relays – only applied to newly created accounts
    val defaultRelays = listOf(
        "wss://nos.lol",
        "wss://ditto.pub/relay",
        "wss://relay.primal.net",
        "wss://relay.fountain.fm"
    )

    // Default blossom servers – redundancy with simultaneous upload
    val defaultBlossomServers = listOf(
        "https://blossom.ditto.pub",
        "https://blossom.primal.net",
        "https://blossom.data.haus"
    )

    // Default follow tags for new accounts
    val defaultFollowTags = listOf(
        "music", "action", "family", "funny", "nature",
        "rock", "country", "alternative", "platformer", "fps"
    )

    // Default zap / xap preset amounts (editable in Settings)
    const val defaultZapSats = 21L
    const val defaultXapAtomic = 100000000L // 0.001 XMR in piconero-ish display

    // Category tags for Mini Apps filtering
    val miniAppCategories = listOf("fps", "platformer", "puzzle", "arcade", "music", "social", "tools")

    // Wisp online-now presence relay (if not provided by user relays)
    const val wispPresenceRelay = "wss://relay.wisp.rocks"
}
