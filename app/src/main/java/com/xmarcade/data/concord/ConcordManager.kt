package com.xmarcade.data.concord

import com.xmarcade.data.models.ChatChannel
import com.xmarcade.data.models.ChatGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concord protocol groups – NOT NIP-29.
 * Reference: https://github.com/concord-protocol/concord and Armada's handling.
 * Supports: group discovery, webxdc integration, call (voice) hooks.
 */
@Singleton
class ConcordManager @Inject constructor() {
    private val _groups = MutableStateFlow<List<ChatGroup>>(sampleGroups())
    val groups: StateFlow<List<ChatGroup>> = _groups

    private fun sampleGroups(): List<ChatGroup> = listOf(
        ChatGroup(
            id = "g1", name = "Arcade Lounge", avatarUrl = "",
            channels = listOf(ChatChannel("c1", "g1", "#general", 3), ChatChannel("c2", "g1", "#gaming", 0)),
            unreadCount = 3, hasMention = true
        ),
        ChatGroup(
            id = "g2", name = "Monero Miners", avatarUrl = "",
            channels = listOf(ChatChannel("c3", "g2", "#xmr", 7), ChatChannel("c4", "g2", "#trading", 2)),
            unreadCount = 9, hasMention = false
        ),
        ChatGroup(
            id = "g3", name = "FPS Squad", avatarUrl = "",
            channels = listOf(ChatChannel("c5","g3","#valorant",0), ChatChannel("c6","g3","#clips",1)),
            unreadCount = 0, hasMention = false
        )
    )

    fun reorder(from: Int, to: Int) {
        val mutable = _groups.value.toMutableList()
        if (from in mutable.indices && to in mutable.indices) {
            val item = mutable.removeAt(from)
            mutable.add(to, item)
            _groups.value = mutable
        }
    }

    fun discoverNewGroups(): List<ChatGroup> = emptyList() // TODO: fetch via concord relay

    fun startCall(groupId: String, channelId: String) { /* TODO: WebRTC via concord */ }
}
