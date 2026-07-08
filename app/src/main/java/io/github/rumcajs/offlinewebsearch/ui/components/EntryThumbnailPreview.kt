package io.github.rumcajs.offlinewebsearch.ui.components

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rumcajs.offlinewebsearch.data.Entry
import io.github.rumcajs.offlinewebsearch.handler.YouTubeVideoHandler

@Composable
fun EntryThumbnailPreview(
    entry: Entry,
    isRestricted: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
    onLongPress: () -> Unit = {}
) {
    val youtubeVideoHandler = remember(entry.link) {
        entry.link?.let { YouTubeVideoHandler(it) }
    }

    val youtubeVideoId = remember(youtubeVideoHandler) {
        if (youtubeVideoHandler?.isHandledBy() == true) {
            youtubeVideoHandler.getVideoId()
        } else {
            null
        }
    }

    if (youtubeVideoId != null && !isRestricted) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            loadWithOverviewMode = true
                            useWideViewPort = true
                        }
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        val embedHtml = """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                <style>
                                    body { margin: 0; padding: 0; background-color: black; }
                                    .video-container { position: relative; padding-bottom: 56.25%; height: 0; overflow: hidden; }
                                    .video-container iframe { position: absolute; top: 0; left: 0; width: 100%; height: 100%; border: 0; }
                                </style>
                            </head>
                            <body>
                                <div class="video-container">
                                    <iframe src="https://www.youtube.com/embed/$youtubeVideoId" allowfullscreen></iframe>
                                </div>
                            </body>
                            </html>
                        """.trimIndent()
                        loadDataWithBaseURL("https://www.youtube.com", embedHtml, "text/html", "UTF-8", null)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    } else if (entry.thumbnail != null) {
        RemoteImage(
            url = entry.thumbnail,
            modifier = modifier
                .heightIn(min = 100.dp, max = 300.dp)
                .pointerInput(entry.link) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onLongPress = { onLongPress() }
                    )
                },
            contentScale = ContentScale.Fit,
            isRestricted = isRestricted
        )
    }
}
