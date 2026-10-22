package com.homesweetlove.instaytgrabber

import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramBrowserConfig
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramCatalogItem
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramCatalogSection
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramDirectFileEngine
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramEngine
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramInstaloaderMediaEngine
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramMediaLink
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramMediaType
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramOutputType
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramProfileCatalog
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramProfileCatalogEngine
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramProfileInput
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramRateLimit
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

private const val IDLE_STATUS = "공개 계정의 게시글·릴스는 로그인 없이도 조회할 수 있어요"

@Composable
fun InstagramProfileBrowserScreen(
    initialUrl: String,
    header: @Composable () -> Unit = {}
) {
    val incomingMedia = remember(initialUrl) { InstagramMediaLink.parse(initialUrl) }
    var url by remember { mutableStateOf(if (incomingMedia == null) initialUrl else "") }
    var directLink by remember { mutableStateOf(incomingMedia?.canonicalUrl.orEmpty()) }
    var directLinkStatus by remember { mutableStateOf("") }
    var resolvedProfileUrl by remember { mutableStateOf("") }
    var section by remember { mutableStateOf(InstagramSection.POST) }
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
    var loggedIn by remember { mutableStateOf(InstagramSession.isLoggedIn()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun downloadDirectLink() {
        val media = InstagramMediaLink.parse(directLink)
        if (media == null) {
            directLinkStatus = "Instagram 게시글 또는 릴스 링크를 입력해 주세요."
            DiagnosticLog.record("IG-LINK-INPUT", "게시물·릴스 링크", "올바르지 않은 게시글 또는 릴스 주소")
            return
        }
        if (InstagramRateLimit.shouldSkipMedia()) {
            directLinkStatus = "해당 미디어 요청에서 HTTP 429가 발생했습니다. " +
                "약 " + InstagramRateLimit.mediaRemainingMinutes() +
                "분간 미디어 재요청을 중단합니다. 실제 제한 시간은 서버가 결정합니다."
            return
        }
        scope.launch {
            downloading = true
            directLinkStatus = ""
            task = TaskState.Running(
                title = media.type.label + " 저장 중",
                detail = "원본 미디어 정보를 확인하고 있어요",
                progress = 0f
            )
            val progressCallback: (com.homesweetlove.instaytgrabber.core.GrabProgress) -> Unit =
                { progress ->
                    task = TaskState.Running(
                        title = media.type.label + " 저장 중",
                        detail = progress.line,
                        progress = (progress.percent / 100f).coerceIn(0f, 1f)
                    )
                }
            // Single-media embeds do not require a profile-info request.
            // Try the real Chromium page before the Python REST extractors.
            val embedded = InstagramWebMediaEngine.download(
                context = context,
                pageUrl = media.canonicalUrl,
                folderName = media.type.folder + "/media",
                baseName = "chrome_shared_" + System.currentTimeMillis(),
                authenticated = InstagramSession.isLoggedIn(),
                onProgress = progressCallback
            )
            val result = if (embedded.isSuccess || InstagramRateLimit.shouldSkipMedia()) {
                embedded
            } else {
                embedded.exceptionOrNull()?.let { error ->
                    DiagnosticLog.failure(
                        "IG-EMBED-01", "단일 게시물 / 브라우저 embed 추출", error
                    )
                }
                InstagramEngine.download(
                    url = media.canonicalUrl,
                    type = media.type,
                    output = InstagramOutputType.MEDIA,
                    allowAuthenticatedFallback = InstagramSession.isLoggedIn(),
                    onProgress = progressCallback
                )
            }
            result.fold(
                onSuccess = {
                    task = TaskState.Done(
                        title = "저장 요청 완료",
                        detail = "다운로드 폴더를 확인해 주세요.",
                        location = "다운로드 › InstaYtGrabber › Instagram"
                    )
                    directLinkStatus = "다운로드가 완료됐어요."
                },
                onFailure = { error ->
                    if (error.message.orEmpty().contains("429") ||
                        error.message.orEmpty().contains("Too Many Requests", ignoreCase = true)) {
                        InstagramRateLimit.onMedia429()
                        DiagnosticLog.record(
                            "IG-LINK-429",
                            "Instagram 단일 미디어 링크",
                            "Instagram 서버에서 HTTP 429 제한이 발생해 요청을 중단했습니다."
                        )
                    }
                    DiagnosticLog.failure(
                        "IG-LINK-DOWNLOAD", "Instagram 단일 미디어 " + media.type.label, error
                    )
                    task = TaskState.Failed(
                        title = "링크 저장에 실패했어요",
                        detail = if (InstagramRateLimit.shouldSkipMedia()) {
                            "Instagram 서버 요청 제한(429)입니다. 잠시 쉬었다가 다시 시도해 주세요."
                        } else error.message ?: "원본을 가져올 수 없습니다."
                    )
                    directLinkStatus = "실패했어요. 오류 기록을 확인해 주세요."
                }
            )
            downloading = false
        }
    }

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

        // Public Instagram stories and highlights are often protected by a
        // logged-in session. This is not evidence that the profile is private.
        // Do not issue anonymous API calls or write a misleading failure log.
        if ((target == InstagramSection.STORY || target == InstagramSection.HIGHLIGHT) &&
            !InstagramSession.isLoggedIn()
        ) {
            status = target.label + "는 Instagram 로그인 연결 후 조회할 수 있어요. " +
                "게시글·릴스는 로그인 없이도 시도할 수 있어요."
            return
        }

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
            section = InstagramSection.POST
            status = "프로필 정보 불러오는 중…"

            InstagramProfileCatalogEngine.loadBase(context, canonicalUrl).fold(
                onSuccess = { loaded ->
                    profile = loaded
                    status = "@" + loaded.username + " 프로필 불러옴"
                    loadSection(InstagramSection.POST, loaded)
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
                        SectionTitle("1. 앱 안에서 로그인 · 탐색 · 저장")
                        Text(
                            "앱 안에 Firefox 엔진을 내장했습니다. 같은 화면에서 로그인하고 프로필, 게시글, " +
                                "릴스를 탐색한 뒤 현재 미디어를 저장할 수 있어요. 외부 브라우저는 필요하지 않습니다.",
                            style = TossType.Caption,
                            color = Toss.Text3
                        )
                        TossTextField(
                            value = url,
                            onValueChange = { url = it },
                            placeholder = "아이디 · @아이디 · instagram.com/아이디",
                            enabled = !baseLoading && !downloading
                        )
                        TossButton(
                            text = "Instagram 내부 브라우저 열기",
                            onClick = {
                                context.startActivity(
                                    Intent(context, InstagramBrowserActivity::class.java).apply {
                                        putExtra(InstagramBrowserActivity.EXTRA_INITIAL_PROFILE,
                                            directLink.ifBlank { url })
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !downloading,
                            style = TossButtonStyle.Secondary
                        )
                        SectionTitle("게시글·릴스 링크 저장")
                        TossTextField(
                            value = directLink,
                            onValueChange = { directLink = it },
                            placeholder = "https://www.instagram.com/reel/...",
                            enabled = !downloading
                        )
                        TossButton(
                            text = "이 링크 저장하기",
                            onClick = { downloadDirectLink() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = directLink.isNotBlank() && !downloading
                        )
                        Text(
                            "아래 단일 링크 저장은 별도 엔진을 사용합니다. " +
                                "기존 WebView·Python 조회가 제한될 경우 위의 내장 브라우저를 이용하세요.",
                            style = TossType.Caption,
                            color = Toss.Text3
                        )
                        if (directLinkStatus.isNotBlank()) {
                            Text(
                                directLinkStatus,
                                style = TossType.Caption,
                                color = if (directLinkStatus.contains("실패") ||
                                    directLinkStatus.contains("제한")
                                ) Toss.Red else Toss.Text2
                            )
                        }
                    }

                    TossCard {
                        SectionTitle("2. 프로필 전체 목록 불러오기 (실험적)")
                        Text(
                            "Instagram 프로필 API는 로그인하지 않은 앱에서 429를 반환할 수 있어요. " +
                                "위의 앱 내부 브라우저에서 로그인·탐색·저장을 먼저 시도해 주세요.",
                            style = TossType.Caption,
                            color = Toss.Text3
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
                            if ((section == InstagramSection.STORY || section == InstagramSection.HIGHLIGHT) &&
                                !loggedIn
                            ) {
                                "공개 계정이어도 " + section.label +
                                    "는 Instagram 로그인이 필요할 수 있어요. 위의 계정 연결에서 로그인해 주세요."
                            } else if (section in loadedSections) {
                                "표시할 " + section.label + " 항목이 없어요"
                            } else {
                                "목록을 불러오지 못했어요. 새로고침을 눌러 주세요"
                            },
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
                    "로그인이 연결되어 있어요 · 스토리·하이라이트도 조회해 보세요"
                } else {
                    "공개 게시글·릴스는 로그인 없이 조회를 시도해요"
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
                Text("앱 내 Instagram 로그인", style = TossType.Body, color = Toss.Text1)
                Text(
                    if (loggedIn) "앱 내 로그인 세션 연결됨 (Chrome 세션과 별개)" else "일부 환경에서 웹 로그인이 차단됩니다. Chrome 탐색을 이용해 주세요",
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
