package com.xmarcade.data.blossom

import com.xmarcade.utils.Defaults
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Blossom upload – mirrors nostu.be redundancy pattern:
 * upload file to ALL profile blossom servers simultaneously for redundancy.
 * Servers are editable/removable in Settings; never forced.
 */
@Singleton
class BlossomManager @Inject constructor(
    private val okHttp: OkHttpClient = OkHttpClient()
) {
    var servers: List<String> = Defaults.defaultBlossomServers
        private set

    fun setServers(urls: List<String>) {
        servers = urls.ifEmpty { Defaults.defaultBlossomServers }
    }

    data class UploadResult(val server: String, val url: String?, val success: Boolean, val error: String? = null)

    suspend fun uploadToAll(file: File, pubkeyHex: String, contentType: String = "video/mp4"): List<UploadResult> = coroutineScope {
        servers.map { server ->
            async {
                try {
                    val resultUrl = uploadSingle(server, file, contentType)
                    UploadResult(server, resultUrl, true)
                } catch (e: Exception) {
                    UploadResult(server, null, false, e.message)
                }
            }
        }.awaitAll()
    }

    private fun uploadSingle(server: String, file: File, contentType: String): String {
        val url = "$server/upload"
        val body = file.asRequestBody(contentType.toMediaType())
        val req = Request.Builder().url(url).put(body)
            .header("Content-Type", contentType)
            // TODO: add NIP-98 HTTP Auth (blossom BUD-01) with Nostr event signature
            .build()
        okHttp.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Blossom $server failed: ${resp.code} ${resp.message}")
            // Response is JSON { url: "https://..." } or similar
            val bodyStr = resp.body?.string() ?: throw IllegalStateException("empty response")
            // naive parse
            return Regex("\"url\"\\s*:\\s*\"([^\"]+)\"").find(bodyStr)?.groupValues?.get(1) ?: bodyStr
        }
    }
}
