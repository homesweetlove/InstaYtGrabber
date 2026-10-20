package com.homesweetlove.instaytgrabber

import android.app.Application
import android.webkit.WebView
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.homesweetlove.instaytgrabber.core.DiagnosticLog
import com.homesweetlove.instaytgrabber.core.StoragePublisher
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

        DiagnosticLog.attach(this)
        StoragePublisher.attach(this)
        YtDlpRuntime.attach(this)
    }
}
