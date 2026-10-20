package com.homesweetlove.instaytgrabber

import android.app.Application
import android.webkit.WebView
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.homesweetlove.instaytgrabber.core.DiagnosticLog
import com.homesweetlove.instaytgrabber.core.StoragePublisher
import com.homesweetlove.instaytgrabber.core.YtDlpRuntime

class GrabberApp : Application() {
    override fun onCreate() {
        super.onCreate()

        WebView.setWebContentsDebuggingEnabled(false)
        // Never clear Instagram cookies on app start. The user must explicitly
        // choose "로그아웃" to remove their login session.
        // Clearing here caused every new launch to look signed out.
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        DiagnosticLog.attach(this)
        StoragePublisher.attach(this)
        YtDlpRuntime.attach(this)
    }
}
