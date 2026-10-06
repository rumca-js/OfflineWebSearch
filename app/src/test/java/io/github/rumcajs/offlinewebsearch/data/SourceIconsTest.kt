package io.github.rumcajs.offlinewebsearch.data

import io.github.rumcajs.offlinewebsearch.data.repositories.SourceIcons
import io.github.rumcajs.offlinewebsearch.ui.components.sourceIconImageVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SourceIconsTest {

    @Test
    fun `all icons have non-blank labels and values`() {
        assertTrue(SourceIcons.all.isNotEmpty())
        for (icon in SourceIcons.all) {
            assertTrue("Label should not be blank for ${icon.value}", icon.label.isNotBlank())
            assertTrue("Value should not be blank for ${icon.label}", icon.value.isNotBlank())
        }
    }

    @Test
    fun `all icon values are unique`() {
        val values = SourceIcons.all.map { it.value }
        assertEquals("Duplicate icon values found", values.size, values.toSet().size)
    }

    @Test
    fun `every icon value maps to a non-null ImageVector`() {
        for (icon in SourceIcons.all) {
            val vector = sourceIconImageVector(icon.value)
            assertNotNull("sourceIconImageVector should resolve '${icon.value}'", vector)
        }
    }

    @Test
    fun `unknown icon name maps to null ImageVector`() {
        val vector = sourceIconImageVector("non_existent_icon_name_12345")
        assertEquals(null, vector)
    }
}
