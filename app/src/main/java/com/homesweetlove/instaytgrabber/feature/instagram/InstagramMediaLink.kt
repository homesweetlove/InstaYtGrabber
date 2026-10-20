package com.homesweetlove.instaytgrabber.feature.instagram

import java.net.URI

/**
 * Chrome/Instagram share sheet links for individual public posts and reels.
 * This is deliberately separate from profile username lookup.
 */
data class InstagramMediaLink(
    val canonicalUrl: String,
    val type: InstagramMediaType
) {
    companion object {
        private val shortcode = Regex("[A-Za-z0-9_-]{5,64}")

        fun parse(raw: String): InstagramMediaLink? = runCatching {
            val input = raw.trim()
            val uri = URI(input)
            val host = uri.host.orEmpty().lowercase()
            if (uri.scheme?.lowercase() != "https" ||
                host !in setOf("instagram.com", "www.instagram.com", "m.instagram.com")
            ) return null

            val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
            if (segments.size != 2 || !segments[1].matches(shortcode)) return null

            val kind = when (segments[0].lowercase()) {
                "p", "tv" -> InstagramMediaType.POST
                "reel", "reels" -> InstagramMediaType.REEL
                else -> return null
            }
            val path = if (kind == InstagramMediaType.REEL) "reel" else "p"
            InstagramMediaLink(
                canonicalUrl = "https://www.instagram.com/$path/${segments[1]}/",
                type = kind
            )
        }.getOrNull()
    }
}
