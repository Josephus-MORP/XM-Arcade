package com.xmarcade.data.nostr

import com.xmarcade.utils.Defaults
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal RelayManager – structure mirrors Amethyst & Ditto relay handling.
 * Production: use full NIP-01 / NIP-11 handling + NIP-42 AUTH etc.
 * Keeps relays per-profile; only newly created accounts get Defaults.defaultRelays
 */
@Singleton
class RelayManager @Inject constructor(
    private val okHttp: OkHttpClient = OkHttpClient()
) {
    private val _relays = MutableStateFlow<List<String>>(Defaults.defaultRelays)
    val relays: StateFlow<List<String>> = _relays

    private val sockets = mutableMapOf<String, WebSocket>()

    fun setRelays(urls: List<String>) {
        _relays.value = urls.ifEmpty { Defaults.defaultRelays }
    }

    fun connectAll(onEvent: (String) -> Unit = {}) {
        _relays.value.forEach { url -> connect(url, onEvent) }
    }

    private fun connect(url: String, onEvent: (String)->Unit) {
        if (sockets.containsKey(url)) return
        val req = Request.Builder().url(url).build()
        val listener = NostrWebSocketListener(onEvent)
        sockets[url] = okHttp.newWebSocket(req, listener)
    }

    fun publish(eventJson: String) {
        val msg = JSONArray().put("EVENT").put(org.json.JSONObject(eventJson)).toString()
        sockets.values.forEach { it.send(msg) }
    }

    fun subscribe(filterJson: String, subscriptionId: String = "sub-${System.currentTimeMillis()}") {
        val msg = JSONArray().put("REQ").put(subscriptionId).put(org.json.JSONObject(filterJson)).toString()
        sockets.values.forEach { it.send(msg) }
    }

    fun disconnectAll() {
        sockets.values.forEach { it.close(1000, "bye") }
        sockets.clear()
    }
}
