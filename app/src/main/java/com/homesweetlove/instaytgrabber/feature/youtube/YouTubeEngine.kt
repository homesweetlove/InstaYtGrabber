package com.homesweetlove.instaytgrabber.feature.youtube

import com.homesweetlove.instaytgrabber.core.GrabKind
import com.homesweetlove.instaytgrabber.core.GrabProgress
import com.homesweetlove.instaytgrabber.core.GrabberEngine
import com.homesweetlove.instaytgrabber.core.YtDlpRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

enum class YouTubeMediaType(val label: String, val folder: String) {
    VIDEO("영상", "video"),
    AUDIO("MP3", "audio"),
    THUMBNAIL("썸네일", "thumbnail")
}

data class YouTubeMediaInfo(
    val id: String,
    val title: String,
    val channel: String,
    val thumbnailUrl: String,
    val durationSeconds: Int,
    val extractor: String,
    val formatCount: Int
)

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

    suspend fun inspect(
        url: String,
        onStatus: (String) -> Unit = {}
    ): Result<YouTubeMediaInfo> = withContext(Dispatchers.IO) {
        runCatching {
            require(accepts(url)) { "YouTube 링크를 입력해 주세요." }

            val chaquopy = YtDlpRuntime.inspect(
                url = url.trim(),
                onStatus = onStatus
            )

            if (chaquopy.isSuccess) {
                val parsed = parseChaquopyInfo(chaquopy.getOrThrow())
                if (parsed.isSuccess) {
                    return@runCatching parsed.getOrThrow()
                }

                onStatus("yt-dlp 조회 실패 · InnerTube 보조 조회 중")
                val native = YouTubeNativeLookup.inspect(url.trim())
                if (native.isSuccess) {
                    return@runCatching native.getOrThrow()
                }

                throw IllegalStateException(
                    "YouTube 링크 조회 실패 · Chaquopy yt-dlp: " +
                        (parsed.exceptionOrNull()?.message ?: "unknown") +
                        " / InnerTube: " +
                        (native.exceptionOrNull()?.message ?: "unknown")
                )
            }

            onStatus("yt-dlp 실행 실패 · InnerTube 보조 조회 중")
            val native = YouTubeNativeLookup.inspect(url.trim())
            if (native.isSuccess) {
                return@runCatching native.getOrThrow()
            }

            throw IllegalStateException(
                "YouTube 링크 조회 실패 · Chaquopy runtime: " +
                    (chaquopy.exceptionOrNull()?.message ?: "unknown") +
                    " / InnerTube: " +
                    (native.exceptionOrNull()?.message ?: "unknown")
            )
        }
    }

    suspend fun download(
        url: String,
        type: YouTubeMediaType,
        onProgress: (GrabProgress) -> Unit
    ): Result<File> {
        require(accepts(url)) { "YouTube 링크를 입력해 주세요." }

        if (type == YouTubeMediaType.THUMBNAIL) {
            return YouTubeThumbnailEngine.download(url.trim(), onProgress)
        }

        val inspected = inspect(url) { status ->
            onProgress(GrabProgress(percent = 0f, line = status))
        }

        if (inspected.isFailure) {
            return Result.failure(
                inspected.exceptionOrNull()
                    ?: IllegalStateException("YouTube 링크를 조회하지 못했습니다.")
            )
        }

        val kind = when (type) {
            YouTubeMediaType.VIDEO -> GrabKind.VIDEO
            YouTubeMediaType.AUDIO -> GrabKind.AUDIO
            YouTubeMediaType.THUMBNAIL -> GrabKind.THUMBNAIL
        }

        return GrabberEngine.download(
            url = url.trim(),
            kind = kind,
            folderName = "YouTube/" + type.folder,
            allowPlaylist = false,
            onProgress = onProgress
        )
    }

    private fun parseChaquopyInfo(raw: String): Result<YouTubeMediaInfo> =
        runCatching {
            val root = JSONObject(raw)

            if (!root.optBoolean("ok", false)) {
                error(
                    root.optString("error")
                        .ifBlank { "yt-dlp가 영상 정보를 반환하지 않았습니다." }
                )
            }

            if (root.optBoolean("is_live", false) ||
                root.optString("live_status") in setOf("is_live", "is_upcoming")
            ) {
                error("라이브/예정 방송은 현재 지원하지 않습니다.")
            }

            val formatCount = root.optInt("format_count", 0)
            if (formatCount <= 0) {
                error("재생 가능한 YouTube 포맷을 찾지 못했습니다.")
            }

            YouTubeMediaInfo(
                id = root.optString("id"),
                title = root.optString("title").ifBlank { "제목 없음" },
                channel = root.optString("channel"),
                thumbnailUrl = root.optString("thumbnail"),
                durationSeconds = root.optInt("duration", 0),
                extractor = "Chaquopy/" +
                    root.optString("extractor").ifBlank { "yt-dlp" },
                formatCount = formatCount
            )
        }
}
