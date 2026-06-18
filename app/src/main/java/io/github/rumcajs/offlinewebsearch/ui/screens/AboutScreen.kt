package io.github.rumcajs.offlinewebsearch.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AboutScreen() {
    val uriHandler = LocalUriHandler.current
    val projectUrl = "https://github.com/rumca-js/OfflineWebSearch"
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "Offline Web Search",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "A fast, lightweight Android application for searching a curated collection of popular websites, YouTube channels, and X channels.",
            style = MaterialTheme.typography.bodyLarge
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.SectionTitle("Features")
        _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.FeatureItem("Instant search across a large collection of well-known domains")
        _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.FeatureItem("Simple and lightweight interface")
        _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.FeatureItem("Fast local search with no waiting for server responses")
        _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.FeatureItem("Privacy-friendly: no network requests required for searching")
        
        Spacer(modifier = Modifier.height(24.dp))

        _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.SectionTitle("Why Use Offline Web Search?")
        Text(
            text = "Finding a website can be faster than opening a browser and typing a full URL. Offline Web Search provides a searchable index of popular domains directly on your device.",
            style = MaterialTheme.typography.bodyMedium
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.SectionTitle("Permissions")
        Text(
            text = "The app requires minimal permissions and does not rely on remote services for searching.",
            style = MaterialTheme.typography.bodyMedium
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.SectionTitle("Open Source")
        Text(
            text = "This project is open source and welcomes contributions, bug reports, and suggestions.",
            style = MaterialTheme.typography.bodyMedium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = projectUrl,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable { uriHandler.openUri(projectUrl) }
        )
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(4.dp))
    Divider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun FeatureItem(feature: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(text = "• ", style = MaterialTheme.typography.bodyMedium)
        Text(text = feature, style = MaterialTheme.typography.bodyMedium)
    }
}
