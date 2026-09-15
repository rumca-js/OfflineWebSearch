package io.github.rumcajs.offlinewebsearch.util

import io.github.rumcajs.offlinewebsearch.webtoolkit.UrlLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun testIsWebLinkDotScenarios() {
        // No dot in domain -> false
        assertFalse(UrlLocation("http://localhost").isWebLink())
        assertFalse(UrlLocation("https://nodot").isWebLink())

        // One dot in domain -> true
        assertTrue(UrlLocation("http://google.com").isWebLink())
        assertTrue(UrlLocation("https://example.org/path").isWebLink())

        // Two dots in domain -> true
        assertTrue(UrlLocation("http://www.google.com").isWebLink())
        assertTrue(UrlLocation("https://sub.example.co.uk/path?q=1").isWebLink())
    }

    @Test
    fun testGetGoogleRedirectFixWithUrlParam() {
        val googleUrl = "https://www.google.com/url?url=https%3A%2F%2Fexample.com%2Ftarget%3Ffoo%3Dbar&sa=D"
        assertEquals("https://example.com/target?foo=bar", UrlLocation.getGoogleRedirectFix(googleUrl))
        assertEquals("https://example.com/target?foo=bar", UrlLocation(googleUrl).getGoogleRedirectFix())
    }

    @Test
    fun testGetGoogleRedirectFixWithQParam() {
        val googleUrl = "https://www.google.com/url?q=https://example.com/article&usg=AOvVaw0123"
        assertEquals("https://example.com/article", UrlLocation.getGoogleRedirectFix(googleUrl))
        assertEquals("https://example.com/article", UrlLocation(googleUrl).getGoogleRedirectFix())
    }

    @Test
    fun testGetGoogleRedirectFixNonGoogleUrl() {
        val regularUrl = "https://example.com/search?q=something"
        assertEquals(regularUrl, UrlLocation.getGoogleRedirectFix(regularUrl))
        assertEquals(regularUrl, UrlLocation(regularUrl).getGoogleRedirectFix())
    }

    @Test
    fun testGetGoogleRedirectFixCustomDomainLocation() {
        val customGoogleUrl = "https://www.google.com/custom_redirect?q=https%3A%2F%2Fexample.com%2Fdestination"
        assertEquals(
            "https://example.com/destination",
            UrlLocation.getGoogleRedirectFix(customGoogleUrl, domainLocation = "custom_redirect")
        )
    }

    @Test
    fun testGetUrlArgAndCleaned() {
        val testUrl = "https://example.com/path?key1=hello+world&key2=foo%2Fbar#frag"
        assertEquals("hello+world", UrlLocation.getUrlArg(testUrl, "key1"))
        assertEquals("hello world", UrlLocation.getCleanedLink(UrlLocation.getUrlArg(testUrl, "key1")!!))
        assertEquals("foo%2Fbar", UrlLocation.getUrlArg2(testUrl, "key2"))
        assertEquals("foo/bar", UrlLocation.getCleanedLink(UrlLocation.getUrlArg2(testUrl, "key2")!!))
        assertEquals(null, UrlLocation.getUrlArg(testUrl, "nonexistent"))
    }
}
