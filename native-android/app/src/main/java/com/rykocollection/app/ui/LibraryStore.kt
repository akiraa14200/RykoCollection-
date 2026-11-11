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
    val savedAt: Long,
    val genres: List<String> = emptyList()
)

object LibraryStore {
    private const val PREFS = "ryko_library_v1"
    private const val KEY_ENTRIES = "entries"
    private const val KEY_ANIME_ORDER = "anime_order"
    private const val KEY_MANGA_ORDER = "manga_order"

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
                    savedAt = item.optLong("savedAt"),
                    genres = item.optJSONArray("genres")?.let { values ->
                        (0 until values.length()).mapNotNull { index -> values.optString(index).takeIf(String::isNotBlank) }
                    }.orEmpty()
                )
            }.getOrNull() ?: continue
            if (type == null || entry.type == type) result.add(entry)
        }
        val orderKey = when (type) {
            "Anime" -> KEY_ANIME_ORDER
            "Manga" -> KEY_MANGA_ORDER
            else -> null
        }
        if (orderKey != null) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val rawOrder = prefs.getString(orderKey, null)
            if (!rawOrder.isNullOrBlank()) {
                val orderedIds = runCatching { JSONArray(rawOrder) }.getOrElse { JSONArray() }
                val positions = (0 until orderedIds.length()).associate { index -> orderedIds.optInt(index) to index }
                return result.sortedWith(compareBy<LibraryEntry> { positions[it.id] ?: Int.MAX_VALUE }
                    .thenByDescending { it.savedAt })
            }
        }
        return result.sortedByDescending { it.savedAt }
    }

    @Synchronized
    fun saveOrder(context: Context, type: String, orderedIds: List<Int>) {
        val key = when (type) {
            "Anime" -> KEY_ANIME_ORDER
            "Manga" -> KEY_MANGA_ORDER
            else -> return
        }
        val array = JSONArray()
        orderedIds.distinct().forEach { array.put(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(key, array.toString()).apply()
    }

    fun hasCustomOrder(context: Context, type: String): Boolean {
        val key = when (type) {
            "Anime" -> KEY_ANIME_ORDER
            "Manga" -> KEY_MANGA_ORDER
            else -> return false
        }
        return !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key, null).isNullOrBlank()
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
                put("genres", JSONArray(entry.genres.distinct()))
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ENTRIES, array.toString()).apply()
    }
}
