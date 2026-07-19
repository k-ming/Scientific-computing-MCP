package com.scicalc.agent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.scicalc.agent.data.AppConfig

/**
 * 配置界面：填写 LLM（API Key 方式）与 scicalc MCP 服务参数。
 */
@Composable
fun SettingsScreen(
    initial: AppConfig,
    onSave: (AppConfig) -> Unit,
) {
    var llmBaseUrl by remember { mutableStateOf(initial.llmBaseUrl) }
    var llmApiKey by remember { mutableStateOf(initial.llmApiKey) }
    var llmModel by remember { mutableStateOf(initial.llmModel) }
    var mcpUrl by remember { mutableStateOf(initial.mcpUrl) }
    var mcpToken by remember { mutableStateOf(initial.mcpToken) }
    var systemPrompt by remember { mutableStateOf(initial.systemPrompt) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("LLM 接入（OpenAI 兼容接口）")
        OutlinedTextField(
            value = llmBaseUrl,
            onValueChange = { llmBaseUrl = it },
            label = { Text("Base URL，例如 https://api.example.com/v1") },
            modifier = Modifier.fillMaxSize(),
            singleLine = true,
        )
        OutlinedTextField(
            value = llmApiKey,
            onValueChange = { llmApiKey = it },
            label = { Text("API Key") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxSize(),
            singleLine = true,
        )
        OutlinedTextField(
            value = llmModel,
            onValueChange = { llmModel = it },
            label = { Text("模型名称") },
            modifier = Modifier.fillMaxSize(),
            singleLine = true,
        )

        Text("scicalc MCP 服务")
        OutlinedTextField(
            value = mcpUrl,
            onValueChange = { mcpUrl = it },
            label = { Text("MCP URL，例如 https://<IP>.nip.io/mcp") },
            modifier = Modifier.fillMaxSize(),
            singleLine = true,
        )
        OutlinedTextField(
            value = mcpToken,
            onValueChange = { mcpToken = it },
            label = { Text("MCP Bearer Token（可选）") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxSize(),
            singleLine = true,
        )

        Text("系统提示词")
        OutlinedTextField(
            value = systemPrompt,
            onValueChange = { systemPrompt = it },
            label = { Text("System Prompt") },
            modifier = Modifier.fillMaxSize(),
        )

        Button(
            onClick = {
                onSave(
                    AppConfig(
                        llmBaseUrl = llmBaseUrl.trim(),
                        llmApiKey = llmApiKey.trim(),
                        llmModel = llmModel.trim(),
                        mcpUrl = mcpUrl.trim(),
                        mcpToken = mcpToken.trim(),
                        systemPrompt = systemPrompt,
                    )
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            Text("保存配置")
        }
    }
}
