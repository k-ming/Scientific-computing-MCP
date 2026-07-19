package com.scicalc.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.scicalc.agent.data.AppSettings
import com.scicalc.agent.ui.ChatScreen
import com.scicalc.agent.ui.ChatViewModel
import com.scicalc.agent.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels {
        val settings = AppSettings(applicationContext)
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                ChatViewModel(settings) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(viewModel)
                }
            }
        }
    }
}

@Composable
private fun AppRoot(viewModel: ChatViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    val messages by viewModel.messages.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val config by viewModel.config.collectAsState()
    val models by viewModel.models.collectAsState()
    val loadingModels by viewModel.loadingModels.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Chat, contentDescription = "对话") },
                    label = { Text("对话") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "设置") },
                    label = { Text("设置") },
                )
            }
        }
    ) { padding ->
        when (tab) {
            0 -> androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
                ChatScreen(
                    messages = messages,
                    busy = busy,
                    onSend = viewModel::send,
                    onClear = viewModel::clearChat,
                )
            }
            else -> androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
                SettingsScreen(
                    initial = config,
                    models = models,
                    loadingModels = loadingModels,
                    onRefreshModels = viewModel::refreshModels,
                    onSave = viewModel::saveConfig,
                )
            }
        }
    }
}
