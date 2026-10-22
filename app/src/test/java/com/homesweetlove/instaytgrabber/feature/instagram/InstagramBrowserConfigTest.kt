package com.homesweetlove.instaytgrabber.feature.instagram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramBrowserConfigTest {
    @Test
    fun removesEmbeddedWebViewMarkerWithoutLosingChromeVersion() {
        val original = "Mozilla/5.0 (Linux; Android 16; SM-S928N; wv) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 " +
            "Chrome/139.0.0.0 Mobile Safari/537.36"
        val actual = InstagramBrowserConfig.browserUserAgent(original)
        assertFalse(actual.contains("; wv"))
        assertFalse(actual.contains("Version/4.0"))
        assertTrue(actual.contains("Chrome/139.0.0.0"))
        assertTrue(actual.contains("Mobile Safari"))
    }

    @Test
    fun preservesAlreadyValidChromeUserAgent() {
        val original = "Mozilla/5.0 (Linux; Android 16) Chrome/140.0 Mobile Safari/537.36"
        assertTrue(InstagramBrowserConfig.browserUserAgent(original) == original)
    }
}
