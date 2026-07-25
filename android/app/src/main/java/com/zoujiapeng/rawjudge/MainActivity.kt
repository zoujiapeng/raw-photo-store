package com.zoujiapeng.rawjudge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
    RAWJudgeTheme(darkTheme = state.settings.darkMode) {
        RawJudgeApp(state = state, actions = viewModel)
    }
}
