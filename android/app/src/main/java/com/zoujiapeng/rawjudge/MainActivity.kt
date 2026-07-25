package com.zoujiapeng.rawjudge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zoujiapeng.rawjudge.domain.AppUiState
import com.zoujiapeng.rawjudge.ui.RawJudgeApp
import com.zoujiapeng.rawjudge.ui.theme.RAWJudgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { RawJudgeRoot() }
    }
}

@Composable
private fun RawJudgeRoot(viewModel: RawJudgeViewModel = viewModel()) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    var browseOffline by remember { mutableStateOf(false) }
    LaunchedEffect(state.isOnline) {
        if (state.isOnline) browseOffline = false
    }
    RAWJudgeTheme(darkTheme = state.settings.darkMode) {
        when {
            !state.isLoading && !state.isOnline && !browseOffline -> ConnectionScreen(
                state = state,
                connect = viewModel::changeServerUrl,
                browseOffline = { browseOffline = true }
            )
            else -> RawJudgeApp(state = state, actions = viewModel)
        }
    }
}

@Composable
private fun ConnectionScreen(
    state: AppUiState,
    connect: (String) -> Unit,
    browseOffline: () -> Unit
) {
    var serverUrl by remember(state.serverUrl) { mutableStateOf(state.serverUrl) }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("连接 RAWJudge", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "模拟器使用 http://10.0.2.2:8000。真机请输入运行后端电脑的局域网地址，例如 http://192.168.1.10:8000；公网部署应使用 HTTPS。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("后端地址") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            state.toastMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { connect(serverUrl) },
                enabled = serverUrl.startsWith("http://") || serverUrl.startsWith("https://"),
                modifier = Modifier.fillMaxWidth()
            ) { Text("连接并建立会话") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = browseOffline, modifier = Modifier.fillMaxWidth()) {
                Text("只浏览离线官方样例")
            }
        }
    }
}
