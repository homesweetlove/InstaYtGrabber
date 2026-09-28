package com.homesweetlove.instaytgrabber.feature.instagram

import android.os.Environment
import com.chaquo.python.Python
import com.homesweetlove.instaytgrabber.core.GrabProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException

object InstagramGalleryEngine {
    suspend fun download(
        url: String,
        type: InstagramMediaType,
        cookieHeader: String? = null,
        onProgress: (GrabProgress) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val root = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "InstaYtGrabber/Instagram/${type.folder}/media"
            ).apply { mkdirs() }

            onProgress(GrabProgress(percent = 5f, line = "Instagram media resolver"))

            val raw = Python.getInstance()
                .getModule("instagram_gallery")
                .callAttr(
                    "download",
                    url.trim(),
                    root.absolutePath,
                    cookieHeader.orEmpty(),
                    type.folder
                )
                .toString()

            val result = JSONObject(raw)
            if (!result.optBoolean("ok", false)) {
                val error = result.optString("error")
                    .ifBlank { "Instagram에서 저장 가능한 미디어를 찾지 못했습니다." }
                throw IOException(error)
            }

            onProgress(GrabProgress(percent = 100f, line = "done"))
            root
        }
    }
}
