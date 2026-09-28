package com.homesweetlove.instaytgrabber.core

import android.content.Context
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

interface YtDlpProgressListener {
    fun onProgress(percent: Double, etaSeconds: Long, line: String)
}

object YtDlpRuntime {
    private lateinit var appContext: Context
    private val mutex = Mutex()

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    private fun qjsPath(): String =
        File(appContext.applicationInfo.nativeLibraryDir, "libqjs.so")
            .takeIf { it.isFile }
            ?.absolutePath
            .orEmpty()

    private fun ffmpegPath(): String =
        File(appContext.applicationInfo.nativeLibraryDir, "libffmpeg.so")
            .takeIf { it.isFile }
            ?.absolutePath
            .orEmpty()

    suspend fun ensureReady(
        onStatus: (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(::appContext.isInitialized) {
                "yt-dlp 런타임이 앱에 연결되지 않았습니다."
            }

            onStatus("Chaquopy yt-dlp 준비 중")
            val raw = Python.getInstance()
                .getModule("youtube_chaquopy")
                .callAttr("version")
                .toString()

            val root = JSONObject(raw)
            if (!root.optBoolean("ok", false)) {
                error(root.optString("error").ifBlank { "yt-dlp 초기화 실패" })
            }

            val version = root.optString("yt_dlp", "unknown")
            val python = root.optString("python", "unknown")
            "yt-dlp " + version + " · Python " + python
        }
    }

    suspend fun inspect(
        url: String,
        onStatus: (String) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            ensureReady(onStatus)
            onStatus("영상 정보 확인 중")

            Python.getInstance()
                .getModule("youtube_chaquopy")
                .callAttr(
                    "inspect",
                    url,
                    qjsPath()
                )
                .toString()
        }
    }

    suspend fun download(
        url: String,
        targetDirectory: File,
        mode: String,
        cookieHeader: String?,
        allowPlaylist: Boolean,
        onProgress: (GrabProgress) -> Unit = {}
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            val version = ensureReady { state ->
                onProgress(GrabProgress(percent = 0f, line = state))
            }
            onProgress(
                GrabProgress(
                    percent = 0f,
                    line = version
                )
            )

            val listener = object : YtDlpProgressListener {
                override fun onProgress(
                    percent: Double,
                    etaSeconds: Long,
                    line: String
                ) {
                    onProgress(
                        GrabProgress(
                            percent = percent.toFloat().coerceIn(0f, 100f),
                            etaSeconds = etaSeconds,
                            line = line
                        )
                    )
                }
            }

            val raw = Python.getInstance()
                .getModule("youtube_chaquopy")
                .callAttr(
                    "download",
                    url,
                    targetDirectory.absolutePath,
                    mode,
                    qjsPath(),
                    ffmpegPath(),
                    cookieHeader.orEmpty(),
                    allowPlaylist,
                    listener
                )
                .toString()

            val root = JSONObject(raw)
            if (!root.optBoolean("ok", false)) {
                error(
                    root.optString("error")
                        .ifBlank { "yt-dlp 다운로드 실패" }
                )
            }

            val array = root.optJSONArray("files")
                ?: error("다운로드 결과 파일이 없습니다.")

            buildList {
                for (i in 0 until array.length()) {
                    val path = array.optString(i)
                    if (path.isNotBlank()) {
                        File(path).takeIf { it.isFile && it.length() > 0L }
                            ?.let(::add)
                    }
                }
            }.ifEmpty {
                error("다운로드 결과 파일을 찾지 못했습니다.")
            }
        }
    }

    fun diagnostics(): String {
        if (!::appContext.isInitialized) {
            return "runtime not attached"
        }
        val qjs = qjsPath().ifBlank { "missing" }
        val ffmpeg = ffmpegPath().ifBlank { "missing" }
        return "qjs=" + qjs + " / ffmpeg=" + ffmpeg
    }
}
