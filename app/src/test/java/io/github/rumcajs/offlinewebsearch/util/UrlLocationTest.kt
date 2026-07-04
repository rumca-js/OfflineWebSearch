package io.github.rumcajs.offlinewebsearch.util

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlLocationTest {

    @Test
    fun testGetDomainWithDifferentProtocols() {
        // HTTPS protocol
        assertEquals("google.com", UrlLocation.getDomain("https://google.com"))
        
        // HTTP protocol
        assertEquals("google.com", UrlLocation.getDomain("http://google.com"))
        
        // Protocol-relative
        assertEquals("google.com", UrlLocation.getDomain("//google.com"))
    }

    @Test
    fun testGetDomainWithoutProtocol() {
        // Simple domain
        assertEquals("google.com", UrlLocation.getDomain("google.com"))
        
        // Subdomain without protocol
        assertEquals("www.google.com", UrlLocation.getDomain("www.google.com"))
    }

    @Test
    fun testGetDomainWithPathsAndQueries() {
        // Path and query
        assertEquals("google.com", UrlLocation.getDomain("https://google.com/search?q=test"))
        
        // Path, query, and fragment
        assertEquals("sub.example.co.uk", UrlLocation.getDomain("http://sub.example.co.uk/path/to/resource?query=val#fragment"))
        
        // No protocol with path and query
        assertEquals("google.com", UrlLocation.getDomain("google.com/path?foo=bar"))
    }

    @Test
    fun testGetDomainWithPorts() {
        // With port
        assertEquals("localhost", UrlLocation.getDomain("http://localhost:8080"))
        assertEquals("127.0.0.1", UrlLocation.getDomain("127.0.0.1:9000/path"))
    }

    @Test
    fun testGetDomainEdgeCases() {
        // Null
        assertEquals("", UrlLocation.getDomain(null))
        
        // Empty
        assertEquals("", UrlLocation.getDomain(""))
        
        // Blank
        assertEquals("", UrlLocation.getDomain("   "))
    }

}
