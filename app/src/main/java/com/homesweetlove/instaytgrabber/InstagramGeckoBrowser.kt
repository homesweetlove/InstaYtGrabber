package com.homesweetlove.instaytgrabber

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.homesweetlove.instaytgrabber.core.DiagnosticLog
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramDirectFileEngine
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramMediaLink
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramMediaUrlPolicy
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramProfileInput
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramRateLimit
import com.homesweetlove.instaytgrabber.ui.Toss
import com.homesweetlove.instaytgrabber.ui.TossType
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension

private object BrowserRuntime {
    // A GeckoRuntime may only be created once per app process. Gecko cookies
    // belong to this browser and persist independently from Android WebView.
    private var runtime: GeckoRuntime? = null

    fun get(context: Context): GeckoRuntime {
        return runtime ?: GeckoRuntime.create(context.applicationContext).also {
            runtime = it
        }
    }
}

private data class BrowserMedia(val url: String, val kind: String)

@Composable
fun InstagramGeckoBrowser(
    initialProfile: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf("Firefox 엔진 시작 중…") }
    var currentPage by remember { mutableStateOf("") }
    var media by remember { mutableStateOf<List<BrowserMedia>>(emptyList()) }
    var downloading by remember { mutableStateOf(false) }
    var account by remember { mutableStateOf(initialProfile) }
    val session = remember { GeckoSession() }

    DisposableEffect(session) {
        try {
            val runtime = BrowserRuntime.get(context)
            session.contentDelegate = object : GeckoSession.ContentDelegate {}
            session.progressDelegate = object : GeckoSession.ProgressDelegate {
                override fun onPageStart(session: GeckoSession, url: String) {
                    state = "페이지 불러오는 중…"
                    currentPage = url
                    media = emptyList()
                }

                override fun onPageStop(session: GeckoSession, success: Boolean) {
                    state = if (success) {
                        "페이지가 열렸습니다 · 미디어를 찾는 중"
                    } else {
                        DiagnosticLog.record(
                            "IG-GECKO-LOAD", "내장 Firefox 웹페이지",
                            "웹 콘텐츠를 표시하지 못했습니다."
                        )
                        "웹페이지를 열지 못했습니다 · 다시 시도해 주세요"
                    }
                }
            }
            session.open(runtime)

            runtime.webExtensionController.ensureBuiltIn(
                "resource://android/assets/instagram_gecko/",
                "instagram-view@instaytgrabber.local"
            ).accept(
                { extension ->
                    session.webExtensionController.setMessageDelegate(
                        extension,
                        object : WebExtension.MessageDelegate {
                            override fun onMessage(
                                nativeApp: String,
                                message: Any,
                                sender: WebExtension.MessageSender
                            ): GeckoResult<Any>? {
                                if (sender.session != session || message !is JSONObject) return null
                                if (message.optString("type") != "media") return null
                                val page = message.optString("page")
                                val host = runCatching {
                                    java.net.URI(page).host.orEmpty().lowercase()
                                }.getOrDefault("")
                                if (host != "instagram.com" &&
                                    !host.endsWith(".instagram.com")) return null
                                currentPage = page
                                val values = message.optJSONArray("items")
                                media = (0 until (values?.length() ?: 0)).mapNotNull { index ->
                                    val item = values?.optJSONObject(index) ?: return@mapNotNull null
                                    val url = item.optString("url")
                                    if (!InstagramMediaUrlPolicy.isAllowed(url)) return@mapNotNull null
                                    BrowserMedia(url, item.optString("kind"))
                                }.distinctBy { it.url }.take(12)
                                if (media.isNotEmpty()) {
                                    state = "현재 게시물에서 미디어 ${media.size}개 발견"
                                }
                                return null
                            }
                        },
                        "instaytgrabber"
                    )
                },
                { error ->
                    DiagnosticLog.failure("IG-GECKO-EXT", "내장 미디어 추출", error)
                    state = "미디어 확장 설정 실패 · 오류 기록을 확인해 주세요"
                }
            )

            val initial = InstagramMediaLink.parse(initialProfile)?.canonicalUrl
                ?: runCatching { InstagramProfileInput.normalize(initialProfile) }
                    .getOrDefault("https://www.instagram.com/accounts/login/")
            session.loadUri(initial)
        } catch (error: Exception) {
            DiagnosticLog.failure("IG-GECKO-INIT", "Firefox 내장 브라우저", error)
            state = "내장 브라우저 초기화 실패 · 오류 기록 확인"
        }
        onDispose {
            if (session.isOpen) session.close()
        }
    }

    fun openProfile() {
        val address = runCatching { InstagramProfileInput.normalize(account) }
            .getOrElse {
                state = "Instagram 아이디를 확인해 주세요"
                return
            }
        session.loadUri(address)
    }

    fun saveMedia() {
        if (downloading) return
        if (media.isEmpty()) {
            state = "현재 페이지에서 미디어를 찾지 못했습니다. 게시물이나 릴스를 열어 주세요."
            return
        }
        if (InstagramRateLimit.shouldSkipMedia()) {
            state = "Instagram CDN 요청 제한 중 · ${InstagramRateLimit.mediaRemainingMinutes()}분 뒤 시도"
            return
        }
        val selected = media.toList()
        scope.launch {
            downloading = true
            var savedCount = 0
            for ((index, item) in selected.withIndex()) {
                state = "현재 게시물 저장 중 ${index + 1}/${selected.size}"
                val result = InstagramDirectFileEngine.download(
                    url = item.url,
                    folderName = "browser/media",
                    baseName = "instagram_${System.currentTimeMillis()}_${index}"
                )
                result.onSuccess { savedCount++ }
                result.onFailure { error ->
                    DiagnosticLog.failure("IG-GECKO-SAVE", "내장 브라우저 미디어 저장", error)
                    if (error.message.orEmpty().contains("429")) {
                        InstagramRateLimit.onMedia429()
                    }
                }
            }
            state = if (savedCount == selected.size) {
                "${savedCount}개 저장 완료 · Downloads/InstaYtGrabber/Instagram"
            } else {
                "${savedCount}/${selected.size}개 저장 · 실패 원인은 오류 기록에서 확인"
            }
            downloading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.98f).fillMaxHeight(0.96f),
            shape = RoundedCornerShape(22.dp),
            color = Color.White
        ) {
            Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Instagram · 앱 내부 브라우저", style = TossType.Section, color = Toss.Text1)
                    TextButton(onClick = onDismiss) { Text("닫기") }
                }
                Text(
                    state,
                    style = TossType.Caption,
                    color = Toss.Text3,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = {
                        session.loadUri("https://www.instagram.com/accounts/login/")
                    }) { Text("로그인") }
                    TextButton(onClick = { openProfile() }) { Text("프로필") }
                    TextButton(onClick = {
                        if (currentPage.isNotBlank()) session.reload()
                    }) { Text("새로고침") }
                    TextButton(
                        enabled = !downloading && media.isNotEmpty(),
                        onClick = { saveMedia() }
                    ) { Text(if (downloading) "저장 중" else "현재 게시물 저장") }
                }
                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    factory = { ctx ->
                        GeckoView(ctx).apply {
                            setSession(session)
                        }
                    }
                )
                Text(
                    "앱 안에서 로그인하고 게시물·릴스를 열어 저장하세요. " +
                        "로그인 쿠키는 이 내장 브라우저에만 보관됩니다. " +
                        "공식 Instagram에서 제한하는 콘텐츠는 저장되지 않을 수 있습니다.",
                    style = TossType.Caption,
                    color = Toss.Text3,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}
