package io.github.rumcajs.offlinewebsearch.handler

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandlerBuilderTest {

    @Test
    fun testYouTubeVideoHandler() {
        val videoUrls = listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "http://youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
            "youtube.com/embed/dQw4w9WgXcQ",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ"
        )

        for (url in videoUrls) {
            val handler = HandlerBuilder(url).build()
            assertTrue("URL $url should be handled by YouTubeVideoHandler", handler is YouTubeVideoHandler)
        }
    }

    @Test
    fun testYouTubeChannelHandler() {
        val channelUrls = listOf(
            "https://www.youtube.com/channel/UCfMJ2MchTSW27WSgsuxG12Q",
            "https://youtube.com/c/YouTubeCreators",
            "https://www.youtube.com/user/youtube",
            "https://www.youtube.com/@youtube",
            "m.youtube.com/@google"
        )

        for (url in channelUrls) {
            val handler = HandlerBuilder(url).build()
            assertTrue("URL $url should be handled by YouTubeChannelHandler", handler is YouTubeChannelHandler)
        }
    }

    @Test
    fun testGitHubRepositoryHandler() {
        val repoUrls = listOf(
            "https://github.com/google/guava",
            "https://www.github.com/kotlin/kotlinx.coroutines",
            "github.com/rumca-js/OfflineWebSearch"
        )

        for (url in repoUrls) {
            val handler = HandlerBuilder(url).build()
            assertTrue("URL $url should be handled by GitHubRepositoryHandler", handler is GitHubRepositoryHandler)
        }
    }

    @Test
    fun testGitHubNonRepoUrls() {
        val nonRepoUrls = listOf(
            "https://github.com",
            "https://github.com/",
            "https://github.com/pricing",
            "https://github.com/features",
            "https://github.com/settings/profile"
        )

        for (url in nonRepoUrls) {
            val handler = HandlerBuilder(url).build()
            assertNull("URL $url should not match GitHubRepositoryHandler", handler)
        }
    }

    @Test
    fun testCustomHandlerRegistration() {
        val customHandler = object : PageHandler {
            override fun isHandledBy(link: String): Boolean {
                return link.contains("example.com")
            }
        }

        // Without registration, example.com shouldn't match any handler
        assertNull(HandlerBuilder("https://example.com").build())

        // With registration, it should match the custom handler
        val handler = HandlerBuilder("https://example.com")
            .registerHandler(customHandler)
            .build()
        
        assertTrue(handler === customHandler)
    }
}
