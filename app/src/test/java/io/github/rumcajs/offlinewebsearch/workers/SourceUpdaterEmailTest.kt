package io.github.rumcajs.offlinewebsearch.workers

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import io.github.rumcajs.offlinewebsearch.data.DatabaseState
import io.github.rumcajs.offlinewebsearch.data.RepositoryTestHelper
import io.github.rumcajs.offlinewebsearch.data.repositories.Credentials
import io.github.rumcajs.offlinewebsearch.data.repositories.CredentialsRepository
import io.github.rumcajs.offlinewebsearch.data.repositories.EntryRule
import io.github.rumcajs.offlinewebsearch.data.repositories.EntryRulesRepository
import io.github.rumcajs.offlinewebsearch.data.repositories.EntrySqliteRepository
import io.github.rumcajs.offlinewebsearch.data.repositories.Source
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceOperationalDataRepository
import io.github.rumcajs.offlinewebsearch.data.repositories.SourceRepository
import io.github.rumcajs.offlinewebsearch.email.EmailClient
import io.github.rumcajs.offlinewebsearch.email.EmailMessage
import io.github.rumcajs.offlinewebsearch.util.DateUtils
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Date

/**
 * Unit tests for [SourceUpdaterEmail].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SourceUpdaterEmailTest {

    private lateinit var context: Context
    private lateinit var dbState: DatabaseState
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val (state, file) = RepositoryTestHelper.setup(context)
        dbState = state
        dbFile = file

        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        CredentialsRepository.ensureTableExists(db)
        EntryRulesRepository.ensureTableExists(db)
        SourceOperationalDataRepository.ensureTableExists(db)
        db.close()
    }

    @After
    fun tearDown() {
        dbFile.delete()
    }

    private class MockEmailClient(
        var connectResult: Boolean = true,
        var loginResult: Boolean = true,
        var selectFolderResult: Int = 2,
        val messages: List<EmailMessage> = emptyList()
    ) : EmailClient {
        var disconnected = false
        var closed = false

        override suspend fun connect(): Boolean = connectResult
        override suspend fun login(username: String, password: String): Boolean = loginResult
        override suspend fun selectFolder(folder: String): Int = selectFolderResult
        override suspend fun fetchMessages(sinceDate: Date?, maxCount: Int): List<EmailMessage> {
            return if (sinceDate == null) {
                messages
            } else {
                messages.filter { it.date == null || it.date.time > sinceDate.time }
            }
        }
        override suspend fun disconnect() { disconnected = true }
        override fun close() { closed = true }
    }

    @Test
    fun `process successfully fetches emails into Entry models with fake link, title and author`() = runBlocking {
        // 1. Setup credentials
        val (credId, _) = CredentialsRepository.insertCredential(
            context = context,
            activeDatabaseState = dbState,
            credential = Credentials(
                name = "mail_account",
                username = "user@example.com",
                password = "secretpassword"
            )
        )
        assertNotNull(credId)

        // 2. Setup Source
        val sourceUrl = "imaps://mail.example.com/INBOX"
        val (okSource, _) = SourceRepository.insertSource(
            context = context,
            activeDatabaseState = dbState,
            title = "Test Email Source",
            url = sourceUrl,
            enabled = true,
            credentials_id = credId
        )
        assertTrue(okSource)
        val source = SourceRepository.getSourceByUrl(context, dbState, sourceUrl)!!

        // 3. Prepare mock messages
        val msgDate = Date(1700000000000L)
        val mockMsg1 = EmailMessage(
            messageId = "<msg1@example.com>",
            from = "Author One <author1@example.com>",
            to = "user@example.com",
            subject = "First Test Email",
            date = msgDate,
            body = "Hello world email body 1",
            uid = 101L
        )
        val mockMsg2 = EmailMessage(
            messageId = "<msg2@example.com>",
            from = "author2@example.com",
            to = "user@example.com",
            subject = "Second Test Email",
            date = Date(1700001000000L),
            body = "Hello world email body 2",
            uid = 102L
        )

        val mockClient = MockEmailClient(messages = listOf(mockMsg1, mockMsg2))

        val updater = SourceUpdaterEmail(
            context = context,
            activeDatabaseState = dbState,
            source = source,
            force = true,
            clientFactory = { mockClient }
        )

        val (ok, msg) = updater.process()
        assertTrue(msg, ok)
        assertTrue(msg.contains("2 entries"))

        // 4. Verify entries inserted in Entry repository
        val totalCount = EntrySqliteRepository.countEntries(context, dbState)
        assertEquals(2, totalCount)

        val entries = EntrySqliteRepository.getEntriesPage(context, dbState, pageSize = 10)
        val entry1 = entries.find { it.title == "First Test Email" }
        assertNotNull(entry1)
        assertEquals("email://${source.url}/${source.id}/msg1@example.com", entry1!!.link)
        assertEquals("Author One <author1@example.com>", entry1.author)
        assertEquals("Hello world email body 1", entry1.description)
        assertEquals(source.id, entry1.source_id)

        val entry2 = entries.find { it.title == "Second Test Email" }
        assertNotNull(entry2)
        assertEquals("email://${source.url}/${source.id}/msg2@example.com", entry2!!.link)
        assertEquals("author2@example.com", entry2.author)

        // 5. Verify operational data updated
        val opData = SourceOperationalDataRepository.getOperationalDataBySourceId(context, dbState, source.id!!)
        assertNotNull(opData)
        assertEquals(2, opData!!.number_of_entries)
        assertEquals(0, opData.consecutive_errors)
        assertNotNull(opData.date_fetched)
    }

    @Test
    fun `process reads emails up to SourceWithOperationalData date_fetched`() = runBlocking {
        // Setup credentials & source
        val (credId, _) = CredentialsRepository.insertCredential(
            context = context,
            activeDatabaseState = dbState,
            credential = Credentials(
                name = "mail_account2",
                username = "user@example.com",
                password = "secretpassword"
            )
        )
        val sourceUrl = "imaps://mail.example2.com"
        SourceRepository.insertSource(context, dbState, "Email Source 2", sourceUrl, enabled = true, credentials_id = credId)
        val source = SourceRepository.getSourceByUrl(context, dbState, sourceUrl)!!

        // Set previous date_fetched in operational data (epoch 1700005000000L)
        val previousFetchDate = Date(1700005000000L)
        val previousFetchIso = DateUtils.toIsoString(previousFetchDate)
        SourceOperationalDataRepository.setSourceFetch(context, dbState, source.id!!, fetchTime = previousFetchIso)

        // Mock messages: one newer than date_fetched, one older than date_fetched
        val newerMsg = EmailMessage(
            messageId = "<newer@example.com>",
            from = "new@example.com",
            subject = "Newer Email",
            date = Date(1700006000000L), // Newer
            body = "New message body",
            uid = 201L
        )
        val olderMsg = EmailMessage(
            messageId = "<older@example.com>",
            from = "old@example.com",
            subject = "Older Email",
            date = Date(1700004000000L), // Older
            body = "Old message body",
            uid = 202L
        )

        val mockClient = MockEmailClient(messages = listOf(newerMsg, olderMsg))

        val updater = SourceUpdaterEmail(
            context = context,
            activeDatabaseState = dbState,
            source = source,
            force = true,
            clientFactory = { mockClient }
        )

        val (ok, msg) = updater.process()
        assertTrue(msg, ok)
        assertTrue(msg.contains("1 entries"))

        // Only newer email should be inserted
        val entries = EntrySqliteRepository.getEntriesPage(context, dbState)
        assertEquals(1, entries.size)
        assertEquals("Newer Email", entries[0].title)
    }

    @Test
    fun `process filters emails with EntryRules and records trigger hits`() = runBlocking {
        val (credId, _) = CredentialsRepository.insertCredential(
            context = context,
            activeDatabaseState = dbState,
            credential = Credentials(
                name = "mail_account3",
                username = "user@example.com",
                password = "secretpassword"
            )
        )
        val sourceUrl = "imaps://mail.example3.com"
        SourceRepository.insertSource(context, dbState, "Email Source 3", sourceUrl, enabled = true, credentials_id = credId)
        val source = SourceRepository.getSourceByUrl(context, dbState, sourceUrl)!!

        // Add blocking rule for spam
        val (ruleId, _) = EntryRulesRepository.insertRule(
            context = context,
            activeDatabaseState = dbState,
            rule = EntryRule(
                rule_name = "Block Spam Msg",
                trigger_rule_url = ".*spam.*",
                block = true,
                enabled = true
            )
        )

        val spamMsg = EmailMessage(
            messageId = "<spam-123@example.com>",
            from = "spammer@example.com",
            subject = "Spam Offer",
            date = Date(),
            body = "Spam content",
            uid = 301L
        )
        val validMsg = EmailMessage(
            messageId = "<valid-456@example.com>",
            from = "friend@example.com",
            subject = "Legit Email",
            date = Date(),
            body = "Clean content",
            uid = 302L
        )

        val mockClient = MockEmailClient(messages = listOf(spamMsg, validMsg))

        val updater = SourceUpdaterEmail(
            context = context,
            activeDatabaseState = dbState,
            source = source,
            force = true,
            clientFactory = { mockClient }
        )

        val (ok, msg) = updater.process()
        assertTrue(msg, ok)
        assertTrue(msg.contains("1 entries"))

        val entries = EntrySqliteRepository.getEntriesPage(context, dbState)
        assertEquals(1, entries.size)
        assertEquals("Legit Email", entries[0].title)

        // Verify blocked rule hit count
        val rule = EntryRulesRepository.getRuleById(context, dbState, ruleId!!)
        assertEquals(1, rule?.trigger_text_hits)
    }

    @Test
    fun `process returns error when credentials missing`() = runBlocking {
        val sourceUrl = "imaps://mail.nocreds.com"
        SourceRepository.insertSource(context, dbState, "No Creds Source", sourceUrl, enabled = true, credentials_id = null)
        val source = SourceRepository.getSourceByUrl(context, dbState, sourceUrl)!!

        val updater = SourceUpdaterEmail(
            context = context,
            activeDatabaseState = dbState,
            source = source,
            force = true
        )

        val (ok, msg) = updater.process()
        assertFalse(ok)
        assertTrue(msg.contains("No credentials found"))

        // Operational data should reflect error
        val opData = SourceOperationalDataRepository.getOperationalDataBySourceId(context, dbState, source.id!!)
        assertNotNull(opData)
        assertEquals(1, opData!!.consecutive_errors)
    }

    @Test
    fun `process returns error when server connection fails`() = runBlocking {
        val (credId, _) = CredentialsRepository.insertCredential(
            context = context,
            activeDatabaseState = dbState,
            credential = Credentials(name = "conn_fail", username = "user", password = "pass")
        )
        val sourceUrl = "imaps://mail.fail.com"
        SourceRepository.insertSource(context, dbState, "Fail Source", sourceUrl, enabled = true, credentials_id = credId)
        val source = SourceRepository.getSourceByUrl(context, dbState, sourceUrl)!!

        val mockClient = MockEmailClient(connectResult = false)
        val updater = SourceUpdaterEmail(
            context = context,
            activeDatabaseState = dbState,
            source = source,
            force = true,
            clientFactory = { mockClient }
        )

        val (ok, msg) = updater.process()
        assertFalse(ok)
        assertTrue(msg.contains("Failed to connect"))
    }
}
