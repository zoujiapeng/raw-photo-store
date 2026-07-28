package com.zoujiapeng.rawjudge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ConnectionBackdrop(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.onBackground
                ) {
                    Text(
                        "R",
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        color = MaterialTheme.colorScheme.background,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "RAWJudge",
                    modifier = Modifier.padding(start = 10.dp),
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2f).sp
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 18.dp
            ) {
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.CloudOff, contentDescription = null)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "连接你的摄影社区",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            "填写可访问的 FastAPI 地址。模拟器使用 10.0.2.2；真机使用电脑局域网 IP；公网部署应使用 HTTPS。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("后端地址") },
                        supportingText = { Text("例如 http://192.168.1.10:8000") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.toastMessage?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                    Button(
                        onClick = { connect(serverUrl) },
                        enabled = serverUrl.startsWith("http://") || serverUrl.startsWith("https://"),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("连接并建立会话")
                        Spacer(Modifier.size(8.dp))
                        Icon(Icons.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    OutlinedButton(
                        onClick = browseOffline,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(Icons.Outlined.Collections, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("浏览离线官方样例")
                    }
                }
            }

            Text(
                "RAW 必须随作品提交 · 社交数据不参与质量分",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.52f),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun ConnectionBackdrop(modifier: Modifier = Modifier) {
    val base = MaterialTheme.colorScheme.background
    val accent = MaterialTheme.colorScheme.tertiary
    Canvas(modifier) {
        drawRect(base)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(accent.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(size.width * 0.82f, size.height * 0.18f),
                radius = size.minDimension * 0.75f
            ),
            radius = size.minDimension * 0.75f,
            center = Offset(size.width * 0.82f, size.height * 0.18f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF67A9C8).copy(alpha = 0.13f), Color.Transparent),
                center = Offset(size.width * 0.05f, size.height * 0.82f),
                radius = size.minDimension * 0.65f
            ),
            radius = size.minDimension * 0.65f,
            center = Offset(size.width * 0.05f, size.height * 0.82f)
        )
    }
}
