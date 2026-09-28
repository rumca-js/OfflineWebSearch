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
        assertEquals("\"Alice Smith\" <alice@example.com>", msg.from)
        assertEquals("Multipart Newsletter", msg.subject)
        assertEquals("Plain text version of newsletter.", msg.body)
        assertNotNull(msg.date)
    }
}
