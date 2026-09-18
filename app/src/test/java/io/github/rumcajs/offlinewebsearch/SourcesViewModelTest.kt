package io.github.rumcajs.offlinewebsearch

import io.github.rumcajs.offlinewebsearch.data.repositories.SourceOrder
import io.github.rumcajs.offlinewebsearch.ui.SOURCE_FILTER_OPTIONS
import io.github.rumcajs.offlinewebsearch.ui.SourcesViewModel
import org.junit.Assert.*
import org.junit.Test

class SourcesViewModelTest {

    @Test
    fun testSearchButtonState() {
        val viewModel = SourcesViewModel()

        // Initial state: disabled because searchQuery == activeSearchQuery == ""
        assertFalse(viewModel.isSearchButtonEnabled)

        // Type something: enabled
        viewModel.searchQuery = "news"
        assertTrue(viewModel.isSearchButtonEnabled)

        // Perform search: disabled
        viewModel.performSearch()
        assertFalse(viewModel.isSearchButtonEnabled)
        assertEquals("news", viewModel.activeSearchQuery)

        // Change query: enabled
        viewModel.searchQuery = "blog"
        assertTrue(viewModel.isSearchButtonEnabled)

        // Clear query
        viewModel.clearSearch()
        assertTrue(viewModel.isSearchButtonEnabled)
        viewModel.performSearch()
        assertFalse(viewModel.isSearchButtonEnabled)
        assertEquals("", viewModel.activeSearchQuery)
    }

    @Test
    fun testFilterToggling() {
        val viewModel = SourcesViewModel()

        // Default: ByUrl
        assertEquals(SourceOrder.ByUrl, viewModel.sourceOrder)
        assertEquals("by_url", viewModel.activeFilterKey)

        // Select ByTitle
        val titleOption = SOURCE_FILTER_OPTIONS.first { it.key == "by_title" }
        viewModel.setFilter(titleOption)
        assertEquals(SourceOrder.ByTitle, viewModel.sourceOrder)
        assertEquals("by_title", viewModel.activeFilterKey)

        // Toggle ByTitle again -> reverts to ByUrl
        viewModel.setFilter(titleOption)
        assertEquals(SourceOrder.ByUrl, viewModel.sourceOrder)
        assertEquals("by_url", viewModel.activeFilterKey)

        // Select ByFetchTime
        val fetchOption = SOURCE_FILTER_OPTIONS.first { it.key == "by_fetch_time" }
        viewModel.setFilter(fetchOption)
        assertEquals(SourceOrder.ByFetchTime, viewModel.sourceOrder)
        assertEquals("by_fetch_time", viewModel.activeFilterKey)

        // Toggle ByFetchTime again -> reverts to ByUrl
        viewModel.setFilter(fetchOption)
        assertEquals(SourceOrder.ByUrl, viewModel.sourceOrder)
        assertEquals("by_url", viewModel.activeFilterKey)
    }
}
