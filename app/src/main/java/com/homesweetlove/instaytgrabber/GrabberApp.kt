package com.homesweetlove.instaytgrabber

import android.app.Application
import com.yausername.youtubedl_android.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

class GrabberApp : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching { YoutubeDL.getInstance().init(this) }
        runCatching { FFmpeg.getInstance().init(this) }
    }
}
