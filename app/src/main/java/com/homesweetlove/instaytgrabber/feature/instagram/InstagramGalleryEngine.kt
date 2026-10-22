package com.homesweetlove.instaytgrabber.feature.instagram

import com.chaquo.python.Python
import com.homesweetlove.instaytgrabber.core.GrabProgress
import com.homesweetlove.instaytgrabber.core.StoragePublisher
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
            // On Android 10+, the public Downloads directory is not a writable
            // filesystem target for ordinary Python code. Download into private
            // cache, then publish via MediaStore.
            val stagingDir = StoragePublisher.tempDirectory("instagram_gallery_${type.folder}")
            try {
                onProgress(GrabProgress(percent = 5f, line = "Instagram 미디어 확인 중"))

                val raw = Python.getInstance()
                    .getModule("instagram_gallery")
                    .callAttr(
                        "download",
                        url.trim(),
                        stagingDir.absolutePath,
                        cookieHeader.orEmpty(),
                        type.folder
                    )
                    .toString()

                val result = JSONObject(raw)
                if (!result.optBoolean("ok", false)) {
                    val problem = result.optString("error")
                        .ifBlank {
                            "Instagram 원본 다운로드를 완료하지 못했습니다 (code=" +
                                result.optInt("code", -1) + ")"
                        }
                    throw IOException(problem)
                }

                val root = stagingDir.canonicalFile.toPath()
                val names = result.optJSONArray("files")
                    ?: throw IOException("다운로드 결과에 파일 목록이 없습니다.")
                val files = (0 until names.length()).mapNotNull { index ->
                    val file = File(names.optString(index)).canonicalFile
                    file.takeIf { it.isFile && it.toPath().startsWith(root) }
                }
                if (files.isEmpty()) {
                    throw IOException("저장된 Instagram 파일을 찾지 못했습니다.")
                }

                val published = StoragePublisher.publishFiles(
                    files = files,
                    relativeFolder = "Instagram/${type.folder}/media"
                )
                onProgress(GrabProgress(percent = 100f, line = "저장 완료"))
                published.first()
            } finally {
                stagingDir.deleteRecursively()
            }
        }
    }
}
