package io.github.rumcajs.offlinewebsearch.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import io.github.rumcajs.offlinewebsearch.data.repositories.EntryRepository
import io.github.rumcajs.offlinewebsearch.data.repositories.ReadLaterRepository
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
 * Unit tests for [ReadLaterRepository].
 *
 * Uses Robolectric + [RepositoryTestHelper] to provide a writable copy of `assets/table.db`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadLaterRepositoryTest {

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
    fun `addReadLater sets Entry bookmarked to true in linkdatamodel`() = runBlocking {
        // Create an entry with bookmarked = false
        val entry = Entry(link = "https://example.com/read-later-test", title = "Read Later Test", bookmarked = false)
        val (addOk, entryId, err) = EntryRepository.add(context, dbState, entry)
        assertTrue("Failed to add entry: $err", addOk)
        assertTrue(entryId > 0)

        // Verify entry is initially not bookmarked
        val initialEntry = queryEntry(entryId)
        assertNotNull(initialEntry)
        assertFalse("Entry should initially have bookmarked false", initialEntry!!.bookmarked == true)

        // Add to read later
        val (rlOk, rlErr) = ReadLaterRepository.addReadLater(context, dbState, entryId)
        assertTrue("Failed to add read later: $rlErr", rlOk)

        // Verify read later entry exists
        val isLater = ReadLaterRepository.isReadLater(context, dbState, entryId)
        assertTrue("isReadLater should return true", isLater)

        // Verify Entry.bookmarked is now true
        val updatedEntry = queryEntry(entryId)
        assertNotNull(updatedEntry)
        assertTrue("Entry.bookmarked should be set to true after adding to ReadLater", updatedEntry!!.bookmarked == true)
    }

    @Test
    fun `getReadLaterEntries retrieves entry with bookmarked true`() = runBlocking {
        val entry = Entry(link = "https://example.com/read-later-list", title = "List Test", bookmarked = false)
        val (_, entryId, _) = EntryRepository.add(context, dbState, entry)

        ReadLaterRepository.addReadLater(context, dbState, entryId)

        val list = ReadLaterRepository.getReadLaterEntries(context, dbState)
        val matching = list.find { it.second.id == entryId }
        assertNotNull("Entry should be found in getReadLaterEntries", matching)
        assertTrue("Entry.bookmarked in getReadLaterEntries should be true", matching!!.second.bookmarked == true)
    }

    @Test
    fun `removeReadLaterByEntryId sets Entry bookmarked to false in linkdatamodel`() = runBlocking {
        val entry = Entry(link = "https://example.com/read-later-remove", title = "Remove Test", bookmarked = false)
        val (_, entryId, _) = EntryRepository.add(context, dbState, entry)

        ReadLaterRepository.addReadLater(context, dbState, entryId)
        assertTrue("Should be bookmarked after add", queryEntry(entryId)?.bookmarked == true)

        val (removeOk, removeErr) = ReadLaterRepository.removeReadLaterByEntryId(context, dbState, entryId)
        assertTrue("Failed to remove read later: $removeErr", removeOk)

        val updatedEntry = queryEntry(entryId)
        assertNotNull(updatedEntry)
        assertFalse("Entry.bookmarked should be set to false after removing from ReadLater", updatedEntry!!.bookmarked == true)
    }

    @Test
    fun `clear sets all read later entries bookmarked to false in linkdatamodel`() = runBlocking {
        val (_, entryId1, _) = EntryRepository.add(context, dbState, Entry(link = "https://example.com/clear-1", title = "C1"))
        val (_, entryId2, _) = EntryRepository.add(context, dbState, Entry(link = "https://example.com/clear-2", title = "C2"))

        ReadLaterRepository.addReadLater(context, dbState, entryId1)
        ReadLaterRepository.addReadLater(context, dbState, entryId2)

        assertTrue(queryEntry(entryId1)?.bookmarked == true)
        assertTrue(queryEntry(entryId2)?.bookmarked == true)

        val (clearOk, clearErr) = ReadLaterRepository.clear(context, dbState)
        assertTrue("Failed to clear read later: $clearErr", clearOk)

        assertFalse("Entry 1 bookmarked should be false after clear", queryEntry(entryId1)?.bookmarked == true)
        assertFalse("Entry 2 bookmarked should be false after clear", queryEntry(entryId2)?.bookmarked == true)
    }

    private fun queryEntry(id: Long): Entry? {
        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return db.use {
            val cursor = it.rawQuery(
                "SELECT id, link, title, description, bookmarked FROM linkdatamodel WHERE id = ?",
                arrayOf(id.toString())
            )
            cursor.use { c ->
                if (c.moveToFirst()) {
                    Entry(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        link = c.getString(c.getColumnIndexOrThrow("link")),
                        title = c.getString(c.getColumnIndexOrThrow("title")),
                        description = c.getString(c.getColumnIndexOrThrow("description")),
                        bookmarked = c.getInt(c.getColumnIndexOrThrow("bookmarked")) == 1
                    )
                } else null
            }
        }
    }
}
