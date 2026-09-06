package io.github.rumcajs.offlinewebsearch.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import kotlinx.coroutines.launch

/**
 * Dialog wizard displayed on first run when the application has not yet been initialized.
 *
 * Prompts the user whether to configure and download recommended startup databases.
 * On acceptance, fetches the databases from DATABASES_LIST_INIT and enqueues them into
 * DatabaseUpdateWorker for sequential background download.
 *
 * @param onDismiss Callback invoked when the dialog is dismissed or wizard finishes.
 */
@Composable
fun StartupWizardDialog(
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun performInitialization() {
        isLoading = true
        errorMessage = null
        scope.launch {
            val result = AppConfigManager.initializeStartupDatabases(context)
            isLoading = false
            if (result.isSuccess) {
                val count = result.getOrNull()?.size ?: 0
                Toast.makeText(
                    context,
                    "Added $count startup database(s) for background download",
                    Toast.LENGTH_SHORT
                ).show()
                onDismiss()
            } else {
                errorMessage = result.exceptionOrNull()?.localizedMessage
                    ?: "Failed to download startup databases list."
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isLoading) {
                AppConfigManager.setInitialized(true)
                onDismiss()
            }
        },
        icon = {
            if (errorMessage != null) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AutoFixHigh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        title = {
            Text(text = if (errorMessage != null) "Setup Error" else "Setup Wizard")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(
                            text = "Fetching startup databases list...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = "Welcome to Offline Web Search! Would you like to create and download recommended startup databases?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Startup databases will be configured and downloaded in the background so you can search immediately once ready.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (!isLoading) {
                if (errorMessage != null) {
                    Button(onClick = { performInitialization() }) {
                        Text("Retry")
                    }
                } else {
                    Button(onClick = { performInitialization() }) {
                        Text("Create Databases")
                    }
                }
            }
        },
        dismissButton = {
            if (!isLoading) {
                TextButton(
                    onClick = {
                        AppConfigManager.setInitialized(true)
                        onDismiss()
                    }
                ) {
                    Text("Skip")
                }
            }
        }
    )
}
