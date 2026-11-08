package com.rykocollection.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LibraryEntry(
    val id: Int,
    val type: String,
    val title: String,
    val cover: String,
    val format: String,
    val status: String,
    val progress: Int,
    val total: Int,
    val savedAt: Long
)

object LibraryStore {
    private const val PREFS = "ryko_library_v1"
    private const val KEY_ENTRIES = "entries"

    @Synchronized
    fun entries(context: Context, type: String? = null): List<LibraryEntry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ENTRIES, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        val result = mutableListOf<LibraryEntry>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val entry = runCatching {
                LibraryEntry(
                    id = item.optInt("id"),
                    type = item.optString("type"),
                    title = item.optString("title"),
                    cover = item.optString("cover"),
                    format = item.optString("format"),
                    status = item.optString("status"),
                    progress = item.optInt("progress"),
                    total = item.optInt("total"),
                    savedAt = item.optLong("savedAt")
                )
            }.getOrNull() ?: continue
            if (type == null || entry.type == type) result.add(entry)
        }
        return result.sortedByDescending { it.savedAt }
    }

    fun contains(context: Context, type: String, id: Int): Boolean =
        entries(context, type).any { it.id == id }

    @Synchronized
    fun save(context: Context, entry: LibraryEntry) {
        val all = entries(context).filterNot { it.type == entry.type && it.id == entry.id }.toMutableList()
        all.add(entry)
        persist(context, all)
    }

    @Synchronized
    fun remove(context: Context, type: String, id: Int) {
        persist(context, entries(context).filterNot { it.type == type && it.id == id })
    }

    @Synchronized
    fun update(context: Context, entry: LibraryEntry) {
        save(context, entry)
    }

    private fun persist(context: Context, entries: List<LibraryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("type", entry.type)
                put("title", entry.title)
                put("cover", entry.cover)
                put("format", entry.format)
                put("status", entry.status)
                put("progress", entry.progress)
                put("total", entry.total)
                put("savedAt", entry.savedAt)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ENTRIES, array.toString()).apply()
    }
}
