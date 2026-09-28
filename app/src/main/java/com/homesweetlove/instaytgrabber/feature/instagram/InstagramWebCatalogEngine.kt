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
import org.json.JSONObject
import java.net.URI
import kotlin.coroutines.resume

object InstagramWebCatalogEngine {
    private const val BASE_TIMEOUT_MS = 25_000L
    private const val GRID_TIMEOUT_MS = 150_000L

    suspend fun loadBase(
        context: Context,
        profileUrl: String,
        authenticated: Boolean
    ): Result<InstagramProfileCatalog> = withContext(Dispatchers.Main) {
        runCatching {
            withTimeout(BASE_TIMEOUT_MS) {
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
            withTimeout(GRID_TIMEOUT_MS) {
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
            attachHidden(root, webView)

            var finished = false
            var routed = false

            fun cleanup() {
                runCatching { root.removeView(webView) }
                runCatching {
                    webView.stopLoading()
                    webView.removeJavascriptInterface("IYGBridge")
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
                fun onBase(payload: String) {
                    webView.post {
                        if (finished) return@post

                        runCatching {
                            val obj = JSONObject(payload)
                            val path = obj.optString("path")
                            val expected = "/" + username

                            if (path.trimEnd('/').lowercase() != expected.lowercase()) {
                                error("Instagram 웹이 프로필 대신 " + path + " 페이지를 열었습니다.")
                            }

                            if (obj.optBoolean("loginWall", false)) {
                                error("Instagram이 비로그인 요청에 로그인 화면을 표시했습니다.")
                            }

                            val avatarUrl = obj.optString("avatar")
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

                            val displayName = obj.optString("displayName")
                                .ifBlank {
                                    obj.optString("title")
                                        .substringBefore("(@")
                                        .substringBefore(" •")
                                        .trim()
                                }

                            if (displayName.isBlank() && avatar == null) {
                                error("Instagram 프로필 DOM을 읽지 못했습니다.")
                            }

                            InstagramProfileCatalog(
                                username = username,
                                fullName = displayName,
                                biography = obj.optString("description"),
                                isPrivate = obj.optBoolean("private", false),
                                avatar = avatar
                            )
                        }.fold(
                            onSuccess = { catalog ->
                                finished = true
                                cleanup()
                                if (cont.isActive) cont.resume(catalog)
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

            webView.addJavascriptInterface(Bridge(), "IYGBridge")
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (finished || routed) return
                    routed = true

                    view.evaluateJavascript(
                        """
                        (() => {
                          try {
                            history.pushState({}, '', '/$username/');
                            window.dispatchEvent(new PopStateEvent('popstate', { state: {} }));

                            setTimeout(() => {
                              try {
                                const bodyText = document.body?.innerText || '';
                                const lower = bodyText.toLowerCase();
                                const path = window.location.pathname || '';

                                const loginWall =
                                  path.startsWith('/accounts/login') ||
                                  lower.includes('log in to see photos and videos') ||
                                  lower.includes('instagram에 로그인');

                                const isPrivate =
                                  lower.includes('this account is private') ||
                                  lower.includes('계정은 비공개') ||
                                  lower.includes('비공개 계정');

                                const title =
                                  document.querySelector('meta[property="og:title"]')?.content ||
                                  document.title || '';

                                const description =
                                  document.querySelector('meta[property="og:description"]')?.content ||
                                  document.querySelector('meta[name="description"]')?.content || '';

                                const header = document.querySelector('header');
                                const avatar =
                                  header?.querySelector('img[src]')?.currentSrc ||
                                  header?.querySelector('img[src]')?.src ||
                                  document.querySelector('meta[property="og:image"]')?.content ||
                                  '';

                                const displayName =
                                  header?.querySelector('h1')?.innerText ||
                                  header?.querySelector('h2')?.innerText ||
                                  '';

                                IYGBridge.onBase(JSON.stringify({
                                  path,
                                  title,
                                  description,
                                  avatar,
                                  displayName,
                                  private: isPrivate,
                                  loginWall
                                }));
                              } catch (inner) {
                                IYGBridge.onError(String(inner));
                              }
                            }, 3500);
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
                    if (request.isForMainFrame) {
                        fail("Instagram 웹 로드 실패: " + error.description)
                    }
                }
            }

            cont.invokeOnCancellation {
                webView.post { cleanup() }
            }

            webView.loadUrl("https://www.instagram.com/")
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
            attachHidden(root, webView)

            var finished = false
            var routed = false

            fun cleanup() {
                runCatching { root.removeView(webView) }
                runCatching {
                    webView.stopLoading()
                    webView.removeJavascriptInterface("IYGBridge")
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
                fun onProgress(count: Int) {
                    webView.post {
                        if (!finished) onProgress(count)
                    }
                }

                @JavascriptInterface
                fun onGrid(payload: String) {
                    webView.post {
                        if (finished) return@post

                        runCatching {
                            val rootObj = JSONObject(payload)
                            val path = rootObj.optString("path")
                            val loginWall = rootObj.optBoolean("loginWall", false)
                            val expected = if (reelsOnly) {
                                "/" + username + "/reels"
                            } else {
                                "/" + username
                            }

                            if (path.trimEnd('/').lowercase() != expected.lowercase()) {
                                error("Instagram 웹이 " + path + " 페이지에 머물렀습니다.")
                            }
                            if (loginWall) {
                                error("Instagram이 비로그인 요청에 로그인 화면을 표시했습니다.")
                            }

                            val array = rootObj.optJSONArray("items") ?: JSONArray()
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

            webView.addJavascriptInterface(Bridge(), "IYGBridge")
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (finished || routed) return
                    routed = true

                    val route = if (reelsOnly) {
                        "/" + username + "/reels/"
                    } else {
                        "/" + username + "/"
                    }

                    val script =
                        """
                        (() => {
                          try {
                            history.pushState({}, '', '$route');
                            window.dispatchEvent(new PopStateEvent('popstate', { state: {} }));

                            setTimeout(() => {
                        """.trimIndent() +
                        gridCollectorScript(reelsOnly) +
                        """
                            }, 3500);
                          } catch (e) {
                            IYGBridge.onError(String(e));
                          }
                        })();
                        """.trimIndent()

                    view.evaluateJavascript(script, null)
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

            webView.loadUrl("https://www.instagram.com/")
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

    private fun attachHidden(root: ViewGroup, webView: WebView) {
        webView.alpha = 0.01f
        webView.translationX = -1400f
        webView.translationY = -2400f
        root.addView(webView, FrameLayout.LayoutParams(1080, 2200))
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

    private fun gridCollectorScript(reelsOnly: Boolean): String {
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
                    preview,
                    title
                  });
                });
              }

              function isLoginWall() {
                const path = window.location.pathname || '';
                const text = (document.body?.innerText || '').toLowerCase();
                return path.startsWith('/accounts/login') ||
                  text.includes('log in to see photos and videos') ||
                  text.includes('instagram에 로그인');
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

                    if (stable >= 12) break;

                    window.scrollTo(0, document.documentElement.scrollHeight);
                    await new Promise((resolve) => setTimeout(resolve, 500));
                  }

                  collect();

                  IYGBridge.onGrid(JSON.stringify({
                    path: window.location.pathname || '',
                    loginWall: isLoginWall(),
                    items: Array.from(found.values())
                  }));
                } catch (e) {
                  IYGBridge.onError(String(e));
                }
              }

              run();
            })();
        """.trimIndent()
    }
}
