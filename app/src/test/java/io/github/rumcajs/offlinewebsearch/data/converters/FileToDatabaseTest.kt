package io.github.rumcajs.offlinewebsearch.data.converters

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.RepositoryTestHelper
import io.github.rumcajs.offlinewebsearch.data.repositories.EntryRepository
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileToDatabaseTest {

    private lateinit var context: Context
    private lateinit var dbState: DatabaseState
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val (state, file) = RepositoryTestHelper.setup(context)
        dbState = state
        dbFile = file
    }

    @After
    fun tearDown() {
        dbFile.delete()
    }

    @Test
    fun testIsSupported() {
        assertTrue(FileToDatabase.isSupported("https://example.com/data.json"))
        assertTrue(FileToDatabase.isSupported("https://example.com/feeds.opml"))
        assertTrue(FileToDatabase.isSupported("DATA.JSON"))
        assertTrue(FileToDatabase.isSupported("FEEDS.OPML"))
        assertFalse(FileToDatabase.isSupported("https://example.com/data.db"))
        assertFalse(FileToDatabase.isSupported("https://example.com/data.txt"))
    }

    @Test
    fun testImportJsonFile() {
        val jsonContent = """
            [
                {
                    "id": 1,
                    "link": "https://example.com/article1",
                    "title": "Article One",
                    "description": "Desc One"
                }
            ]
        """.trimIndent()

        val jsonFile = File(context.cacheDir, "test_entries.json")
        jsonFile.writeText(jsonContent)

        try {
            FileToDatabase.importToDatabase(jsonFile, dbFile)

            val count = runBlocking { EntryRepository.countEntries(context, dbState) }
            assertTrue(count >= 1)
        } finally {
            jsonFile.delete()
        }
    }

    @Test
    fun testImportOpmlFile() {
        val opmlContent = """
            <opml version="1.0">
                <head><title>Test Feeds</title></head>
                <body>
                    <outline text="Tech News" type="rss" xmlUrl="https://technews.example.com/rss" htmlUrl="https://technews.example.com"/>
                </body>
            </opml>
        """.trimIndent()

        val opmlFile = File(context.cacheDir, "test_feeds.opml")
        opmlFile.writeText(opmlContent)

        try {
            FileToDatabase.importToDatabase(opmlFile, dbFile)

            val sources = runBlocking { SourceRepository.getAllSourcesWithOperationalData(context, dbState) }
            val match = sources.find { it.source.url == "https://technews.example.com/rss" }
            assertNotNull(match)
            assertEquals("Tech News", match!!.source.title)
        } finally {
            opmlFile.delete()
        }
    }

    @Test
    fun testImportBytesForJsonAndOpml() {
        val jsonContent = """
            [
                {
                    "id": 2,
                    "link": "https://example.com/article2",
                    "title": "Article Two"
                }
            ]
        """.trimIndent()
        FileToDatabase.importToDatabase(jsonContent.toByteArray(), "test.json", dbFile)

        val count = runBlocking { EntryRepository.countEntries(context, dbState) }
        assertTrue(count >= 1)

        val opmlContent = """
            <opml version="1.0">
                <head><title>Bytes Feeds</title></head>
                <body>
                    <outline text="Science News" type="rss" xmlUrl="https://science.example.com/rss"/>
                </body>
            </opml>
        """.trimIndent()
        FileToDatabase.importToDatabase(opmlContent.toByteArray(), "https://example.com/science.opml", dbFile)

        val sources = runBlocking { SourceRepository.getAllSourcesWithOperationalData(context, dbState) }
        assertTrue(sources.any { it.source.url == "https://science.example.com/rss" })
    }

    @Test
    fun testIsSourceJson() {
        assertTrue(FileToDatabase.isSourceJson("""{"sources":[{"url":"https://example.com"}]}"""))
        assertFalse(FileToDatabase.isSourceJson("""{"entries":[{"link":"https://example.com"}]}"""))
        assertTrue(FileToDatabase.isSourceJson("""[{"url":"https://example.com","source_type":"rss"}]"""))
        assertFalse(FileToDatabase.isSourceJson("""[{"link":"https://example.com","title":"Test"}]"""))
        assertTrue(FileToDatabase.isSourceJson("[]", "sources.json"))
        assertFalse(FileToDatabase.isSourceJson("[]", "entries.json"))
    }

    @Test
    fun testImportSourceJsonFile() {
        val jsonContent = """
            [
                {
                    "id": 1,
                    "url": "https://example.com/feed1.rss",
                    "title": "Feed One",
                    "source_type": "BaseRssPlugin"
                }
            ]
        """.trimIndent()

        val jsonFile = File(context.cacheDir, "sources.json")
        jsonFile.writeText(jsonContent)

        try {
            FileToDatabase.importToDatabase(jsonFile, dbFile)

            val sources = runBlocking { SourceRepository.getAllSourcesWithOperationalData(context, dbState) }
            val match = sources.find { it.source.url == "https://example.com/feed1.rss" }
            assertNotNull(match)
            assertEquals("Feed One", match!!.source.title)
        } finally {
            jsonFile.delete()
        }
    }

    @Test
    fun testImportSourceJsonDictFile() {
        val jsonContent = """
            {
                "sources": [
                    {
                        "id": 2,
                        "url": "https://example.com/feed2.rss",
                        "title": "Feed Two"
                    }
                ]
            }
        """.trimIndent()

        val jsonFile = File(context.cacheDir, "custom.json")
        jsonFile.writeText(jsonContent)

        try {
            FileToDatabase.importToDatabase(jsonFile, dbFile)

            val sources = runBlocking { SourceRepository.getAllSourcesWithOperationalData(context, dbState) }
            val match = sources.find { it.source.url == "https://example.com/feed2.rss" }
            assertNotNull(match)
            assertEquals("Feed Two", match!!.source.title)
        } finally {
            jsonFile.delete()
        }
    }

    @Test
    fun testImportEntryJsonDictFile() {
        val jsonContent = """
            {
                "entries": [
                    {
                        "id": 10,
                        "link": "https://example.com/dict-article",
                        "title": "Dict Article"
                    }
                ]
            }
        """.trimIndent()

        val jsonFile = File(context.cacheDir, "entries_dict.json")
        jsonFile.writeText(jsonContent)

        try {
            FileToDatabase.importToDatabase(jsonFile, dbFile)

            val count = runBlocking { EntryRepository.countEntries(context, dbState) }
            assertTrue(count >= 1)
        } finally {
            jsonFile.delete()
        }
    }

    @Test
    fun testUnsupportedExtensionThrowsException() {
        val dummyFile = File(context.cacheDir, "test.unsupported")
        dummyFile.writeText("dummy")
        try {
            FileToDatabase.importToDatabase(dummyFile, dbFile)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Unsupported") == true)
        } finally {
            dummyFile.delete()
        }
    }

    @Test
    fun testParseZipAndImportZipToDatabase() {
        val entriesJson = """
            [
                {
                    "id": 100,
                    "link": "https://example.com/zip-article",
                    "title": "Zip Article"
                }
            ]
        """.trimIndent()

        val sourcesJson = """
            [
                {
                    "id": 200,
                    "url": "https://example.com/zip-feed.rss",
                    "title": "Zip Feed",
                    "source_type": "rss"
                }
            ]
        """.trimIndent()

        val zipBytes = buildZip(
            "entries.json" to entriesJson,
            "sources.json" to sourcesJson,
            "readme.txt" to "ignore this"
        )

        val tempZipFile = File(context.cacheDir, "test_archive.zip")
        try {
            tempZipFile.writeBytes(zipBytes)

            FileToDatabase.importZipToDatabase(tempZipFile, dbFile)

            val entryCount = runBlocking { EntryRepository.countEntries(context, dbState) }
            assertTrue(entryCount >= 1)

            val sources = runBlocking { SourceRepository.getAllSourcesWithOperationalData(context, dbState) }
            assertTrue(sources.any { it.source.url == "https://example.com/zip-feed.rss" })
        } finally {
            tempZipFile.delete()
        }
    }

    @Test
    fun testParseZipStreamWithErrors() {
        val zipBytes = buildZip(
            "corrupt.json" to "{ not valid json",
            "readme.txt" to "ignored"
        )

        val errors = mutableListOf<String>()
        java.util.zip.ZipInputStream(zipBytes.inputStream()).use { zis ->
            FileToDatabase.parseZip(zis, dbFile, errors)
        }

        assertTrue("Expected error for corrupt JSON entry", errors.isNotEmpty())
        assertTrue(errors.any { it.contains("corrupt.json") })
    }

    @Test
    fun testImportZipToDatabaseSuspend() = runBlocking {
        val entriesJson = """
            [
                {
                    "id": 300,
                    "link": "https://example.com/suspend-article",
                    "title": "Suspend Article"
                }
            ]
        """.trimIndent()

        val zipBytes = buildZip("entries.json" to entriesJson)
        val tempZipFile = File(context.cacheDir, "test_suspend.zip")
        try {
            tempZipFile.writeBytes(zipBytes)

            FileToDatabase.importZipToDatabase(context, tempZipFile, dbState)

            val entryCount = EntryRepository.countEntries(context, dbState)
            assertTrue(entryCount >= 1)
        } finally {
            tempZipFile.delete()
        }
    }

    private fun buildZip(vararg nameToContent: Pair<String, String>): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(baos).use { zos ->
            for ((name, content) in nameToContent) {
                zos.putNextEntry(java.util.zip.ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    // ── JSON content detection ───────────────────────────────────────────────

    @Test
    fun testIsJsonContent_emptyReturnsFalse() {
        assertFalse(FileToDatabase.isJsonContent(""))
        assertFalse(FileToDatabase.isJsonContent("   "))
        assertFalse(FileToDatabase.isJsonContent("# comment only\n  "))
    }

    @Test
    fun testIsJsonContent_jsonArrayReturnsTrue() {
        assertTrue(FileToDatabase.isJsonContent("[{\"url\":\"https://x.com\"}]"))
    }

    @Test
    fun testIsJsonContent_jsonObjectReturnsTrue() {
        assertTrue(FileToDatabase.isJsonContent("{\"sources\":[]}"))
    }

    @Test
    fun testIsJsonContent_urlLineReturnsFalse() {
        assertFalse(FileToDatabase.isJsonContent("https://example.com/feed.rss"))
        assertFalse(FileToDatabase.isJsonContent("# header\nhttps://example.com/feed.rss"))
    }

    // ── resolveConverter detects plain-text .sources / .entries ─────────────

    @Test
    fun testResolveConverter_sourcesPlainText() {
        val text = "https://example.com/feed.rss\nhttps://another.com/atom.xml"
        val converter = FileToDatabase.resolveConverter("feeds.sources", text)
        assertTrue(converter is SourceUrlListToDatabase)
    }

    @Test
    fun testResolveConverter_sourcesJson() {
        val text = "[{\"url\":\"https://example.com\",\"source_type\":\"rss\"}]"
        val converter = FileToDatabase.resolveConverter("feeds.sources", text)
        assertTrue(converter is SourceJsonToDatabase)
    }

    @Test
    fun testResolveConverter_entriesPlainText() {
        val text = "https://example.com/article1\nhttps://example.com/article2"
        val converter = FileToDatabase.resolveConverter("links.entries", text)
        assertTrue(converter is EntryUrlListToDatabase)
    }

    @Test
    fun testResolveConverter_entriesJson() {
        val text = "[{\"link\":\"https://example.com/article1\",\"title\":\"A\"}]"
        val converter = FileToDatabase.resolveConverter("links.entries", text)
        assertTrue(converter is EntryJsonToDatabase)
    }

    // ── Plain-text .sources import ───────────────────────────────────────────

    @Test
    fun testImportPlainTextSourcesFile() {
        val content = """
            # My feeds
            https://technews.example.com/rss
            https://science.example.com/atom.xml
        """.trimIndent()

        val sourcesFile = File(context.cacheDir, "test_feeds.sources")
        sourcesFile.writeText(content)
        try {
            FileToDatabase.importToDatabase(sourcesFile, dbFile)

            val sources = runBlocking { SourceRepository.getAllSourcesWithOperationalData(context, dbState) }
            assertTrue(sources.any { it.source.url == "https://technews.example.com/rss" })
            assertTrue(sources.any { it.source.url == "https://science.example.com/atom.xml" })
        } finally {
            sourcesFile.delete()
        }
    }

    @Test
    fun testImportPlainTextSourcesByteArray() {
        val content = "https://example.com/rss\nhttps://example.com/atom"
        FileToDatabase.importToDatabase(content.toByteArray(Charsets.UTF_8), "my.sources", dbFile)

        val sources = runBlocking { SourceRepository.getAllSourcesWithOperationalData(context, dbState) }
        assertTrue(sources.any { it.source.url == "https://example.com/rss" })
    }

    // ── Plain-text .entries import ───────────────────────────────────────────

    @Test
    fun testImportPlainTextEntriesFile() {
        val content = """
            # Bookmarks
            https://article1.example.com/post
            https://article2.example.com/post
        """.trimIndent()

        val entriesFile = File(context.cacheDir, "test_links.entries")
        entriesFile.writeText(content)
        try {
            FileToDatabase.importToDatabase(entriesFile, dbFile)

            val count = runBlocking { EntryRepository.countEntries(context, dbState) }
            assertTrue(count >= 2)
        } finally {
            entriesFile.delete()
        }
    }

    @Test
    fun testImportPlainTextEntriesByteArray() {
        val content = "https://example.com/link1\nhttps://example.com/link2\nhttps://example.com/link3"
        FileToDatabase.importToDatabase(content.toByteArray(Charsets.UTF_8), "my.entries", dbFile)

        val count = runBlocking { EntryRepository.countEntries(context, dbState) }
        assertTrue(count >= 3)
    }

    @Test
    fun testImportPlainTextSourcesSkipsCommentsAndBlanks() {
        val content = """
            # this is a comment
            
            https://valid.example.com/feed.rss
            
            # another comment
        """.trimIndent()

        val sourcesFile = File(context.cacheDir, "test_skip.sources")
        sourcesFile.writeText(content)
        try {
            FileToDatabase.importToDatabase(sourcesFile, dbFile)

            val sources = runBlocking { SourceRepository.getAllSourcesWithOperationalData(context, dbState) }
            assertEquals(1, sources.size)
            assertEquals("https://valid.example.com/feed.rss", sources.first().source.url)
        } finally {
            sourcesFile.delete()
        }
    }
}

