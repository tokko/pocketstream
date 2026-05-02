package ai.lillith.pocketstream.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.lillith.pocketstream.data.SettingsRepository
import ai.lillith.pocketstream.network.PocketStreamApi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    api: PocketStreamApi,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    var backendUrl by remember { mutableStateOf(SettingsRepository.DEFAULT_BACKEND_URL) }
    var apiKey by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    // Load saved settings
    LaunchedEffect(Unit) {
        settingsRepository.backendUrl.collect { url ->
            backendUrl = url
            api.updateConfig(url, apiKey)
        }
    }
    LaunchedEffect(Unit) {
        settingsRepository.apiKey.collect { key ->
            apiKey = key
            api.updateConfig(backendUrl, key)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Backend URL
            OutlinedTextField(
                value = backendUrl,
                onValueChange = { backendUrl = it },
                label = { Text("Backend URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("e.g. http://192.168.68.110:8080") }
            )

            // API Key
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Save button
            Button(
                onClick = {
                    api.updateConfig(backendUrl, apiKey)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Settings")
            }

            HorizontalDivider()

            // Test Connection
            Text(
                text = "Connection Test",
                style = MaterialTheme.typography.titleMedium
            )

            Button(
                onClick = {
                    isTesting = true
                    testResult = null
                    api.updateConfig(backendUrl, apiKey)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isTesting
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Test Connection")
            }

            // Test result
            LaunchedEffect(isTesting) {
                if (isTesting) {
                    val result = api.healthCheck()
                    isTesting = false
                    result.onSuccess { health ->
                        testResult = "✓ Connected! Status: ${health.status}"
                        snackbarHostState.showSnackbar("Connection successful!")
                    }.onFailure { error ->
                        testResult = "✗ Failed: ${error.message}"
                        snackbarHostState.showSnackbar("Connection failed: ${error.message}")
                    }
                }
            }

            if (testResult != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (testResult!!.startsWith("✓"))
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (testResult!!.startsWith("✓"))
                                Icons.Default.CheckCircle
                            else
                                Icons.Default.Error,
                            contentDescription = null
                        )
                        Text(testResult!!)
                    }
                }
            }

            // Save settings when changing (auto-save on back)
            DisposableEffect(backendUrl, apiKey) {
                onDispose {
                    // Schedule a coroutine to save - we use LaunchedEffect for actual saves
                }
            }
        }
    }

    // Auto-save settings when values change
    LaunchedEffect(backendUrl) {
        settingsRepository.saveBackendUrl(backendUrl)
        api.updateConfig(backendUrl, apiKey)
    }
    LaunchedEffect(apiKey) {
        settingsRepository.saveApiKey(apiKey)
        api.updateConfig(backendUrl, apiKey)
    }
}
