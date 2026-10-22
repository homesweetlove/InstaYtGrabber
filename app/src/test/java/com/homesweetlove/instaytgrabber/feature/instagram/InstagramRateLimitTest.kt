package com.homesweetlove.instaytgrabber.feature.instagram

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramRateLimitTest {
    private val baseTime = 1_700_000_000_000L

    @After
    fun cleanup() = InstagramRateLimit.resetForTests()

    @Test
    fun blocksRepeatedRequestsFor15MinutesAfter429() {
        assertFalse(InstagramRateLimit.shouldSkip(baseTime))
        assertTrue(InstagramRateLimit.onFailure(
            "ConnectionException: 429 Too Many Requests", baseTime
        ))
        assertTrue(InstagramRateLimit.shouldSkip(baseTime + 120_000L))
        assertEquals(15L, InstagramRateLimit.remainingMinutes(baseTime + 1L))
        assertFalse(InstagramRateLimit.shouldSkip(baseTime + 900_000L))
    }

    @Test
    fun ordinaryUnauthorizedErrorDoesNotBlockFurtherRequests() {
        assertFalse(InstagramRateLimit.onFailure("HTTP 401 Unauthorized", baseTime))
        assertFalse(InstagramRateLimit.shouldSkip(baseTime + 1000L))
    }

    @Test
    fun loginHttp429BlocksLoginAndAnonymousApiRetries() {
        InstagramRateLimit.onLoginHttp429(now = baseTime)
        assertTrue(InstagramRateLimit.shouldSkipLogin(baseTime + 5_000L))
        assertTrue(InstagramRateLimit.shouldSkip(baseTime + 5_000L))
        assertEquals(60L, InstagramRateLimit.loginRemainingMinutes(baseTime))
        assertFalse(InstagramRateLimit.shouldSkipLogin(baseTime + 60L * 60L * 1000L))
    }

    @Test
    fun honorsNumericRetryAfterWithoutGuessingServerExpiry() {
        InstagramRateLimit.onLoginHttp429(retryAfterSeconds = 120L, now = baseTime)
        assertTrue(InstagramRateLimit.shouldSkipLogin(baseTime + 119_000L))
        assertFalse(InstagramRateLimit.shouldSkipLogin(baseTime + 120_000L))
    }

    @Test
    fun recognizesRateLimitMessageWithoutStatusCode() {
        assertTrue(InstagramRateLimit.onFailure(
            "rate limited by remote service", baseTime
        ))
        assertTrue(InstagramRateLimit.shouldSkip(baseTime + 1000L))
    }
}
