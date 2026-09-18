package com.xmarcade.data.nostr

import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

class NostrWebSocketListener(
    private val onMessage: (String) -> Unit
) : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {}
    override fun onMessage(webSocket: WebSocket, text: String) { onMessage(text) }
    override fun onMessage(webSocket: WebSocket, bytes: ByteString) { onMessage(bytes.utf8()) }
    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {}
}
