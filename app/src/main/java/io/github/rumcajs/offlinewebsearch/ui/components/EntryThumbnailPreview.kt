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
import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import io.github.rumcajs.offlinewebsearch.webtoolkit.YouTubeVideoHandler

/**
 * Shared thumbnail and video preview component for web links and entries.
 *
 * Displays an embedded YouTube video player when [videoPreview] is enabled and [link] is a supported YouTube URL.
 * Otherwise renders a remote image thumbnail at full width.
 *
 * @param link Optional target link URL. Used to detect YouTube video embeds and for tap/long-press actions.
 * @param thumbnailUrl Optional thumbnail image URL.
 * @param isRestricted When true, disables video embedding and masks thumbnail content.
 * @param modifier Optional modifier applied to the preview container.
 * @param videoPreview Whether YouTube video preview is enabled.
 * @param onTap Callback invoked on single tap.
 * @param onLongPress Callback invoked on long press.
 */
@Composable
fun ThumbnailPreview(
    link: String?,
    thumbnailUrl: String?,
    isRestricted: Boolean = false,
    modifier: Modifier = Modifier,
    videoPreview: Boolean = true,
    onTap: () -> Unit = {},
    onLongPress: () -> Unit = {}
) {
    val youtubeVideoHandler = remember(link) {
        link?.let { YouTubeVideoHandler(it) }
    }

    val youtubeVideoId = remember(youtubeVideoHandler, videoPreview) {
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
    } else if (!thumbnailUrl.isNullOrBlank()) {
        RemoteImage(
            url = thumbnailUrl,
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp)
                .pointerInput(link, isRestricted) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onLongPress = { onLongPress() }
                    )
                },
            contentScale = ContentScale.Crop,
            isRestricted = isRestricted
        )
    }
}

/**
 * Convenience wrapper for [ThumbnailPreview] when displaying an [Entry].
 *
 * @param entry The entry to display thumbnail/video preview for.
 * @param isRestricted When true, disables video embedding and masks thumbnail content.
 * @param modifier Optional modifier applied to the preview container.
 * @param videoPreview Whether YouTube video preview is enabled.
 * @param onTap Callback invoked on single tap.
 * @param onLongPress Callback invoked on long press.
 */
@Composable
fun EntryThumbnailPreview(
    entry: Entry,
    isRestricted: Boolean,
    modifier: Modifier = Modifier,
    videoPreview: Boolean = true,
    onTap: () -> Unit = {},
    onLongPress: () -> Unit = {}
) {
    ThumbnailPreview(
        link = entry.link,
        thumbnailUrl = entry.thumbnail,
        isRestricted = isRestricted,
        modifier = modifier,
        videoPreview = videoPreview,
        onTap = onTap,
        onLongPress = onLongPress
    )
}
