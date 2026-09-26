package io.github.rumcajs.offlinewebsearch.ui.screens

import io.github.rumcajs.offlinewebsearch.data.repositories.SourceRepository
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseScreenTest {

    @Test
    fun `getRepositoriesToClear includes SourceRepository`() {
        val reposToClear = getRepositoriesToClear()
        val sourceRepoItem = reposToClear.find { it.repository == SourceRepository }

        assertNotNull("getRepositoriesToClear should contain SourceRepository", sourceRepoItem)
        assertTrue(
            "Source repository label should be valid",
            sourceRepoItem!!.label.isNotBlank()
        )
    }
}
