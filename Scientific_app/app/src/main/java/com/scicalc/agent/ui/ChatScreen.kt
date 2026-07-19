package com.scicalc.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scicalc.agent.data.ChatMessage
import com.scicalc.agent.data.Role

/** 聊天界面：展示对话与工具调用过程，底部输入框发送消息。 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    busy: Boolean,
    onSend: (String) -> Unit,
    onClear: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // 收起键盘并清除输入框焦点的公共动作。
    fun dismissKeyboard() {
        keyboard?.hide()
        focusManager.clearFocus()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部栏：标题 + 一键清空按钮。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "对话",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { if (messages.isNotEmpty()) showClearDialog = true },
                enabled = messages.isNotEmpty() && !busy,
            ) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "清空聊天记录")
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                // 点击聊天区域任意位置收起键盘。
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { dismissKeyboard() })
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages) { msg -> MessageBubble(msg) }
        }

        if (busy) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                label = { Text("输入问题") },
            )
            Button(
                onClick = {
                    val text = input.trim()
                    if (text.isNotEmpty()) {
                        dismissKeyboard()
                        onSend(text)
                        input = ""
                    }
                },
                enabled = !busy,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text("发送")
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空聊天记录") },
            text = { Text("确定要清空当前所有对话吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    showClearDialog = false
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    // 用户与助手采用对比鲜明的配色，工具/系统消息用中性/警示色。
    val bg = when (msg.role) {
        Role.USER -> Color(0xFF2E7D32)       // 深绿：我
        Role.ASSISTANT -> Color(0xFF1565C0)  // 深蓝：助手
        Role.TOOL -> Color(0xFFECEFF1)       // 浅灰：工具
        Role.SYSTEM -> Color(0xFFC62828)     // 红：系统提示
    }
    val textColor = when (msg.role) {
        Role.TOOL -> Color(0xFF263238)
        else -> Color.White
    }
    val align = if (msg.role == Role.USER) Alignment.End else Alignment.Start
    val label = when (msg.role) {
        Role.USER -> "我"
        Role.ASSISTANT -> "助手"
        Role.TOOL -> "工具 ${msg.toolName ?: ""}"
        Role.SYSTEM -> "系统"
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .align(if (align == Alignment.End) Alignment.CenterEnd else Alignment.CenterStart)
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bg)
                .padding(10.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = FontWeight.Bold,
            )
            Text(
                msg.text,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
            )
        }
    }
}
