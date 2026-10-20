package com.homesweetlove.instaytgrabber.feature.instagram

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.homesweetlove.instaytgrabber.core.GrabProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import kotlin.coroutines.resume

private data class InstagramResolvedMedia(
    val url: String,
    val isVideo: Boolean
)

object InstagramWebMediaEngine {
    suspend fun download(
        context: Context,
        pageUrl: String,
        folderName: String,
        baseName: String,
        authenticated: Boolean,
        onProgress: (GrabProgress) -> Unit = {}
    ): Result<File> = runCatching {
        val media = resolve(
            context = context,
            pageUrl = pageUrl,
            authenticated = authenticated
        ).getOrThrow()

        require(media.isNotEmpty()) {
            "웹페이지에서 저장 가능한 원본 미디어를 찾지 못했습니다."
        }

        var first: File? = null

        media.forEachIndexed { index, item ->
            onProgress(
                GrabProgress(
                    percent = ((index.toFloat() / media.size.toFloat()) * 100f),
                    line = (index + 1).toString() + "/" + media.size + " 저장 중"
                )
            )

            val saved = InstagramDirectFileEngine.download(
                url = item.url,
                folderName = folderName,
                baseName = baseName + "_" + (index + 1)
            ).getOrThrow()

            if (first == null) first = saved
        }

        onProgress(GrabProgress(percent = 100f, line = "완료"))
        first ?: error("저장된 파일이 없습니다.")
    }

    private suspend fun resolve(
        context: Context,
        pageUrl: String,
        authenticated: Boolean
    ): Result<List<InstagramResolvedMedia>> = withContext(Dispatchers.Main) {
        runCatching {
            withTimeout(65_000L) {
                resolveInternal(context, pageUrl, authenticated)
            }
        }
    }

    private suspend fun resolveInternal(
        context: Context,
        pageUrl: String,
        authenticated: Boolean
    ): List<InstagramResolvedMedia> {
        val mediaLink = InstagramMediaLink.parse(pageUrl)
            ?: error("Instagram 게시물 또는 릴스 링크를 입력해 주세요.")
        val route = URI(mediaLink.canonicalUrl).path.trimEnd('/')
        // Loading the Instagram home page and changing window.history does
        // not request the media. Use a real embed URL as successful Android
        // downloaders do, which avoids web_profile_info profile API calls.
        val embedUrl = mediaLink.canonicalUrl + "embed/captioned/"

        prepareCookies(authenticated)

        return suspendCancellableCoroutine { cont ->
            val activity = context as? Activity
            if (activity == null) {
                cont.resumeWith(Result.failure(IllegalStateException("Activity context required")))
                return@suspendCancellableCoroutine
            }

            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            val webView = makeWebView(activity)
            webView.alpha = 0.01f
            root.addView(webView, 0, FrameLayout.LayoutParams(1080, 2200))

            var finished = false
            var routed = false

            fun cleanup() {
                runCatching { root.removeView(webView) }
                runCatching {
                    webView.stopLoading()
                    webView.removeJavascriptInterface("IYGMediaBridge")
                    webView.destroy()
                }
            }

            fun fail(message: String) {
                if (finished) return
                finished = true
                cleanup()
                if (cont.isActive) {
                    cont.resumeWith(Result.failure(IllegalStateException(message)))
                }
            }

            class Bridge {
                @JavascriptInterface
                fun onMedia(payload: String) {
                    webView.post {
                        if (finished) return@post

                        runCatching {
                            val obj = JSONObject(payload)
                            val path = obj.optString("path")

                            if (!path.trimEnd('/').startsWith(route)) {
                                error("Instagram 웹이 게시물 페이지를 열지 못했습니다: " + path)
                            }

                            if (obj.optBoolean("loginWall", false)) {
                                error("Instagram이 이 미디어에 로그인 화면을 표시했습니다.")
                            }

                            val array = obj.optJSONArray("items") ?: JSONArray()
                            buildList {
                                for (i in 0 until array.length()) {
                                    val item = array.optJSONObject(i) ?: continue
                                    val url = item.optString("url")
                                    if (!InstagramMediaUrlPolicy.isAllowed(url)) continue

                                    add(
                                        InstagramResolvedMedia(
                                            url = url,
                                            isVideo = item.optBoolean("video", false)
                                        )
                                    )
                                }
                            }.distinctBy { it.url }
                        }.fold(
                            onSuccess = { items ->
                                finished = true
                                cleanup()
                                if (cont.isActive) cont.resume(items)
                            },
                            onFailure = { error ->
                                fail(error.message ?: error.javaClass.simpleName)
                            }
                        )
                    }
                }

                @JavascriptInterface
                fun onError(message: String) {
                    webView.post { fail(message) }
                }
            }

            webView.addJavascriptInterface(Bridge(), "IYGMediaBridge")
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (finished || routed) return
                    routed = true

                    if (url.contains("/accounts/login")) {
                        fail("Instagram이 이 미디어에 로그인 또는 추가 인증을 요구했습니다.")
                        return
                    }
                    // Render/embed hydration takes time; execute collector on
                    // the actual media page rather than falsifying its URL.
                    view.postDelayed({
                        if (!finished && view.url.orEmpty().contains(route)) {
                            view.evaluateJavascript(collectorScript(), null)
                        }
                    }, 1000L)
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse
                ) {
                    if (!request.isForMainFrame) return
                    if (errorResponse.statusCode == 429) {
                        InstagramRateLimit.onFailure("HTTP 429")
                    }
                    fail("Instagram 미디어 페이지 HTTP " + errorResponse.statusCode)
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    if (request.isForMainFrame) {
                        fail("Instagram 웹 로드 실패: " + error.description)
                    }
                }
            }

            cont.invokeOnCancellation {
                webView.post { cleanup() }
            }

            webView.loadUrl(embedUrl)
        }
    }

    private fun collectorScript(): String =
        """
        (() => {
          const items = new Map();

          function add(url, video) {
            if (!url || typeof url !== 'string') return;
            if (!url.startsWith('https://')) return;
            if (url.startsWith('blob:')) return;
            items.set(url, { url: url, video: !!video });
          }

          function bestImage(img) {
            const srcset = img.getAttribute('srcset') || '';
            if (srcset) {
              const candidates = srcset.split(',').map((part) => {
                const bits = part.trim().split(/\s+/);
                const width = parseInt((bits[1] || '0').replace('w', ''), 10) || 0;
                return { url: bits[0], width: width };
              }).filter((x) => x.url);
              candidates.sort((a, b) => b.width - a.width);
              if (candidates.length) return candidates[0].url;
            }
            return img.currentSrc || img.src || '';
          }

          function collect() {
            const videoMeta =
              document.querySelector('meta[property="og:video:secure_url"]')?.content ||
              document.querySelector('meta[property="og:video"]')?.content ||
              '';
            add(videoMeta, true);

            document.querySelectorAll('article video, main video').forEach((video) => {
              const url = video.currentSrc || video.src || '';
              add(url, true);
            });

            document.querySelectorAll('article img, main img').forEach((img) => {
              const width = img.naturalWidth || img.width || 0;
              const height = img.naturalHeight || img.height || 0;
              if (width < 250 || height < 250) return;
              add(bestImage(img), false);
            });

            if (items.size === 0) {
              const imageMeta =
                document.querySelector('meta[property="og:image:secure_url"]')?.content ||
                document.querySelector('meta[property="og:image"]')?.content ||
                '';
              add(imageMeta, false);
            }
          }

          function nextButton() {
            const direct = document.querySelector(
              'button[aria-label="Next"], button[aria-label="다음"], [role="button"][aria-label="Next"], [role="button"][aria-label="다음"]'
            );
            if (direct) return direct;

            const icon = document.querySelector(
              'svg[aria-label="Next"], svg[aria-label="다음"]'
            );
            return icon?.closest('button, [role="button"]') || null;
          }

          function loginWall() {
            const path = window.location.pathname || '';
            const text = (document.body?.innerText || '').toLowerCase();
            return path.startsWith('/accounts/login') ||
              text.includes('log in to see photos and videos') ||
              text.includes('instagram에 로그인');
          }

          async function run() {
            try {
              let stable = 0;
              let previous = 0;

              for (let i = 0; i < 30; i++) {
                collect();

                if (items.size > previous) {
                  previous = items.size;
                  stable = 0;
                } else {
                  stable++;
                }

                const next = nextButton();
                if (!next || next.getAttribute('aria-disabled') === 'true') {
                  if (stable >= 2) break;
                  await new Promise((resolve) => setTimeout(resolve, 700));
                  continue;
                }

                next.click();
                await new Promise((resolve) => setTimeout(resolve, 850));
              }

              collect();

              IYGMediaBridge.onMedia(JSON.stringify({
                path: window.location.pathname || '',
                loginWall: loginWall(),
                items: Array.from(items.values())
              }));
            } catch (e) {
              IYGMediaBridge.onError(String(e));
            }
          }

          run();
        })();
        """.trimIndent()

    private fun makeWebView(context: Context): WebView =
        WebView(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.mediaPlaybackRequiresUserGesture = true
            settings.userAgentString = InstagramBrowserConfig.mobileUserAgent(context)
            isClickable = false
            isFocusable = false
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }

    private fun prepareCookies(authenticated: Boolean) {
        // WebView's CookieManager is shared. Never wipe a valid Instagram
        // login session while trying to download a public post.
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        if (authenticated) {
            InstagramSession.cookie().orEmpty().split(";")
                .map { it.trim() }
                .filter { it.contains("=") }
                .forEach { cookie ->
                    manager.setCookie(
                        "https://www.instagram.com",
                        cookie + "; Domain=.instagram.com; Path=/; Secure"
                    )
                }
            manager.flush()
        }
    }
}
