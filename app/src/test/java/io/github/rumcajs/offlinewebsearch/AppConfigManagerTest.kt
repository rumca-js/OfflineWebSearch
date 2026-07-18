package io.github.rumcajs.offlinewebsearch

import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.AppConfiguration
import io.github.rumcajs.offlinewebsearch.data.DatabaseConfiguration
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.OrderBy
import io.github.rumcajs.offlinewebsearch.data.ViewStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
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
            // Corrected: databases is now a Map, so we look up by key (URL)
            assertNotNull(config.databases[url1])
            assertEquals(url1, config.databases[url1]?.url)

            val url2 = "http://example.com/db2"
            AppConfigManager.updateDatabase(url1, url2)
            config = AppConfigManager.config.first()
            assertEquals(1, config.databases.size)
            // Corrected: Check that old key is gone, and the new key/state is present
            assertTrue(!config.databases.containsKey(url1))
            assertNotNull(config.databases[url2])
            assertEquals(url2, config.databases[url2]?.url)

            AppConfigManager.removeDatabase(url2)
            config = AppConfigManager.config.first()
            assertTrue(config.databases.isEmpty())
        }

        @Test
        fun testSerialization() {
            val originalConfig = AppConfiguration(
                // Corrected: databases is now Map<String, DatabaseState>
                databases = mapOf(
                    "http://example.com/db1" to DatabaseState.fromUrl("http://example.com/db1"),
                    "http://example.com/db2" to DatabaseState.fromUrl("http://example.com/db2")
                ),
                activeDatabase = "http://example.com/db1",
                userAge = 25,
                defaultDbConfig = DatabaseConfiguration(
                    directLinks = true,
                    showIcons = true,
                    videoPreview = true,
                    orderBy = OrderBy.DATE_CREATED,
                    viewStyle = ViewStyle.GALLERY
                ),
                dbConfigs = mapOf(
                    "http://example.com/db1" to DatabaseConfiguration(
                        directLinks = false,
                        showIcons = false,
                        videoPreview = false,
                        orderBy = OrderBy.DATE_PUBLISHED,
                        viewStyle = ViewStyle.STANDARD
                    )
                )
            )
            val jsonString = Json.encodeToString(originalConfig)
            val decodedConfig = Json.decodeFromString<AppConfiguration>(jsonString)

            assertEquals(originalConfig.databases, decodedConfig.databases)
            assertEquals(originalConfig.activeDatabase, decodedConfig.activeDatabase)
            assertEquals(originalConfig.userAge, decodedConfig.userAge)
            assertEquals(originalConfig.defaultDbConfig.directLinks, decodedConfig.defaultDbConfig.directLinks)
            assertEquals(originalConfig.defaultDbConfig.showIcons, decodedConfig.defaultDbConfig.showIcons)

            // Assert that the active database's configuration is returned by dbconfig
            assertEquals(false, decodedConfig.dbconfig.directLinks)
            assertEquals(OrderBy.DATE_PUBLISHED, decodedConfig.dbconfig.orderBy)
            assertEquals(ViewStyle.STANDARD, decodedConfig.dbconfig.viewStyle)

            // Assert that when active database is changed or cleared, dbconfig falls back to default
            val configWithNoActive = decodedConfig.copy(activeDatabase = null)
            assertEquals(true, configWithNoActive.dbconfig.directLinks)
            assertEquals(OrderBy.DATE_CREATED, configWithNoActive.dbconfig.orderBy)
            assertEquals(ViewStyle.GALLERY, configWithNoActive.dbconfig.viewStyle)
    }

    @Test
    fun testUnzipDatabaseBytes() {
        val dbContent = "dummy db content".toByteArray()
        val baos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(baos).use { zos ->
            val entry = java.util.zip.ZipEntry("test.db")
            zos.putNextEntry(entry)
            zos.write(dbContent)
            zos.closeEntry()
        }
        val zipBytes = baos.toByteArray()
        val result = AppConfigManager.unzipDatabaseBytes(zipBytes)
        assertNotNull(result)
        assertEquals("dummy db content", String(result!!, Charsets.UTF_8))
    }
}
