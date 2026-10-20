package com.homesweetlove.instaytgrabber.feature.instagram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstagramMediaLinkTest {
    @Test
    fun parsesPublicPostSharedFromChrome() {
        val result = InstagramMediaLink.parse(
            "https://www.instagram.com/p/AbCd1234/?igsh=foobar"
        )!!
        assertEquals("https://www.instagram.com/p/AbCd1234/", result.canonicalUrl)
        assertEquals(InstagramMediaType.POST, result.type)
    }

    @Test
    fun parsesReelFromInstagramShareSheet() {
        val result = InstagramMediaLink.parse(
            "https://instagram.com/reels/AbCd_1234/"
        )!!
        assertEquals("https://www.instagram.com/reel/AbCd_1234/", result.canonicalUrl)
        assertEquals(InstagramMediaType.REEL, result.type)
    }

    @Test
    fun doesNotMistakeProfilesForMedia() {
        assertNull(InstagramMediaLink.parse("https://www.instagram.com/example/"))
    }

    @Test
    fun rejectsUntrustedHostsAndStoryLinks() {
        assertNull(InstagramMediaLink.parse("https://instagram.com.evil.example/p/AbCd1234/"))
        assertNull(InstagramMediaLink.parse("https://www.instagram.com/stories/example/123456789/"))
        assertNull(InstagramMediaLink.parse("javascript:alert(1)"))
    }
}
