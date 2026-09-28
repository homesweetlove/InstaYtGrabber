package com.homesweetlove.instaytgrabber.feature.youtube

import com.homesweetlove.instaytgrabber.core.GrabKind
import com.homesweetlove.instaytgrabber.core.GrabProgress
import com.homesweetlove.instaytgrabber.core.GrabberEngine
import com.homesweetlove.instaytgrabber.core.YtDlpRuntime
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
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

            val version = YtDlpRuntime.ensureReady(onStatus)
            onStatus("yt-dlp " + version + " · 영상 정보 확인 중")

            val first = inspectWithFallbacks(url.trim())
            if (first.isSuccess) {
                return@runCatching first.getOrThrow()
            }

            onStatus("조회 실패 · yt-dlp 업데이트 후 재시도")
            YtDlpRuntime.refreshEngine(onStatus)

            val second = inspectWithFallbacks(url.trim())
            if (second.isSuccess) {
                return@runCatching second.getOrThrow()
            }

            onStatus("yt-dlp 조회 실패 · InnerTube 보조 조회 중")
            val native = YouTubeNativeLookup.inspect(url.trim())
            if (native.isSuccess) {
                return@runCatching native.getOrThrow()
            }

            throw IllegalStateException(
                "YouTube 링크 조회 실패 · yt-dlp: " +
                    (second.exceptionOrNull()?.message ?: first.exceptionOrNull()?.message ?: "unknown") +
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

    private fun inspectWithFallbacks(url: String): Result<YouTubeMediaInfo> {
        val attempts = listOf(
            InspectAttempt(
                label = "default",
                extractorArgs = null,
                remoteEjs = true
            ),
            InspectAttempt(
                label = "multi-client",
                extractorArgs = "youtube:player_client=android,web,web_safari,mweb,ios",
                remoteEjs = true
            ),
            InspectAttempt(
                label = "android-client",
                extractorArgs = "youtube:player_client=android",
                remoteEjs = false
            )
        )

        val errors = mutableListOf<String>()

        for (attempt in attempts) {
            val result = runCatching {
                val request = YoutubeDLRequest(url)
                    .addOption("--ignore-config")
                    .addOption("--no-cache-dir")
                    .addOption("--dump-single-json")
                    .addOption("--skip-download")
                    .addOption("--no-playlist")
                    .addOption("--no-warnings")
                    .addOption("--socket-timeout", "20")
                    .addOption("--retries", "2")
                    .addOption("--extractor-retries", "2")
                    .addOption("--fragment-retries", "2")
                    .addOption("--compat-options", "no-youtube-unavailable-videos")

                if (attempt.remoteEjs) {
                    request.addOption("--remote-components", "ejs:github")
                }

                attempt.extractorArgs?.let {
                    request.addOption("--extractor-args", it)
                }

                val response = YoutubeDL.getInstance().execute(
                    request,
                    "yt-inspect-" + System.nanoTime()
                )

                parseInfo(response.out)
            }

            if (result.isSuccess) {
                return result
            }

            errors += attempt.label + ": " +
                (result.exceptionOrNull()?.message ?: "unknown")
        }

        return Result.failure(
            IllegalStateException(
                "YouTube 링크 조회 실패 · " + errors.joinToString(" / ")
            )
        )
    }

    private fun parseInfo(raw: String): YouTubeMediaInfo {
        val root = JSONObject(raw.trim())

        if (root.optBoolean("is_live", false) ||
            root.optString("live_status") in setOf("is_live", "is_upcoming")
        ) {
            error("라이브/예정 방송은 현재 지원하지 않습니다.")
        }

        val formats = root.optJSONArray("formats")
        val formatCount = formats?.length() ?: 0

        if (formatCount == 0 &&
            root.optString("url").isBlank()
        ) {
            error("재생 가능한 YouTube 포맷을 찾지 못했습니다.")
        }

        return YouTubeMediaInfo(
            id = root.optString("id"),
            title = root.optString("title").ifBlank { "제목 없음" },
            channel = root.optString("uploader")
                .ifBlank { root.optString("channel") },
            thumbnailUrl = root.optString("thumbnail"),
            durationSeconds = root.optDouble("duration", 0.0).toInt(),
            extractor = root.optString("extractor_key")
                .ifBlank { root.optString("extractor") },
            formatCount = formatCount
        )
    }

    private data class InspectAttempt(
        val label: String,
        val extractorArgs: String?,
        val remoteEjs: Boolean
    )
}
