package io.github.rumcajs.offlinewebsearch.data.repositories

/**
 * Predefined icon identifiers for use in [Source.favicon].
 *
 * Each entry is a [SourceIcon] holding a human-readable [label] and a string [value]
 * that can be stored directly in the `favicon` column of the `sourcedatamodel` table.
 *
 * The [value] uses the Material Symbols / Icons ligature name convention so that the UI
 * layer can resolve it to the corresponding vector drawable or font glyph without any
 * network round-trip.
 */
data class SourceIcon(
    /** Display name shown to the user when picking an icon. */
    val label: String,
    /** Identifier stored in [Source.favicon]; corresponds to a Material icon name or URI scheme. */
    val value: String,
)

/**
 * Catalogue of predefined [SourceIcon] entries available for [Source.favicon].
 *
 * Usage:
 * ```kotlin
 * val emailIcon = SourceIcons.all.first { it.value == "email" }
 * source.copy(favicon = emailIcon.value)
 * ```
 */
object SourceIcons {

    /** Complete list of predefined source icons. */
    val all: List<SourceIcon> = listOf(

        // ── Communication ────────────────────────────────────────────────────────
        SourceIcon(label = "Email",           value = "email"),
        SourceIcon(label = "Chat",            value = "chat"),
        SourceIcon(label = "Forum",           value = "forum"),
        SourceIcon(label = "SMS",             value = "sms"),

        // ── News & Publishing ────────────────────────────────────────────────────
        SourceIcon(label = "RSS Feed",        value = "rss_feed"),
        SourceIcon(label = "Article",         value = "article"),
        SourceIcon(label = "Newspaper",       value = "newspaper"),
        SourceIcon(label = "Book",            value = "book"),
        SourceIcon(label = "Menu Book",       value = "menu_book"),
        SourceIcon(label = "Notes",           value = "notes"),

        // ── Social & Community ───────────────────────────────────────────────────
        SourceIcon(label = "People",          value = "people"),
        SourceIcon(label = "Person",          value = "person"),
        SourceIcon(label = "Group",           value = "group"),
        SourceIcon(label = "Public",          value = "public"),
        SourceIcon(label = "Share",           value = "share"),

        // ── Multimedia ───────────────────────────────────────────────────────────
        SourceIcon(label = "Podcast",         value = "podcasts"),
        SourceIcon(label = "Video",           value = "videocam"),
        SourceIcon(label = "Movie",           value = "movie"),
        SourceIcon(label = "Music",           value = "music_note"),
        SourceIcon(label = "Image",           value = "image"),
        SourceIcon(label = "Photo",           value = "photo_camera"),

        // ── Technology ───────────────────────────────────────────────────────────
        SourceIcon(label = "Code",            value = "code"),
        SourceIcon(label = "Terminal",        value = "terminal"),
        SourceIcon(label = "Bug",             value = "bug_report"),
        SourceIcon(label = "Computer",        value = "computer"),
        SourceIcon(label = "Smartphone",      value = "smartphone"),
        SourceIcon(label = "Cloud",           value = "cloud"),

        // ── Science & Knowledge ──────────────────────────────────────────────────
        SourceIcon(label = "Science",         value = "science"),
        SourceIcon(label = "Biotech",         value = "biotech"),
        SourceIcon(label = "School",          value = "school"),
        SourceIcon(label = "Library",         value = "local_library"),
        SourceIcon(label = "Research",        value = "manage_search"),

        // ── Finance & Business ───────────────────────────────────────────────────
        SourceIcon(label = "Business",        value = "business"),
        SourceIcon(label = "Finance",         value = "account_balance"),
        SourceIcon(label = "Trending",        value = "trending_up"),
        SourceIcon(label = "Payments",        value = "payments"),

        // ── Government & Law ─────────────────────────────────────────────────────
        SourceIcon(label = "Gavel",           value = "gavel"),
        SourceIcon(label = "Balance",         value = "balance"),
        SourceIcon(label = "Policy",          value = "policy"),

        // ── Sports & Health ──────────────────────────────────────────────────────
        SourceIcon(label = "Sports",          value = "sports"),
        SourceIcon(label = "Fitness",         value = "fitness_center"),
        SourceIcon(label = "Health",          value = "health_and_safety"),
        SourceIcon(label = "Medical",         value = "local_hospital"),

        // ── Nature & Environment ─────────────────────────────────────────────────
        SourceIcon(label = "Nature",          value = "nature"),
        SourceIcon(label = "Weather",         value = "wb_sunny"),
        SourceIcon(label = "Globe",           value = "language"),

        // ── Generic ──────────────────────────────────────────────────────────────
        SourceIcon(label = "Star",            value = "star"),
        SourceIcon(label = "Bookmark",        value = "bookmark"),
        SourceIcon(label = "Link",            value = "link"),
        SourceIcon(label = "Info",            value = "info"),
        SourceIcon(label = "Search",          value = "search"),
    )
}
