package com.homesweetlove.instaytgrabber.feature.youtube

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object YouTubeNativeLookup {
    private data class Client(
        val name: String,
        val version: String,
        val userAgent: String,
        val androidSdk: Int? = null,
        val deviceMake: String? = null,
        val deviceModel: String? = null
    )

    private val clients = listOf(
        Client(
            name = "ANDROID",
            version = "19.30.36",
            userAgent = "com.google.android.youtube/19.30.36 (Linux; U; Android 11) gzip",
            androidSdk = 30
        ),
        Client(
            name = "ANDROID_VR",
            version = "1.50.29",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.50.29 (Linux; U; Android 12) gzip",
            androidSdk = 32,
            deviceMake = "Oculus",
            deviceModel = "Quest 3"
        ),
        Client(
            name = "TVHTML5",
            version = "7.20250312.16.00",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )
    )

    suspend fun inspect(url: String): Result<YouTubeMediaInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val videoId = extractVideoId(url)
                    ?: error("YouTube 영상 ID를 찾지 못했습니다.")

                val failures = mutableListOf<String>()

                for (client in clients) {
                    val result = runCatching {
                        inspectWithClient(videoId, client)
                    }

                    if (result.isSuccess) {
                        return@runCatching result.getOrThrow()
                    }

                    failures += client.name + ": " +
                        (result.exceptionOrNull()?.message ?: "unknown")
                }

                error("InnerTube 조회 실패 · " + failures.joinToString(" / "))
            }
        }

    private fun inspectWithClient(
        videoId: String,
        client: Client
    ): YouTubeMediaInfo {
        val connection = URL(
            "https://www.youtube.com/youtubei/v1/player"
        ).openConnection() as HttpURLConnection

        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 7_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("User-Agent", client.userAgent)

        val payload = JSONObject().apply {
            put("videoId", videoId)
            put(
                "context",
                JSONObject().apply {
                    put(
                        "client",
                        JSONObject().apply {
                            put("clientName", client.name)
                            put("clientVersion", client.version)
                            put("hl", "ko")
                            put("gl", "KR")
                            put("utcOffsetMinutes", 540)
                            client.androidSdk?.let {
                                put("androidSdkVersion", it)
                            }
                            client.deviceMake?.let {
                                put("deviceMake", it)
                            }
                            client.deviceModel?.let {
                                put("deviceModel", it)
                            }
                        }
                    )
                }
            )
        }

        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(payload.toString())
            writer.flush()
        }

        val statusCode = connection.responseCode
        if (statusCode != 200) {
            connection.disconnect()
            error("HTTP " + statusCode)
        }

        val response = connection.inputStream
            .bufferedReader()
            .use { it.readText() }
        connection.disconnect()

        val root = JSONObject(response)
        val playability = root.optJSONObject("playabilityStatus")
        val playabilityStatus = playability?.optString("status").orEmpty()

        if (playabilityStatus.isNotBlank() && playabilityStatus != "OK") {
            error(
                playability?.optString("reason")
                    ?.takeIf { it.isNotBlank() }
                    ?: playabilityStatus
            )
        }

        val details = root.optJSONObject("videoDetails")
            ?: error("videoDetails 없음")

        val streaming = root.optJSONObject("streamingData")
            ?: error("재생 가능한 스트림 없음")

        val standardCount = streaming.optJSONArray("formats")?.length() ?: 0
        val adaptiveCount = streaming.optJSONArray("adaptiveFormats")?.length() ?: 0
        val formatCount = standardCount + adaptiveCount

        if (formatCount == 0) {
            error("재생 가능한 포맷 없음")
        }

        val thumbnailArray = details
            .optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")

        var thumbnail = ""
        if (thumbnailArray != null && thumbnailArray.length() > 0) {
            thumbnail = thumbnailArray
                .optJSONObject(thumbnailArray.length() - 1)
                ?.optString("url")
                .orEmpty()
        }

        if (thumbnail.isBlank()) {
            thumbnail = "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg"
        }

        return YouTubeMediaInfo(
            id = videoId,
            title = details.optString("title").ifBlank { "YouTube 영상" },
            channel = details.optString("author"),
            thumbnailUrl = thumbnail,
            durationSeconds = details.optString("lengthSeconds")
                .toIntOrNull()
                ?: 0,
            extractor = "InnerTube/" + client.name,
            formatCount = formatCount
        )
    }

    private fun extractVideoId(url: String): String? {
        val trimmed = url.trim()

        return runCatching {
            val uri = Uri.parse(trimmed)
            val host = uri.host.orEmpty().lowercase()
            val segments = uri.pathSegments

            val candidate = when {
                host == "youtu.be" -> segments.firstOrNull()
                segments.firstOrNull() == "shorts" -> segments.getOrNull(1)
                segments.firstOrNull() == "embed" -> segments.getOrNull(1)
                segments.firstOrNull() == "v" -> segments.getOrNull(1)
                else -> uri.getQueryParameter("v")
            }

            candidate
                ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
        }.getOrNull()
    }
}
