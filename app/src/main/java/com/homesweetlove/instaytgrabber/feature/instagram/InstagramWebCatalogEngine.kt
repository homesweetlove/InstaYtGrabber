package com.homesweetlove.instaytgrabber.feature.instagram

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import java.net.URI
import kotlin.coroutines.resume

object InstagramWebCatalogEngine {
    suspend fun loadBase(
        context: Context,
        profileUrl: String,
        authenticated: Boolean
    ): Result<InstagramProfileCatalog> = withContext(Dispatchers.Main) {
        runCatching {
            withTimeout(20_000) {
                loadBaseInternal(context, profileUrl, authenticated)
            }
        }
    }

    suspend fun loadGrid(
        context: Context,
        profileUrl: String,
        reelsOnly: Boolean,
        authenticated: Boolean,
        onProgress: (Int) -> Unit = {}
    ): Result<List<InstagramCatalogItem>> = withContext(Dispatchers.Main) {
        runCatching {
            withTimeout(150_000) {
                loadGridInternal(
                    context = context,
                    profileUrl = profileUrl,
                    reelsOnly = reelsOnly,
                    authenticated = authenticated,
                    onProgress = onProgress
                )
            }
        }
    }

    private suspend fun loadBaseInternal(
        context: Context,
        profileUrl: String,
        authenticated: Boolean
    ): InstagramProfileCatalog {
        val username = usernameFrom(profileUrl)
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
            webView.translationX = -1200f
            root.addView(webView, FrameLayout.LayoutParams(1080, 2200))

            var finished = false

            fun cleanup() {
                runCatching { root.removeView(webView) }
                runCatching {
                    webView.stopLoading()
                    webView.removeJavascriptInterface("IYGBridge")
                    webView.destroy()
                }
            }

            class Bridge {
                @JavascriptInterface
                fun onBase(payload: String) {
                    webView.post {
                        if (finished) return@post
                        finished = true
                        runCatching {
                            val obj = org.json.JSONObject(payload)
                            val pagePath = obj.optString("path")
                            if (pagePath.trimEnd('/').lowercase() != ("/" + username).lowercase()) {
                                error("Instagram이 프로필 대신 " + pagePath + " 페이지를 열었습니다.")
                            }

                            val avatarUrl = obj.optString("avatar")
                            val pageTitle = obj.optString("title")
                            if (pageTitle.isBlank() && avatarUrl.isBlank()) {
                                error("Instagram 프로필 메타데이터를 읽지 못했습니다.")
                            }

                            val avatar = avatarUrl.takeIf { it.startsWith("http") }?.let {
                                InstagramCatalogItem(
                                    id = "avatar:" + username,
                                    kind = "avatar",
                                    title = "프로필 사진",
                                    targetUrl = it,
                                    previewUrl = it,
                                    isVideo = false,
                                    date = "",
                                    count = 1
                                )
                            }

                            InstagramProfileCatalog(
                                username = username,
                                fullName = pageTitle
                                    .substringBefore("(@")
                                    .substringBefore(" •")
                                    .trim(),
                                biography = obj.optString("description"),
                                isPrivate = obj.optBoolean("private", false),
                                avatar = avatar
                            )
                        }.fold(
                            onSuccess = { catalog ->
                                cleanup()
                                if (cont.isActive) cont.resume(catalog)
                            },
                            onFailure = { error ->
                                cleanup()
                                if (cont.isActive) cont.resumeWith(Result.failure(error))
                            }
                        )
                    }
                }

                @JavascriptInterface
                fun onError(message: String) {
                    webView.post {
                        if (finished) return@post
                        finished = true
                        cleanup()
                        if (cont.isActive) {
                            cont.resumeWith(Result.failure(IllegalStateException(message)))
                        }
                    }
                }
            }

            webView.addJavascriptInterface(Bridge(), "IYGBridge")
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (finished) return
                    view.evaluateJavascript(
                        """
                        (() => {
                          try {
                            const text = (document.body?.innerText || '').toLowerCase();
                            const title =
                              document.querySelector('meta[property="og:title"]')?.content ||
                              document.title || '';
                            const description =
                              document.querySelector('meta[property="og:description"]')?.content ||
                              document.querySelector('meta[name="description"]')?.content || '';
                            const avatar =
                              document.querySelector('meta[property="og:image"]')?.content || '';
                            const isPrivate =
                              text.includes('this account is private') ||
                              text.includes('계정은 비공개') ||
                              text.includes('비공개 계정');
                            IYGBridge.onBase(JSON.stringify({
                              title: title,
                              description: description,
                              avatar: avatar,
                              private: isPrivate
                            }));
                          } catch (e) {
                            IYGBridge.onError(String(e));
                          }
                        })();
                        """.trimIndent(),
                        null
                    )
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    if (request.isForMainFrame && !finished) {
                        finished = true
                        cleanup()
                        if (cont.isActive) {
                            cont.resumeWith(
                                Result.failure(
                                    IllegalStateException(
                                        "Instagram 웹 로드 실패: " + error.description
                                    )
                                )
                            )
                        }
                    }
                }
            }

            cont.invokeOnCancellation { cleanup() }
            webView.loadUrl("https://www.instagram.com/" + username + "/")
        }
    }

    private suspend fun loadGridInternal(
        context: Context,
        profileUrl: String,
        reelsOnly: Boolean,
        authenticated: Boolean,
        onProgress: (Int) -> Unit
    ): List<InstagramCatalogItem> {
        val username = usernameFrom(profileUrl)
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
            webView.translationX = -1200f
            root.addView(webView, FrameLayout.LayoutParams(1080, 2200))

            var finished = false

            fun cleanup() {
                runCatching { root.removeView(webView) }
                runCatching {
                    webView.stopLoading()
                    webView.removeJavascriptInterface("IYGBridge")
                    webView.destroy()
                }
            }

            class Bridge {
                @JavascriptInterface
                fun onProgress(count: Int) {
                    webView.post {
                        if (!finished) onProgress(count)
                    }
                }

                @JavascriptInterface
                fun onGrid(payload: String) {
                    webView.post {
                        if (finished) return@post
                        finished = true
                        runCatching {
                            val array = JSONArray(payload)
                            buildList {
                                for (i in 0 until array.length()) {
                                    val obj = array.optJSONObject(i) ?: continue
                                    val href = obj.optString("href")
                                    val preview = obj.optString("preview")
                                    val kind = if (href.contains("/reel/")) "reel" else "post"
                                    if (reelsOnly && kind != "reel") continue
                                    if (!reelsOnly && kind != "post") continue

                                    add(
                                        InstagramCatalogItem(
                                            id = href,
                                            kind = kind,
                                            title = obj.optString("title").ifBlank {
                                                if (kind == "reel") "릴스" else "게시글"
                                            },
                                            targetUrl = "https://www.instagram.com" + href,
                                            previewUrl = preview,
                                            isVideo = kind == "reel",
                                            date = "",
                                            count = 1
                                        )
                                    )
                                }
                            }
                        }.fold(
                            onSuccess = { items ->
                                cleanup()
                                if (cont.isActive) cont.resume(items)
                            },
                            onFailure = { error ->
                                cleanup()
                                if (cont.isActive) cont.resumeWith(Result.failure(error))
                            }
                        )
                    }
                }

                @JavascriptInterface
                fun onError(message: String) {
                    if (finished) return
                    finished = true
                    cleanup()
                    if (cont.isActive) {
                        cont.resumeWith(Result.failure(IllegalStateException(message)))
                    }
                }
            }

            webView.addJavascriptInterface(Bridge(), "IYGBridge")
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (finished) return
                    view.evaluateJavascript(gridScript(reelsOnly), null)
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    if (request.isForMainFrame && !finished) {
                        finished = true
                        cleanup()
                        if (cont.isActive) {
                            cont.resumeWith(
                                Result.failure(
                                    IllegalStateException(
                                        "Instagram 웹 로드 실패: " + error.description
                                    )
                                )
                            )
                        }
                    }
                }
            }

            cont.invokeOnCancellation { cleanup() }

            val target = if (reelsOnly) {
                "https://www.instagram.com/" + username + "/reels/"
            } else {
                "https://www.instagram.com/" + username + "/"
            }
            webView.loadUrl(target)
        }
    }

    private fun makeWebView(context: Context): WebView =
        WebView(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.mediaPlaybackRequiresUserGesture = true
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 16; SM-S928N) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/140.0.0.0 Mobile Safari/537.36"
            isClickable = false
            isFocusable = false
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }

    private suspend fun prepareCookies(authenticated: Boolean) {
        val manager = CookieManager.getInstance()

        suspendCancellableCoroutine<Unit> { cont ->
            manager.removeAllCookies {
                if (authenticated) {
                    InstagramSession.cookie()
                        .orEmpty()
                        .split(";")
                        .map { value -> value.trim() }
                        .filter { value -> value.contains("=") }
                        .forEach { cookie ->
                            manager.setCookie(
                                "https://www.instagram.com",
                                cookie + "; Domain=.instagram.com; Path=/; Secure"
                            )
                        }
                    manager.flush()
                }

                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    private fun usernameFrom(url: String): String {
        val uri = URI(url.trim())
        val username = uri.path.orEmpty()
            .trim('/')
            .substringBefore('/')
            .removePrefix("@")

        require(username.matches(Regex("[A-Za-z0-9._]{1,30}"))) {
            "Instagram 프로필 링크를 입력해 주세요."
        }
        return username
    }

    private fun gridScript(reelsOnly: Boolean): String {
        val wanted = if (reelsOnly) "/reel/" else "/p/"

        return """
            (() => {
              if (window.__IYG_RUNNING__) return;
              window.__IYG_RUNNING__ = true;

              const wanted = "$wanted";
              const found = new Map();
              let stable = 0;
              let previous = 0;

              function collect() {
                document.querySelectorAll('a[href]').forEach((a) => {
                  const href = a.getAttribute('href') || '';
                  if (!href.includes(wanted)) return;

                  const clean = href.split('?')[0];
                  const img = a.querySelector('img');
                  const preview = img?.currentSrc || img?.src || '';
                  const title = img?.alt || a.getAttribute('aria-label') || '';

                  found.set(clean, {
                    href: clean,
                    preview: preview,
                    title: title
                  });
                });
              }

              async function run() {
                try {
                  for (let i = 0; i < 240; i++) {
                    collect();

                    if (found.size !== previous) {
                      previous = found.size;
                      stable = 0;
                      IYGBridge.onProgress(found.size);
                    } else {
                      stable++;
                    }

                    if (stable >= 5) break;

                    window.scrollTo(0, document.documentElement.scrollHeight);
                    await new Promise((resolve) => setTimeout(resolve, 500));
                  }

                  collect();
                  IYGBridge.onGrid(JSON.stringify(Array.from(found.values())));
                } catch (e) {
                  IYGBridge.onError(String(e));
                }
              }

              run();
            })();
        """.trimIndent()
    }
}
