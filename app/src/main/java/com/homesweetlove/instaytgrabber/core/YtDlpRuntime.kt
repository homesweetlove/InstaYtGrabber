package com.homesweetlove.instaytgrabber.core

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object YtDlpRuntime {
    private const val PREFS = "yt_dlp_runtime"
    private const val LAST_UPDATE = "last_update"
    private const val UPDATE_INTERVAL_MS = 12L * 60L * 60L * 1000L

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
                        "yt-dlp 초기화 실패: " + (error.message ?: error.javaClass.simpleName),
                        error
                    )
                }
            }

            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val lastUpdate = prefs.getLong(LAST_UPDATE, 0L)

            if (now - lastUpdate >= UPDATE_INTERVAL_MS) {
                onStatus("yt-dlp nightly 업데이트 중")
                val nightly = runCatching {
                    YoutubeDL.getInstance().updateYoutubeDL(
                        appContext,
                        YoutubeDL.UpdateChannel.NIGHTLY
                    )
                }

                if (nightly.isSuccess) {
                    updateError = null
                    prefs.edit().putLong(LAST_UPDATE, now).apply()
                } else {
                    onStatus("nightly 실패 · stable 업데이트 중")
                    val stable = runCatching {
                        YoutubeDL.getInstance().updateYoutubeDL(
                            appContext,
                            YoutubeDL.UpdateChannel.STABLE
                        )
                    }

                    if (stable.isSuccess) {
                        updateError = null
                        prefs.edit().putLong(LAST_UPDATE, now).apply()
                    } else {
                        updateError = stable.exceptionOrNull() ?: nightly.exceptionOrNull()
                        onStatus("업데이트 실패 · 번들 yt-dlp로 계속")
                    }
                }
            }

            YoutubeDL.getInstance().versionName(appContext)
                ?: YoutubeDL.getInstance().version(appContext)
                ?: "bundled"
        }
    }

    fun lastInitError(): String? =
        initError?.let { it.message ?: it.javaClass.simpleName }

    fun lastUpdateError(): String? =
        updateError?.let { it.message ?: it.javaClass.simpleName }
}
