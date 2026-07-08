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
    fun testYouTubeVideoHandlerGetVideoId() {
        val videoUrls = mapOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ" to "dQw4w9WgXcQ",
            "http://youtube.com/watch?v=dQw4w9WgXcQ" to "dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ" to "dQw4w9WgXcQ",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ" to "dQw4w9WgXcQ",
            "youtube.com/embed/dQw4w9WgXcQ" to "dQw4w9WgXcQ",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ" to "dQw4w9WgXcQ",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10s" to "dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ?t=10s" to "dQw4w9WgXcQ"
        )

        for ((url, expectedId) in videoUrls) {
            val handler = YouTubeVideoHandler(url)
            org.junit.Assert.assertEquals("Failed for URL: $url", expectedId, handler.getVideoId())
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
        val url = "https://example.com"
        val customHandler = object : PageHandler {
            override fun isHandledBy(): Boolean {
                return url.contains("example.com")
            }
            override fun getUrl(): String = url
        }

        // Without registration, example.com shouldn't match any handler
        assertNull(HandlerBuilder(url).build())

        // With registration, it should match the custom handler
        val handler = HandlerBuilder(url)
            .registerHandler(customHandler)
            .build()
        
        assertTrue(handler === customHandler)
    }

    @Test
    fun testGetFeeds() {
        // YouTubeChannelHandler with channel ID (has UID)
        val channelWithUid = "https://www.youtube.com/channel/UCfMJ2MchTSW27WSgsuxG12Q"
        val channelHandler = HandlerBuilder(channelWithUid).build()
        assertTrue(channelHandler is YouTubeChannelHandler)
        val channelFeeds = channelHandler?.getFeeds()
        org.junit.Assert.assertEquals(
            listOf("https://www.youtube.com/feeds/videos.xml?channel_id=UCfMJ2MchTSW27WSgsuxG12Q"),
            channelFeeds
        )

        // YouTubeChannelHandler instantiated with RSS URL directly
        val rssUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UCfMJ2MchTSW27WSgsuxG12Q"
        val rssHandler = YouTubeChannelHandler(rssUrl)
        org.junit.Assert.assertEquals(
            listOf("https://www.youtube.com/feeds/videos.xml?channel_id=UCfMJ2MchTSW27WSgsuxG12Q"),
            rssHandler.getFeeds()
        )

        // YouTubeChannelHandler without channel ID (custom URL/handle)
        val channelWithoutUid = "https://www.youtube.com/@youtube"
        val channelHandler2 = HandlerBuilder(channelWithoutUid).build()
        assertTrue(channelHandler2 is YouTubeChannelHandler)
        assertTrue(channelHandler2?.getFeeds()?.isEmpty() == true)

        // YouTubeVideoHandler (default getFeeds returns empty)
        val videoUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val videoHandler = HandlerBuilder(videoUrl).build()
        assertTrue(videoHandler is YouTubeVideoHandler)
        assertTrue(videoHandler?.getFeeds()?.isEmpty() == true)

        // GitHubRepositoryHandler (default getFeeds returns empty)
        val githubUrl = "https://github.com/google/guava"
        val githubHandler = HandlerBuilder(githubUrl).build()
        assertTrue(githubHandler is GitHubRepositoryHandler)
        assertTrue(githubHandler?.getFeeds()?.isEmpty() == false)
    }

    @Test
    fun testGetUrl() {
        val testUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val handler = HandlerBuilder(testUrl).build()
        org.junit.Assert.assertEquals(testUrl, handler?.getUrl())
    }

    @Test
    fun testGetChannel() {
        // YouTubeVideoHandler (returns "")
        val videoUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val videoHandler = HandlerBuilder(videoUrl).build()
        org.junit.Assert.assertEquals("", videoHandler?.getChannel())

        // YouTubeChannelHandler with Channel ID
        val ytChannelId = "https://www.youtube.com/channel/UCfMJ2MchTSW27WSgsuxG12Q"
        val ytChannelHandlerId = HandlerBuilder(ytChannelId).build()
        org.junit.Assert.assertEquals("https://www.youtube.com/channel/UCfMJ2MchTSW27WSgsuxG12Q", ytChannelHandlerId?.getChannel())

        // YouTubeChannelHandler with custom c/ path
        val ytChannelC = "https://youtube.com/c/YouTubeCreators"
        val ytChannelHandlerC = HandlerBuilder(ytChannelC).build()
        org.junit.Assert.assertEquals("", ytChannelHandlerC?.getChannel())

        // YouTubeChannelHandler with user/ path
        val ytChannelUser = "https://www.youtube.com/user/youtube"
        val ytChannelHandlerUser = HandlerBuilder(ytChannelUser).build()
        org.junit.Assert.assertEquals("", ytChannelHandlerUser?.getChannel())

        // YouTubeChannelHandler with @ handle path
        val ytChannelHandle = "https://www.youtube.com/@youtube"
        val ytChannelHandlerHandle = HandlerBuilder(ytChannelHandle).build()
        org.junit.Assert.assertEquals("", ytChannelHandlerHandle?.getChannel())

        // GitHubRepositoryHandler
        val ghUrl = "https://github.com/google/guava"
        val ghHandler = HandlerBuilder(ghUrl).build()
        org.junit.Assert.assertEquals("google", ghHandler?.getChannel())

        // RedditChannelHandler subreddit
        val subUrl = "https://www.reddit.com/r/kotlin"
        val subHandler = HandlerBuilder(subUrl).build()
        org.junit.Assert.assertEquals("kotlin", subHandler?.getChannel())

        // RedditChannelHandler user
        val redUserUrl = "https://www.reddit.com/user/some_user"
        val redUserHandler = HandlerBuilder(redUserUrl).build()
        org.junit.Assert.assertEquals("some_user", redUserHandler?.getChannel())
    }

    @Test
    fun testYouTubeChannelUidHelpers() {
        val channelUrl = "https://www.youtube.com/channel/UCfMJ2MchTSW27WSgsuxG12Q"
        val rssUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UCfMJ2MchTSW27WSgsuxG12Q"
        val customUrl = "https://www.youtube.com/@google"

        // test getUidFromChannelLink
        org.junit.Assert.assertEquals("UCfMJ2MchTSW27WSgsuxG12Q", YouTubeChannelHandler.getUidFromChannelLink(channelUrl))
        assertNull(YouTubeChannelHandler.getUidFromChannelLink(customUrl))

        // test getUidFromRssLink
        org.junit.Assert.assertEquals("UCfMJ2MchTSW27WSgsuxG12Q", YouTubeChannelHandler.getUidFromRssLink(rssUrl))
        assertNull(YouTubeChannelHandler.getUidFromRssLink(channelUrl))

        // test linkToUid
        org.junit.Assert.assertEquals("UCfMJ2MchTSW27WSgsuxG12Q", YouTubeChannelHandler.linkToUid(channelUrl))
        org.junit.Assert.assertEquals("UCfMJ2MchTSW27WSgsuxG12Q", YouTubeChannelHandler.linkToUid(rssUrl))
        assertNull(YouTubeChannelHandler.linkToUid(customUrl))
    }

    @Test
    fun testYouTubeChannelUidMemberAndUrl() {
        val channelUrl = "https://www.youtube.com/channel/UCfMJ2MchTSW27WSgsuxG12Q"
        val rssUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=UCfMJ2MchTSW27WSgsuxG12Q"
        val customUrl = "https://www.youtube.com/@google"

        val handlerFromChannel = YouTubeChannelHandler(channelUrl)
        org.junit.Assert.assertEquals("UCfMJ2MchTSW27WSgsuxG12Q", handlerFromChannel.channelUid)
        org.junit.Assert.assertEquals("https://www.youtube.com/channel/UCfMJ2MchTSW27WSgsuxG12Q", handlerFromChannel.getChannelUrl())

        val handlerFromRss = YouTubeChannelHandler(rssUrl)
        org.junit.Assert.assertEquals("UCfMJ2MchTSW27WSgsuxG12Q", handlerFromRss.channelUid)
        org.junit.Assert.assertEquals("https://www.youtube.com/channel/UCfMJ2MchTSW27WSgsuxG12Q", handlerFromRss.getChannelUrl())

        val handlerFromCustom = YouTubeChannelHandler(customUrl)
        assertNull(handlerFromCustom.channelUid)
        assertNull(handlerFromCustom.getChannelUrl())
    }

    @Test
    fun testRedditChannelHandler() {
        val subredditUrl = "https://www.reddit.com/r/kotlin"
        val userUrl = "reddit.com/user/some_user/new/"
        val postUrl = "https://www.reddit.com/r/kotlin/comments/12345/some_post"
        val redditRoot = "https://www.reddit.com"

        // Subreddit matching and feed URL
        val handler1 = HandlerBuilder(subredditUrl).build()
        assertTrue(handler1 is RedditChannelHandler)
        org.junit.Assert.assertEquals(
            listOf("https://www.reddit.com/r/kotlin/.rss"),
            handler1?.getFeeds()
        )

        // User profile matching and feed URL
        val handler2 = HandlerBuilder(userUrl).build()
        assertTrue(handler2 is RedditChannelHandler)
        org.junit.Assert.assertEquals(
            listOf("https://reddit.com/user/some_user/new/.rss"),
            handler2?.getFeeds()
        )

        // Non-channels (posts and root pages) should not match
        assertNull(HandlerBuilder(postUrl).build())
        assertNull(HandlerBuilder(redditRoot).build())
    }
}
