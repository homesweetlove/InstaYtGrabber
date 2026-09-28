package com.homesweetlove.instaytgrabber.feature.instagram

import com.homesweetlove.instaytgrabber.core.GrabKind
import com.homesweetlove.instaytgrabber.core.GrabProgress
import com.homesweetlove.instaytgrabber.core.GrabberEngine
import java.io.File

enum class InstagramMediaType(val label: String, val folder: String) {
    STORY("스토리", "story"),
    HIGHLIGHT("하이라이트", "highlight"),
    POST("게시글", "post"),
    REEL("릴스", "reel")
}

enum class InstagramOutputType(val label: String, val folder: String) {
    MEDIA("원본 미디어", "media"),
    AUDIO("MP3", "audio")
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
        output: InstagramOutputType,
        onProgress: (GrabProgress) -> Unit
    ): Result<File> {
        require(accepts(url)) { "Instagram 링크를 입력해 주세요." }

        onProgress(GrabProgress(percent = 0f, line = "anonymous"))

        val anonymousResult = downloadWithSession(
            url = url,
            type = type,
            output = output,
            cookieHeader = null,
            onProgress = onProgress
        )

        if (anonymousResult.isSuccess) {
            return anonymousResult
        }

        val cookie = InstagramSession.cookie()
        if (cookie.isNullOrBlank()) {
            return anonymousResult
        }

        onProgress(GrabProgress(percent = 0f, line = "authenticated"))

        return downloadWithSession(
            url = url,
            type = type,
            output = output,
            cookieHeader = cookie,
            onProgress = onProgress
        )
    }

    private suspend fun downloadWithSession(
        url: String,
        type: InstagramMediaType,
        output: InstagramOutputType,
        cookieHeader: String?,
        onProgress: (GrabProgress) -> Unit
    ): Result<File> {
        return when (output) {
            InstagramOutputType.MEDIA -> InstagramGalleryEngine.download(
                url = url,
                type = type,
                cookieHeader = cookieHeader,
                onProgress = onProgress
            )

            InstagramOutputType.AUDIO -> GrabberEngine.download(
                url = url.trim(),
                kind = GrabKind.AUDIO,
                folderName = "Instagram/${type.folder}/audio",
                cookieHeader = cookieHeader,
                allowPlaylist = type == InstagramMediaType.HIGHLIGHT,
                onProgress = onProgress
            )
        }
    }
}
