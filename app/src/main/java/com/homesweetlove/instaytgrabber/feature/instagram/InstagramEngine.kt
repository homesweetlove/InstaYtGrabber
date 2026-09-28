package com.homesweetlove.instaytgrabber.feature.instagram

import com.homesweetlove.instaytgrabber.core.GrabKind
import com.homesweetlove.instaytgrabber.core.GrabProgress
import com.homesweetlove.instaytgrabber.core.GrabberEngine
import java.io.File

enum class InstagramMediaType(val label: String, val folder: String) {
    STORY("스토리", "story"),
    HIGHLIGHT("하이라이트", "highlight"),
    POST("게시글 / 릴스", "post")
}

object InstagramEngine {
    fun accepts(url: String): Boolean =
        runCatching {
            val host = java.net.URI(url.trim()).host.orEmpty().lowercase()
            host == "instagram.com" || host.endsWith(".instagram.com")
        }.getOrDefault(false)

    suspend fun download(
        url: String,
        type: InstagramMediaType,
        onProgress: (GrabProgress) -> Unit
    ): Result<File> {
        require(accepts(url)) { "Instagram 링크를 입력해 주세요." }
        return GrabberEngine.download(
            url = url.trim(),
            kind = GrabKind.MEDIA,
            folderName = "Instagram/${type.folder}",
            cookieHeader = InstagramSession.cookie(),
            allowPlaylist = type == InstagramMediaType.HIGHLIGHT,
            onProgress = onProgress
        )
    }
}
