package com.homesweetlove.instaytgrabber.feature.instagram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramMediaUrlPolicyTest {
    @Test
    fun acceptsInstagramSignedCdns() {
        assertTrue(InstagramMediaUrlPolicy.isAllowed(
            "https://scontent.cdninstagram.com/v/t50.1234-16/video.mp4?token=redacted"
        ))
        assertTrue(InstagramMediaUrlPolicy.isAllowed(
            "https://scontent-fra1-1.xx.fbcdn.net/v/t51.2885-15/picture.jpg"
        ))
    }

    @Test
    fun rejectsUnrelatedSitesAndNonHttps() {
        assertFalse(InstagramMediaUrlPolicy.isAllowed(
            "https://cdninstagram.com.attacker.example/steal"
        ))
        assertFalse(InstagramMediaUrlPolicy.isAllowed(
            "http://scontent.cdninstagram.com/video.mp4"
        ))
        assertFalse(InstagramMediaUrlPolicy.isAllowed(
            "https://example.com/file.mp4"
        ))
        assertFalse(InstagramMediaUrlPolicy.isAllowed("javascript:alert(1)"))
    }
}
