package io.github.rumcajs.offlinewebsearch.email

import java.util.Date

/**
 * Interface representing an email client capable of connecting to an email server,
 * authenticating, and fetching messages.
 */
interface EmailClient : AutoCloseable {

    /**
     * Connects to the remote email server.
     *
     * @return True if connection was successful.
     */
    suspend fun connect(): Boolean

    /**
     * Authenticates with the email server using the specified credentials.
     *
     * @param username Email username or login.
     * @param password Password or authentication token.
     * @return True if authentication succeeded.
     */
    suspend fun login(username: String, password: String): Boolean

    /**
     * Selects a mailbox or folder on the server.
     *
     * @param folder Name of the mailbox (defaults to "INBOX").
     * @return Number of existing messages in the folder, or -1 on error.
     */
    suspend fun selectFolder(folder: String = "INBOX"): Int

    /**
     * Fetches messages from the selected folder.
     *
     * @param sinceDate If non-null, only messages received after or up to this date are read.
     * @param maxCount Maximum number of messages to fetch (default: 100).
     * @return List of fetched [EmailMessage] instances.
     */
    suspend fun fetchMessages(sinceDate: Date? = null, maxCount: Int = 100): List<EmailMessage>

    /**
     * Disconnects from the email server and closes open resources.
     */
    suspend fun disconnect()

    override fun close()
}
