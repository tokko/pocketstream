package ai.lillith.pocketstream

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ai.lillith.pocketstream.data.SettingsRepository
import ai.lillith.pocketstream.network.PocketStreamApi
import ai.lillith.pocketstream.ui.MainScreen
import ai.lillith.pocketstream.ui.SettingsScreen
import ai.lillith.pocketstream.ui.theme.PocketStreamTheme

class MainActivity : ComponentActivity() {
    private val sharedUrlState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsRepository = SettingsRepository(this)
        val api = PocketStreamApi(
            SettingsRepository.DEFAULT_BACKEND_URL,
            ""
        )

        // Set initial shared URL
        sharedUrlState.value = extractSharedUrl(intent)

        setContent {
            PocketStreamTheme {
                val navController = rememberNavController()
                val snackbarHostState = remember { SnackbarHostState() }

                NavHost(
                    navController = navController,
                    startDestination = "main"
                ) {
                    composable("main") {
                        MainScreen(
                            settingsRepository = settingsRepository,
                            api = api,
                            onNavigateToSettings = { navController.navigate("settings") },
                            sharedUrl = sharedUrlState.value,
                            snackbarHostState = snackbarHostState
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            settingsRepository = settingsRepository,
                            api = api,
                            onBack = { navController.popBackStack() },
                            snackbarHostState = snackbarHostState
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val url = extractSharedUrl(intent)
        if (url != null) {
            sharedUrlState.value = url
        }
    }

    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
            return extractYouTubeUrl(text)
        }
        return null
    }

    private fun extractYouTubeUrl(text: String): String? {
        // Extract video ID from various YouTube URL formats
        val videoIdPattern = Regex(
            """(?:youtube\.com/(?:watch\?.*v=|shorts/|embed/)|youtu\.be/|music\.youtube\.com/watch\?.*v=)([a-zA-Z0-9_-]{11})"""
        )
        val match = videoIdPattern.find(text)
        return match?.let { "https://www.youtube.com/watch?v=${it.groupValues[1]}" }
    }
}
