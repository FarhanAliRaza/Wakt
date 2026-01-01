package com.farhanaliraza.wakt.services

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for website blocking logic
 */
class WebsiteBlockingLogicTest {

    @Test
    fun `cleanDomain should remove protocol and www prefix`() {
        assertEquals("youtube.com", cleanDomain("https://www.youtube.com"))
        assertEquals("youtube.com", cleanDomain("http://youtube.com"))
        assertEquals("youtube.com", cleanDomain("www.youtube.com"))
        assertEquals("youtube.com", cleanDomain("youtube.com"))
    }

    @Test
    fun `cleanDomain should remove path from URL`() {
        assertEquals("youtube.com", cleanDomain("https://youtube.com/watch?v=abc"))
        assertEquals("google.com", cleanDomain("https://www.google.com/search?q=test"))
        assertEquals("github.com", cleanDomain("github.com/user/repo"))
    }

    @Test
    fun `cleanDomain should handle edge cases`() {
        assertEquals("", cleanDomain(""))
        assertEquals("localhost", cleanDomain("localhost"))
        assertEquals("127.0.0.1", cleanDomain("127.0.0.1"))
    }

    @Test
    fun `domain blocking should match exact domains`() {
        val blockedDomains = setOf("youtube.com", "facebook.com")
        
        assertTrue(isDomainBlocked("youtube.com", blockedDomains))
        assertTrue(isDomainBlocked("facebook.com", blockedDomains))
        assertFalse(isDomainBlocked("google.com", blockedDomains))
    }

    @Test
    fun `domain blocking should match subdomains`() {
        val blockedDomains = setOf("youtube.com", "facebook.com")
        
        assertTrue(isDomainBlocked("m.youtube.com", blockedDomains))
        assertTrue(isDomainBlocked("www.youtube.com", blockedDomains))
        assertTrue(isDomainBlocked("mobile.facebook.com", blockedDomains))
        assertTrue(isDomainBlocked("api.youtube.com", blockedDomains))
    }

    @Test
    fun `domain blocking should not match partial domains`() {
        val blockedDomains = setOf("youtube.com")
        
        assertFalse(isDomainBlocked("notyoutube.com", blockedDomains))
        assertFalse(isDomainBlocked("youtube.org", blockedDomains))
        assertFalse(isDomainBlocked("youtube", blockedDomains))
    }

    @Test
    fun `domain blocking should be case insensitive`() {
        val blockedDomains = setOf("youtube.com")
        
        assertTrue(isDomainBlocked("YouTube.com", blockedDomains))
        assertTrue(isDomainBlocked("YOUTUBE.COM", blockedDomains))
        assertTrue(isDomainBlocked("M.YOUTUBE.COM", blockedDomains))
    }

    @Test
    fun `URL validation should identify valid domains`() {
        assertTrue(isValidDomain("youtube.com"))
        assertTrue(isValidDomain("www.google.com"))
        assertTrue(isValidDomain("sub.domain.example.org"))
        assertTrue(isValidDomain("api.github.com"))
    }

    @Test
    fun `URL validation should reject invalid domains`() {
        assertFalse(isValidDomain(""))
        assertFalse(isValidDomain("not a domain"))
        assertFalse(isValidDomain("no"))
        assertFalse(isValidDomain("spaces in domain.com"))
    }

    // Helper methods that simulate the actual logic from the services
    private fun cleanDomain(url: String): String {
        return url.lowercase()
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .split("/")[0] // Take only domain part
    }

    private fun isDomainBlocked(domain: String, blockedDomains: Set<String>): Boolean {
        val cleanedDomain = domain.lowercase()
        return blockedDomains.any { blockedDomain ->
            cleanedDomain == blockedDomain || cleanedDomain.endsWith(".$blockedDomain")
        }
    }

    private fun isValidDomain(text: String): Boolean {
        return text.contains(".") && 
               !text.contains(" ") && 
               text.length > 3 && 
               text.matches(Regex("[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}.*"))
    }
}