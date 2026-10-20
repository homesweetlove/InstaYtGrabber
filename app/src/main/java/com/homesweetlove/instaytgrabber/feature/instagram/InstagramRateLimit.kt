package com.homesweetlove.instaytgrabber.feature.instagram

import android.content.Context
import android.content.SharedPreferences

/**
 * Client-side safety guard: 429 responses should not cause repeated rapid API
 * calls from one device. Does not bypass any Instagram authentication controls.
 */
object InstagramRateLimit {
    private const val COOLDOWN_MS = 15L * 60L * 1000L
    // Local safety delay only; Instagram controls the real unblock time.
    private const val LOGIN_COOLDOWN_MS = 60L * 60L * 1000L

    @Volatile
    private var instaloaderBlockedUntil: Long = 0L

    @Volatile
    private var loginBlockedUntil: Long = 0L

    @Volatile
    private var preferences: SharedPreferences? = null

    @Synchronized
    fun attach(context: Context) {
        preferences = context.applicationContext.getSharedPreferences(
            "instagram_rate_limits", Context.MODE_PRIVATE
        )
        instaloaderBlockedUntil = maxOf(
            instaloaderBlockedUntil,
            preferences?.getLong("instaloader_blocked_until", 0L) ?: 0L
        )
        loginBlockedUntil = maxOf(
            loginBlockedUntil,
            preferences?.getLong("login_blocked_until", 0L) ?: 0L
        )
    }

    @Synchronized
    fun onFailure(message: String?, now: Long = System.currentTimeMillis()): Boolean {
        val text = message.orEmpty()
        val rateLimited = Regex("""(?i)(?:\b429\b|too many requests|rate limit(?:ed)?)""")
            .containsMatchIn(text)
        if (rateLimited) {
            instaloaderBlockedUntil = maxOf(instaloaderBlockedUntil, now + COOLDOWN_MS)
            preferences?.edit()?.putLong(
                "instaloader_blocked_until", instaloaderBlockedUntil
            )?.apply()
        }
        return rateLimited
    }

    fun shouldSkip(now: Long = System.currentTimeMillis()): Boolean =
        now < instaloaderBlockedUntil

    fun remainingMinutes(now: Long = System.currentTimeMillis()): Long =
        ((instaloaderBlockedUntil - now).coerceAtLeast(0L) + 59_999L) / 60_000L

    /** Use Retry-After when Instagram provides it. Otherwise pause retries for
     * one hour to avoid hammering the login endpoint. This is NOT the server's
     * advertised unblock time and the user may still see 429 afterward. */
    @Synchronized
    fun onLoginHttp429(
        retryAfterSeconds: Long? = null,
        now: Long = System.currentTimeMillis()
    ) {
        val duration = retryAfterSeconds
            ?.takeIf { it > 0L }
            ?.coerceAtMost(24L * 60L * 60L)
            ?.times(1000L)
            ?: LOGIN_COOLDOWN_MS
        loginBlockedUntil = maxOf(loginBlockedUntil, now + duration)
        preferences?.edit()?.putLong("login_blocked_until", loginBlockedUntil)?.apply()
    }

    fun shouldSkipLogin(now: Long = System.currentTimeMillis()): Boolean =
        now < loginBlockedUntil

    fun loginRemainingMinutes(now: Long = System.currentTimeMillis()): Long =
        ((loginBlockedUntil - now).coerceAtLeast(0L) + 59_999L) / 60_000L

    @Synchronized
    fun resetForTests() {
        instaloaderBlockedUntil = 0L
        loginBlockedUntil = 0L
        preferences?.edit()?.remove("instaloader_blocked_until")?.remove("login_blocked_until")?.apply()
    }
}
