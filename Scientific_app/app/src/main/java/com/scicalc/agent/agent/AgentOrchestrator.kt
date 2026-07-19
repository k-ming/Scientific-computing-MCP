package com.scicalc.agent.agent

import com.scicalc.agent.data.AppConfig
import com.scicalc.agent.data.llm.LlmClient
import com.scicalc.agent.data.mcp.McpClient
import com.scicalc.agent.data.mcp.McpJson
import com.scicalc.agent.data.mcp.McpTool
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** agent 执行过程中的事件，供 UI 实时展示。 */
sealed interface AgentEvent {
    data class ToolCall(val name: String, val arguments: String) : AgentEvent
    data class ToolResult(val name: String, val result: String) : AgentEvent
    data class FinalAnswer(val text: String) : AgentEvent
    data class Failure(val message: String) : AgentEvent
}

/**
 * agent 编排器：驱动 "LLM 思考 → 调用 MCP 工具 → 结果回传 → 再思考" 的循环，
 * 直到 LLM 给出不含工具调用的最终回答。
 */
class AgentOrchestrator(private val config: AppConfig) {

    private val llm = LlmClient(config.llmBaseUrl, config.llmApiKey, config.llmModel)
    private val mcp = McpClient(config.mcpUrl, config.mcpToken)
    private val json = McpJson.instance

    private var tools: List<McpTool> = emptyList()
    private var toolsPayload: JsonArray = JsonArray(emptyList())
    private var initialized = false

    /** 惰性初始化 MCP 会话并拉取工具列表。 */
    private suspend fun ensureReady() {
        if (initialized) return
        if (config.mcpUrl.isNotBlank()) {
            mcp.initialize()
            tools = mcp.listTools()
            toolsPayload = llm.toolsPayload(tools)
        }
        initialized = true
    }

    /**
     * 处理一轮用户输入。
     * @param history 已有对话（OpenAI 消息数组，含 system 提示）。会被就地扩展。
     * @param userInput 本次用户输入。
     * @param onEvent 过程事件回调。
     * @return 追加了本轮所有消息后的新对话历史，供下一轮复用。
     */
    suspend fun run(
        history: MutableList<JsonObject>,
        userInput: String,
        onEvent: (AgentEvent) -> Unit,
        maxSteps: Int = 8,
    ): List<JsonObject> {
        try {
            ensureReady()
        } catch (e: Exception) {
            onEvent(AgentEvent.Failure("MCP 初始化失败: ${e.message}"))
            // MCP 不可用时仍允许纯对话。
        }

        history.add(buildJsonObject {
            put("role", "user")
            put("content", userInput)
        })

        repeat(maxSteps) {
            val response = try {
                llm.chat(JsonArray(history), toolsPayload)
            } catch (e: Exception) {
                onEvent(AgentEvent.Failure("LLM 调用失败: ${e.message}"))
                return history
            }

            // 把 assistant 消息原样加入历史（可能含 tool_calls）。
            history.add(response.rawAssistantMessage)

            if (response.toolCalls.isEmpty()) {
                val answer = response.content?.trim().orEmpty()
                onEvent(AgentEvent.FinalAnswer(answer.ifBlank { "(无内容)" }))
                return history
            }

            // 依次执行工具调用，并把结果作为 role=tool 消息回填。
            for (call in response.toolCalls) {
                onEvent(AgentEvent.ToolCall(call.name, call.argumentsJson))
                val result = try {
                    val args = runCatching {
                        json.parseToJsonElement(call.argumentsJson).jsonObject
                    }.getOrElse { buildJsonObject { } }
                    mcp.callTool(call.name, args)
                } catch (e: Exception) {
                    "工具执行失败: ${e.message}"
                }
                onEvent(AgentEvent.ToolResult(call.name, result))
                history.add(buildJsonObject {
                    put("role", "tool")
                    put("tool_call_id", call.id)
                    put("name", call.name)
                    put("content", result)
                })
            }
        }

        onEvent(AgentEvent.Failure("已达到最大步数，未能得到最终回答。"))
        return history
    }

    companion object {
        /** 构造带 system 提示的初始对话历史。 */
        fun newHistory(systemPrompt: String): MutableList<JsonObject> = mutableListOf(
            buildJsonObject {
                put("role", "system")
                put("content", systemPrompt)
            }
        )
    }
}
