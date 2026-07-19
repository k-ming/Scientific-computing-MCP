package com.scicalc.agent.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "agent_settings")

/**
 * 使用 DataStore 持久化用户配置（LLM 与 MCP 接入参数）。
 *
 * 说明：这里以明文存储 API Key，便于示例。若用于生产，建议改用
 * EncryptedSharedPreferences 或 Android Keystore 加密敏感字段。
 */
class AppSettings(private val context: Context) {

    private object Keys {
        val LLM_BASE_URL = stringPreferencesKey("llm_base_url")
        val LLM_API_KEY = stringPreferencesKey("llm_api_key")
        val LLM_MODEL = stringPreferencesKey("llm_model")
        val MCP_URL = stringPreferencesKey("mcp_url")
        val MCP_TOKEN = stringPreferencesKey("mcp_token")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
    }

    val configFlow: Flow<AppConfig> = context.dataStore.data.map { p ->
        AppConfig(
            llmBaseUrl = p[Keys.LLM_BASE_URL] ?: "",
            llmApiKey = p[Keys.LLM_API_KEY] ?: "",
            llmModel = p[Keys.LLM_MODEL] ?: "",
            mcpUrl = p[Keys.MCP_URL] ?: "",
            mcpToken = p[Keys.MCP_TOKEN] ?: "",
            systemPrompt = p[Keys.SYSTEM_PROMPT] ?: AppConfig.DEFAULT_SYSTEM_PROMPT,
        )
    }

    suspend fun save(config: AppConfig) {
        context.dataStore.edit { p ->
            p[Keys.LLM_BASE_URL] = config.llmBaseUrl
            p[Keys.LLM_API_KEY] = config.llmApiKey
            p[Keys.LLM_MODEL] = config.llmModel
            p[Keys.MCP_URL] = config.mcpUrl
            p[Keys.MCP_TOKEN] = config.mcpToken
            p[Keys.SYSTEM_PROMPT] = config.systemPrompt
        }
    }
}
