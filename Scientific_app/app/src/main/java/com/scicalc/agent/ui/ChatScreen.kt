package com.scicalc.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.scicalc.agent.data.ChatMessage
import com.scicalc.agent.data.Role

/** 聊天界面：展示对话与工具调用过程，底部输入框发送消息。 */
@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    busy: Boolean,
    onSend: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
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
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val (bg, align) = when (msg.role) {
        Role.USER -> MaterialTheme.colorScheme.primaryContainer to Alignment.End
        Role.ASSISTANT -> MaterialTheme.colorScheme.secondaryContainer to Alignment.Start
        Role.TOOL -> Color(0xFFEEEEEE) to Alignment.Start
        Role.SYSTEM -> MaterialTheme.colorScheme.errorContainer to Alignment.Start
    }
    val label = when (msg.role) {
        Role.USER -> "我"
        Role.ASSISTANT -> "助手"
        Role.TOOL -> "工具 ${msg.toolName ?: ""}"
        Role.SYSTEM -> "系统"
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .align(align)
                .clip(RoundedCornerShape(12.dp))
                .background(bg)
                .padding(10.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(msg.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
