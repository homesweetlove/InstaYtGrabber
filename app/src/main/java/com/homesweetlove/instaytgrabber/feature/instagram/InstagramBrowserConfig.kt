package com.homesweetlove.instaytgrabber.feature.instagram

import android.content.Context
import android.webkit.WebSettings

object InstagramBrowserConfig {
    /**
     * Uses the device's installed Chromium version while removing the WebView
     * identification token. Instagram can return an empty login page for the
     * default embedded-WebView user agent.
     */
    fun mobileUserAgent(context: Context): String =
        browserUserAgent(WebSettings.getDefaultUserAgent(context))

    fun browserUserAgent(defaultUserAgent: String): String =
        defaultUserAgent
            .replace("; wv", "")
            .replace(";wv", "")
            .replace("Version/4.0 ", "")
            .replace(" wv)", ")")
}
