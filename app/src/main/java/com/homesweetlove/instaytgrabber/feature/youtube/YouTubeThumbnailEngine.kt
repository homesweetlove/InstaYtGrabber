package com.homesweetlove.instaytgrabber.feature.youtube

import com.homesweetlove.instaytgrabber.core.GrabProgress
import com.homesweetlove.instaytgrabber.core.StoragePublisher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * YouTube thumbnails are publicly served as JPEG images. Fetching them directly
 * avoids requiring yt-dlp / a JavaScript runtime for thumbnail-only downloads.
 */
object YouTubeThumbnailEngine {
    suspend fun download(
        url: String,
        onProgress: (GrabProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val videoId = YouTubeNativeLookup.videoId(url)
                ?: error("올바른 YouTube 영상 링크가 아닙니다.")

            val folder = "YouTube/thumbnail"
            val tempDir = StoragePublisher.tempDirectory(folder)

            try {
                val target = File(tempDir, "YouTube-$videoId.jpg")
                val qualities = listOf(
                    "maxresdefault.jpg",
                    "sddefault.jpg",
                    "hqdefault.jpg",
                    "mqdefault.jpg"
                )

                onProgress(GrabProgress(line = "YouTube 썸네일을 확인하고 있어요"))
                for (quality in qualities) {
                    val connection = URL(
                        "https://i.ytimg.com/vi/$videoId/$quality"
                    ).openConnection() as HttpURLConnection

                    connection.connectTimeout = 10_000
                    connection.readTimeout = 20_000
                    connection.instanceFollowRedirects = true

                    try {
                        if (connection.responseCode !in 200..299 ||
                            !connection.contentType.orEmpty().startsWith("image/jpeg", true)
                        ) {
                            continue
                        }
                        connection.inputStream.use { input ->
                            target.outputStream().use { output ->
                                input.copyTo(output, 64 * 1024)
                            }
                        }
                        if (target.length() > 0L) {
                            val published = StoragePublisher.publishFiles(
                                files = listOf(target),
                                relativeFolder = folder
                            ).first()
                            onProgress(GrabProgress(percent = 100f, line = "썸네일 저장 완료"))
                            return@runCatching published
                        }
                    } finally {
                        connection.disconnect()
                    }
                }
                error("사용 가능한 YouTube JPG 썸네일을 찾지 못했습니다.")
            } finally {
                tempDir.deleteRecursively()
            }
        }
    }
}
