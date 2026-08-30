package io.github.rumcajs.offlinewebsearch

import io.github.rumcajs.offlinewebsearch.ui.SearchViewModel
import org.junit.Assert.*
import org.junit.Test

class SearchViewModelTest {

    @Test
    fun testSuggestionsVisibility() {
        val viewModel = _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.SearchViewModel()
        
        // Initial state
        assertTrue(viewModel.suggestions.isEmpty())
        assertFalse(viewModel.showSuggestions)

        // Type something
        viewModel.searchQuery = "test"
        viewModel.showSuggestions = true
        
        // Add to history to have suggestions
        viewModel.performSearch() // This will hide suggestions but add "test" to history
        assertFalse(viewModel.showSuggestions)
        assertTrue(viewModel.suggestions.isEmpty())

        // Type again
        viewModel.searchQuery = "te"
        viewModel.showSuggestions = true
        
        // Suggestions should be visible now
        assertFalse(viewModel.suggestions.isEmpty())
        assertEquals("test", viewModel.suggestions[0])

        // Perform search (simulating clicking a suggestion or pressing search)
        viewModel.performSearch()
        
        // Suggestions should be hidden
        assertFalse(viewModel.showSuggestions)
        assertTrue(viewModel.suggestions.isEmpty())
    }

    @Test
    fun testSearchButtonState() {
        val viewModel = _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.SearchViewModel()
        
        // Initial state: disabled because searchQuery == activeSearchQuery == ""
        assertFalse(viewModel.isSearchButtonEnabled)

        // Type something: enabled
        viewModel.searchQuery = "paris"
        assertTrue(viewModel.isSearchButtonEnabled)

        // Perform search: disabled
        viewModel.performSearch()
        assertFalse(viewModel.isSearchButtonEnabled)
        assertEquals("paris", viewModel.activeSearchQuery)

        // Change input: enabled
        viewModel.searchQuery = "pari"
        assertTrue(viewModel.isSearchButtonEnabled)

        // Change back to original: disabled
        viewModel.searchQuery = "paris"
        assertFalse(viewModel.isSearchButtonEnabled)

        // Clear search: enabled (if activeSearchQuery is not "" yet)
        viewModel.searchQuery = ""
        assertTrue(viewModel.isSearchButtonEnabled)

        // Perform search (clearing): disabled
        viewModel.performSearch()
        assertFalse(viewModel.isSearchButtonEnabled)
        assertEquals("", viewModel.activeSearchQuery)
    }

    @Test
    fun testFilterOptionsMutualExclusivity() {
        val viewModel = SearchViewModel()

        // Initial state: None
        assertEquals(io.github.rumcajs.offlinewebsearch.ui.SearchFilter.None, viewModel.activeFilter)
        assertFalse(viewModel.isFilterVisited)
        assertFalse(viewModel.isFilterReadLater)

        // Select Visited
        viewModel.setFilter(filter = io.github.rumcajs.offlinewebsearch.ui.SearchFilter.Visited)
        assertEquals(io.github.rumcajs.offlinewebsearch.ui.SearchFilter.Visited, viewModel.activeFilter)
        assertTrue(viewModel.isFilterVisited)
        assertFalse(viewModel.isFilterReadLater)

        // Select Read Later -> Visited should be turned off, Read Later active
        viewModel.setFilter(filter = io.github.rumcajs.offlinewebsearch.ui.SearchFilter.ReadLater)
        assertEquals(io.github.rumcajs.offlinewebsearch.ui.SearchFilter.ReadLater, viewModel.activeFilter)
        assertFalse(viewModel.isFilterVisited)
        assertTrue(viewModel.isFilterReadLater)

        // Select Visited again -> Read Later turned off, Visited active
        viewModel.setFilter(filter = io.github.rumcajs.offlinewebsearch.ui.SearchFilter.Visited)
        assertEquals(io.github.rumcajs.offlinewebsearch.ui.SearchFilter.Visited, viewModel.activeFilter)
        assertTrue(viewModel.isFilterVisited)
        assertFalse(viewModel.isFilterReadLater)

        // Toggle Visited off by selecting it again -> resets to None
        viewModel.setFilter(filter = io.github.rumcajs.offlinewebsearch.ui.SearchFilter.Visited)
        assertEquals(io.github.rumcajs.offlinewebsearch.ui.SearchFilter.None, viewModel.activeFilter)
        assertFalse(viewModel.isFilterVisited)
        assertFalse(viewModel.isFilterReadLater)

        // Select ByVotes -> neither visited nor read later, but activeFilter is ByVotes
        viewModel.setFilter(filter = io.github.rumcajs.offlinewebsearch.ui.SearchFilter.ByVotes)
        assertEquals(io.github.rumcajs.offlinewebsearch.ui.SearchFilter.ByVotes, viewModel.activeFilter)
        assertFalse(viewModel.isFilterVisited)
        assertFalse(viewModel.isFilterReadLater)
        assertEquals(io.github.rumcajs.offlinewebsearch.data.OrderBy.PAGE_RATING_VOTES, viewModel.activeFilter.orderByOverride())
    }

    @Test
    fun testSourceFetchOutdated() {
        val repo = io.github.rumcajs.offlinewebsearch.data.SourceOperationalDataRepository

        // Null or blank timestamp is considered outdated
        assertTrue(repo.isFetchOutdated(null))
        assertTrue(repo.isFetchOutdated(""))
        assertTrue(repo.isFetchOutdated("invalid-timestamp"))

        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }

        // 10 seconds ago -> not outdated
        val nowIso = sdf.format(java.util.Date(System.currentTimeMillis() - 10_000L))
        assertFalse(repo.isFetchOutdated(nowIso))

        // 30 minutes ago -> not outdated for 1 hour threshold
        val halfHourAgo = sdf.format(java.util.Date(System.currentTimeMillis() - 30 * 60 * 1000L))
        assertFalse(repo.isFetchOutdated(halfHourAgo))

        // 2 hours ago -> outdated for 1 hour threshold
        val twoHoursAgo = sdf.format(java.util.Date(System.currentTimeMillis() - 2 * 3600 * 1000L))
        assertTrue(repo.isFetchOutdated(twoHoursAgo))
    }
}
