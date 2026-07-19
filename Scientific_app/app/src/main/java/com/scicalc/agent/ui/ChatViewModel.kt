package com.scicalc.agent.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scicalc.agent.agent.AgentEvent
import com.scicalc.agent.agent.AgentOrchestrator
import com.scicalc.agent.data.AppConfig
import com.scicalc.agent.data.AppSettings
import com.scicalc.agent.data.ChatMessage
import com.scicalc.agent.data.Role
import com.scicalc.agent.data.llm.LlmClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

class ChatViewModel(private val settings: AppSettings) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _config = MutableStateFlow(AppConfig())
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    // 可用模型列表（从网关 /v1/models 拉取）。
    private val _models = MutableStateFlow<List<String>>(emptyList())
    val models: StateFlow<List<String>> = _models.asStateFlow()

    private val _loadingModels = MutableStateFlow(false)
    val loadingModels: StateFlow<Boolean> = _loadingModels.asStateFlow()

    // 跨轮次保留的对话历史（OpenAI 消息格式）。
    private var history: MutableList<JsonObject>? = null

    init {
        viewModelScope.launch {
            settings.configFlow.collect { _config.value = it }
        }
    }

    fun saveConfig(config: AppConfig) {
        viewModelScope.launch {
            settings.save(config)
            // 配置变更后重置对话，以便用新的 MCP/LLM 参数重新初始化。
            history = null
            _messages.value = emptyList()
        }
    }

    private fun append(message: ChatMessage) {
        _messages.value = _messages.value + message
    }

    /** 一键清空聊天记录，并重置对话历史。 */
    fun clearChat() {
        if (_busy.value) return
        _messages.value = emptyList()
        history = null
    }

    /**
     * 用给定的连接参数拉取可用模型列表。
     * 供设置页在填好 Base URL / API Key 后点击"刷新模型"时调用。
     */
    fun refreshModels(baseUrl: String, apiKey: String) {
        if (baseUrl.isBlank() || apiKey.isBlank()) return
        _loadingModels.value = true
        viewModelScope.launch {
            val list = try {
                LlmClient(baseUrl, apiKey, "").listModels()
            } catch (e: Exception) {
                emptyList()
            }
            _models.value = list
            _loadingModels.value = false
        }
    }

    fun send(userInput: String) {
        val cfg = _config.value
        if (!cfg.isLlmConfigured) {
            append(ChatMessage(Role.SYSTEM, "请先在设置中配置 LLM 接口（Base URL / API Key / 模型）。"))
            return
        }
        if (_busy.value) return

        append(ChatMessage(Role.USER, userInput))
        _busy.value = true

        viewModelScope.launch {
            val h = history ?: AgentOrchestrator.newHistory(cfg.systemPrompt).also { history = it }
            val orchestrator = AgentOrchestrator(cfg)
            try {
                orchestrator.run(h, userInput, onEvent = ::onAgentEvent)
            } finally {
                _busy.value = false
            }
        }
    }

    private fun onAgentEvent(event: AgentEvent) {
        when (event) {
            is AgentEvent.ToolCall ->
                append(ChatMessage(Role.TOOL, "调用工具 ${event.name}\n参数: ${event.arguments}", event.name))
            is AgentEvent.ToolResult ->
                append(ChatMessage(Role.TOOL, "结果: ${event.result}", event.name))
            is AgentEvent.FinalAnswer ->
                append(ChatMessage(Role.ASSISTANT, event.text))
            is AgentEvent.Failure ->
                append(ChatMessage(Role.SYSTEM, event.message))
        }
    }
}
