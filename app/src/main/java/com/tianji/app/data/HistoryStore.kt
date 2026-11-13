package com.tianji.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.historyStore: DataStore<Preferences> by preferencesDataStore(name = "history")

@Serializable
data class HistoryEntry(
    val time: Long,
    val module: String,
    val input: String,
    val result: String,
)

object HistoryStore {
    private val KEY = stringPreferencesKey("entries")
    private val json = Json { ignoreUnknownKeys = true }
    private const val MAX = 50

    fun flow(context: Context): Flow<List<HistoryEntry>> =
        context.historyStore.data.map { p -> decode(p[KEY]) }

    suspend fun add(context: Context, entry: HistoryEntry) {
        context.historyStore.edit { p ->
            val cur = decode(p[KEY])
            p[KEY] = json.encodeToString((listOf(entry) + cur).take(MAX))
        }
    }

    suspend fun clear(context: Context) {
        context.historyStore.edit { it.remove(KEY) }
    }

    private fun decode(raw: String?): List<HistoryEntry> =
        raw?.let { s -> runCatching { json.decodeFromString<List<HistoryEntry>>(s) }.getOrNull() } ?: emptyList()
}
