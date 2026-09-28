package io.github.rumcajs.offlinewebsearch.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.rumcajs.offlinewebsearch.data.repositories.Credentials
import io.github.rumcajs.offlinewebsearch.data.repositories.CredentialsRepository
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
 * Unit tests for [CredentialsRepository].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CredentialsRepositoryTest {

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
    fun `insertCredential and getCredentialById correctly persist and read all fields`() = runBlocking {
        val credential = Credentials(
            name = "github",
            credential_type = "oauth2_token",
            username = "octocat",
            password = "supersecretpassword",
            secret = "client_secret_xyz",
            token = "ghp_1234567890abcdef",
            user_id = 42L
        )

        val (rowId, err) = CredentialsRepository.insertCredential(context, dbState, credential)
        assertNull("Insert error should be null: $err", err)
        assertNotNull("Inserted ID should not be null", rowId)
        assertTrue(rowId!! > 0)

        val fetched = CredentialsRepository.getCredentialById(context, dbState, rowId)
        assertNotNull("Fetched credential should not be null", fetched)
        assertEquals(rowId, fetched!!.id)
        assertEquals("github", fetched.name)
        assertEquals("oauth2_token", fetched.credential_type)
        assertEquals("octocat", fetched.username)
        assertEquals("supersecretpassword", fetched.password)
        assertEquals("client_secret_xyz", fetched.secret)
        assertEquals("ghp_1234567890abcdef", fetched.token)
        assertEquals(42L, fetched.user_id)
    }

    @Test
    fun `getCredentialByName finds credential by unique name`() = runBlocking {
        val credential = Credentials(
            name = "reddit",
            credential_type = "refresh_token",
            token = "reddit_refresh_token_123",
            user_id = 1L
        )
        val (rowId, _) = CredentialsRepository.insertCredential(context, dbState, credential)
        assertNotNull(rowId)

        val fetched = CredentialsRepository.getCredentialByName(context, dbState, "reddit")
        assertNotNull(fetched)
        assertEquals(rowId, fetched!!.id)
        assertEquals("reddit", fetched.name)
        assertEquals("refresh_token", fetched.credential_type)
        assertEquals("reddit_refresh_token_123", fetched.token)
    }

    @Test
    fun `getCredentialsByType returns matching credentials`() = runBlocking {
        CredentialsRepository.insertCredential(context, dbState, Credentials(name = "token1", credential_type = "bearer"))
        CredentialsRepository.insertCredential(context, dbState, Credentials(name = "token2", credential_type = "bearer"))
        CredentialsRepository.insertCredential(context, dbState, Credentials(name = "pwd1", credential_type = "password"))

        val bearerCreds = CredentialsRepository.getCredentialsByType(context, dbState, "bearer")
        assertEquals(2, bearerCreds.size)
        assertTrue(bearerCreds.any { it.name == "token1" })
        assertTrue(bearerCreds.any { it.name == "token2" })

        val passwordCreds = CredentialsRepository.getCredentialsByType(context, dbState, "password")
        assertEquals(1, passwordCreds.size)
        assertEquals("pwd1", passwordCreds[0].name)
    }

    @Test
    fun `getAllCredentials returns all credentials`() = runBlocking {
        CredentialsRepository.insertCredential(context, dbState, Credentials(name = "cred1"))
        CredentialsRepository.insertCredential(context, dbState, Credentials(name = "cred2"))

        val all = CredentialsRepository.getAllCredentials(context, dbState)
        assertEquals(2, all.size)
    }

    @Test
    fun `updateCredential updates existing credential`() = runBlocking {
        val (rowId, _) = CredentialsRepository.insertCredential(
            context,
            dbState,
            Credentials(name = "initial_service", username = "old_user", token = "old_token")
        )
        assertNotNull(rowId)

        val updated = Credentials(
            id = rowId,
            name = "updated_service",
            username = "new_user",
            token = "new_token",
            user_id = 5L
        )
        val (ok, err) = CredentialsRepository.updateCredential(context, dbState, updated)
        assertTrue("Update should succeed: $err", ok)

        val reloaded = CredentialsRepository.getCredentialById(context, dbState, rowId!!)
        assertNotNull(reloaded)
        assertEquals("updated_service", reloaded!!.name)
        assertEquals("new_user", reloaded.username)
        assertEquals("new_token", reloaded.token)
        assertEquals(5L, reloaded.user_id)
    }

    @Test
    fun `deleteById and deleteByName remove credential`() = runBlocking {
        val (id1, _) = CredentialsRepository.insertCredential(context, dbState, Credentials(name = "service1"))
        val (id2, _) = CredentialsRepository.insertCredential(context, dbState, Credentials(name = "service2"))

        assertEquals(2L, CredentialsRepository.count(context, dbState))

        val (del1Ok, _) = CredentialsRepository.deleteById(context, dbState, id1!!)
        assertTrue(del1Ok)
        assertEquals(1L, CredentialsRepository.count(context, dbState))
        assertNull(CredentialsRepository.getCredentialById(context, dbState, id1))

        val (del2Ok, _) = CredentialsRepository.deleteByName(context, dbState, "service2")
        assertTrue(del2Ok)
        assertEquals(0L, CredentialsRepository.count(context, dbState))
        assertNull(CredentialsRepository.getCredentialByName(context, dbState, "service2"))
    }

    @Test
    fun `clear removes all credentials`() = runBlocking {
        CredentialsRepository.insertCredential(context, dbState, Credentials(name = "s1"))
        CredentialsRepository.insertCredential(context, dbState, Credentials(name = "s2"))
        assertEquals(2L, CredentialsRepository.count(context, dbState))

        val (clearOk, _) = CredentialsRepository.clear(context, dbState)
        assertTrue(clearOk)
        assertEquals(0L, CredentialsRepository.count(context, dbState))
    }
}
