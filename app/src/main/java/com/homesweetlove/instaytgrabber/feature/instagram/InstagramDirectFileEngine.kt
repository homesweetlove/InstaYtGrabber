package com.homesweetlove.instaytgrabber.feature.instagram

import android.webkit.CookieManager
import com.homesweetlove.instaytgrabber.core.StoragePublisher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object InstagramDirectFileEngine {
    suspend fun download(
        url: String,
        folderName: String,
        baseName: String
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(InstagramMediaUrlPolicy.isAllowed(url)) {
                "신뢰할 수 있는 Instagram HTTPS 미디어 주소가 아닙니다."
            }

            val connection = URL(url).openConnection() as HttpURLConnection
            var tempDir: File? = null
            try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/140.0.0.0 Mobile Safari/537.36"
            )
            connection.setRequestProperty("Referer", "https://www.instagram.com/")
            connection.setRequestProperty("Accept", "*/*")
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            connection.setRequestProperty("Range", "bytes=0-")
            connection.setRequestProperty("Sec-Fetch-Mode", "no-cors")
            connection.setRequestProperty("Sec-Fetch-Site", "cross-site")
            val cdnCookies = CookieManager.getInstance().getCookie(url).orEmpty()
            if (cdnCookies.isNotBlank()) {
                connection.setRequestProperty("Cookie", cdnCookies)
            }
            connection.connect()

            // The extracted URL may redirect; reject untrusted final hosts.
            require(InstagramMediaUrlPolicy.isAllowed(connection.url.toString())) {
                "Instagram 미디어가 외부 도메인으로 이동했습니다."
            }

            if (connection.responseCode !in 200..299) {
                if (connection.responseCode == 429) InstagramRateLimit.onMedia429()
                error("Instagram CDN 미디어 요청 실패 · HTTP ${connection.responseCode}")
            }

            val extension = when (connection.contentType?.substringBefore(";")?.lowercase()) {
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                "image/webp" -> "webp"
                "video/mp4" -> "mp4"
                else -> url.substringBefore("?")
                    .substringAfterLast(".", "bin")
                    .takeIf { it.length in 2..5 }
                    ?: "bin"
            }

            val stagingDir = StoragePublisher.tempDirectory(folderName)
            tempDir = stagingDir
            val safeName = baseName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val tempFile = File(stagingDir, safeName + "." + extension)

            connection.inputStream.use { input ->
                tempFile.outputStream().use { stream ->
                    input.copyTo(stream, 64 * 1024)
                }
            }
            connection.disconnect()

            val published = StoragePublisher.publishFiles(
                files = listOf(tempFile),
                relativeFolder = "Instagram/" + folderName.trim('/')
            ).first()

            published
            } finally {
                connection.disconnect()
                tempDir?.deleteRecursively()
            }
        }
    }
}
