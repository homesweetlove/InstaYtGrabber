package com.homesweetlove.instaytgrabber.feature.instagram

import com.chaquo.python.Python
import com.homesweetlove.instaytgrabber.core.GrabProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object InstagramInstaloaderMediaEngine {
    suspend fun download(
        target: String,
        folderName: String,
        baseName: String,
        authenticated: Boolean,
        onProgress: (GrabProgress) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val cookie = if (authenticated) InstagramSession.cookie().orEmpty() else ""

            onProgress(GrabProgress(percent = 5f, line = "원본 미디어 확인 중"))

            val raw = Python.getInstance()
                .getModule("instagram_instaloader")
                .callAttr("resolve", target, cookie)
                .toString()

            val root = JSONObject(raw)
            if (!root.optBoolean("ok", false)) {
                error(root.optString("error").ifBlank { "Instagram 원본 미디어를 찾지 못했습니다." })
            }

            val items = root.optJSONArray("items")
                ?: error("Instagram 원본 미디어가 없습니다.")
            if (items.length() == 0) {
                error("Instagram 원본 미디어가 없습니다.")
            }

            var lastFile: File? = null
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val url = item.optString("url")
                if (!url.startsWith("https://")) continue

                val percent = 10f + (i.toFloat() / items.length().coerceAtLeast(1)) * 85f
                onProgress(
                    GrabProgress(
                        percent = percent,
                        line = (i + 1).toString() + "/" + items.length() + " 저장 중"
                    )
                )

                lastFile = InstagramDirectFileEngine.download(
                    url = url,
                    folderName = folderName,
                    baseName = baseName + "_" + (i + 1)
                ).getOrThrow()
            }

            val output = lastFile ?: error("저장할 Instagram 미디어가 없습니다.")
            onProgress(GrabProgress(percent = 100f, line = "완료"))
            output
        }
    }
}
