package com.homesweetlove.instaytgrabber.core

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object YtDlpRuntime {
    private lateinit var appContext: Context
    private val mutex = Mutex()

    @Volatile
    private var initialized = false

    @Volatile
    private var initError: Throwable? = null

    @Volatile
    private var updateError: Throwable? = null

    fun attach(context: Context) {
        appContext = context.applicationContext
        try {
            YoutubeDL.getInstance().init(appContext)
            FFmpeg.getInstance().init(appContext)
            initialized = true
            initError = null
        } catch (error: Throwable) {
            initialized = false
            initError = error
        }
    }

    suspend fun ensureReady(
        onStatus: (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!::appContext.isInitialized) {
                error("yt-dlp 런타임이 앱에 연결되지 않았습니다.")
            }

            if (!initialized) {
                onStatus("yt-dlp 초기화 중")
                try {
                    YoutubeDL.getInstance().init(appContext)
                    FFmpeg.getInstance().init(appContext)
                    initialized = true
                    initError = null
                } catch (error: Throwable) {
                    initError = error
                    throw IllegalStateException(
                        "yt-dlp 초기화 실패: " +
                            (error.message ?: error.javaClass.simpleName),
                        error
                    )
                }
            }

            YoutubeDL.getInstance().versionName(appContext)
                ?: YoutubeDL.getInstance().version(appContext)
                ?: "bundled"
        }
    }

    suspend fun refreshEngine(
        onStatus: (String) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                if (!initialized) {
                    YoutubeDL.getInstance().init(appContext)
                    FFmpeg.getInstance().init(appContext)
                    initialized = true
                }

                onStatus("yt-dlp stable 업데이트 확인 중")
                val stable = runCatching {
                    YoutubeDL.getInstance().updateYoutubeDL(
                        appContext,
                        YoutubeDL.UpdateChannel.STABLE
                    )
                }

                if (stable.isFailure) {
                    onStatus("stable 실패 · nightly 확인 중")
                    YoutubeDL.getInstance().updateYoutubeDL(
                        appContext,
                        YoutubeDL.UpdateChannel.NIGHTLY
                    )
                }

                updateError = null
                YoutubeDL.getInstance().versionName(appContext)
                    ?: YoutubeDL.getInstance().version(appContext)
                    ?: "bundled"
            }.onFailure {
                updateError = it
            }
        }
    }

    fun lastInitError(): String? =
        initError?.let { it.message ?: it.javaClass.simpleName }

    fun lastUpdateError(): String? =
        updateError?.let { it.message ?: it.javaClass.simpleName }
}
