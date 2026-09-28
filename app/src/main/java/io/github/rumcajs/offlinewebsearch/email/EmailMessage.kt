package io.github.rumcajs.offlinewebsearch.email

import io.github.rumcajs.offlinewebsearch.data.repositories.Entry
import io.github.rumcajs.offlinewebsearch.data.repositories.Source
import io.github.rumcajs.offlinewebsearch.util.DateUtils
import java.util.Date

/**
 * Data class representing a parsed email message.
 *
 * @property messageId Unique Message-ID from email headers (or generated UID).
 * @property from Sender information ("From" header).
 * @property to Recipient information ("To" header).
 * @property subject Email subject line.
 * @property date Date when the email was sent or received.
 * @property body Text content of the email (plain text or stripped HTML).
 * @property uid Server-assigned message UID (if available).
 */
data class EmailMessage(
    val messageId: String = "",
    val from: String = "",
    val to: String = "",
    val subject: String = "",
    val date: Date? = null,
    val body: String = "",
    val uid: Long? = null
) {
    /**
     * Converts this [EmailMessage] into an [Entry] associated with [source].
     *
     * Constructs a fake link conforming to `email://{source.url}/{source.id}/{message.id}`.
     *
     * @param source The source the email belongs to.
     * @return Populated [Entry] object.
     */
    fun toEntry(source: Source): Entry {
        val cleanMsgId = messageId.trim().trimStart('<').trimEnd('>').ifBlank {
            uid?.toString() ?: (subject.hashCode().toString() + "_" + (date?.time ?: 0))
        }

        val sourceIdPart = source.id ?: 0L
        val fakeLink = "email://${source.url}/$sourceIdPart/$cleanMsgId"

        val datePublished = date?.let { DateUtils.toIsoString(it) } ?: DateUtils.getCurrentIsoTimestamp()

        return Entry(
            link = fakeLink,
            title = subject.ifBlank { "(No Subject)" },
            description = body,
            author = from,
            date_published = datePublished,
            date_created = DateUtils.getCurrentTimestamp(),
            language = source.language,
            age = source.age ?: 0,
            status_code = Entry.STATUS_CODE_OK,
            source_id = source.id,
            source_url = source.url
        )
    }
}
