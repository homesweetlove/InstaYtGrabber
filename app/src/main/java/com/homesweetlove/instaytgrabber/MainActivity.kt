package com.homesweetlove.instaytgrabber

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.homesweetlove.instaytgrabber.feature.instagram.InstagramEngine
import coil.compose.AsyncImage
import com.homesweetlove.instaytgrabber.feature.youtube.YouTubeEngine
import com.homesweetlove.instaytgrabber.feature.youtube.YouTubeMediaInfo
import com.homesweetlove.instaytgrabber.feature.youtube.YouTubeMediaType
import com.homesweetlove.instaytgrabber.ui.BottomCta
import com.homesweetlove.instaytgrabber.ui.BrandMark
import com.homesweetlove.instaytgrabber.ui.EqualizerGlyph
import com.homesweetlove.instaytgrabber.ui.GrabberTheme
import com.homesweetlove.instaytgrabber.ui.IconTile
import com.homesweetlove.instaytgrabber.ui.ImageGlyph
import com.homesweetlove.instaytgrabber.ui.InstagramGlyph
import com.homesweetlove.instaytgrabber.ui.PlayGlyph
import com.homesweetlove.instaytgrabber.ui.SectionTitle
import com.homesweetlove.instaytgrabber.ui.SegmentedControl
import com.homesweetlove.instaytgrabber.ui.SelectRow
import com.homesweetlove.instaytgrabber.ui.TaskOverlay
import com.homesweetlove.instaytgrabber.ui.TaskState
import com.homesweetlove.instaytgrabber.ui.Toss
import com.homesweetlove.instaytgrabber.ui.TossButton
import com.homesweetlove.instaytgrabber.ui.TossButtonStyle
import com.homesweetlove.instaytgrabber.ui.TossCard
import com.homesweetlove.instaytgrabber.ui.TossTextField
import com.homesweetlove.instaytgrabber.ui.TossType
import com.homesweetlove.instaytgrabber.ui.YouTubeGlyph
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var incomingUrl by mutableStateOf("")
    private var incomingVersion by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 100)
        }
        incomingUrl = sharedLink(intent)

        setContent {
            key(incomingVersion) {
                InstaYtGrabberApp(incomingUrl)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingUrl = sharedLink(intent)
        incomingVersion++
    }

    private fun sharedLink(intent: Intent?): String {
        if (intent?.action != Intent.ACTION_SEND) return ""
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        return Regex("""https?://\S+""").findAll(sharedText)
            .map { it.value.trimEnd('.', ',', ')', ']', '>', '"') }
            .firstOrNull { InstagramEngine.accepts(it) || YouTubeEngine.accepts(it) }
            .orEmpty()
    }
}

enum class PlatformTab(val label: String) {
    INSTAGRAM("Instagram"),
    YOUTUBE("YouTube")
}

@Composable
private fun InstaYtGrabberApp(sharedUrl: String) {
    val initialTab = when {
        InstagramEngine.accepts(sharedUrl) -> PlatformTab.INSTAGRAM
        YouTubeEngine.accepts(sharedUrl) -> PlatformTab.YOUTUBE
        else -> PlatformTab.INSTAGRAM
    }
    var tab by remember { mutableStateOf(initialTab) }
    val header: @Composable () -> Unit = {
        AppHeader(tab = tab, onTab = { tab = it })
    }

    GrabberTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Toss.Bg)
                .statusBarsPadding()
        ) {
            when (tab) {
                PlatformTab.INSTAGRAM -> InstagramProfileBrowserScreen(
                    initialUrl = sharedUrl.takeIf(InstagramEngine::accepts).orEmpty(),
                    header = header
                )

                PlatformTab.YOUTUBE -> YouTubeScreen(
                    initialUrl = sharedUrl.takeIf(YouTubeEngine::accepts).orEmpty(),
                    header = header
                )
            }
        }
    }
}

/** Brand row + large title + platform segmented control. Scrolls with the page. */
@Composable
fun AppHeader(tab: PlatformTab, onTab: (PlatformTab) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BrandMark()
                Text("InstaYtGrabber", style = TossType.Section.copy(letterSpacing = TossType.Sub.letterSpacing), color = Toss.Text2)
                Spacer(Modifier.weight(1f))
                Text("v" + BuildConfig.VERSION_NAME, style = TossType.Caption, color = Toss.Text4)
            }
            Text("어떤 걸\n저장할까요?", style = TossType.Title, color = Toss.Text1)
        }
        SegmentedControl(
            options = PlatformTab.entries,
            selected = tab,
            onSelect = onTab,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) { option, on ->
            Box(modifier = Modifier.alpha(if (on) 1f else 0.45f)) {
                when (option) {
                    PlatformTab.INSTAGRAM -> InstagramGlyph(16.dp)
                    PlatformTab.YOUTUBE -> YouTubeGlyph(13.dp)
                }
            }
            Text(
                option.label,
                style = if (on) TossType.Section else TossType.Section.copy(fontWeight = TossType.Body.fontWeight),
                color = if (on) Toss.Text1 else Toss.Text3
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

private val YouTubeMediaType.title: String
    get() = when (this) {
        YouTubeMediaType.VIDEO -> "영상"
        YouTubeMediaType.AUDIO -> "음원"
        YouTubeMediaType.THUMBNAIL -> "썸네일"
    }

private val YouTubeMediaType.subtitle: String
    get() = when (this) {
        YouTubeMediaType.VIDEO -> "영상 파일 그대로 저장해요"
        YouTubeMediaType.AUDIO -> "MP3로 소리만 추출해요"
        YouTubeMediaType.THUMBNAIL -> "JPG 이미지로 저장해요"
    }

private val YouTubeMediaType.actionLabel: String
    get() = when (this) {
        YouTubeMediaType.VIDEO -> "영상 저장하기"
        YouTubeMediaType.AUDIO -> "MP3 추출하기"
        YouTubeMediaType.THUMBNAIL -> "썸네일 저장하기"
    }

private val YouTubeMediaType.runningTitle: String
    get() = when (this) {
        YouTubeMediaType.VIDEO -> "영상을 저장하고 있어요"
        YouTubeMediaType.AUDIO -> "음원을 추출하고 있어요"
        YouTubeMediaType.THUMBNAIL -> "썸네일을 저장하고 있어요"
    }

private fun formatDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

@Composable
private fun YouTubeMediaIcon(type: YouTubeMediaType) {
    when (type) {
        YouTubeMediaType.VIDEO -> IconTile(Toss.RedLight) { PlayGlyph(Toss.Red) }
        YouTubeMediaType.AUDIO -> IconTile(Toss.PurpleLight) { EqualizerGlyph(Toss.Purple) }
        YouTubeMediaType.THUMBNAIL -> IconTile(Toss.GreenLight) { ImageGlyph(Toss.Green) }
    }
}

@Composable
private fun YouTubeScreen(initialUrl: String, header: @Composable () -> Unit) {
    var url by remember { mutableStateOf(initialUrl) }
    var type by remember { mutableStateOf(YouTubeMediaType.VIDEO) }
    var task by remember { mutableStateOf<TaskState>(TaskState.Idle) }
    var info by remember { mutableStateOf<YouTubeMediaInfo?>(null) }
    var inspecting by remember { mutableStateOf(false) }
    var inspectStatus by remember { mutableStateOf("") }
    var inspectError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val running = task is TaskState.Running
    val invalidUrl = url.isNotBlank() && !YouTubeEngine.accepts(url)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            header()
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TossCard {
                    SectionTitle("링크")
                    TossTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            info = null
                            inspectError = null
                            inspectStatus = ""
                        },
                        placeholder = "youtube.com 또는 youtu.be 링크",
                        enabled = !running,
                        isError = invalidUrl
                    )
                    if (invalidUrl) {
                        Text("YouTube 링크가 아닌 것 같아요", style = TossType.Caption, color = Toss.Red)
                    }

                    TossButton(
                        text = if (inspecting) "영상 정보 확인 중…" else "링크 확인하기",
                        enabled = url.isNotBlank() && !invalidUrl && !running && !inspecting,
                        style = TossButtonStyle.Secondary,
                        compact = true,
                        onClick = {
                            scope.launch {
                                inspecting = true
                                info = null
                                inspectError = null
                                inspectStatus = "YouTube 링크를 확인하고 있어요"

                                val result = YouTubeEngine.inspect(url) { status ->
                                    inspectStatus = status
                                }

                                result.fold(
                                    onSuccess = {
                                        info = it
                                        inspectStatus = "조회 완료"
                                    },
                                    onFailure = {
                                        inspectError = it.message ?: it.javaClass.simpleName
                                        inspectStatus = ""
                                    }
                                )
                                inspecting = false
                            }
                        }
                    )

                    if (inspectStatus.isNotBlank()) {
                        Text(inspectStatus, style = TossType.Caption, color = Toss.Text3)
                    }
                    inspectError?.let {
                        Text(it, style = TossType.Caption, color = Toss.Red)
                    }
                }

                info?.let { media ->
                    TossCard {
                        if (media.thumbnailUrl.isNotBlank()) {
                            AsyncImage(
                                model = media.thumbnailUrl,
                                contentDescription = media.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Text(media.title, style = TossType.Section, color = Toss.Text1)
                        Text(
                            buildString {
                                if (media.channel.isNotBlank()) append(media.channel)
                                if (media.durationSeconds > 0) {
                                    if (isNotEmpty()) append(" · ")
                                    append(formatDuration(media.durationSeconds))
                                }
                                if (media.formatCount > 0) {
                                    if (isNotEmpty()) append(" · ")
                                    append(media.formatCount)
                                    append(" formats")
                                }
                                if (media.extractor.isNotBlank()) {
                                    if (isNotEmpty()) append(" · ")
                                    append(media.extractor)
                                }
                            },
                            style = TossType.Caption,
                            color = Toss.Text3
                        )
                    }
                }

                TossCard(
                    padding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
                    spacing = 4.dp
                ) {
                    SectionTitle("저장 형식")
                    YouTubeMediaType.entries.forEach { item ->
                        SelectRow(
                            title = item.title,
                            subtitle = item.subtitle,
                            selected = type == item,
                            onClick = { type = item },
                            enabled = !running,
                            icon = { YouTubeMediaIcon(item) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(170.dp))
        }

        BottomCta(
            modifier = Modifier.align(Alignment.BottomCenter),
            caption = "재생목록 링크는 첫 번째 영상만 저장해요"
        ) {
            TossButton(
                text = type.actionLabel,
                modifier = Modifier.weight(1f),
                enabled = url.isNotBlank() && !invalidUrl &&
                    (info != null || type == YouTubeMediaType.THUMBNAIL) &&
                    !running && !inspecting,
                onClick = {
                    val target = type
                    scope.launch {
                        val title = target.runningTitle
                        task = TaskState.Running(title, "YouTube 엔진을 준비하고 있어요")
                        val result = runCatching {
                            YouTubeEngine.download(url, target) {
                                task = TaskState.Running(
                                    title = title,
                                    detail = when {
                                        it.percent > 0f -> "잠시만 기다려 주세요"
                                        it.line.isNotBlank() -> it.line
                                        else -> "YouTube 엔진을 준비하고 있어요"
                                    },
                                    progress = if (it.percent > 0f) (it.percent / 100f).coerceIn(0f, 1f) else null
                                )
                            }.getOrThrow()
                        }
                        task = result.fold(
                            onSuccess = {
                                TaskState.Done(
                                    title = "저장했어요",
                                    detail = target.title + " 파일을 기기에 저장했어요",
                                    location = "다운로드 › InstaYtGrabber › YouTube › " + target.folder
                                )
                            },
                            onFailure = {
                                TaskState.Failed(
                                    title = "저장하지 못했어요",
                                    detail = it.message ?: it.javaClass.simpleName
                                )
                            }
                        )
                    }
                }
            )
        }

        TaskOverlay(state = task, onDismiss = { task = TaskState.Idle })
    }
}
