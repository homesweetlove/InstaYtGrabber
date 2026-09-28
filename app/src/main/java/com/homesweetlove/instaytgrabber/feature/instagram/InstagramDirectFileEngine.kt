package com.homesweetlove.instaytgrabber.feature.instagram

import android.os.Environment
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
            require(url.startsWith("https://")) { "HTTPS 미디어 URL이 아닙니다." }

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 8_000
            connection.readTimeout = 20_000
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/140.0.0.0 Mobile Safari/537.36"
            )
            connection.setRequestProperty("Referer", "https://www.instagram.com/")
            connection.connect()

            if (connection.responseCode !in 200..299) {
                error("미디어 요청 실패 · HTTP ${connection.responseCode}")
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

            val root = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "InstaYtGrabber/Instagram/$folderName"
            ).apply { mkdirs() }

            val safeName = baseName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val output = File(root, "$safeName.$extension")

            connection.inputStream.use { input ->
                output.outputStream().use { stream ->
                    input.copyTo(stream)
                }
            }
            connection.disconnect()
            output
        }
    }
}
