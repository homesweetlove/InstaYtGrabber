package com.homesweetlove.instaytgrabber.core

import android.os.Environment
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class GrabKind { VIDEO, AUDIO, MEDIA }

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
            val root = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "InstaYtGrabber/$folderName"
            ).apply { mkdirs() }

            val request = YoutubeDLRequest(url)
            request.addOption("--newline")
            request.addOption("--no-mtime")
            request.addOption("--restrict-filenames")
            if (!allowPlaylist) request.addOption("--no-playlist")
            cookieHeader?.takeIf { it.isNotBlank() }?.let {
                request.addOption("--add-header", "Cookie:$it")
            }

            when (kind) {
                GrabKind.AUDIO -> {
                    request.addOption("-x")
                    request.addOption("--audio-format", "mp3")
                    request.addOption("--audio-quality", "0")
                }
                GrabKind.VIDEO -> {
                    request.addOption("-f", "bv*+ba/b")
                    request.addOption("--merge-output-format", "mp4")
                }
                GrabKind.MEDIA -> request.addOption("-f", "best")
            }

            val template = File(root, "%(title).180B [%(id)s].%(ext)s").absolutePath
            request.addOption("-o", template)

            YoutubeDL.getInstance().execute(request) { progress, eta ->
                onProgress(GrabProgress(progress, eta, ""))
            }
            root
        }
    }
}
