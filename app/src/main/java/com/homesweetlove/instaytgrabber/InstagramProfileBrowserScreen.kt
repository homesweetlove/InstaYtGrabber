package com.homesweetlove.instaytgrabber

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.homesweetlove.instaytgrabber.core.DiagnosticLog
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramCatalogItem
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramCatalogSection
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramDirectFileEngine
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramEngine
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramInstaloaderMediaEngine
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramMediaType
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramOutputType
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramProfileCatalog
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramProfileCatalogEngine
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramProfileInput
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramSession
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramWebMediaEngine
import com.homesweetlove.instaytgrabber.ui.BottomCta
import com.homesweetlove.instaytgrabber.ui.ChevronGlyph
import com.homesweetlove.instaytgrabber.ui.IconTile
import com.homesweetlove.instaytgrabber.ui.InstagramGlyph
import com.homesweetlove.instaytgrabber.ui.Pill
import com.homesweetlove.instaytgrabber.ui.PlayGlyph
import com.homesweetlove.instaytgrabber.ui.SectionTitle
import com.homesweetlove.instaytgrabber.ui.SmallSpinner
import com.homesweetlove.instaytgrabber.ui.TaskOverlay
import com.homesweetlove.instaytgrabber.ui.TaskState
import com.homesweetlove.instaytgrabber.ui.Toss
import com.homesweetlove.instaytgrabber.ui.TossButton
import com.homesweetlove.instaytgrabber.ui.TossButtonStyle
import com.homesweetlove.instaytgrabber.ui.TossCard
import com.homesweetlove.instaytgrabber.ui.TossTextField
import com.homesweetlove.instaytgrabber.ui.TossType
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

private const val IDLE_STATUS = "공개 프로필은 로그인 없이 익명으로 조회해요"

@Composable
fun InstagramProfileBrowserScreen(
    initialUrl: String,
    header: @Composable () -> Unit = {}
) {
    var url by remember { mutableStateOf(initialUrl) }
    var resolvedProfileUrl by remember { mutableStateOf("") }
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
    var status by remember { mutableStateOf(IDLE_STATUS) }
    var task by remember { mutableStateOf<TaskState>(TaskState.Idle) }
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
                profileUrl = resolvedProfileUrl.ifBlank { url },
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
                    DiagnosticLog.failure("IG-UI-SECTION", target.label + " 목록 불러오기", error)
                    status = target.label + " 불러오기 실패 · " +
                        (error.message ?: error.javaClass.simpleName)
                }
            )

            loadingSections = loadingSections - target
        }
    }

    fun loadProfile() {
        scope.launch {
            val canonicalUrl = runCatching { InstagramProfileInput.normalize(url) }
                .getOrElse { error ->
                    DiagnosticLog.failure("IG-INPUT-01", "프로필 아이디 확인", error)
                    status = "아이디 확인 실패 · " + (error.message ?: "입력을 확인해 주세요.")
                    return@launch
                }
            resolvedProfileUrl = canonicalUrl
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

            InstagramProfileCatalogEngine.loadBase(context, canonicalUrl).fold(
                onSuccess = { loaded ->
                    profile = loaded
                    status = "@" + loaded.username + " 프로필 불러옴"
                    loadSection(InstagramSection.STORY, loaded)
                },
                onFailure = { error ->
                    DiagnosticLog.failure("IG-UI-PROFILE", "프로필 불러오기", error)
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
    val selectedOrder = selected.toList()

    fun startDownload() {
        scope.launch {
            downloading = true
            val chosen = allItems.filter { it.selectionKey() in selected }
            val title = chosen.size.toString() + "개 저장하고 있어요"
            var success = 0
            var failed = 0
            var lastError = ""

            chosen.forEachIndexed { index, item ->
                val step = (index + 1).toString() + "/" + chosen.size
                val base = index.toFloat() / chosen.size
                task = TaskState.Running(
                    title = title,
                    detail = step + " · " + item.title.ifBlank { kindLabel(item.kind) } + " 저장 중",
                    progress = base
                )

                val type = when (item.kind) {
                    "avatar", "story" -> InstagramMediaType.STORY
                    "highlight" -> InstagramMediaType.HIGHLIGHT
                    "reel" -> InstagramMediaType.REEL
                    else -> InstagramMediaType.POST
                }

                val itemBaseName = (profile?.username ?: "instagram") + "_" +
                    item.kind + "_" + (index + 1)

                val result = when {
                    item.kind == "avatar" -> InstagramDirectFileEngine.download(
                        url = item.targetUrl,
                        folderName = "profile/media",
                        baseName = "profile_" + (profile?.username ?: "instagram")
                    )

                    item.kind == "story" &&
                        item.targetUrl.startsWith("https://") &&
                        !item.targetUrl.contains("instagram.com/stories/") ->
                        InstagramDirectFileEngine.download(
                            url = item.targetUrl,
                            folderName = "story/media",
                            baseName = itemBaseName
                        )

                    item.targetUrl.startsWith("instaloader://") ->
                        InstagramInstaloaderMediaEngine.download(
                            target = item.targetUrl,
                            folderName = item.kind + "/media",
                            baseName = itemBaseName,
                            authenticated = profile?.isPrivate == true ||
                                item.kind == "highlight"
                        ) { progress ->
                            task = TaskState.Running(
                                title = title,
                                detail = step + " · " + progress.line,
                                progress = base + (progress.percent / 100f)
                                    .coerceIn(0f, 1f) / chosen.size
                            )
                        }

                    item.kind == "post" || item.kind == "reel" ->
                        InstagramWebMediaEngine.download(
                            context = context,
                            pageUrl = item.targetUrl,
                            folderName = item.kind + "/media",
                            baseName = itemBaseName,
                            authenticated = profile?.isPrivate == true
                        ) { progress ->
                            task = TaskState.Running(title, step + " · " + progress.line, base)
                        }

                    else -> InstagramEngine.download(
                        url = item.targetUrl,
                        type = type,
                        output = InstagramOutputType.MEDIA,
                        allowAuthenticatedFallback = profile?.isPrivate == true
                    ) { progress ->
                        if (progress.percent > 0f) {
                            task = TaskState.Running(
                                title = title,
                                detail = step + " · " + progress.percent.toInt() + "%",
                                progress = base + (progress.percent / 100f)
                                    .coerceIn(0f, 1f) / chosen.size
                            )
                        }
                    }
                }

                if (result.isSuccess) {
                    success++
                } else {
                    failed++
                    lastError = result.exceptionOrNull()?.message.orEmpty()
                    DiagnosticLog.failure("IG-UI-DOWNLOAD", "선택 항목 다운로드 / " + item.kind, result.exceptionOrNull())
                }
            }

            status = "완료 · 성공 " + success + "개" +
                if (failed > 0) " · 실패 " + failed + "개" else ""
            task = if (success == 0) {
                TaskState.Failed(
                    title = "저장하지 못했어요",
                    detail = lastError.ifBlank { failed.toString() + "개 모두 실패했어요" }
                )
            } else {
                TaskState.Done(
                    title = if (failed == 0) "저장했어요" else "일부만 저장했어요",
                    detail = "성공 " + success + "개" + if (failed > 0) " · 실패 " + failed + "개" else "",
                    location = "다운로드 › InstaYtGrabber › Instagram"
                )
            }
            if (failed == 0) selected = emptySet()
            downloading = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 150.dp)
        ) {
            fullWidth { header() }

            fullWidth {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TossCard {
                        SectionTitle("프로필 아이디 또는 링크")
                        TossTextField(
                            value = url,
                            onValueChange = { url = it },
                            placeholder = "아이디 · @아이디 · instagram.com/아이디",
                            enabled = !baseLoading && !downloading
                        )
                        TossButton(
                            text = when {
                                baseLoading -> "불러오는 중…"
                                profile != null -> "다시 불러오기"
                                else -> "프로필 불러오기"
                            },
                            onClick = { loadProfile() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = url.isNotBlank() && !baseLoading && !downloading,
                            style = if (profile != null) TossButtonStyle.Secondary else TossButtonStyle.Primary
                        )
                        if (profile == null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (baseLoading) SmallSpinner()
                                Text(
                                    status,
                                    style = TossType.Caption,
                                    color = if (status.contains("실패")) Toss.Red else Toss.Text3
                                )
                            }
                        }
                    }

                    AccountCard(
                        loggedIn = loggedIn,
                        enabled = !baseLoading && !downloading,
                        onLogin = { loginDialog = true },
                        onLogout = {
                            InstagramSession.clear()
                            loggedIn = false
                            status = "로그인 세션을 삭제했어요"
                        }
                    )

                    profile?.let { current ->
                        ProfileCard(current)
                    }
                }
            }

            if (profile != null) {
                fullWidth {
                    Column(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                            .background(Color.White)
                    ) {
                        SectionTabs(
                            selected = section,
                            countOf = { item ->
                                if (item in loadedSections) sectionItems(item).size else null
                            },
                            onSelect = { item ->
                                section = item
                                loadSection(item)
                            }
                        )
                        Toolbar(
                            loading = currentSectionLoading,
                            status = status,
                            loaded = section in loadedSections,
                            count = sectionItems(section).size,
                            selectedCount = selected.size,
                            allSelected = visibleItems.isNotEmpty() &&
                                visibleItems.all { it.selectionKey() in selected },
                            enabled = !downloading,
                            onToggleAll = {
                                val keys = visibleItems.map { it.selectionKey() }.toSet()
                                selected = if (keys.all { it in selected }) selected - keys else selected + keys
                            },
                            onRefresh = {
                                if (section in loadedSections) {
                                    loadSection(section, force = true)
                                } else {
                                    loadSection(section)
                                }
                            }
                        )
                    }
                }

                if (visibleItems.isEmpty() && !currentSectionLoading) {
                    fullWidth {
                        Text(
                            if (section in loadedSections) "표시할 " + section.label + " 항목이 없어요"
                            else "목록을 불러오지 못했어요. 새로고침을 눌러 주세요",
                            style = TossType.BodyRegular,
                            color = Toss.Text3,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                                .padding(vertical = 56.dp, horizontal = 24.dp)
                        )
                    }
                }

                items(
                    items = visibleItems,
                    key = { it.selectionKey() }
                ) { item ->
                    val key = item.selectionKey()
                    val order = selectedOrder.indexOf(key)
                    MediaTile(
                        item = item,
                        order = if (order >= 0) order + 1 else null,
                        enabled = !downloading,
                        onToggle = {
                            selected = if (key in selected) selected - key else selected + key
                        }
                    )
                }
            }
        }

        if (profile != null) {
            BottomCta(
                modifier = Modifier.align(Alignment.BottomCenter),
                caption = if (loggedIn) {
                    "공개 프로필은 로그인 쿠키 없이 저장하고, 비공개 프로필만 연결한 세션을 사용해요"
                } else {
                    "공개 프로필은 익명으로 저장해요 · 기록은 남기지 않아요"
                }
            ) {
                TossButton(
                    text = if (selected.isEmpty()) "저장할 항목을 선택해 주세요" else selected.size.toString() + "개 저장하기",
                    onClick = { startDownload() },
                    modifier = Modifier.weight(1f),
                    enabled = selected.isNotEmpty() && !downloading
                )
            }
        }

        TaskOverlay(state = task, onDismiss = { task = TaskState.Idle })
    }

    if (loginDialog) {
        ProfileInstagramLoginDialog(
            onDismiss = { loginDialog = false },
            onUseSession = {
                loggedIn = InstagramSession.captureFromWebView()
                if (!loggedIn) DiagnosticLog.record(
                    "IG-AUTH-01", "Instagram 로그인 연결", "세션 쿠키를 확인하지 못했습니다."
                )
                status = if (loggedIn) {
                    "Instagram 임시 로그인을 연결했어요"
                } else {
                    "로그인 세션을 찾지 못했어요"
                }
                loginDialog = false
                if (loggedIn && url.isNotBlank()) loadProfile()
            }
        )
    }
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

private fun kindLabel(kind: String): String =
    when (kind) {
        "avatar" -> "프로필 사진"
        "story" -> "스토리"
        "highlight" -> "하이라이트"
        "reel" -> "릴스"
        else -> "게시글"
    }

@Composable
private fun AccountCard(
    loggedIn: Boolean,
    enabled: Boolean,
    onLogin: () -> Unit,
    onLogout: () -> Unit
) {
    TossCard(padding = PaddingValues(horizontal = 20.dp, vertical = 6.dp), spacing = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(enabled = enabled, onClick = onLogin)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IconTile(Toss.Grey) { InstagramGlyph(20.dp) }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Instagram 로그인", style = TossType.Body, color = Toss.Text1)
                Text(
                    if (loggedIn) "비공개 계정에만 세션을 사용해요" else "비공개 계정을 볼 때만 필요해요",
                    style = TossType.Sub,
                    color = Toss.Text3
                )
            }
            if (loggedIn) {
                Pill("연결됨", Toss.Green, Toss.GreenLight)
                Text(
                    "로그아웃",
                    style = TossType.ButtonSmall,
                    color = Toss.Text2,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = enabled, onClick = onLogout)
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("연결 안 됨", style = TossType.Sub.copy(fontWeight = FontWeight.Medium), color = Toss.Text3)
                    ChevronGlyph()
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(current: InstagramProfileCatalog) {
    TossCard(spacing = 12.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(Toss.InstagramColors))
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(Toss.Track)
            ) {
                current.avatar?.let {
                    AsyncImage(
                        model = it.previewUrl,
                        contentDescription = "프로필 사진",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    current.fullName.ifBlank { current.username },
                    style = TossType.Name,
                    color = Toss.Text1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "@" + current.username,
                        style = TossType.BodyRegular.copy(fontSize = TossType.Sub.fontSize),
                        color = Toss.Text3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (current.isPrivate) {
                        Pill("비공개", Toss.Text2, Toss.Grey)
                    } else {
                        Pill("공개", Toss.Green, Toss.GreenLight)
                    }
                }
            }
        }
        if (current.biography.isNotBlank()) {
            Text(
                current.biography,
                style = TossType.Sub,
                color = Toss.Text2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionTabs(
    selected: InstagramSection,
    countOf: (InstagramSection) -> Int?,
    onSelect: (InstagramSection) -> Unit
) {
    Column {
        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)) {
            InstagramSection.entries.forEach { item ->
                val on = item == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(item) }
                        .padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            item.label,
                            style = if (on) TossType.Section else TossType.Section.copy(fontWeight = FontWeight.Medium),
                            color = if (on) Toss.Text1 else Toss.Text3,
                            maxLines = 1
                        )
                        countOf(item)?.let {
                            Text(
                                it.toString(),
                                style = TossType.Caption,
                                color = if (on) Toss.Blue else Toss.Text4
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (on) Toss.Text1 else Color.Transparent)
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Toss.Line)
        )
    }
}

@Composable
private fun Toolbar(
    loading: Boolean,
    status: String,
    loaded: Boolean,
    count: Int,
    selectedCount: Int,
    allSelected: Boolean,
    enabled: Boolean,
    onToggleAll: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when {
                loading -> {
                    SmallSpinner()
                    Text(status, style = TossType.Sub, color = Toss.Text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                loaded -> {
                    Text(count.toString() + "개", style = TossType.Sub.copy(fontWeight = FontWeight.Medium), color = Toss.Text3)
                    if (selectedCount > 0) {
                        Text(selectedCount.toString() + "개 선택", style = TossType.Sub.copy(fontWeight = FontWeight.Bold), color = Toss.Blue)
                    }
                }

                else -> Text(
                    status,
                    style = TossType.Sub,
                    color = if (status.contains("실패")) Toss.Red else Toss.Text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        ToolbarAction(if (loaded) "새로고침" else "불러오기", enabled && !loading, onRefresh)
        ToolbarAction(if (allSelected) "선택 해제" else "전체 선택", enabled && count >= 0, onToggleAll)
    }
}

@Composable
private fun ToolbarAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = TossType.ButtonSmall,
        color = if (enabled) Toss.Text2 else Toss.Text4,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    )
}

@Composable
private fun MediaTile(
    item: InstagramCatalogItem,
    order: Int?,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val isSelected = order != null
    Box(
        modifier = Modifier
            .aspectRatio(0.8f)
            .background(Toss.Track)
            .clickable(enabled = enabled, onClick = onToggle)
    ) {
        AsyncImage(
            model = item.previewUrl,
            contentDescription = item.title.ifBlank { kindLabel(item.kind) },
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        if (item.kind == "highlight" && item.title.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.55f)
                        )
                    )
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.22f))
                    .border(3.dp, Toss.Blue)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isSelected) Toss.Blue else Color.Black.copy(alpha = 0.12f))
                .then(if (isSelected) Modifier else Modifier.border(1.5.dp, Color.White, CircleShape)),
            contentAlignment = Alignment.Center
        ) {
            if (order != null) {
                Text(order.toString(), style = TossType.ButtonSmall.copy(fontSize = TossType.Badge.fontSize), color = Color.White)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (item.kind == "highlight" && item.title.isNotBlank()) {
                Text(
                    item.title,
                    style = TossType.Caption.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (item.kind == "avatar") MediaBadge("프로필")
                if (item.isVideo) MediaBadge("영상", video = true)
                if (item.count > 1) MediaBadge(item.count.toString() + "장")
            }
        }
    }
}

@Composable
private fun MediaBadge(text: String, video: Boolean = false) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (video) PlayGlyph(Color.White, size = 8.dp)
        Text(text, style = TossType.Badge, color = Color.White)
    }
}

private fun InstagramCatalogItem.selectionKey(): String =
    kind + "|" + id + "|" + targetUrl

@Composable
private fun ProfileInstagramLoginDialog(
    onDismiss: () -> Unit,
    onUseSession: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 12.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color.White
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Instagram 로그인", style = TossType.Name.copy(fontSize = TossType.Button.fontSize), color = Toss.Text1)
                        Text("로그인한 뒤 '세션 사용'을 눌러 주세요", style = TossType.Sub, color = Toss.Text3)
                    }
                    ToolbarAction("닫기", true, onDismiss)
                    TossButton(text = "세션 사용", onClick = onUseSession, compact = true)
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
