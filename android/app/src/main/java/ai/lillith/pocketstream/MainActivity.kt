package ai.lillith.pocketstream

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHostState
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsRepository = SettingsRepository(this)
        val api = PocketStreamApi(
            SettingsRepository.DEFAULT_BACKEND_URL,
            ""
        )

        // Extract shared URL if launched via share intent
        val sharedUrl = extractSharedUrl(intent)

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
                            sharedUrl = sharedUrl,
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
        // Handle subsequent share intents while activity is alive
        // For simplicity, recreate to pick up new shared URL
        val sharedUrl = extractSharedUrl(intent)
        if (sharedUrl != null) {
            recreate()
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
        // Match YouTube URLs in the shared text
        val patterns = listOf(
            Regex("""https?://(?:www\.)?youtube\.com/watch\?v=\S+"""),
            Regex("""https?://(?:www\.)?youtube\.com/shorts/\S+"""),
            Regex("""https?://youtu\.be/\S+"""),
            Regex("""https?://(?:m\.)?youtube\.com/watch\?v=\S+"""),
            Regex("""https?://music\.youtube\.com/watch\?v=\S+""")
        )

        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                // Clean up trailing punctuation/whitespace
                return match.value.trimEnd(')', ']', '}', ',', '.', ' ', '\n')
            }
        }
        return null
    }
}
