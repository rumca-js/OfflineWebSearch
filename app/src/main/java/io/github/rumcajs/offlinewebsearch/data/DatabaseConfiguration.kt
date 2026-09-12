package io.github.rumcajs.offlinewebsearch.data

import io.github.rumcajs.offlinewebsearch.webtoolkit.UrlLocation
import kotlinx.serialization.Serializable

@Serializable
enum class OrderBy(val displayName: String) {
    PAGE_RATING_VOTES("Page Rating Votes"),
    PAGE_RATING_VISITS_DESC("Page Rating Visits (Highest first)"),
    PAGE_RATING_VISITS_ASC("Page Rating Visits (Lowest first)"),
    DATE_CREATED("Date Created"),
    DATE_PUBLISHED("Date Published"),
    STARS_DESC("Stars (Highest first)"),
    STARS_ASC("Stars (Lowest first)"),
    FOLLOWERS_COUNT_DESC("Followers (Highest first)"),
    FOLLOWERS_COUNT_ASC("Followers (Lowest first)")
}

@Serializable
enum class DatabaseStatus {
    INIT,
    DOWNLOADING,
    UNPACKING,
    READY,
    FAILED
}

@Serializable
data class DatabaseState(
    /** Network URL or local:// source path */
    val url: String = "",
    /** File name used in app internal storage (e.g. "db_12345.db") */
    val localFileName: String = "",
    val displayNameField: String = "",
    val status: DatabaseStatus = DatabaseStatus.INIT,
    val progress: Float = 0f,
    val errorMessage: String? = null,
    val sizeInBytes: Long = 0L,
    val isReadOnly: Boolean = true,
    /** ISO-8601 timestamp of when the database was first added. Null if unknown. */
    val dateCreated: String? = null,
    /** ISO-8601 timestamp of the most recent successful fetch or refresh. Null if never refreshed. */
    val dateLastRefresh: String? = null
) {
    /** The storage extension of the local file: ".db" or ".json" */
    val extension: String
        get() = if (localFileName.endsWith(".db")) ".db" else ".json"

    val isLocal: Boolean
        get() = url.startsWith(LOCAL_PREFIX)

    val isSQLite: Boolean
        get() = extension == ".db"

    val displayName: String
        get() = when {
            displayNameField.isNotBlank() -> displayNameField
            isLocal -> url.removePrefix(LOCAL_PREFIX)
            url.isBlank() -> DEFAULT_DATABASE_NAME
            else -> {
                val fileName = UrlLocation(url).getFileName()
                fileName.ifEmpty { DEFAULT_DATABASE_NAME }
            }
        }

    companion object {
        const val LOCAL_PREFIX = "local://"

        fun toLocalUrl(fileName: String): String {
            return if (fileName.startsWith(LOCAL_PREFIX)) fileName else "$LOCAL_PREFIX$fileName"
        }

        /**
         * Derives the local file name from a source URL.
         * .db.zip URLs are stored as .db after unpacking.
         */
        private fun deriveLocalFileName(url: String): String {
            val ext = when {
                url.endsWith(".db.zip", ignoreCase = true) -> ".db"
                url.endsWith(".db", ignoreCase = true) -> ".db"
                else -> ".json"
            }
            return "db_${url.hashCode()}$ext"
        }

        /** Creates a DatabaseState with a localFileName derived from the URL */
        fun fromUrl(url: String): DatabaseState {
            val localName = deriveLocalFileName(url)
            return DatabaseState(
                url = url,
                localFileName = localName,
                isReadOnly = !localName.endsWith(".db")
            )
        }
    }
}

@Serializable
enum class ViewStyle(val displayName: String) {
    GALLERY("Gallery"),
    SEARCH_ENGINE("Search Engine"),
    STANDARD("Standard")
}

@Serializable
data class DatabaseConfiguration(
    val instanceTitle: String = "",
    val instanceDescription: String = "",

    val directLinks: Boolean = false,
    val showIcons: Boolean = false,
    val videoPreview: Boolean = false,
    val orderBy: OrderBy = OrderBy.PAGE_RATING_VOTES,
    val viewStyle: ViewStyle = ViewStyle.SEARCH_ENGINE,
    val linksPerPage: Int = MIN_LINKS_PER_PAGE,
    val trackUserSearches: Boolean = true,
    val trackUserNavigation: Boolean = true,
    // Capability flags read from configurationentry table
    val enableKeywordSupport: Boolean = false,
    val enableDomainSupport: Boolean = false,
    val enableFileSupport: Boolean = false,
    val enableLinkArchiving: Boolean = false,
    val enableSourceArchiving: Boolean = false,
    val enableCrawling: Boolean = false,
    val enableSocialData: Boolean = false,
    // Link acceptance policy read from configurationentry table
    val acceptDeadLinks: Boolean = false,
    val acceptIpLinks: Boolean = false,
    val acceptDomainLinks: Boolean = false,
    val acceptNonDomainLinks: Boolean = false,
    val acceptUnknownLinks: Boolean = false,
    val acceptOnionLinks: Boolean = false,
    val acceptSameHashes: Boolean = false,
    // Visual alpha settings read from configurationentry table
    val entriesVisitAlpha: Float = 0.6f,
    val entriesDeadAlpha: Float = 0.6f
) {
    val effectiveLinksPerPage: Int
        get() = kotlin.math.max(MIN_LINKS_PER_PAGE, linksPerPage)

    companion object {
        const val MIN_LINKS_PER_PAGE = 100
    }
}
