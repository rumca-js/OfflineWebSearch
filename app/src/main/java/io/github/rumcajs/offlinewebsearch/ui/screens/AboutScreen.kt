package io.github.rumcajs.offlinewebsearch.ui.screens

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AboutScreen() {
    val uriHandler = LocalUriHandler.current
    val projectUrl = "https://github.com/rumca-js/OfflineWebSearch"
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val versionName = try {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        packageInfo.versionName ?: "Unknown"
    } catch (e: Exception) {
        "1.0"
    }

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
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = "Version $versionName",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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

        _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.SectionTitle("Ready Databases")
        Text(
            text = "Explore and import pre-compiled offline databases to expand your search options. You can add them in the Options tab.",
            style = MaterialTheme.typography.bodyMedium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.DatabaseItem(
            name = "Awesome Database Feeds",
            description = "Pre-compiled databases mapping sites to their RSS feeds.",
            url = "https://github.com/rumca-js/awesome-database-feeds"
        )

        _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.DatabaseItem(
            name = "Awesome Database Top",
            description = "Curated databases of top websites and web locations.",
            url = "https://github.com/rumca-js/awesome-database-top"
        )

        _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.screens.DatabaseItem(
            name = "Awesome Database AwesomeLists",
            description = "Offline search databases built from community-curated awesome lists.",
            url = "https://github.com/rumca-js/awesome-database-awesomelists"
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
    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
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

@Composable
private fun DatabaseItem(name: String, description: String, url: String) {
    val uriHandler = LocalUriHandler.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = url,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { uriHandler.openUri(url) }
            )
        }
    }
}
