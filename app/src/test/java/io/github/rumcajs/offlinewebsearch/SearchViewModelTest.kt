package io.github.rumcajs.offlinewebsearch

import io.github.rumcajs.offlinewebsearch.ui.SearchViewModel
import io.github.rumcajs.offlinewebsearch.data.Entry
import org.junit.Assert.*
import org.junit.Test
import kotlin.collections.get

class SearchViewModelTest {

    @Test
    fun testFilteredData() {
        val viewModel = _root_ide_package_.io.github.rumcajs.offlinewebsearch.ui.SearchViewModel()
        val places = listOf(
            _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.Entry(
                title = "Test 1",
                description = "Desc 1",
                link = "http://test1.com",
                tags = listOf("tag1")
            ),
            _root_ide_package_.io.github.rumcajs.offlinewebsearch.data.Entry(
                title = "Other",
                description = "Other desc",
                link = "http://other.com",
                tags = listOf("tag2")
            )
        )
        viewModel.allEntries = places

        // Initial state (empty query)
        assertTrue(viewModel.filteredData.size == 2)
        assertEquals("Test 1", viewModel.filteredData[0].title)

        // Search for "Te" (short query)
        viewModel.searchQuery = "Te"
        viewModel.performSearch()
        assertTrue(viewModel.filteredData.size == 1)
        assertEquals("Test 1", viewModel.filteredData[0].title)

        // Search for "x" (single character)
        viewModel.searchQuery = "x"
        viewModel.performSearch()
        assertTrue(viewModel.filteredData.isEmpty())

        // Search for empty string again
        viewModel.searchQuery = ""
        viewModel.performSearch()
        assertTrue(viewModel.filteredData.size == 2)
    }

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
}
