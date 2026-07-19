package com.scicalc.agent.data.mcp

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/** 全局共享的 Json 实例，宽松解析以兼容不同服务端。 */
object McpJson {
    @OptIn(ExperimentalSerializationApi::class)
    val instance = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        // explicitNulls 属于实验性 API，需 opt-in。
        explicitNulls = false
    }
}
