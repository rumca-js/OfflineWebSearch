package io.github.rumcajs.offlinewebsearch.ui.components

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest

@Composable
fun RemoteImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    showErrorText: Boolean = true,
    isRestricted: Boolean = false
) {
    val context = LocalContext.current
    var imageError by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (url == null || isRestricted) {
            Icon(
                imageVector = Icons.Default.ImageNotSupported,
                contentDescription = if (isRestricted) "Restricted Content" else "No Image",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.outline
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(true)
                    .listener(
                        onError = { _, result ->
                            val errorMsg = result.throwable.message ?: "Unknown error"
                            imageError = errorMsg
                            Log.e("RemoteImage", "Image load failed for $url: $errorMsg")
                        }
                    )
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                onState = { state ->
                    if (state is AsyncImagePainter.State.Error) {
                        Log.e("RemoteImage", "AsyncImage State Error: ${state.result.throwable.message}")
                    }
                }
            )

            if (imageError != null && showErrorText) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ImageNotSupported,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Error",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
