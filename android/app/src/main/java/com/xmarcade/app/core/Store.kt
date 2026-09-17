package com.xmarcade.app.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.json.JSONArray
import org.json.JSONObject

val AppScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

fun uid(): String = Hex.encode(random32()).take(10)

/** Key-value store mirroring the web app's localStorage keys. */
object Store {
  private lateinit var prefs: SharedPreferences

  fun init(ctx: Context) {
    prefs = ctx.applicationContext.getSharedPreferences("xm-arcade", Context.MODE_PRIVATE)
  }

  fun del(k: String) = prefs.edit().remove(k).apply()

  fun setStr(k: String, v: String) = prefs.edit().putString(k, v).apply()
  fun getStr(k: String, d: String = ""): String = prefs.getString(k, null) ?: d
  fun has(k: String): Boolean = prefs.contains(k)

  fun setBool(k: String, v: Boolean) = prefs.edit().putBoolean(k, v).apply()
  fun getBool(k: String, d: Boolean = false): Boolean = prefs.getBoolean(k, d)

  fun setInt(k: String, v: Int) = prefs.edit().putInt(k, v).apply()
  fun getInt(k: String, d: Int = 0): Int = prefs.getInt(k, d)

  fun setLong(k: String, v: Long) = prefs.edit().putLong(k, v).apply()
  fun getLong(k: String, d: Long = 0L): Long = prefs.getLong(k, d)

  fun setDouble(k: String, v: Double) = prefs.edit().putLong(k, v.toBits()).apply()
  fun getDouble(k: String, d: Double = 0.0): Double =
    if (has(k)) Double.fromBits(prefs.getLong(k, 0)) else d

  fun setStrList(k: String, v: List<String>) =
    prefs.edit().putStringSet(k, v.toSet()).apply()
  fun getStrList(k: String, d: List<String> = emptyList()): List<String> =
    prefs.getStringSet(k, null)?.toList() ?: d

  fun setObj(k: String, o: JSONObject) = prefs.edit().putString(k, o.toString()).apply()
  fun getObj(k: String): JSONObject? = try {
    prefs.getString(k, null)?.let { JSONObject(it) }
  } catch (_: Exception) { null }

  fun setArr(k: String, a: JSONArray) = prefs.edit().putString(k, a.toString()).apply()
  fun getArr(k: String): JSONArray? = try {
    prefs.getString(k, null)?.let { JSONArray(it) }
  } catch (_: Exception) { null }
}
