package io.github.rumcajs.offlinewebsearch.email

import android.util.Base64
import io.github.rumcajs.offlinewebsearch.util.DateUtils
import io.github.rumcajs.offlinewebsearch.util.HtmlUtils
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.Date
import java.util.regex.Pattern

/**
 * Parser and MIME utilities for decoding RFC 2047 encoded words,
 * Quoted-Printable, Base64, and raw RFC 822 email content into [EmailMessage].
 */
object EmailMimeParser {

    private val RFC2047_PATTERN = Pattern.compile("=\\?([^?]+)\\?([bBqQ])\\?([^?]+)\\?=")

    /**
     * Decodes RFC 2047 encoded-word strings (e.g. ` =?UTF-8?B?...?= ` or ` =?ISO-8859-1?Q?...?= `).
     *
     * @param input Raw header text.
     * @return Decoded readable string.
     */
    fun decodeRfc2047(input: String): String {
        if (!input.contains("=?") || !input.contains("?=")) {
            return input
        }

        val matcher = RFC2047_PATTERN.matcher(input)
        val sb = StringBuffer()
        while (matcher.find()) {
            val charsetName = matcher.group(1) ?: "UTF-8"
            val encodingType = matcher.group(2)?.uppercase() ?: "B"
            val encodedText = matcher.group(3) ?: ""

            val decodedWord = try {
                val charset = runCatching { Charset.forName(charsetName) }.getOrDefault(Charsets.UTF_8)
                if (encodingType == "B") {
                    val bytes = Base64.decode(encodedText, Base64.DEFAULT)
                    String(bytes, charset)
                } else if (encodingType == "Q") {
                    decodeQuotedPrintableHeader(encodedText, charset)
                } else {
                    matcher.group(0) ?: ""
                }
            } catch (_: Exception) {
                matcher.group(0) ?: ""
            }

            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(decodedWord))
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    /**
     * Decodes Quoted-Printable text within headers (where `_` represents space).
     */
    private fun decodeQuotedPrintableHeader(encoded: String, charset: Charset): String {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < encoded.length) {
            val c = encoded[i]
            if (c == '_') {
                out.write(' '.code)
                i++
            } else if (c == '=' && i + 2 < encoded.length) {
                val hex = encoded.substring(i + 1, i + 3)
                val byteVal = hex.toIntOrNull(16)
                if (byteVal != null) {
                    out.write(byteVal)
                    i += 3
                } else {
                    out.write(c.code)
                    i++
                }
            } else {
                out.write(c.code)
                i++
            }
        }
        return out.toString(charset.name())
    }

    /**
     * Decodes standard Quoted-Printable body content.
     */
    fun decodeQuotedPrintableBody(encoded: String, charset: Charset = Charsets.UTF_8): String {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < encoded.length) {
            val c = encoded[i]
            if (c == '=' && i + 1 < encoded.length && encoded[i + 1] == '\n') {
                // Soft line break =\n
                i += 2
            } else if (c == '=' && i + 2 < encoded.length && encoded[i + 1] == '\r' && encoded[i + 2] == '\n') {
                // Soft line break =\r\n
                i += 3
            } else if (c == '=' && i + 2 < encoded.length) {
                val hex = encoded.substring(i + 1, i + 3)
                val byteVal = hex.toIntOrNull(16)
                if (byteVal != null) {
                    out.write(byteVal)
                    i += 3
                } else {
                    out.write(c.code)
                    i++
                }
            } else {
                out.write(c.code)
                i++
            }
        }
        return try {
            out.toString(charset.name())
        } catch (_: Exception) {
            out.toString(Charsets.UTF_8.name())
        }
    }

    /**
     * Parses a raw RFC 822 email text into an [EmailMessage].
     *
     * @param rawText Complete raw RFC 822 email message content.
     * @param uid Optional message UID.
     * @return Populated [EmailMessage].
     */
    fun parseRawMessage(rawText: String, uid: Long? = null): EmailMessage {
        val headerBodySplit = rawText.indexOf("\r\n\r\n")
        val headerPart: String
        val bodyPart: String

        if (headerBodySplit != -1) {
            headerPart = rawText.substring(0, headerBodySplit)
            bodyPart = rawText.substring(headerBodySplit + 4)
        } else {
            val unixSplit = rawText.indexOf("\n\n")
            if (unixSplit != -1) {
                headerPart = rawText.substring(0, unixSplit)
                bodyPart = rawText.substring(unixSplit + 2)
            } else {
                headerPart = rawText
                bodyPart = ""
            }
        }

        val headers = parseHeaders(headerPart)

        val messageId = headers["message-id"] ?: ""
        val from = extractEmailAuthor(headers)
        val to = decodeRfc2047(headers["to"] ?: "")
        val subject = decodeRfc2047(headers["subject"] ?: "")
        val dateHeader = headers["date"]
        val date = dateHeader?.let { parseDate(it) }

        val contentType = headers["content-type"] ?: "text/plain"
        val contentTransferEncoding = headers["content-transfer-encoding"] ?: "7bit"

        val body = extractBody(bodyPart, contentType, contentTransferEncoding)

        return EmailMessage(
            messageId = messageId,
            from = from,
            to = to,
            subject = subject,
            date = date,
            body = body.trim(),
            uid = uid
        )
    }

    /**
     * Splits an email address header value into a `(displayName, address)` pair,
     * mirroring Python's `email.utils.parseaddr`.
     *
     * Handles the following common formats:
     * - `Display Name <user@host>` → `("Display Name", "user@host")`
     * - `<user@host>` → `("", "user@host")`
     * - `user@host` → `("", "user@host")`
     * - `"Quoted Name" <user@host>` → `("Quoted Name", "user@host")`
     *
     * @param raw Raw header value (not yet RFC 2047 decoded).
     * @return Pair of (decoded display name, bare email address). Either may be blank.
     */
    fun parseAddr(raw: String): Pair<String, String> {
        val angleStart = raw.lastIndexOf('<')
        val angleEnd = raw.indexOf('>', angleStart.coerceAtLeast(0))
        return if (angleStart != -1 && angleEnd != -1 && angleEnd > angleStart) {
            val addr = raw.substring(angleStart + 1, angleEnd).trim()
            val namePart = raw.substring(0, angleStart).trim().trimStart('"').trimEnd('"').trim()
            val name = decodeRfc2047(namePart)
            Pair(name, addr)
        } else {
            // No angle brackets — treat the whole value as a bare address
            Pair("", raw.trim())
        }
    }

    /**
     * Extracts the sender display string from [headers], trying `From` first then `Sender` as
     * a fallback — mirroring the Python reference:
     * ```python
     * name, addr = parseaddr(msg.get("From") or msg.get("Sender"))
     * return f"{name} <{addr}>" if name else addr
     * ```
     *
     * @param headers Lowercase-keyed map of parsed email headers.
     * @return Formatted sender string (`"Name <addr>"` or bare `addr`), or blank if neither header is present.
     */
    fun extractEmailAuthor(headers: Map<String, String>): String {
        for (headerKey in listOf("from", "sender")) {
            val raw = headers[headerKey]?.takeIf { it.isNotBlank() } ?: continue
            val (name, addr) = parseAddr(raw)
            if (addr.isNotBlank()) {
                return if (name.isNotBlank()) "$name <$addr>" else addr
            }
        }
        return ""
    }

    /**
     * Parses email headers handling multiline header folding.
     */
    fun parseHeaders(headerText: String): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        val lines = headerText.lines()
        var currentKey: String? = null
        var currentValue = StringBuilder()

        for (line in lines) {
            if (line.startsWith(" ") || line.startsWith("\t")) {
                if (currentKey != null) {
                    currentValue.append(" ").append(line.trim())
                }
            } else {
                if (currentKey != null) {
                    headers[currentKey.lowercase()] = currentValue.toString().trim()
                }
                val colonIdx = line.indexOf(':')
                if (colonIdx != -1) {
                    currentKey = line.substring(0, colonIdx).trim()
                    currentValue = StringBuilder(line.substring(colonIdx + 1).trim())
                } else {
                    currentKey = null
                }
            }
        }
        if (currentKey != null) {
            headers[currentKey.lowercase()] = currentValue.toString().trim()
        }
        return headers
    }

    /**
     * Extracts and decodes plain text body from the message body, handling multipart boundaries.
     */
    private fun extractBody(bodyPart: String, contentType: String, transferEncoding: String): String {
        val lowerType = contentType.lowercase()
        val charset = extractCharset(contentType)

        if (lowerType.contains("multipart/")) {
            val boundary = extractBoundary(contentType)
            if (boundary != null) {
                return extractMultipartText(bodyPart, boundary)
            }
        }

        val decoded = when {
            transferEncoding.equals("base64", ignoreCase = true) -> {
                try {
                    val cleanBase64 = bodyPart.filterNot { it.isWhitespace() }
                    val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                    String(bytes, charset)
                } catch (_: Exception) {
                    bodyPart
                }
            }
            transferEncoding.equals("quoted-printable", ignoreCase = true) -> {
                decodeQuotedPrintableBody(bodyPart, charset)
            }
            else -> bodyPart
        }

        return if (lowerType.contains("text/html")) {
            stripHtml(decoded)
        } else {
            decoded
        }
    }

    /**
     * Extracts text from a multipart MIME body by finding text/plain or text/html parts.
     */
    private fun extractMultipartText(body: String, boundary: String): String {
        val delimiter = "--$boundary"
        val parts = body.split(delimiter)
        var plainText: String? = null
        var htmlText: String? = null

        for (part in parts) {
            val trimmedPart = part.trim()
            if (trimmedPart.isEmpty() || trimmedPart == "--") continue

            val headerBodySplit = trimmedPart.indexOf("\r\n\r\n")
            val partHeadersText: String
            val partBody: String

            if (headerBodySplit != -1) {
                partHeadersText = trimmedPart.substring(0, headerBodySplit)
                partBody = trimmedPart.substring(headerBodySplit + 4)
            } else {
                val unixSplit = trimmedPart.indexOf("\n\n")
                if (unixSplit != -1) {
                    partHeadersText = trimmedPart.substring(0, unixSplit)
                    partBody = trimmedPart.substring(unixSplit + 2)
                } else {
                    partHeadersText = ""
                    partBody = trimmedPart
                }
            }

            val partHeaders = parseHeaders(partHeadersText)
            val partType = partHeaders["content-type"] ?: "text/plain"
            val partEncoding = partHeaders["content-transfer-encoding"] ?: "7bit"

            val decoded = extractBody(partBody, partType, partEncoding)

            if (partType.contains("text/plain", ignoreCase = true) && plainText == null) {
                plainText = decoded
            } else if (partType.contains("text/html", ignoreCase = true) && htmlText == null) {
                htmlText = decoded
            }
        }

        return plainText ?: htmlText ?: ""
    }

    private fun extractCharset(contentType: String): Charset {
        val match = Pattern.compile("charset=[\"']?([^\"';\\s]+)[\"']?", Pattern.CASE_INSENSITIVE).matcher(contentType)
        if (match.find()) {
            val name = match.group(1) ?: "UTF-8"
            return runCatching { Charset.forName(name) }.getOrDefault(Charsets.UTF_8)
        }
        return Charsets.UTF_8
    }

    private fun extractBoundary(contentType: String): String? {
        val match = Pattern.compile("boundary=[\"']?([^\"';\\s]+)[\"']?", Pattern.CASE_INSENSITIVE).matcher(contentType)
        return if (match.find()) match.group(1) else null
    }

    /**
     * Strips HTML tags and unescapes HTML entities.
     */
    private fun stripHtml(html: String): String {
        return HtmlUtils.stripHtml(html)
    }

    /**
     * Parses an email date string.
     */
    fun parseDate(dateStr: String): Date? {
        return DateUtils.parseDateString(dateStr)
    }
}
