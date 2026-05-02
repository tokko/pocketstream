package ai.lillith.pocketstream.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.lillith.pocketstream.data.SettingsRepository
import ai.lillith.pocketstream.network.PocketStreamApi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    settingsRepository: SettingsRepository,
    api: PocketStreamApi,
    onNavigateToSettings: () -> Unit,
    sharedUrl: String? = null,
    snackbarHostState: SnackbarHostState
) {
    var isProcessing by remember { mutableStateOf(false) }
    var lastUrl by remember { mutableStateOf<String?>(null) }
    var lastResult by remember { mutableStateOf<String?>(null) }
    var hasEnqueuedSharedUrl by remember { mutableStateOf(false) }

    // Load last result from DataStore
    LaunchedEffect(Unit) {
        settingsRepository.lastResult.collect { pair ->
            if (pair != null) {
                lastUrl = pair.first
                lastResult = pair.second
            }
        }
    }

    // Auto-enqueue shared URL
    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null && !hasEnqueuedSharedUrl) {
            hasEnqueuedSharedUrl = true
            isProcessing = true
            val result = api.enqueue(sharedUrl)
            isProcessing = false

            result.onSuccess { response ->
                val msg = if (response.message != null) {
                    "${response.status}: ${response.message}"
                } else {
                    "Added to queue! (id: ${response.id})"
                }
                lastUrl = sharedUrl
                lastResult = msg
                settingsRepository.saveLastResult(sharedUrl, msg)
                snackbarHostState.showSnackbar("✓ $msg")
            }.onFailure { error ->
                val msg = "Failed: ${error.message}"
                lastUrl = sharedUrl
                lastResult = msg
                settingsRepository.saveLastResult(sharedUrl, msg)
                snackbarHostState.showSnackbar("✗ $msg")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // App icon/branding area
            Icon(
                imageVector = Icons.Default.PlayCircleFilled,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "PocketStream",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = "Share YouTube videos to add them to your podcast feed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Last result card
            if (lastUrl != null && lastResult != null) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Last Action",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = lastUrl!!,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = lastResult!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (lastResult!!.startsWith("Added") || lastResult!!.startsWith("queued"))
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No videos queued yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Share a YouTube link from any app to get started",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            if (isProcessing) {
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Adding to queue...",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
