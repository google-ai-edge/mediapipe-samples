package com.example.mediapipe.llminference

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.example.mediapipe.llminference.ui.screens.ChatRoute
import com.example.mediapipe.llminference.ui.screens.LoadingRoute
import com.example.mediapipe.llminference.ui.theme.LLMInferenceTheme

const val START_SCREEN = "start_screen"
const val CHAT_SCREEN = "chat_screen"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LLMInferenceTheme {
                Scaffold(
                    topBar = { AppBar() }
                ) { innerPadding ->
                    // A surface container using the 'background' color from the theme
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        val navHostController = rememberNavController()

                        NavHost(
                            navController = navHostController,
                            startDestination = START_SCREEN
                        ) {
                            composable(START_SCREEN) {
                                LoadingRoute(
                                    onModelLoaded = {
                                        navHostController.navigate(CHAT_SCREEN,
                                            navOptions { launchSingleTop = true })
                                    })
                            }

                            composable(CHAT_SCREEN) {
                                ChatRoute()
                            }
                        }
                    }
                }
            }
        }
    }

    // TopAppBar is marked as experimental in Material 3
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AppBar() {
        TopAppBar(
            title = { Text(stringResource(R.string.app_name)) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}
