package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.octoavr.ui.OctoIdeScreen
import com.example.octoavr.ui.OctoIdeViewModel
import com.example.ui.theme.OctoAVR32ATheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            OctoAVR32ATheme {
                val ideViewModel: OctoIdeViewModel = viewModel()
                OctoIdeScreen(viewModel = ideViewModel)
            }
        }
    }
}
