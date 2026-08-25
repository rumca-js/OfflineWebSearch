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
    fun testFilterChipsMutualExclusivity() {
        val viewModel = SearchViewModel()

        // Initial state: both false
        assertFalse(viewModel.isFilterVisited)
        assertFalse(viewModel.isFilterReadLater)

        // Toggle Visited on
        viewModel.toggleVisitedFilter()
        assertTrue(viewModel.isFilterVisited)
        assertFalse(viewModel.isFilterReadLater)

        // Toggle Read Later on -> Visited should be turned off
        viewModel.toggleReadLaterFilter()
        assertFalse(viewModel.isFilterVisited)
        assertTrue(viewModel.isFilterReadLater)

        // Toggle Visited on -> Read Later should be turned off
        viewModel.toggleVisitedFilter()
        assertTrue(viewModel.isFilterVisited)
        assertFalse(viewModel.isFilterReadLater)

        // Toggle Visited off -> both false
        viewModel.toggleVisitedFilter()
        assertFalse(viewModel.isFilterVisited)
        assertFalse(viewModel.isFilterReadLater)

        // Toggle Read Later on -> Read Later true, Visited false
        viewModel.toggleReadLaterFilter()
        assertFalse(viewModel.isFilterVisited)
        assertTrue(viewModel.isFilterReadLater)

        // Toggle Read Later off -> both false
        viewModel.toggleReadLaterFilter()
        assertFalse(viewModel.isFilterVisited)
        assertFalse(viewModel.isFilterReadLater)
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
