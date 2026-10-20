package com.homesweetlove.instaytgrabber.feature.instagram

/**
 * Client-side safety guard: 429 responses should not cause repeated rapid API
 * calls from one device. Does not bypass any Instagram authentication controls.
 */
object InstagramRateLimit {
    private const val COOLDOWN_MS = 15L * 60L * 1000L

    @Volatile
    private var instaloaderBlockedUntil: Long = 0L

    @Synchronized
    fun onFailure(message: String?, now: Long = System.currentTimeMillis()): Boolean {
        val text = message.orEmpty()
        val rateLimited = Regex("""(?i)(?:\\b429\\b|too many requests|rate limit(?:ed)?)""")
            .containsMatchIn(text)
        if (rateLimited) {
            instaloaderBlockedUntil = maxOf(instaloaderBlockedUntil, now + COOLDOWN_MS)
        }
        return rateLimited
    }

    fun shouldSkip(now: Long = System.currentTimeMillis()): Boolean =
        now < instaloaderBlockedUntil

    fun remainingMinutes(now: Long = System.currentTimeMillis()): Long =
        ((instaloaderBlockedUntil - now).coerceAtLeast(0L) + 59_999L) / 60_000L

    @Synchronized
    fun resetForTests() {
        instaloaderBlockedUntil = 0L
    }
}
