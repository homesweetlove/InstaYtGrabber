package com.homesweetlove.instaytgrabber

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.homesweetlove.instaytgrabber.ui.GrabberTheme

/**
 * Dedicated in-app browser screen. A GeckoView needs a real Activity window,
 * keyboard focus and an attached Android view, rather than a nested Dialog.
 */
class InstagramBrowserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val initial = intent?.getStringExtra(EXTRA_INITIAL_PROFILE).orEmpty()
        setContent {
            GrabberTheme {
                InstagramGeckoBrowser(
                    initialProfile = initial,
                    onDismiss = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_INITIAL_PROFILE = "initial_instagram_profile"
    }
}
