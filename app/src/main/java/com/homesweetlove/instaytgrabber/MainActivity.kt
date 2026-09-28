package com.homesweetlove.instaytgrabber

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramEngine
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramMediaType
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramOutputType
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramSession
import com.homesweetlove.instaytgrabber.feature.youtube.YouTubeEngine
import com.homesweetlove.instaytgrabber.feature.youtube.YouTubeMediaType
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 100)
        }
        val sharedUrl = if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        } else {
            ""
        }

        setContent {
            InstaYtGrabberApp(sharedUrl)
        }
    }
}

private enum class PlatformTab {
    INSTAGRAM,
    YOUTUBE
}

@Composable
private fun InstaYtGrabberApp(sharedUrl: String) {
    val initialTab = when {
        InstagramEngine.accepts(sharedUrl) -> PlatformTab.INSTAGRAM
        YouTubeEngine.accepts(sharedUrl) -> PlatformTab.YOUTUBE
        else -> PlatformTab.INSTAGRAM
    }
    var tab by remember { mutableStateOf(initialTab) }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                    Text("InstaYtGrabber", style = MaterialTheme.typography.headlineMedium)
                    Text("광고 없이 필요한 것만.", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "v" + BuildConfig.VERSION_NAME + " · WEB-RUNTIME",
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                TabRow(selectedTabIndex = tab.ordinal) {
                    Tab(
                        selected = tab == PlatformTab.INSTAGRAM,
                        onClick = { tab = PlatformTab.INSTAGRAM },
                        text = { Text("Instagram") }
                    )
                    Tab(
                        selected = tab == PlatformTab.YOUTUBE,
                        onClick = { tab = PlatformTab.YOUTUBE },
                        text = { Text("YouTube") }
                    )
                }

                when (tab) {
                    PlatformTab.INSTAGRAM -> InstagramProfileBrowserScreen(
                        initialUrl = sharedUrl.takeIf(InstagramEngine::accepts).orEmpty()
                    )
                    PlatformTab.YOUTUBE -> YouTubeScreen(
                        initialUrl = sharedUrl.takeIf(YouTubeEngine::accepts).orEmpty()
                    )
                }
            }
        }
    }
}

@Composable
private fun InstagramScreen(initialUrl: String) {
    var url by remember { mutableStateOf(initialUrl) }
    var type by remember { mutableStateOf(InstagramMediaType.POST) }
    var output by remember { mutableStateOf(InstagramOutputType.MEDIA) }
    var status by remember { mutableStateOf("링크를 붙여 넣어 주세요.") }
    var progress by remember { mutableFloatStateOf(0f) }
    var loginDialog by remember { mutableStateOf(false) }
    var loggedIn by remember { mutableStateOf(InstagramSession.isLoggedIn()) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Instagram", style = MaterialTheme.typography.headlineSmall)
        Text(
            "공개 콘텐츠는 로그인 상태여도 항상 익명으로 먼저 처리합니다. 익명 접근이 실패한 경우에만 연결된 로그인 세션으로 재시도합니다.",
            style = MaterialTheme.typography.bodyMedium
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InstagramMediaType.entries.forEach { item ->
                FilterChip(
                    selected = type == item,
                    onClick = { type = item },
                    label = { Text(item.label) }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InstagramOutputType.entries.forEach { item ->
                FilterChip(
                    selected = output == item,
                    onClick = { output = item },
                    label = { Text(item.label) }
                )
            }
        }

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Instagram 링크") }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { loginDialog = true }) {
                Text(if (loggedIn) "세션 갱신" else "Instagram 로그인")
            }
            if (loggedIn) {
                OutlinedButton(
                    onClick = {
                        InstagramSession.clear()
                        loggedIn = false
                        status = "로그아웃 및 임시 세션 삭제 완료"
                    }
                ) {
                    Text("로그아웃")
                }
            }
        }

        Text(
            if (loggedIn) "로그인됨 · 공개 콘텐츠는 익명 우선 · 히스토리 저장 안 함"
            else "비로그인 · 공개 콘텐츠 익명 처리"
        )

        LinearProgressIndicator(
            progress = progress.coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = url.isNotBlank() && !running,
            onClick = {
                scope.launch {
                    running = true
                    progress = 0f
                    status = "추출 준비 중"
                    val result = runCatching {
                        InstagramEngine.download(url, type, output) {
                            progress = (it.percent / 100f).coerceIn(0f, 1f)
                            status = when (it.line) {
                                "anonymous" -> "공개 콘텐츠 · 비로그인으로 확인 중"
                                "authenticated" -> "익명 접근 실패 · 로그인 세션으로 재시도 중"
                                else -> "처리 중 ${it.percent.toInt()}%"
                            }
                        }.getOrThrow()
                    }
                    status = result.fold(
                        onSuccess = { "완료 · Downloads/InstaYtGrabber/Instagram/${type.folder}/${output.folder}" },
                        onFailure = { "실패 · ${it.message ?: it.javaClass.simpleName}" }
                    )
                    running = false
                }
            }
        ) {
            Text(
                when {
                    running -> "처리 중…"
                    output == InstagramOutputType.AUDIO -> "${type.label} MP3 추출"
                    else -> "${type.label} 저장"
                }
            )
        }

        Text(status, style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text(
            "최근 URL이나 다운로드 목록은 저장하지 않습니다. 공개 콘텐츠 처리에는 Instagram 로그인 쿠키를 전송하지 않으며, 스토리도 앱 안에서 재생·미리보기하지 않습니다.",
            style = MaterialTheme.typography.bodySmall
        )
    }

    if (loginDialog) {
        InstagramLoginDialog(
            onDismiss = { loginDialog = false },
            onUseSession = {
                loggedIn = InstagramSession.captureFromWebView()
                status = if (loggedIn) {
                    "Instagram 임시 세션 연결 완료"
                } else {
                    "로그인 세션을 찾지 못했습니다."
                }
                loginDialog = false
            }
        )
    }
}

@Composable
private fun InstagramLoginDialog(
    onDismiss: () -> Unit,
    onUseSession: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Instagram 로그인", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) { Text("닫기") }
                        Button(onClick = onUseSession) { Text("세션 사용") }
                    }
                }

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { context ->
                        CookieManager.getInstance().setAcceptCookie(true)
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = WebViewClient()
                            loadUrl("https://www.instagram.com/accounts/login/")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun YouTubeScreen(initialUrl: String) {
    var url by remember { mutableStateOf(initialUrl) }
    var type by remember { mutableStateOf(YouTubeMediaType.VIDEO) }
    var status by remember { mutableStateOf("링크를 붙여 넣어 주세요.") }
    var progress by remember { mutableFloatStateOf(0f) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("YouTube", style = MaterialTheme.typography.headlineSmall)
        Text(
            "영상 저장 · MP3 음원 추출 · 썸네일 저장을 분리해서 처리합니다.",
            style = MaterialTheme.typography.bodyMedium
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            YouTubeMediaType.entries.forEach { item ->
                FilterChip(
                    selected = type == item,
                    onClick = { type = item },
                    label = { Text(item.label) }
                )
            }
        }

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("YouTube 링크") }
        )

        LinearProgressIndicator(
            progress = progress.coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = url.isNotBlank() && !running,
            onClick = {
                scope.launch {
                    running = true
                    progress = 0f
                    status = "추출 준비 중"
                    val result = runCatching {
                        YouTubeEngine.download(url, type) {
                            progress = (it.percent / 100f).coerceIn(0f, 1f)
                            status = when {
                                it.percent > 0f -> "처리 중 " + it.percent.toInt() + "%"
                                it.line.isNotBlank() -> it.line
                                else -> "YouTube 엔진 준비 중"
                            }
                        }.getOrThrow()
                    }
                    status = result.fold(
                        onSuccess = { "완료 · Downloads/InstaYtGrabber/YouTube/${type.folder}" },
                        onFailure = { "실패 · ${it.message ?: it.javaClass.simpleName}" }
                    )
                    running = false
                }
            }
        ) {
            Text(
                when {
                    running -> "처리 중…"
                    type == YouTubeMediaType.AUDIO -> "MP3 추출"
                    type == YouTubeMediaType.THUMBNAIL -> "썸네일 저장"
                    else -> "영상 저장"
                }
            )
        }

        Text(status, style = MaterialTheme.typography.bodySmall)
    }
}
