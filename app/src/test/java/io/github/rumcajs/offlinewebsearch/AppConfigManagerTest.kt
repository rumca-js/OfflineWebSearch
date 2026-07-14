package io.github.rumcajs.offlinewebsearch

import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.ViewStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConfigManagerTest {

    @Test
    fun testDefaultConfig() = runBlocking {
        val config = AppConfigManager.config.first()
        assertFalse(config.dbconfig.directLinks)
        assertFalse(config.dbconfig.showIcons)
    }

    @Test
    fun testUpdateDirectLinks() = runBlocking {
        AppConfigManager.setDirectLinks(true)
        val config = AppConfigManager.config.first()
        assertTrue(config.dbconfig.directLinks)
        
        // Reset for other tests if needed, though they run in parallel or sequence
        AppConfigManager.setDirectLinks(false)
    }

    @Test
    fun testUpdateShowIcons() = runBlocking {
        AppConfigManager.setShowIcons(true)
        val config = AppConfigManager.config.first()
        assertTrue(config.dbconfig.showIcons)
        
        AppConfigManager.setShowIcons(false)
    }

    @Test
    fun testUpdateViewStyle() = runBlocking {
        val initialConfig = AppConfigManager.config.first()
        assertEquals(ViewStyle.SEARCH_ENGINE, initialConfig.dbconfig.viewStyle)

        AppConfigManager.setViewStyle(
            ViewStyle.GALLERY)
        var config = AppConfigManager.config.first()
        assertEquals(ViewStyle.GALLERY, config.dbconfig.viewStyle)

        AppConfigManager.setViewStyle(
            ViewStyle.STANDARD)
        config = AppConfigManager.config.first()
        assertEquals(ViewStyle.STANDARD, config.dbconfig.viewStyle)

        // Reset
        AppConfigManager.setViewStyle(
            ViewStyle.SEARCH_ENGINE)
    }

    @Test
    fun testDatabaseManagement() = runBlocking {
        val initialConfig = AppConfigManager.config.first()
        assertTrue(initialConfig.databases.isEmpty())

        val url1 = "http://example.com/db1"
        AppConfigManager.addDatabase(url1)
        var config = AppConfigManager.config.first()
        assertEquals(1, config.databases.size)
        assertEquals(url1, config.databases[0])

        val url2 = "http://example.com/db2"
        AppConfigManager.updateDatabase(url1, url2)
        config = AppConfigManager.config.first()
        assertEquals(1, config.databases.size)
        assertEquals(url2, config.databases[0])

        AppConfigManager.removeDatabase(url2)
        config = AppConfigManager.config.first()
        assertTrue(config.databases.isEmpty())
    }
}
