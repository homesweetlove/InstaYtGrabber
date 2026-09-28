package com.homesweetlove.instaytgrabber.feature.youtube

import com.homesweetlove.instaytgrabber.core.GrabKind
import com.homesweetlove.instaytgrabber.core.GrabProgress
import com.homesweetlove.instaytgrabber.core.GrabberEngine
import java.io.File

enum class YouTubeMediaType(val label: String, val folder: String) {
    VIDEO("영상", "video"),
    AUDIO("MP3", "audio"),
    THUMBNAIL("썸네일", "thumbnail")
}

object YouTubeEngine {
    fun accepts(url: String): Boolean =
        runCatching {
            val host = java.net.URI(url.trim()).host.orEmpty().lowercase()
            host == "youtu.be" ||
                host == "youtube.com" ||
                host.endsWith(".youtube.com") ||
                host == "youtube-nocookie.com" ||
                host.endsWith(".youtube-nocookie.com")
        }.getOrDefault(false)

    suspend fun download(
        url: String,
        type: YouTubeMediaType,
        onProgress: (GrabProgress) -> Unit
    ): Result<File> {
        require(accepts(url)) { "YouTube 링크를 입력해 주세요." }
        val kind = when (type) {
            YouTubeMediaType.VIDEO -> GrabKind.VIDEO
            YouTubeMediaType.AUDIO -> GrabKind.AUDIO
            YouTubeMediaType.THUMBNAIL -> GrabKind.THUMBNAIL
        }
        return GrabberEngine.download(
            url = url.trim(),
            kind = kind,
            folderName = "YouTube/${type.folder}",
            allowPlaylist = false,
            onProgress = onProgress
        )
    }
}
