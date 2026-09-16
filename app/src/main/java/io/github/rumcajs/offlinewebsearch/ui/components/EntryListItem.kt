package io.github.rumcajs.offlinewebsearch.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.rumcajs.offlinewebsearch.data.AppConfigManager
import io.github.rumcajs.offlinewebsearch.data.ViewStyle
import io.github.rumcajs.offlinewebsearch.data.repositories.Entry

/**
 * Polymorphic entry item dispatcher that produces a specific entry list item
 * ([EntryListGalleryItem], [EntryListStandardItem], or [EntryListSearchEngineItem])
 * depending on the provided or configured [ViewStyle].
 *
 * @param entry The database entry to display.
 * @param onClick Callback triggered when the item is tapped.
 * @param modifier Optional modifier for styling.
 * @param viewStyle Optional override for view style; defaults to active database configuration.
 */
@Composable
fun EntryListItem(
    entry: Entry,
    onClick: (Entry) -> Unit,
    modifier: Modifier = Modifier,
    viewStyle: ViewStyle? = null
) {
    val config by AppConfigManager.config.collectAsState()
    val effectiveStyle = viewStyle ?: config.dbconfig.viewStyle

    when (effectiveStyle) {
        ViewStyle.GALLERY -> EntryListGalleryItem(
            entry = entry,
            onClick = onClick,
            modifier = modifier
        )
        ViewStyle.STANDARD -> EntryListStandardItem(
            entry = entry,
            onClick = onClick,
            modifier = modifier
        )
        ViewStyle.SEARCH_ENGINE -> EntryListSearchEngineItem(
            entry = entry,
            onClick = onClick,
            modifier = modifier
        )
    }
}
