package com.homesweetlove.instaytgrabber.feature.instagram

import android.webkit.CookieManager

object InstagramSession {
    @Volatile
    private var cookieHeader: String? = null

    fun captureFromWebView(): Boolean {
        val cookies = CookieManager.getInstance()
            .getCookie("https://www.instagram.com")
            ?.trim()
            .orEmpty()
        // Guest cookies do not mean the user successfully logged in.
        val hasSession = cookies.split(";").any { part ->
            part.trim().startsWith("sessionid=") &&
                part.substringAfter("=", "").isNotBlank()
        }
        cookieHeader = cookies.takeIf { hasSession }
        return hasSession
    }

    fun cookie(): String? = cookieHeader

    fun isLoggedIn(): Boolean = !cookieHeader.isNullOrBlank()

    fun clear() {
        cookieHeader = null
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }
}
