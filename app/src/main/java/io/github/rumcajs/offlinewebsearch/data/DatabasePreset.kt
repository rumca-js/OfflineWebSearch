package io.github.rumcajs.offlinewebsearch.data

import kotlinx.serialization.Serializable

/**
 * Data class representing a preselected database entry in `databases.json`.
 *
 * @property url   Remote or download URL for the database archive or file.
 * @property title Optional human-readable title or label for the database preset.
 */
@Serializable
data class DatabasePreset(
    val url: String = "",
    val title: String? = null
)
