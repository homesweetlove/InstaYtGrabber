package com.homesweetlove.instaytgrabber.feature.instagram

import java.net.URI

/**
 * The media extractor only accepts HTTPS links hosted by Instagram's media
 * delivery network. Prevents arbitrary web-page content from being passed
 * into the app's unrestricted downloader.
 */
object InstagramMediaUrlPolicy {
    fun isAllowed(url: String): Boolean = runCatching {
        val uri = URI(url.trim())
        val host = uri.host.orEmpty().lowercase()
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.userInfo == null &&
            (host == "cdninstagram.com" ||
                host.endsWith(".cdninstagram.com") ||
                host == "fbcdn.net" ||
                host.endsWith(".fbcdn.net") ||
                host == "instagram.com" ||
                host.endsWith(".instagram.com"))
    }.getOrDefault(false)
}
