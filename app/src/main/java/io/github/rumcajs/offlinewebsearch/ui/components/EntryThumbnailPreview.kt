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
    videoPreview: Boolean = true,
    onTap: () -> Unit = {},
    onLongPress: () -> Unit = {}
) {
    val youtubeVideoHandler = remember(entry.link) {
        entry.link?.let { YouTubeVideoHandler(it) }
    }

    val youtubeVideoId = remember(youtubeVideoHandler) {
        if (videoPreview && youtubeVideoHandler?.isHandledBy() == true) {
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
                    WebView(context).also { webView ->
                        webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                        webView.settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            allowContentAccess = true
                            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        }
                        android.webkit.CookieManager.getInstance().apply {
                            setAcceptCookie(true)
                            setAcceptThirdPartyCookies(webView, true)
                        }
                        webView.webViewClient = WebViewClient()
                        webView.webChromeClient = WebChromeClient()
                        // loadDataWithBaseURL has no headers parameter; extra headers
                        // can only be passed via the two-argument loadUrl overload.
                        webView.loadUrl(
                            "https://www.youtube-nocookie.com/embed/$youtubeVideoId?rel=0&playsinline=1",
                            mapOf("Referer" to "https://www.youtube.com")
                        )
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
