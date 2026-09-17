package com.xmarcade.app.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Shared OkHttp client (with HTTP cache) + suspend JSON helpers. */
object Net {
  lateinit var client: OkHttpClient
    private set

  fun init(ctx: Context) {
    client = OkHttpClient.Builder()
      .cache(Cache(File(ctx.cacheDir, "http"), 40L * 1024 * 1024))
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(20, TimeUnit.SECONDS)
      .writeTimeout(60, TimeUnit.SECONDS)
      .build()
  }

  suspend fun getText(url: String, headers: Map<String, String> = emptyMap()): String =
    withContext(Dispatchers.IO) {
      val b = Request.Builder().url(url).get()
      headers.forEach { (k, v) -> b.header(k, v) }
      client.newCall(b.build()).execute().use { r ->
        if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}")
        r.body?.string() ?: throw RuntimeException("empty response")
      }
    }

  suspend fun getJson(url: String, headers: Map<String, String> = emptyMap()): JSONObject =
    JSONObject(getText(url, headers))

  suspend fun postJson(url: String, body: JSONObject, headers: Map<String, String> = emptyMap()): JSONObject =
    withContext(Dispatchers.IO) {
      val b = Request.Builder().url(url)
        .post(body.toString().toRequestBody("application/json".toMediaType()))
      headers.forEach { (k, v) -> b.header(k, v) }
      client.newCall(b.build()).execute().use { r ->
        if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}")
        JSONObject(r.body?.string() ?: "{}")
      }
    }

  suspend fun head(url: String, headers: Map<String, String> = emptyMap()): Int =
    withContext(Dispatchers.IO) {
      val b = Request.Builder().url(url).head()
      headers.forEach { (k, v) -> b.header(k, v) }
      client.newCall(b.build()).execute().use { r -> r.code }
    }

  suspend fun putJson(url: String, body: JSONObject, headers: Map<String, String> = emptyMap()): JSONObject =
    withContext(Dispatchers.IO) {
      val b = Request.Builder().url(url)
        .put(body.toString().toRequestBody("application/json".toMediaType()))
      headers.forEach { (k, v) -> b.header(k, v) }
      client.newCall(b.build()).execute().use { r ->
        if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}")
        try { JSONObject(r.body?.string() ?: "{}") } catch (_: Exception) { JSONObject() }
      }
    }

  suspend fun putBytes(url: String, bytes: ByteArray, contentType: String, headers: Map<String, String> = emptyMap()): JSONObject =
    withContext(Dispatchers.IO) {
      val b = Request.Builder().url(url)
        .put(bytes.toRequestBody(contentType.toMediaType()))
      headers.forEach { (k, v) -> b.header(k, v) }
      client.newCall(b.build()).execute().use { r ->
        if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}")
        JSONObject(r.body?.string() ?: "{}")
      }
    }
}
