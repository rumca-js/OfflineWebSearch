package io.github.rumcajs.offlinewebsearch.util

import io.github.rumcajs.offlinewebsearch.webtoolkit.UrlLocation
import org.junit.Assert.assertEquals
import org.junit.Test

class UrlLocationTest {

    @Test
    fun testGetDomainWithDifferentProtocols() {
        // HTTPS protocol
        assertEquals("google.com", UrlLocation("https://google.com").getDomain())
        
        // HTTP protocol
        assertEquals("google.com", UrlLocation("http://google.com").getDomain())
        
        // Protocol-relative
        assertEquals("google.com", UrlLocation("//google.com").getDomain())
    }

    @Test
    fun testGetDomainWithoutProtocol() {
        // Simple domain
        assertEquals("google.com", UrlLocation("google.com").getDomain())
        
        // Subdomain without protocol
        assertEquals("www.google.com", UrlLocation("www.google.com").getDomain())
    }

    @Test
    fun testGetDomainWithPathsAndQueries() {
        // Path and query
        assertEquals("google.com", UrlLocation("https://google.com/search?q=test").getDomain())
        
        // Path, query, and fragment
        assertEquals("sub.example.co.uk", UrlLocation("http://sub.example.co.uk/path/to/resource?query=val#fragment").getDomain())
        
        // No protocol with path and query
        assertEquals("google.com", UrlLocation("google.com/path?foo=bar").getDomain())
    }

    @Test
    fun testGetDomainWithPorts() {
        // With port
        assertEquals("localhost", UrlLocation("http://localhost:8080").getDomain())
        assertEquals("127.0.0.1", UrlLocation("127.0.0.1:9000/path").getDomain())
    }

    @Test
    fun testGetDomainEdgeCases() {
        // Null
        assertEquals("", UrlLocation(null).getDomain())
        
        // Empty
        assertEquals("", UrlLocation("").getDomain())
        
        // Blank
        assertEquals("", UrlLocation("   ").getDomain())
    }

    @Test
    fun testGetProtocolles() {
        assertEquals("google.com", UrlLocation("http://google.com").getProtocolles())
        assertEquals("google.com", UrlLocation("https://google.com").getProtocolles())
        assertEquals("ftp.example.com", UrlLocation("ftp://ftp.example.com").getProtocolles())
        assertEquals("google.com/search?q=test", UrlLocation("HTTPS://google.com/search?q=test").getProtocolles())
        assertEquals("google.com", UrlLocation("google.com").getProtocolles())
        assertEquals("", UrlLocation(null).getProtocolles())
        assertEquals("", UrlLocation("").getProtocolles())
        assertEquals("", UrlLocation("   ").getProtocolles())
    }
}
