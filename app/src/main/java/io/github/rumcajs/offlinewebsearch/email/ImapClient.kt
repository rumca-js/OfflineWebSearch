package io.github.rumcajs.offlinewebsearch.email

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Lightweight IMAP4rev1 client implementation using standard sockets.
 *
 * Supports plain IMAP (port 143) and direct SSL/TLS IMAPS (port 993).
 *
 * @property config Connection and credential configuration.
 */
class ImapClient(
    private val config: EmailConnectionConfig
) : EmailClient {

    private var socket: Socket? = null
    private var inStream: BufferedInputStream? = null
    private var outStream: BufferedOutputStream? = null
    private val tagSequence = AtomicInteger(1)
    private var existsCount: Int = 0

    companion object {
        const val SOCKET_TIMEOUT_MS = 15000
    }

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (config.host.isBlank()) return@withContext false

            val sock: Socket = if (config.useSsl) {
                val sslFactory = SSLSocketFactory.getDefault()
                val sslSock = sslFactory.createSocket() as SSLSocket
                sslSock.connect(InetSocketAddress(config.host, config.port), SOCKET_TIMEOUT_MS)
                sslSock.soTimeout = SOCKET_TIMEOUT_MS
                sslSock.startHandshake()
                sslSock
            } else {
                val plainSock = Socket()
                plainSock.connect(InetSocketAddress(config.host, config.port), SOCKET_TIMEOUT_MS)
                plainSock.soTimeout = SOCKET_TIMEOUT_MS
                plainSock
            }

            socket = sock
            inStream = BufferedInputStream(sock.getInputStream())
            outStream = BufferedOutputStream(sock.getOutputStream())

            // Read initial greeting line
            val greeting = readLine()
            greeting != null && (greeting.startsWith("* OK", ignoreCase = true) || greeting.startsWith("* PREAUTH", ignoreCase = true))
        } catch (_: Exception) {
            close()
            false
        }
    }

    override suspend fun login(username: String, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (username.isBlank()) return@withContext false
            val tag = nextTag()
            val escapedUser = escapeImapString(username)
            val escapedPass = escapeImapString(password)
            val command = "$tag LOGIN \"$escapedUser\" \"$escapedPass\"\r\n"
            sendRaw(command)

            val response = readCommandResponse(tag)
            response.isOk
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun selectFolder(folder: String): Int = withContext(Dispatchers.IO) {
        try {
            val tag = nextTag()
            val escapedFolder = escapeImapString(folder)
            val command = "$tag SELECT \"$escapedFolder\"\r\n"
            sendRaw(command)

            val response = readCommandResponse(tag)
            if (!response.isOk) return@withContext -1

            // Parse "* <N> EXISTS" from response lines
            var count = 0
            for (line in response.lines) {
                val upper = line.uppercase()
                if (upper.startsWith("*") && upper.contains("EXISTS")) {
                    val parts = line.trim().split("\\s+".toRegex())
                    val num = parts.getOrNull(1)?.toIntOrNull()
                    if (num != null) {
                        count = num
                    }
                }
            }
            existsCount = count
            count
        } catch (_: Exception) {
            -1
        }
    }

    override suspend fun fetchMessages(sinceDate: Date?, maxCount: Int): List<EmailMessage> = withContext(Dispatchers.IO) {
        val messages = mutableListOf<EmailMessage>()
        if (existsCount <= 0) return@withContext messages

        val startSeq = existsCount
        val endSeq = maxOf(1, existsCount - maxCount + 1)

        for (seq in startSeq downTo endSeq) {
            try {
                val tag = nextTag()
                val command = "$tag FETCH $seq (BODY.PEEK[])\r\n"
                sendRaw(command)

                val response = readCommandResponse(tag)
                if (response.isOk) {
                    val rawText = if (response.rawPayload.isNotBlank()) {
                        response.rawPayload
                    } else {
                        // Fallback for servers that send BODY[] content inline without a literal block.
                        // Strip leading IMAP envelope lines (e.g. "* N FETCH (BODY[] ...)") and
                        // trailing IMAP tag/status lines so only the raw RFC 822 message remains.
                        response.lines
                            .dropWhile { line ->
                                line.startsWith("*") && line.uppercase().contains("FETCH")
                            }
                            .dropLastWhile { line ->
                                line.startsWith(tag, ignoreCase = true) || line == ")"
                            }
                            .joinToString("\r\n")
                    }
                    val emailMsg = EmailMimeParser.parseRawMessage(rawText, uid = seq.toLong())

                    if (sinceDate != null && emailMsg.date != null && emailMsg.date.time <= sinceDate.time) {
                        // Encountered email older than or equal to last fetched date; stop processing older messages
                        break
                    }

                    messages.add(emailMsg)
                }
            } catch (_: Exception) {
                break
            }
        }

        messages
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            if (socket?.isConnected == true) {
                val tag = nextTag()
                sendRaw("$tag LOGOUT\r\n")
            }
        } catch (_: Exception) { }
        close()
    }

    override fun close() {
        try { inStream?.close() } catch (_: Exception) { }
        try { outStream?.close() } catch (_: Exception) { }
        try { socket?.close() } catch (_: Exception) { }
        inStream = null
        outStream = null
        socket = null
    }

    private fun nextTag(): String = "A%04d".format(tagSequence.getAndIncrement())

    private fun escapeImapString(str: String): String = str.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun sendRaw(command: String) {
        val bytes = command.toByteArray(StandardCharsets.UTF_8)
        outStream?.write(bytes)
        outStream?.flush()
    }

    private fun readLine(): String? {
        val inS = inStream ?: return null
        val baos = ByteArrayOutputStream()
        var prev = -1
        while (true) {
            val b = inS.read()
            if (b == -1) {
                if (baos.size() == 0) return null
                break
            }
            if (b == '\n'.code && prev == '\r'.code) {
                break
            }
            if (prev != -1 && prev != '\r'.code) {
                baos.write(prev)
            }
            prev = b
        }
        return baos.toString(StandardCharsets.UTF_8.name())
    }

    private class CommandResponse(
        val isOk: Boolean,
        val lines: List<String>,
        val rawPayload: String
    )

    private fun readCommandResponse(tag: String): CommandResponse {
        val inS = inStream ?: return CommandResponse(false, emptyList(), "")
        val lines = mutableListOf<String>()
        val payloadBaos = ByteArrayOutputStream()
        var isOk = false

        while (true) {
            val line = readLine() ?: break
            lines.add(line)

            if (line.startsWith("$tag OK", ignoreCase = true)) {
                isOk = true
                break
            } else if (line.startsWith("$tag NO", ignoreCase = true) || line.startsWith("$tag BAD", ignoreCase = true)) {
                isOk = false
                break
            }

            // Check for IMAP literal {<size>}
            val literalMatch = Regex("\\{(\\d+)\\}$").find(line.trim())
            if (literalMatch != null) {
                val size = literalMatch.groupValues[1].toIntOrNull() ?: 0
                if (size > 0) {
                    val buffer = ByteArray(size)
                    var bytesRead = 0
                    while (bytesRead < size) {
                        val count = inS.read(buffer, bytesRead, size - bytesRead)
                        if (count == -1) break
                        bytesRead += count
                    }
                    payloadBaos.write(buffer, 0, bytesRead)
                }
            }
        }

        val rawPayload = payloadBaos.toString(StandardCharsets.UTF_8.name())
        return CommandResponse(isOk, lines, rawPayload)
    }
}
