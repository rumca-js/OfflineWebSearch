package io.github.rumcajs.offlinewebsearch.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import io.github.rumcajs.offlinewebsearch.data.repositories.AppLogging
import io.github.rumcajs.offlinewebsearch.data.repositories.AppLoggingRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Unit tests for [io.github.rumcajs.offlinewebsearch.data.repositories.AppLoggingRepository.info] and [io.github.rumcajs.offlinewebsearch.data.repositories.AppLoggingRepository.error].
 *
 * Uses Robolectric + [RepositoryTestHelper] to provide a writable copy of
 * `assets/table.db` for each test case.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLoggingRepositoryTest {

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

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Queries all rows from `applogging` ordered by id DESC.
     */
    private fun queryAllLogs(): List<AppLogging> {
        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return db.use {
            val cursor = it.rawQuery(
                "SELECT id, info_text, detail_text, level, date FROM applogging ORDER BY id DESC",
                null
            )
            cursor.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(
                            AppLogging(
                                id = c.getLong(c.getColumnIndexOrThrow("id")),
                                info_text = c.getString(c.getColumnIndexOrThrow("info_text")) ?: "",
                                detail_text = c.getString(c.getColumnIndexOrThrow("detail_text")),
                                level = c.getInt(c.getColumnIndexOrThrow("level")),
                                date = c.getString(c.getColumnIndexOrThrow("date"))
                            )
                        )
                    }
                }
            }
        }
    }

    // ── info: return value ────────────────────────────────────────────────────

    @Test
    fun `info returns success pair for valid database`() = runBlocking {
        val (ok, error) = AppLoggingRepository.info(context, dbState, "Test info message")
        assertTrue("Expected success but got: $error", ok)
        assertNull(error)
    }

    @Test
    fun `info fails when database is null`() = runBlocking {
        val (ok, error) = AppLoggingRepository.info(context, null, "Should fail")
        assertFalse(ok)
        assertNotNull(error)
    }

    @Test
    fun `info fails when database is read-only`() = runBlocking {
        val readOnlyState = dbState.copy(isReadOnly = true)
        val (ok, error) = AppLoggingRepository.info(context, readOnlyState, "Should fail")
        assertFalse(ok)
        assertNotNull(error)
    }

    @Test
    fun `info fails when database file does not exist`() = runBlocking {
        val missingState = dbState.copy(localFileName = "nonexistent.db")
        val (ok, error) = AppLoggingRepository.info(context, missingState, "Should fail")
        assertFalse(ok)
        assertNotNull(error)
    }

    @Test
    fun `info fails when database is not SQLite`() = runBlocking {
        val jsonState = dbState.copy(localFileName = "some_data.json")
        val (ok, error) = AppLoggingRepository.info(context, jsonState, "Should fail")
        assertFalse(ok)
        assertNotNull(error)
    }

    // ── info: persistence ─────────────────────────────────────────────────────

    @Test
    fun `info persists info_text in applogging`() = runBlocking {
        AppLoggingRepository.info(context, dbState, "Persisted info text")

        val logs = queryAllLogs()
        val inserted = logs.firstOrNull { it.info_text == "Persisted info text" }
        assertNotNull("Expected log row to be inserted", inserted)
    }

    @Test
    fun `info stores level INFO = 0`() = runBlocking {
        AppLoggingRepository.info(context, dbState, "Info level test")

        val logs = queryAllLogs()
        val inserted = logs.firstOrNull { it.info_text == "Info level test" }
        assertNotNull(inserted)
        assertEquals(AppLoggingRepository.LEVEL_INFO, inserted!!.level)
        assertEquals(0, inserted.level)
    }

    @Test
    fun `info persists optional detail_text`() = runBlocking {
        AppLoggingRepository.info(context, dbState, "With detail", "Some detail text")

        val logs = queryAllLogs()
        val inserted = logs.firstOrNull { it.info_text == "With detail" }
        assertNotNull(inserted)
        assertEquals("Some detail text", inserted!!.detail_text)
    }

    @Test
    fun `info with null detail_text stores null detail_text`() = runBlocking {
        AppLoggingRepository.info(context, dbState, "No detail", null)

        val logs = queryAllLogs()
        val inserted = logs.firstOrNull { it.info_text == "No detail" }
        assertNotNull(inserted)
        assertNull(inserted!!.detail_text)
    }

    @Test
    fun `info persists a non-null date`() = runBlocking {
        AppLoggingRepository.info(context, dbState, "Date check")

        val logs = queryAllLogs()
        val inserted = logs.firstOrNull { it.info_text == "Date check" }
        assertNotNull(inserted)
        assertNotNull("Expected a date to be stored", inserted!!.date)
    }

    @Test
    fun `info truncates info_text longer than 2000 characters`() = runBlocking {
        val longText = "A".repeat(3000)
        AppLoggingRepository.info(context, dbState, longText)

        val logs = queryAllLogs()
        val inserted = logs.firstOrNull { it.info_text.length <= 2000 && it.info_text.startsWith("A") }
        assertNotNull("Expected a truncated log row", inserted)
        assertEquals(2000, inserted!!.info_text.length)
    }

    @Test
    fun `info truncates detail_text longer than 2000 characters`() = runBlocking {
        val longDetail = "B".repeat(3000)
        AppLoggingRepository.info(context, dbState, "Truncate detail", longDetail)

        val logs = queryAllLogs()
        val inserted = logs.firstOrNull { it.info_text == "Truncate detail" }
        assertNotNull(inserted)
        assertEquals(2000, inserted!!.detail_text?.length)
    }

    // ── info: multiple inserts ────────────────────────────────────────────────

    @Test
    fun `info each insert gets a unique row id`() = runBlocking {
        AppLoggingRepository.info(context, dbState, "First info")
        AppLoggingRepository.info(context, dbState, "Second info")

        val logs = queryAllLogs()
        val ids = logs.mapNotNull { it.id }
        assertEquals("All IDs must be unique", ids.size, ids.distinct().size)
    }

    // ── error: return value ───────────────────────────────────────────────────

    @Test
    fun `error returns success pair for valid database`() = runBlocking {
        val (ok, error) = AppLoggingRepository.error(context, dbState, "Test error message")
        assertTrue("Expected success but got: $error", ok)
        assertNull(error)
    }

    @Test
    fun `error fails when database is null`() = runBlocking {
        val (ok, error) = AppLoggingRepository.error(context, null, "Should fail")
        assertFalse(ok)
        assertNotNull(error)
    }

    @Test
    fun `error fails when database is read-only`() = runBlocking {
        val readOnlyState = dbState.copy(isReadOnly = true)
        val (ok, error) = AppLoggingRepository.error(context, readOnlyState, "Should fail")
        assertFalse(ok)
        assertNotNull(error)
    }

    @Test
    fun `error fails when database file does not exist`() = runBlocking {
        val missingState = dbState.copy(localFileName = "nonexistent.db")
        val (ok, error) = AppLoggingRepository.error(context, missingState, "Should fail")
        assertFalse(ok)
        assertNotNull(error)
    }

    @Test
    fun `error fails when database is not SQLite`() = runBlocking {
        val jsonState = dbState.copy(localFileName = "data.json")
        val (ok, error) = AppLoggingRepository.error(context, jsonState, "Should fail")
        assertFalse(ok)
        assertNotNull(error)
    }

    // ── error: persistence ────────────────────────────────────────────────────

    @Test
    fun `error persists info_text in applogging`() = runBlocking {
        AppLoggingRepository.error(context, dbState, "Persisted error text")

        val logs = queryAllLogs()
        val inserted = logs.firstOrNull { it.info_text == "Persisted error text" }
        assertNotNull("Expected log row to be inserted", inserted)
    }

    @Test
    fun `error stores level ERROR = 2`() = runBlocking {
        AppLoggingRepository.error(context, dbState, "Error level test")

        val logs = queryAllLogs()
        val inserted = logs.firstOrNull { it.info_text == "Error level test" }
        assertNotNull(inserted)
        assertEquals(AppLoggingRepository.LEVEL_ERROR, inserted!!.level)
        assertEquals(2, inserted.level)
    }

    @Test
    fun `error persists optional detail_text`() = runBlocking {
        AppLoggingRepository.error(context, dbState, "Error with detail", "Exception stacktrace here")

        val logs = queryAllLogs()
        val inserted = logs.firstOrNull { it.info_text == "Error with detail" }
        assertNotNull(inserted)
        assertEquals("Exception stacktrace here", inserted!!.detail_text)
    }

    @Test
    fun `error with null detail_text stores null detail_text`() = runBlocking {
        AppLoggingRepository.error(context, dbState, "Error no detail", null)

        val logs = queryAllLogs()
        val inserted = logs.firstOrNull { it.info_text == "Error no detail" }
        assertNotNull(inserted)
        assertNull(inserted!!.detail_text)
    }

    @Test
    fun `error persists a non-null date`() = runBlocking {
        AppLoggingRepository.error(context, dbState, "Error date check")

        val logs = queryAllLogs()
        val inserted = logs.firstOrNull { it.info_text == "Error date check" }
        assertNotNull(inserted)
        assertNotNull("Expected a date to be stored", inserted!!.date)
    }

    @Test
    fun `error truncates info_text longer than 2000 characters`() = runBlocking {
        val longText = "E".repeat(3000)
        AppLoggingRepository.error(context, dbState, longText)

        val logs = queryAllLogs()
        val inserted = logs.firstOrNull { it.info_text.length <= 2000 && it.info_text.startsWith("E") }
        assertNotNull("Expected a truncated error log row", inserted)
        assertEquals(2000, inserted!!.info_text.length)
    }

    // ── info vs error: level distinction ─────────────────────────────────────

    @Test
    fun `info and error store different level values`() = runBlocking {
        AppLoggingRepository.info(context, dbState, "level info check")
        AppLoggingRepository.error(context, dbState, "level error check")

        val logs = queryAllLogs()
        val infoLog = logs.firstOrNull { it.info_text == "level info check" }
        val errorLog = logs.firstOrNull { it.info_text == "level error check" }

        assertNotNull(infoLog)
        assertNotNull(errorLog)
        assertNotEquals(infoLog!!.level, errorLog!!.level)
        assertEquals(AppLoggingRepository.LEVEL_INFO, infoLog.level)
        assertEquals(AppLoggingRepository.LEVEL_ERROR, errorLog.level)
    }
}
