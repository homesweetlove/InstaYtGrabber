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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramCatalogItem
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramCatalogSection
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramDirectFileEngine
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramEngine
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramMediaType
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramOutputType
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramProfileCatalog
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramProfileCatalogEngine
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramSession
import kotlinx.coroutines.launch

private enum class InstagramSection(
    val label: String,
    val catalogSection: InstagramCatalogSection
) {
    STORY("스토리", InstagramCatalogSection.STORIES),
    HIGHLIGHT("하이라이트", InstagramCatalogSection.HIGHLIGHTS),
    POST("게시글", InstagramCatalogSection.POSTS),
    REEL("릴스", InstagramCatalogSection.REELS)
}

@Composable
fun InstagramProfileBrowserScreen(initialUrl: String) {
    var url by remember { mutableStateOf(initialUrl) }
    var section by remember { mutableStateOf(InstagramSection.STORY) }
    var profile by remember { mutableStateOf<InstagramProfileCatalog?>(null) }
    var stories by remember { mutableStateOf<List<InstagramCatalogItem>>(emptyList()) }
    var highlights by remember { mutableStateOf<List<InstagramCatalogItem>>(emptyList()) }
    var posts by remember { mutableStateOf<List<InstagramCatalogItem>>(emptyList()) }
    var reels by remember { mutableStateOf<List<InstagramCatalogItem>>(emptyList()) }
    var loadedSections by remember { mutableStateOf(setOf<InstagramSection>()) }
    var loadingSections by remember { mutableStateOf(setOf<InstagramSection>()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var baseLoading by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Instagram 프로필 링크를 넣고 불러오기를 누르세요.") }
    var loginDialog by remember { mutableStateOf(false) }
    var loggedIn by remember { mutableStateOf(InstagramSession.isLoggedIn()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun sectionItems(target: InstagramSection): List<InstagramCatalogItem> =
        when (target) {
            InstagramSection.STORY -> stories
            InstagramSection.HIGHLIGHT -> highlights
            InstagramSection.POST -> posts
            InstagramSection.REEL -> reels
        }

    fun loadSection(
        target: InstagramSection,
        base: InstagramProfileCatalog? = profile,
        force: Boolean = false
    ) {
        val current = base ?: return
        if (!force && (target in loadedSections || target in loadingSections)) return

        scope.launch {
            loadingSections = loadingSections + target
            status = target.label + " 전체 불러오는 중…"

            InstagramProfileCatalogEngine.loadSection(
                context = context,
                profileUrl = url,
                section = target.catalogSection,
                isPrivate = current.isPrivate,
                onProgress = { count ->
                    status = target.label + " " + count + "개 찾는 중…"
                }
            ).fold(
                onSuccess = { items ->
                    when (target) {
                        InstagramSection.STORY -> stories = items
                        InstagramSection.HIGHLIGHT -> highlights = items
                        InstagramSection.POST -> posts = items
                        InstagramSection.REEL -> reels = items
                    }
                    loadedSections = loadedSections + target
                    status = target.label + " " + items.size + "개 불러옴"
                },
                onFailure = { error ->
                    status = target.label + " 불러오기 실패 · " +
                        (error.message ?: error.javaClass.simpleName)
                }
            )

            loadingSections = loadingSections - target
        }
    }

    fun loadProfile() {
        scope.launch {
            baseLoading = true
            profile = null
            stories = emptyList()
            highlights = emptyList()
            posts = emptyList()
            reels = emptyList()
            loadedSections = emptySet()
            loadingSections = emptySet()
            selected = emptySet()
            section = InstagramSection.STORY
            status = "프로필 정보 불러오는 중…"

            InstagramProfileCatalogEngine.loadBase(context, url).fold(
                onSuccess = { loaded ->
                    profile = loaded
                    status = "@" + loaded.username + " 프로필 불러옴"
                    loadSection(InstagramSection.STORY, loaded)
                },
                onFailure = { error ->
                    status = "프로필 불러오기 실패 · " +
                        (error.message ?: error.javaClass.simpleName)
                }
            )

            baseLoading = false
        }
    }

    val visibleItems = buildList {
        if (section == InstagramSection.STORY) {
            profile?.avatar?.let(::add)
        }
        addAll(sectionItems(section))
    }

    val allItems = buildList {
        profile?.avatar?.let(::add)
        addAll(stories)
        addAll(highlights)
        addAll(posts)
        addAll(reels)
    }

    val currentSectionLoading = section in loadingSections

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Instagram", style = MaterialTheme.typography.headlineSmall)
        Text(
            "프로필은 먼저 바로 표시하고, 스토리·하이라이트·게시글·릴스는 탭별로 전체 목록을 불러옵니다.",
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
                enabled = url.isNotBlank() && !baseLoading && !downloading,
                onClick = { loadProfile() }
            ) {
                Text(if (baseLoading) "불러오는 중…" else "프로필 불러오기")
            }

            OutlinedButton(
                enabled = !baseLoading && !downloading,
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

        if (baseLoading || currentSectionLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        profile?.let { current ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    current.avatar?.let {
                        AsyncImage(
                            model = it.previewUrl,
                            contentDescription = "프로필 사진",
                            modifier = Modifier.size(64.dp),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = current.fullName.ifBlank { "@" + current.username },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "@" + current.username +
                                if (current.isPrivate) " · 비공개" else " · 공개",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (current.biography.isNotBlank()) {
                            Text(
                                text = current.biography,
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
                        onClick = {
                            section = item
                            loadSection(item)
                        },
                        label = { Text(item.label) }
                    )
                }
            }

            Text(
                when {
                    currentSectionLoading -> section.label + " 전체 불러오는 중…"
                    section !in loadedSections -> section.label + " 탭을 눌러 전체 목록 불러오기"
                    else -> section.label + " " + sectionItems(section).size + "개"
                },
                style = MaterialTheme.typography.labelMedium
            )
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
                enabled = profile != null && !currentSectionLoading && !downloading,
                onClick = {
                    if (section in loadedSections) {
                        loadSection(section, force = true)
                    } else {
                        loadSection(section)
                    }
                }
            ) {
                Text(if (section in loadedSections) "새로고침" else "전체 불러오기")
            }

            OutlinedButton(
                enabled = visibleItems.isNotEmpty() && !downloading,
                onClick = {
                    val keys = visibleItems.map { it.selectionKey() }.toSet()
                    selected = if (keys.all { it in selected }) selected - keys else selected + keys
                }
            ) {
                Text("전체 선택")
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

                            val result = if (item.kind == "avatar") {
                                InstagramDirectFileEngine.download(
                                    url = item.targetUrl,
                                    folderName = "profile/media",
                                    baseName = "profile_" + (profile?.username ?: "instagram")
                                )
                            } else {
                                InstagramEngine.download(
                                    url = item.targetUrl,
                                    type = type,
                                    output = InstagramOutputType.MEDIA,
                                    allowAuthenticatedFallback = profile?.isPrivate == true
                                ) { progress ->
                                    if (progress.percent > 0f) {
                                        status = (index + 1).toString() + "/" + chosen.size +
                                            " · " + progress.percent.toInt() + "%"
                                    }
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
                    else "저장 (" + selected.size + ")"
                )
            }
        }

        Text(status, style = MaterialTheme.typography.bodySmall)
        Text(
            if (loggedIn) {
                "공개 프로필은 로그인 쿠키 없이 조회·다운로드하고, 비공개 프로필만 연결한 세션을 사용합니다."
            } else {
                "공개 프로필은 비로그인으로 조회합니다."
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
