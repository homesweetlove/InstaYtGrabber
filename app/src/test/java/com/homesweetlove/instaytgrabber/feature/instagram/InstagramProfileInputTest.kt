package com.homesweetlove.instaytgrabber.feature.instagram

import org.junit.Assert.assertEquals
import org.junit.Test

class InstagramProfileInputTest {
    @Test
    fun acceptsBareUsername() {
        assertEquals(
            "https://www.instagram.com/example_user.2/",
            InstagramProfileInput.normalize("example_user.2")
        )
    }

    @Test
    fun acceptsAtUsername() {
        assertEquals(
            "https://www.instagram.com/example_user/",
            InstagramProfileInput.normalize("  @example_user  ")
        )
    }

    @Test
    fun acceptsPublicProfileLinksWithTrackingParams() {
        assertEquals(
            "https://www.instagram.com/example/",
            InstagramProfileInput.normalize("instagram.com/example/?utm_source=share")
        )
        assertEquals(
            "https://www.instagram.com/example/",
            InstagramProfileInput.normalize("https://m.instagram.com/example/")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOtherWebsites() {
        InstagramProfileInput.normalize("https://evil.test/example")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPostLink() {
        InstagramProfileInput.normalize("https://instagram.com/p/shortcode/")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidUsername() {
        InstagramProfileInput.normalize("@not valid")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsReservedUsername() {
        InstagramProfileInput.normalize("stories")
    }
}
