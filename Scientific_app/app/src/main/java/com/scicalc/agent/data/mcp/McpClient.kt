package com.scicalc.agent.data.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** 从 MCP 拿到的一个工具定义。 */
data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
)

/**
 * 精简的 MCP streamable-http 客户端。
 *
 * 实现了 agent 需要的三个方法：
 *  - initialize（建立会话，记录 Mcp-Session-Id）
 *  - listTools（tools/list）
 *  - callTool（tools/call）
 *
 * 服务端在 streamable-http 下会以 SSE（text/event-stream）返回结果，
 * 这里读取整段响应并从中解析出 JSON-RPC 消息。
 */
class McpClient(
    private val baseUrl: String,
    private val bearerToken: String = "",
) {
    private val json = McpJson.instance
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private var sessionId: String? = null
    private var nextId = 1

    private fun newId(): Int = nextId++

    /** 发送一个 JSON-RPC 请求并返回其中的 result 对象。 */
    private suspend fun rpc(method: String, params: JsonObject?): JsonElement =
        withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", newId())
                put("method", method)
                if (params != null) put("params", params)
            }
            val body = json.encodeToString(JsonObject.serializer(), payload)
                .toRequestBody("application/json".toMediaType())

            val builder = Request.Builder()
                .url(baseUrl)
                .addHeader("Content-Type", "application/json")
                // 必须同时接受 JSON 与 SSE，否则服务端返回 406。
                .addHeader("Accept", "application/json, text/event-stream")
                .post(body)
            if (bearerToken.isNotBlank()) {
                builder.addHeader("Authorization", "Bearer $bearerToken")
            }
            sessionId?.let { builder.addHeader("Mcp-Session-Id", it) }

            http.newCall(builder.build()).execute().use { resp ->
                // 首次 initialize 的响应头会带回会话 ID，后续请求需携带。
                resp.header("Mcp-Session-Id")?.let { sessionId = it }
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    error("MCP HTTP ${resp.code}: $raw")
                }
                val message = extractJsonRpc(raw)
                    ?: error("无法解析 MCP 响应: $raw")
                message["error"]?.let { err ->
                    error("MCP 错误: ${err.jsonObject["message"]?.jsonPrimitive?.content ?: err}")
                }
                message["result"] ?: buildJsonObject { }
            }
        }

    /**
     * 从响应体中提取 JSON-RPC 消息。
     * 兼容两种格式：纯 JSON，或 SSE（每行以 "data: " 前缀）。
     */
    private fun extractJsonRpc(raw: String): JsonObject? {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) {
            return runCatching { json.parseToJsonElement(trimmed).jsonObject }.getOrNull()
        }
        // SSE：找出最后一个包含 result/error 的 data 行。
        val dataLines = trimmed.lineSequence()
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trim() }
            .toList()
        for (line in dataLines.asReversed()) {
            val obj = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull()
            if (obj != null && (obj.containsKey("result") || obj.containsKey("error"))) {
                return obj
            }
        }
        return dataLines.lastOrNull()
            ?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
    }

    /** 建立会话。必须在其它调用之前执行一次。 */
    suspend fun initialize() {
        val params = buildJsonObject {
            put("protocolVersion", "2024-11-05")
            put("capabilities", buildJsonObject { })
            put("clientInfo", buildJsonObject {
                put("name", "scientific-agent-android")
                put("version", "1.0")
            })
        }
        rpc("initialize", params)
        // 通知服务端初始化完成（notification，无需等待响应）。
        runCatching { rpc("notifications/initialized", null) }
    }

    /** 列出服务端注册的所有工具。 */
    suspend fun listTools(): List<McpTool> {
        val result = rpc("tools/list", buildJsonObject { }).jsonObject
        val tools = result["tools"]?.let { it as? kotlinx.serialization.json.JsonArray } ?: return emptyList()
        return tools.mapNotNull { el ->
            val obj = el.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            McpTool(
                name = name,
                description = obj["description"]?.jsonPrimitive?.content ?: "",
                inputSchema = obj["inputSchema"]?.jsonObject ?: buildJsonObject { },
            )
        }
    }

    /** 调用一个工具，返回其文本化结果，供回传给 LLM。 */
    suspend fun callTool(name: String, arguments: JsonObject): String {
        val params = buildJsonObject {
            put("name", name)
            put("arguments", arguments)
        }
        val result = rpc("tools/call", params).jsonObject
        return stringifyToolResult(result)
    }

    /** 把 tools/call 的 content 数组转成给 LLM 阅读的纯文本。 */
    private fun stringifyToolResult(result: JsonObject): String {
        val content = result["content"] as? kotlinx.serialization.json.JsonArray
            ?: return result.toString()
        val sb = StringBuilder()
        for (item in content) {
            val obj = item.jsonObject
            when (obj["type"]?.jsonPrimitive?.content) {
                "text" -> sb.appendLine(obj["text"]?.jsonPrimitive?.content ?: "")
                "image" -> sb.appendLine("[图像结果: ${obj["mimeType"]?.jsonPrimitive?.content ?: "image"}]")
                else -> sb.appendLine(obj.toString())
            }
        }
        return sb.toString().trim().ifBlank { "(工具无文本输出)" }
    }
}
