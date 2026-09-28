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
        cookieHeader = cookies.ifBlank { null }
        return cookieHeader != null
    }

    fun cookie(): String? = cookieHeader

    fun isLoggedIn(): Boolean = !cookieHeader.isNullOrBlank()

    fun clear() {
        cookieHeader = null
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }
}
