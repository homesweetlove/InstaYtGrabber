package com.homesweetlove.instaytgrabber

import android.app.Application
import android.webkit.WebView
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.homesweetlove.instaytgrabber.core.YtDlpRuntime
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramSession

class GrabberApp : Application() {
    override fun onCreate() {
        super.onCreate()

        WebView.setWebContentsDebuggingEnabled(false)
        InstagramSession.clear()

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        YtDlpRuntime.attach(this)
    }
}
