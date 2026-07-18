package io.github.rumcajs.offlinewebsearch.util

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlServicesTest {

    @Test
    fun testUrlServices() {
        val urlServices = UrlServices()
        val links = urlServices.getServiceLinks("https://google.com")
        
        assertEquals(2, links.size)
        
        // Verify Web Archive
        assertEquals("Web Archive", links[0].first)
        assertEquals("https://web.archive.org/web/https%3A%2F%2Fgoogle.com", links[0].second)
        
        // Verify Is It Down Right Now
        assertEquals("Is It Down Right Now", links[1].first)
        assertEquals("https://www.isitdownrightnow.com/google.com.html", links[1].second)
    }
}
