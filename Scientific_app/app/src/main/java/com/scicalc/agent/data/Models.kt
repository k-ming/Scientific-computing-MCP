package com.scicalc.agent.data

/** 聊天消息在 UI 上的角色。 */
enum class Role { USER, ASSISTANT, TOOL, SYSTEM }

/** UI 层展示用的一条消息。 */
data class ChatMessage(
    val role: Role,
    val text: String,
    /** 可选：工具调用的展示名称，仅当 role == TOOL 时有意义。 */
    val toolName: String? = null,
)

/** 应用配置项。 */
data class AppConfig(
    // LLM（OpenAI 兼容接口）配置
    val llmBaseUrl: String = "",
    val llmApiKey: String = "",
    val llmModel: String = "",
    // 远程 MCP 服务配置
    val mcpUrl: String = "",
    // 可选：MCP 服务如需鉴权时携带的 Bearer Token
    val mcpToken: String = "",
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
) {
    val isLlmConfigured: Boolean
        get() = llmBaseUrl.isNotBlank() && llmApiKey.isNotBlank() && llmModel.isNotBlank()

    companion object {
        const val DEFAULT_SYSTEM_PROMPT =
            "你是一个科学计算助手。当需要进行矩阵运算、统计、积分、求根、" +
                "数据分析或绘图时，调用提供的工具来完成，并用简洁中文解释结果。"
    }
}
