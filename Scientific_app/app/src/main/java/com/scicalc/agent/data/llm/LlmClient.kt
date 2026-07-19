package com.scicalc.agent.data.llm

import com.scicalc.agent.data.mcp.McpJson
import com.scicalc.agent.data.mcp.McpTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** LLM 决定要调用的一个工具。 */
data class LlmToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

/** 一次 LLM 调用的结果：要么是最终文本，要么是一组工具调用。 */
data class LlmResponse(
    val content: String?,
    val toolCalls: List<LlmToolCall>,
    /** 原始的 assistant 消息对象，需原样放回下一轮对话历史。 */
    val rawAssistantMessage: JsonObject,
)

/**
 * OpenAI 兼容的 chat/completions 客户端，支持 tool calling。
 *
 * 通过 baseUrl + apiKey + model 配置，可指向任何兼容该协议的服务网关。
 */
class LlmClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) {
    private val json = McpJson.instance
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun endpoint(): String {
        val trimmed = baseUrl.trimEnd('/')
        // 允许用户填写到 /v1 或完整路径，做一次兼容处理。
        return when {
            trimmed.endsWith("/chat/completions") -> trimmed
            trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
            else -> "$trimmed/v1/chat/completions"
        }
    }

    /** 把 MCP 工具定义转换为 OpenAI tools 数组。 */
    fun toolsPayload(tools: List<McpTool>): JsonArray = buildJsonArray {
        tools.forEach { tool ->
            addJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("parameters", tool.inputSchema)
                }
            }
        }
    }

    /**
     * 发送一次对话请求。
     * @param messages 完整的对话历史（含 system / user / assistant / tool）。
     * @param tools 可用工具，为空则不带 tools 字段。
     */
    suspend fun chat(messages: JsonArray, tools: JsonArray): LlmResponse =
        withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
                put("model", model)
                put("messages", messages)
                if (tools.isNotEmpty()) {
                    put("tools", tools)
                    put("tool_choice", "auto")
                }
            }
            val body = json.encodeToString(JsonObject.serializer(), payload)
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(endpoint())
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(body)
                .build()

            http.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    error("LLM HTTP ${resp.code}: $raw")
                }
                parseResponse(raw)
            }
        }

    private fun parseResponse(raw: String): LlmResponse {
        val root = json.parseToJsonElement(raw).jsonObject
        val choices = root["choices"]?.jsonArray
            ?: error("LLM 响应缺少 choices: $raw")
        val message = choices.first().jsonObject["message"]?.jsonObject
            ?: error("LLM 响应缺少 message: $raw")

        val content = message["content"]?.let {
            runCatching { it.jsonPrimitive.content }.getOrNull()
        }

        val toolCalls = message["tool_calls"]?.jsonArray?.mapNotNull { el ->
            val obj = el.jsonObject
            val fn = obj["function"]?.jsonObject ?: return@mapNotNull null
            LlmToolCall(
                id = obj["id"]?.jsonPrimitive?.content ?: "",
                name = fn["name"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                argumentsJson = fn["arguments"]?.jsonPrimitive?.content ?: "{}",
            )
        } ?: emptyList()

        return LlmResponse(content = content, toolCalls = toolCalls, rawAssistantMessage = message)
    }
}
