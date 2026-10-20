package com.homesweetlove.instaytgrabber.feature.instagram

import java.net.URI

/**
 * Accept a bare Instagram username, @username, or profile URL.
 * Always returns a canonical HTTPS profile URL and never a URL on another host.
 */
object InstagramProfileInput {
    private val usernamePattern = Regex("[A-Za-z0-9._]{1,30}")
    private val reserved = setOf(
        "accounts", "about", "api", "challenge", "developer", "directory", "direct",
        "explore", "legal", "p", "reel", "reels", "stories", "tv"
    )

    fun normalize(value: String): String {
        val input = value.trim()
        require(input.isNotEmpty()) { "Instagram 아이디 또는 프로필 링크를 입력해 주세요." }

        val username = if ("://" in input || input.startsWith("instagram.com/", true) ||
            input.startsWith("www.instagram.com/", true) ||
            input.startsWith("m.instagram.com/", true)
        ) {
            val withScheme = if ("://" in input) input else "https://$input"
            val uri = try {
                URI(withScheme)
            } catch (_: Exception) {
                throw IllegalArgumentException("Instagram 프로필 주소 형식이 올바르지 않습니다.")
            }
            val host = uri.host.orEmpty().lowercase()
            require(host == "instagram.com" || host == "www.instagram.com" || host == "m.instagram.com") {
                "Instagram 프로필 주소만 입력할 수 있습니다."
            }
            require(uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) {
                "Instagram 주소는 HTTP 또는 HTTPS만 지원합니다."
            }
            val segments = uri.path.orEmpty().trim('/').split('/').filter { it.isNotBlank() }
            require(segments.size == 1) {
                "게시글·릴스가 아닌 프로필 주소 또는 아이디를 입력해 주세요."
            }
            segments.first()
        } else {
            input.removePrefix("@").trimEnd('/')
        }

        require(username.matches(usernamePattern) && username.lowercase() !in reserved) {
            "Instagram 아이디가 올바르지 않습니다. 영문·숫자·마침표·밑줄만 사용할 수 있어요."
        }

        return "https://www.instagram.com/$username/"
    }
}
