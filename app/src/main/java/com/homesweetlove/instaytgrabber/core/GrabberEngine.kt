package com.homesweetlove.instaytgrabber.core

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
            val root = StoragePublisher.tempDirectory(folderName)

            val mode = when (kind) {
                GrabKind.VIDEO -> "video"
                GrabKind.AUDIO -> "audio"
                GrabKind.MEDIA -> "media"
                GrabKind.THUMBNAIL -> "thumbnail"
            }

            val downloaded = YtDlpRuntime.download(
                url = url,
                targetDirectory = root,
                mode = mode,
                cookieHeader = cookieHeader,
                allowPlaylist = allowPlaylist,
                onProgress = onProgress
            ).getOrThrow()

            val published = StoragePublisher.publishFiles(
                files = downloaded,
                relativeFolder = folderName
            )

            root.deleteRecursively()

            published.firstOrNull()
                ?: error("다운로드된 파일을 저장하지 못했습니다.")
        }
    }
}
