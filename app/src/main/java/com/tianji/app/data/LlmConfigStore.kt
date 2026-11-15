package com.tianji.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "llm_config")

data class LlmConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val provider: String = "",
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
    val isAnthropic: Boolean get() = provider == "anthropic"
}

object LlmConfigStore {
    private val KEY_BASE_URL = stringPreferencesKey("base_url")
    private val KEY_API_KEY = stringPreferencesKey("api_key")
    private val KEY_MODEL = stringPreferencesKey("model")
    private val KEY_PROVIDER = stringPreferencesKey("provider")

    fun flow(context: Context): Flow<LlmConfig> =
        context.dataStore.data.map { p ->
            LlmConfig(
                baseUrl = p[KEY_BASE_URL] ?: "",
                apiKey = p[KEY_API_KEY] ?: "",
                model = p[KEY_MODEL] ?: "",
                provider = p[KEY_PROVIDER] ?: "",
            )
        }

    suspend fun save(context: Context, config: LlmConfig) {
        context.dataStore.edit { p ->
            p[KEY_BASE_URL] = config.baseUrl.trim()
            p[KEY_API_KEY] = config.apiKey.trim()
            p[KEY_MODEL] = config.model.trim()
            p[KEY_PROVIDER] = config.provider.trim()
        }
    }
}

object AppSettingsStore {
    private val KEY_THEME = stringPreferencesKey("theme_mode")
    private val KEY_DYNAMIC_COLOR = androidx.datastore.preferences.core.booleanPreferencesKey("dynamic_color")

    fun flow(context: Context): Flow<String> =
        context.dataStore.data.map { p -> p[KEY_THEME] ?: "system" }

    suspend fun save(context: Context, mode: String) {
        context.dataStore.edit { p -> p[KEY_THEME] = mode }
    }

    /** Material You 动态取色开关（Android 12+ 生效），默认关闭以保留品牌配色。 */
    fun dynamicColorFlow(context: Context): Flow<Boolean> =
        context.dataStore.data.map { p -> p[KEY_DYNAMIC_COLOR] ?: false }

    suspend fun saveDynamicColor(context: Context, enabled: Boolean) {
        context.dataStore.edit { p -> p[KEY_DYNAMIC_COLOR] = enabled }
    }
}
