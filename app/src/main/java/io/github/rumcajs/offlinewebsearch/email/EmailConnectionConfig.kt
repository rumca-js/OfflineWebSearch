package io.github.rumcajs.offlinewebsearch.email

import io.github.rumcajs.offlinewebsearch.data.repositories.Credentials
import io.github.rumcajs.offlinewebsearch.data.repositories.Source
import java.net.URI

/**
 * Configuration parameters for connecting to an email server.
 *
 * @property host Hostname or IP address of the email server.
 * @property port Port number (default: 993 for IMAPS, 143 for IMAP).
 * @property useSsl True if SSL/TLS connection should be established directly.
 * @property folder Mailbox/folder to read from (default: "INBOX").
 * @property username Account username or email address for authentication.
 * @property password Account password or authentication secret.
 */
data class EmailConnectionConfig(
    val host: String,
    val port: Int = 993,
    val useSsl: Boolean = true,
    val folder: String = "INBOX",
    val username: String = "",
    val password: String = ""
) {
    companion object {
        const val DEFAULT_IMAPS_PORT = 993
        const val DEFAULT_IMAP_PORT = 143
        const val DEFAULT_FOLDER = "INBOX"

        /**
         * Parses and builds an [EmailConnectionConfig] from a [Source] and optional [Credentials].
         *
         * Supports [Source.url] formats such as:
         * - `imaps://mail.example.com`
         * - `imap://mail.example.com:143`
         * - `imaps://user:pass@mail.example.com:993/INBOX`
         * - `mail.example.com:993`
         * - `mail.example.com`
         * - `email://mail.example.com/INBOX`
         *
         * @param source The source containing the server URL.
         * @param credentials Optional credentials record associated with the source.
         * @return Populated [EmailConnectionConfig].
         */
        fun fromSource(source: Source, credentials: Credentials? = null): EmailConnectionConfig {
            var rawUrl = source.url.trim()
            if (rawUrl.startsWith("email://", ignoreCase = true)) {
                rawUrl = "imaps://" + rawUrl.substring("email://".length)
            } else if (!rawUrl.contains("://")) {
                rawUrl = "imaps://$rawUrl"
            }

            var host = ""
            var port = DEFAULT_IMAPS_PORT
            var useSsl = true
            var folder = DEFAULT_FOLDER
            var urlUser = ""
            var urlPass = ""

            try {
                val uri = URI(rawUrl)
                val scheme = uri.scheme?.lowercase() ?: "imaps"
                useSsl = scheme != "imap"

                host = uri.host ?: ""
                val uriPort = uri.port
                port = if (uriPort > 0) {
                    uriPort
                } else if (useSsl) {
                    DEFAULT_IMAPS_PORT
                } else {
                    DEFAULT_IMAP_PORT
                }

                val path = uri.path?.trimStart('/') ?: ""
                if (path.isNotBlank()) {
                    folder = path
                }

                val userInfo = uri.userInfo
                if (!userInfo.isNullOrBlank()) {
                    val parts = userInfo.split(":", limit = 2)
                    urlUser = parts.getOrNull(0) ?: ""
                    urlPass = parts.getOrNull(1) ?: ""
                }
            } catch (_: Exception) {
                // Fallback basic host[:port] extraction
                var clean = rawUrl.substringAfter("://")
                if (clean.contains("/")) {
                    val pathPart = clean.substringAfter("/")
                    if (pathPart.isNotBlank()) folder = pathPart
                    clean = clean.substringBefore("/")
                }
                if (clean.contains("@")) {
                    val userPart = clean.substringBefore("@")
                    val parts = userPart.split(":", limit = 2)
                    urlUser = parts.getOrNull(0) ?: ""
                    urlPass = parts.getOrNull(1) ?: ""
                    clean = clean.substringAfter("@")
                }
                if (clean.contains(":")) {
                    host = clean.substringBefore(":")
                    port = clean.substringAfter(":").toIntOrNull() ?: DEFAULT_IMAPS_PORT
                } else {
                    host = clean
                }
            }

            val finalUsername = credentials?.username?.takeIf { it.isNotBlank() }
                ?: urlUser.takeIf { it.isNotBlank() }
                ?: ""

            val finalPassword = credentials?.password?.takeIf { it.isNotBlank() }
                ?: credentials?.secret?.takeIf { it.isNotBlank() }
                ?: credentials?.token?.takeIf { it.isNotBlank() }
                ?: urlPass.takeIf { it.isNotBlank() }
                ?: ""

            return EmailConnectionConfig(
                host = host,
                port = port,
                useSsl = useSsl,
                folder = folder,
                username = finalUsername,
                password = finalPassword
            )
        }
    }
}
