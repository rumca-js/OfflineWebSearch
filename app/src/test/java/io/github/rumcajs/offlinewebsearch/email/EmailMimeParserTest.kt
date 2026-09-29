package io.github.rumcajs.offlinewebsearch.email

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmailMimeParserTest {

    @Test
    fun testDecodeRfc2047Base64() {
        val encoded = "=?UTF-8?B?VGVzdCBTdWJqZWN0?="
        val decoded = EmailMimeParser.decodeRfc2047(encoded)
        assertEquals("Test Subject", decoded)
    }

    @Test
    fun testDecodeRfc2047QuotedPrintable() {
        val encoded = "=?ISO-8859-1?Q?Hello_World=21?="
        val decoded = EmailMimeParser.decodeRfc2047(encoded)
        assertEquals("Hello World!", decoded)
    }

    @Test
    fun testDecodeRfc2047PlainText() {
        val plain = "Normal Plain Subject"
        val decoded = EmailMimeParser.decodeRfc2047(plain)
        assertEquals("Normal Plain Subject", decoded)
    }

    @Test
    fun testParseRawMessageSinglePart() {
        val raw = """
            From: sender@example.com
            To: recipient@example.com
            Subject: Hello World
            Date: Mon, 28 Sep 2026 14:00:00 +0000
            Message-ID: <msg-12345@example.com>
            Content-Type: text/plain; charset=UTF-8

            This is the email body content.
        """.trimIndent()

        val msg = EmailMimeParser.parseRawMessage(raw, uid = 1L)
        assertEquals("<msg-12345@example.com>", msg.messageId)
        assertEquals("sender@example.com", msg.from)
        assertEquals("recipient@example.com", msg.to)
        assertEquals("Hello World", msg.subject)
        assertEquals("This is the email body content.", msg.body)
        assertNotNull(msg.date)
        assertEquals(1L, msg.uid)
    }

    @Test
    fun testParseRawMessageMultipart() {
        val boundary = "==boundary123=="
        val raw = """
            From: "Alice Smith" <alice@example.com>
            To: Bob <bob@example.com>
            Subject: =?UTF-8?B?TXVsdGlwYXJ0IE5ld3NsZXR0ZXI=?=
            Date: Mon, 28 Sep 2026 15:30:00 +0000
            Message-ID: <multi-789@example.com>
            Content-Type: multipart/alternative; boundary="$boundary"

            --$boundary
            Content-Type: text/plain; charset=UTF-8

            Plain text version of newsletter.

            --$boundary
            Content-Type: text/html; charset=UTF-8

            <html><body><p>HTML version of newsletter.</p></body></html>

            --$boundary--
        """.trimIndent()

        val msg = EmailMimeParser.parseRawMessage(raw, uid = 2L)
        assertEquals("<multi-789@example.com>", msg.messageId)
        // parseAddr strips surrounding quotes from display name
        assertEquals("Alice Smith <alice@example.com>", msg.from)
        assertEquals("Multipart Newsletter", msg.subject)
        assertEquals("Plain text version of newsletter.", msg.body)
        assertNotNull(msg.date)
    }

    // --- parseAddr ---

    @Test
    fun `parseAddr display name and angle address`() {
        val (name, addr) = EmailMimeParser.parseAddr("John Doe <john@example.com>")
        assertEquals("John Doe", name)
        assertEquals("john@example.com", addr)
    }

    @Test
    fun `parseAddr quoted display name strips quotes`() {
        val (name, addr) = EmailMimeParser.parseAddr("\"Alice Smith\" <alice@example.com>")
        assertEquals("Alice Smith", name)
        assertEquals("alice@example.com", addr)
    }

    @Test
    fun `parseAddr bare address no display name`() {
        val (name, addr) = EmailMimeParser.parseAddr("user@example.com")
        assertEquals("", name)
        assertEquals("user@example.com", addr)
    }

    @Test
    fun `parseAddr angle address only`() {
        val (name, addr) = EmailMimeParser.parseAddr("<user@example.com>")
        assertEquals("", name)
        assertEquals("user@example.com", addr)
    }

    @Test
    fun `parseAddr RFC2047 encoded display name is decoded`() {
        // "John Smith" Base64-encoded
        val (name, addr) = EmailMimeParser.parseAddr("=?UTF-8?B?Sm9obiBTbWl0aA==?= <john.smith@example.com>")
        assertEquals("John Smith", name)
        assertEquals("john.smith@example.com", addr)
    }

    // --- extractEmailAuthor ---

    @Test
    fun `extractEmailAuthor uses From header when present`() {
        val headers = mapOf("from" to "Jane Doe <jane@example.com>", "sender" to "relay@example.com")
        assertEquals("Jane Doe <jane@example.com>", EmailMimeParser.extractEmailAuthor(headers))
    }

    @Test
    fun `extractEmailAuthor falls back to Sender when From is absent`() {
        val headers = mapOf("sender" to "relay <relay@example.com>")
        assertEquals("relay <relay@example.com>", EmailMimeParser.extractEmailAuthor(headers))
    }

    @Test
    fun `extractEmailAuthor returns bare address when no display name`() {
        val headers = mapOf("from" to "plain@example.com")
        assertEquals("plain@example.com", EmailMimeParser.extractEmailAuthor(headers))
    }

    @Test
    fun `extractEmailAuthor returns blank when neither From nor Sender present`() {
        assertEquals("", EmailMimeParser.extractEmailAuthor(emptyMap()))
    }

    @Test
    fun `parseRawMessage uses Sender header as fallback when From is absent`() {
        val raw = "To: me@example.com\r\nSender: relay <relay@example.com>\r\nSubject: Via Sender\r\nDate: Mon, 28 Sep 2026 10:00:00 +0000\r\nMessage-ID: <sender-only@example.com>\r\n\r\nBody."

        val msg = EmailMimeParser.parseRawMessage(raw, uid = 20L)
        assertEquals("relay <relay@example.com>", msg.from)
    }

    @Test
    fun `parseRawMessage correctly parses From header as sender not author alias`() {
        val raw = """
            From: John Sender <john@sender.com>
            To: recipient@example.com
            Subject: Sender Test
            Date: Mon, 28 Sep 2026 10:00:00 +0000
            Message-ID: <sender-test@example.com>
            Content-Type: text/plain; charset=UTF-8

            Body text.
        """.trimIndent()

        val msg = EmailMimeParser.parseRawMessage(raw, uid = 10L)
        assertEquals("John Sender <john@sender.com>", msg.from)
        assertEquals("Sender Test", msg.subject)
        assertEquals("<sender-test@example.com>", msg.messageId)
    }

    @Test
    fun `parseRawMessage From header with RFC2047 encoded display name is decoded correctly`() {
        // Real-world case: display name is encoded, angle-address follows
        val encodedFrom = "=?UTF-8?B?Sm9obiBTbWl0aA==?= <john.smith@example.com>"
        val raw = "From: $encodedFrom\r\nTo: me@example.com\r\nSubject: Hi\r\nDate: Mon, 28 Sep 2026 10:00:00 +0000\r\nMessage-ID: <rfc2047-from@example.com>\r\n\r\nBody."

        val msg = EmailMimeParser.parseRawMessage(raw, uid = 11L)
        assertEquals("John Smith <john.smith@example.com>", msg.from)
    }
}
