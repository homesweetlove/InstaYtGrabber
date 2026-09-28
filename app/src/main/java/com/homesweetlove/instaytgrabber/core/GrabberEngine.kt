package com.homesweetlove.instaytgrabber.core

import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class GrabKind { VIDEO, AUDIO, MEDIA, THUMBNAIL }

data class GrabProgress(
    val percent: Float = 0f,
    val etaSeconds: Long = 0,
    val line: String = ""
)

object GrabberEngine {
    suspend fun download(
        url: String,
        kind: GrabKind,
        folderName: String,
        cookieHeader: String? = null,
        allowPlaylist: Boolean = false,
        onProgress: (GrabProgress) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val engineVersion = YtDlpRuntime.ensureReady { state ->
                onProgress(GrabProgress(percent = 0f, line = state))
            }
            onProgress(GrabProgress(percent = 0f, line = "yt-dlp " + engineVersion))

            val root = StoragePublisher.tempDirectory(folderName)

            val request = YoutubeDLRequest(url)
            val host = runCatching {
                java.net.URI(url).host.orEmpty().lowercase()
            }.getOrDefault("")
            val isYouTube =
                host == "youtu.be" ||
                    host == "youtube.com" ||
                    host.endsWith(".youtube.com") ||
                    host == "youtube-nocookie.com" ||
                    host.endsWith(".youtube-nocookie.com")

            request.addOption("--ignore-config")
            request.addOption("--no-cache-dir")
            request.addOption("--newline")
            request.addOption("--no-mtime")
            request.addOption("--restrict-filenames")
            request.addOption("--socket-timeout", "20")
            request.addOption("--retries", "2")
            request.addOption("--extractor-retries", "2")
            request.addOption("--fragment-retries", "2")
            request.addOption("--remote-components", "ejs:github")
            if (isYouTube) {
                request.addOption(
                    "--extractor-args",
                    "youtube:player_client=android,web,web_safari,mweb,ios"
                )
                request.addOption(
                    "--compat-options",
                    "no-youtube-unavailable-videos"
                )
            }
            if (!allowPlaylist) request.addOption("--no-playlist")
            cookieHeader?.takeIf { it.isNotBlank() }?.let {
                request.addOption("--add-header", "Cookie:$it")
            }

            when (kind) {
                GrabKind.AUDIO -> {
                    request.addOption("-f", "bestaudio/best")
                    request.addOption("-x")
                    request.addOption("--audio-format", "mp3")
                    request.addOption("--audio-quality", "0")
                }

                GrabKind.VIDEO -> {
                    request.addOption("-f", "bv*+ba/b")
                    request.addOption("-S", "vcodec:h264,acodec:aac")
                    request.addOption("--merge-output-format", "mp4")
                    request.addOption("--recode-video", "mp4")
                }

                GrabKind.MEDIA -> request.addOption("-f", "best")

                GrabKind.THUMBNAIL -> {
                    request.addOption("--skip-download")
                    request.addOption("--write-thumbnail")
                    request.addOption("--convert-thumbnails", "jpg")
                }
            }

            val template = File(root, "%(title).180B [%(id)s].%(ext)s").absolutePath
            request.addOption("-o", template)

            YoutubeDL.getInstance().execute(request) { progress, eta, line ->
                onProgress(GrabProgress(progress, eta, line))
            }

            val completed = root.walkTopDown()
                .filter { file ->
                    file.isFile &&
                        file.length() > 0L &&
                        file.extension.lowercase() !in setOf("part", "ytdl", "json")
                }
                .toList()

            val published = StoragePublisher.publishFiles(completed, folderName)
            root.deleteRecursively()
            published.firstOrNull()
                ?: error("다운로드된 파일을 저장하지 못했습니다.")
        }
    }
}
