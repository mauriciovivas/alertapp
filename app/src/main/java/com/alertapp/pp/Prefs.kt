package com.alertapp.pp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Article(val title: String, val link: String, val date: Long)

class Prefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("alerta_pp", Context.MODE_PRIVATE)

    var url: String
        get() = sp.getString("url", DEFAULT_URL) ?: DEFAULT_URL
        set(v) { sp.edit().putString("url", v).apply() }

    var intervalMinutes: Int
        get() = sp.getInt("interval", 60)
        set(v) { sp.edit().putInt("interval", v).apply() }

    var days: Int
        get() = sp.getInt("days", 5)
        set(v) { sp.edit().putInt("days", v).apply() }

    var keywords: List<String>
        get() {
            val raw = sp.getString("keywords", null) ?: return DEFAULT_KEYWORDS
            return try {
                val arr = JSONArray(raw)
                List(arr.length()) { arr.getString(it) }
            } catch (e: Exception) {
                DEFAULT_KEYWORDS
            }
        }
        set(v) { sp.edit().putString("keywords", JSONArray(v).toString()).apply() }

    var articles: List<Article>
        get() {
            val raw = sp.getString("articles", null) ?: return emptyList()
            return try {
                val arr = JSONArray(raw)
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    Article(o.getString("t"), o.getString("l"), o.getLong("d"))
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
        set(v) {
            val arr = JSONArray()
            v.forEach { arr.put(JSONObject().put("t", it.title).put("l", it.link).put("d", it.date)) }
            sp.edit().putString("articles", arr.toString()).apply()
        }

    /** true depois da primeira verificação bem-sucedida para a URL atual (evita notificar tudo de uma vez). */
    var initialized: Boolean
        get() = sp.getBoolean("initialized", false)
        set(v) { sp.edit().putBoolean("initialized", v).apply() }

    var lastCheck: Long
        get() = sp.getLong("lastCheck", 0L)
        set(v) { sp.edit().putLong("lastCheck", v).apply() }

    var lastError: String?
        get() = sp.getString("lastError", null)
        set(v) { sp.edit().putString("lastError", v).apply() }

    var lastSource: String?
        get() = sp.getString("lastSource", null)
        set(v) { sp.edit().putString("lastSource", v).apply() }

    companion object {
        const val DEFAULT_URL = "https://passageirodeprimeira.com/categorias/promocoes/"
        val DEFAULT_KEYWORDS = listOf("bônus", "livelo", "smiles", "latam pass", "esfera", "azul")
    }
}
