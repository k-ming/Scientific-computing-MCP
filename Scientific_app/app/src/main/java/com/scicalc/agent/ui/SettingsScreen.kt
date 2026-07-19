package com.scicalc.agent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.scicalc.agent.data.AppConfig

/**
 * 配置界面：填写 LLM（API Key 方式）与 scicalc MCP 服务参数。
 * 模型支持从服务端拉取列表后下拉选择。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    initial: AppConfig,
    models: List<String>,
    loadingModels: Boolean,
    onRefreshModels: (baseUrl: String, apiKey: String) -> Unit,
    onSave: (AppConfig) -> Unit,
) {
    var llmBaseUrl by remember { mutableStateOf(initial.llmBaseUrl) }
    var llmApiKey by remember { mutableStateOf(initial.llmApiKey) }
    var llmModel by remember { mutableStateOf(initial.llmModel) }
    var mcpUrl by remember { mutableStateOf(initial.mcpUrl) }
    var mcpToken by remember { mutableStateOf(initial.mcpToken) }
    var systemPrompt by remember { mutableStateOf(initial.systemPrompt) }
    var expanded by remember { mutableStateOf(false) }

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
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = llmApiKey,
            onValueChange = { llmApiKey = it },
            label = { Text("API Key") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        // 模型选择：下拉框 + 刷新按钮。
        Text("模型")
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    value = llmModel,
                    onValueChange = { llmModel = it },
                    label = { Text("模型名称（可下拉选择或手填）") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryEditable, true),
                )
                ExposedDropdownMenu(
                    expanded = expanded && models.isNotEmpty(),
                    onDismissRequest = { expanded = false },
                ) {
                    models.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m) },
                            onClick = {
                                llmModel = m
                                expanded = false
                            },
                        )
                    }
                }
            }
            if (loadingModels) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(start = 8.dp).size(24.dp)
                )
            } else {
                OutlinedButton(
                    onClick = { onRefreshModels(llmBaseUrl.trim(), llmApiKey.trim()) },
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text("刷新")
                }
            }
        }
        if (models.isNotEmpty()) {
            Text(
                "共 ${models.size} 个可用模型",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
        }

        Text("scicalc MCP 服务")
        OutlinedTextField(
            value = mcpUrl,
            onValueChange = { mcpUrl = it },
            label = { Text("MCP URL（路线2留空）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = mcpToken,
            onValueChange = { mcpToken = it },
            label = { Text("MCP Bearer Token（可选）") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Text("系统提示词")
        OutlinedTextField(
            value = systemPrompt,
            onValueChange = { systemPrompt = it },
            label = { Text("System Prompt") },
            modifier = Modifier.fillMaxWidth(),
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
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("保存配置")
        }
    }
}
