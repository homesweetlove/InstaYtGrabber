package com.homesweetlove.instaytgrabber

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramCatalogItem
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramEngine
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramMediaType
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramOutputType
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramProfileCatalog
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramProfileCatalogEngine
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramSession
import kotlinx.coroutines.launch

private enum class InstagramSection(val label: String) {
    STORY("스토리"),
    HIGHLIGHT("하이라이트"),
    POST("게시글"),
    REEL("릴스")
}

@Composable
fun InstagramProfileBrowserScreen(initialUrl: String) {
    var url by remember { mutableStateOf(initialUrl) }
    var section by remember { mutableStateOf(InstagramSection.STORY) }
    var catalog by remember { mutableStateOf<InstagramProfileCatalog?>(null) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var loading by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Instagram 프로필 링크를 넣고 불러오기를 누르세요.") }
    var loginDialog by remember { mutableStateOf(false) }
    var loggedIn by remember { mutableStateOf(InstagramSession.isLoggedIn()) }
    val scope = rememberCoroutineScope()

    fun loadProfile() {
        scope.launch {
            loading = true
            catalog = null
            selected = emptySet()
            status = "프로필 정보와 미디어 목록을 불러오는 중…"
            InstagramProfileCatalogEngine.load(url).fold(
                onSuccess = {
                    catalog = it
                    status = "@" + it.username +
                        " · 스토리 " + it.stories.size +
                        " · 하이라이트 " + it.highlights.size +
                        " · 게시글 " + it.posts.size +
                        " · 릴스 " + it.reels.size
                },
                onFailure = {
                    status = "불러오기 실패 · " + (it.message ?: it.javaClass.simpleName)
                }
            )
            loading = false
        }
    }

    val visibleItems = catalog?.let { current ->
        when (section) {
            InstagramSection.STORY -> listOfNotNull(current.avatar) + current.stories
            InstagramSection.HIGHLIGHT -> current.highlights
            InstagramSection.POST -> current.posts
            InstagramSection.REEL -> current.reels
        }
    }.orEmpty()

    val allItems = catalog?.let {
        listOfNotNull(it.avatar) + it.stories + it.highlights + it.posts + it.reels
    }.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Instagram", style = MaterialTheme.typography.headlineSmall)
        Text(
            "프로필 링크 하나로 목록을 먼저 불러온 뒤 원하는 항목만 선택해서 최고 화질로 저장합니다.",
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Instagram 프로필 링크") },
            placeholder = { Text("https://www.instagram.com/username/") }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = url.isNotBlank() && !loading && !downloading,
                onClick = { loadProfile() }
            ) {
                Text(if (loading) "불러오는 중…" else "프로필 불러오기")
            }

            OutlinedButton(
                enabled = !loading && !downloading,
                onClick = { loginDialog = true }
            ) {
                Text(if (loggedIn) "로그인 갱신" else "Instagram 로그인")
            }

            if (loggedIn) {
                TextButton(
                    onClick = {
                        InstagramSession.clear()
                        loggedIn = false
                        status = "로그인 세션 삭제 완료"
                    }
                ) {
                    Text("로그아웃")
                }
            }
        }

        if (loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        catalog?.let { profile ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    profile.avatar?.let {
                        AsyncImage(
                            model = it.previewUrl,
                            contentDescription = "프로필 사진",
                            modifier = Modifier.size(64.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.fullName.ifBlank { "@" + profile.username },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "@" + profile.username +
                                if (profile.isPrivate) " · 비공개" else " · 공개",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (profile.biography.isNotBlank()) {
                            Text(
                                text = profile.biography,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InstagramSection.entries.forEach { item ->
                    FilterChip(
                        selected = section == item,
                        onClick = { section = item },
                        label = { Text(item.label) }
                    )
                }
            }

            Text(
                when (section) {
                    InstagramSection.STORY -> "프로필 사진 + 현재 스토리"
                    InstagramSection.HIGHLIGHT -> "하이라이트 묶음"
                    InstagramSection.POST -> "최근 게시글 24개"
                    InstagramSection.REEL -> "최근 릴스 24개"
                },
                style = MaterialTheme.typography.labelMedium
            )

            if (visibleItems.isEmpty()) {
                Text(
                    "표시할 항목이 없습니다. 스토리/하이라이트는 로그인이 필요한 경우가 있습니다.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = visibleItems,
                key = { it.selectionKey() }
            ) { item ->
                InstagramCatalogRow(
                    item = item,
                    checked = item.selectionKey() in selected,
                    onToggle = {
                        val key = item.selectionKey()
                        selected = if (key in selected) selected - key else selected + key
                    }
                )
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                enabled = visibleItems.isNotEmpty() && !downloading,
                onClick = {
                    val keys = visibleItems.map { it.selectionKey() }.toSet()
                    selected = if (keys.all { it in selected }) selected - keys else selected + keys
                }
            ) {
                Text("현재 탭 전체 선택")
            }

            Button(
                enabled = selected.isNotEmpty() && !downloading,
                onClick = {
                    scope.launch {
                        downloading = true
                        val chosen = allItems.filter { it.selectionKey() in selected }
                        var success = 0
                        var failed = 0

                        chosen.forEachIndexed { index, item ->
                            status = (index + 1).toString() + "/" + chosen.size +
                                " · " + item.title.ifBlank { item.kind } + " 저장 중"

                            val type = when (item.kind) {
                                "avatar", "story" -> InstagramMediaType.STORY
                                "highlight" -> InstagramMediaType.HIGHLIGHT
                                "reel" -> InstagramMediaType.REEL
                                else -> InstagramMediaType.POST
                            }

                            val result = InstagramEngine.download(
                                url = item.targetUrl,
                                type = type,
                                output = InstagramOutputType.MEDIA
                            ) { progress ->
                                if (progress.percent > 0f) {
                                    status = (index + 1).toString() + "/" + chosen.size +
                                        " · " + progress.percent.toInt() + "%"
                                }
                            }

                            if (result.isSuccess) success++ else failed++
                        }

                        status = "완료 · 성공 " + success + "개" +
                            if (failed > 0) " · 실패 " + failed + "개" else ""
                        downloading = false
                    }
                }
            ) {
                Text(
                    if (downloading) "저장 중…"
                    else "선택 항목 저장 (" + selected.size + ")"
                )
            }
        }

        Text(status, style = MaterialTheme.typography.bodySmall)
        Text(
            if (loggedIn) {
                "공개 콘텐츠는 로그인 쿠키 없이 먼저 조회하고, 필요한 항목만 로그인 세션으로 재시도합니다."
            } else {
                "공개 콘텐츠는 비로그인 상태로 조회합니다."
            },
            style = MaterialTheme.typography.bodySmall
        )
    }

    if (loginDialog) {
        ProfileInstagramLoginDialog(
            onDismiss = { loginDialog = false },
            onUseSession = {
                loggedIn = InstagramSession.captureFromWebView()
                status = if (loggedIn) {
                    "Instagram 임시 로그인 연결 완료"
                } else {
                    "로그인 세션을 찾지 못했습니다."
                }
                loginDialog = false
                if (loggedIn && url.isNotBlank()) loadProfile()
            }
        )
    }
}

@Composable
private fun InstagramCatalogRow(
    item: InstagramCatalogItem,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = item.previewUrl,
                contentDescription = item.title,
                modifier = Modifier.size(82.dp),
                contentScale = ContentScale.Crop
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title.ifBlank {
                        when (item.kind) {
                            "avatar" -> "프로필 사진"
                            "story" -> "스토리"
                            "highlight" -> "하이라이트"
                            "reel" -> "릴스"
                            else -> "게시글"
                        }
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = (if (item.isVideo) "영상" else "사진") +
                        if (item.count > 1) " · " + item.count + "개" else "",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Checkbox(
                checked = checked,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

private fun InstagramCatalogItem.selectionKey(): String =
    kind + "|" + id + "|" + targetUrl

@Composable
private fun ProfileInstagramLoginDialog(
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
