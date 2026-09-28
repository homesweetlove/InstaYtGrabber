package com.homesweetlove.instaytgrabber.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Full screen state for a long running save job (progress → done / failed). */
sealed interface TaskState {
    data object Idle : TaskState

    data class Running(
        val title: String,
        val detail: String,
        val progress: Float? = null
    ) : TaskState

    data class Done(
        val title: String,
        val detail: String,
        val location: String
    ) : TaskState

    data class Failed(
        val title: String,
        val detail: String
    ) : TaskState
}

@Composable
fun TaskOverlay(state: TaskState, onDismiss: () -> Unit) {
    val last = remember { arrayOf<TaskState>(TaskState.Idle) }
    if (state !is TaskState.Idle) last[0] = state
    val shown = if (state is TaskState.Idle) last[0] else state

    BackHandler(enabled = state !is TaskState.Idle) {
        if (state !is TaskState.Running) onDismiss()
    }

    AnimatedVisibility(
        visible = state !is TaskState.Idle,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { }
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 96.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (shown) {
                    is TaskState.Running -> Box(contentAlignment = Alignment.Center) {
                        ProgressRing(progress = shown.progress, modifier = Modifier.size(132.dp))
                        shown.progress?.let {
                            Text(
                                "${(it * 100).toInt()}%",
                                style = TossType.Heading.copy(fontSize = 30.sp, letterSpacing = (-1).sp),
                                color = Toss.Text1
                            )
                        }
                    }

                    is TaskState.Done -> ResultBadge(success = true)
                    is TaskState.Failed -> ResultBadge(success = false)
                    TaskState.Idle -> Unit
                }

                Spacer(Modifier.height(28.dp))

                val (title, detail) = when (shown) {
                    is TaskState.Running -> shown.title to shown.detail
                    is TaskState.Done -> shown.title to shown.detail
                    is TaskState.Failed -> shown.title to shown.detail
                    TaskState.Idle -> "" to ""
                }
                Text(title, style = TossType.Heading, color = Toss.Text1, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    detail,
                    style = TossType.BodyRegular,
                    color = Toss.Text3,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )

                if (shown is TaskState.Done) {
                    Spacer(Modifier.height(28.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Toss.Grey)
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text("저장 위치", style = TossType.Sub, color = Toss.Text3)
                        Text(shown.location, style = TossType.Body.copy(fontWeight = FontWeight.Bold), color = Toss.Text1)
                    }
                }
            }

            if (shown is TaskState.Running) {
                Text(
                    "앱을 닫지 말고 잠시만 기다려 주세요",
                    style = TossType.Caption,
                    color = Toss.Text3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                )
            } else {
                BottomCta(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    background = Color.White
                ) {
                    TossButton(
                        text = "확인",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun ProgressRing(progress: Float?, modifier: Modifier = Modifier, strokeWidth: Dp = 11.dp) {
    val animated by animateFloatAsState((progress ?: 0f).coerceIn(0f, 1f), label = "ring")
    val transition = rememberInfiniteTransition(label = "spin")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "rotation"
    )
    Canvas(modifier) {
        val sw = strokeWidth.toPx()
        val arcSize = Size(size.width - sw, size.height - sw)
        val topLeft = Offset(sw / 2f, sw / 2f)
        drawArc(Toss.Grey, 0f, 360f, false, topLeft, arcSize, style = Stroke(sw))
        val indeterminate = progress == null
        drawArc(
            brush = Brush.linearGradient(listOf(Toss.Blue, Toss.Indigo)),
            startAngle = if (indeterminate) rotation - 90f else -90f,
            sweepAngle = if (indeterminate) 100f else (360f * animated).coerceAtLeast(1f),
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(sw, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun SmallSpinner(modifier: Modifier = Modifier) {
    ProgressRing(progress = null, modifier = modifier.size(16.dp), strokeWidth = 2.5.dp)
}

@Composable
private fun ResultBadge(success: Boolean) {
    val main = if (success) Toss.Blue else Toss.Red
    Box(
        modifier = Modifier
            .size(112.dp)
            .clip(CircleShape)
            .background(if (success) Toss.BlueLight else Toss.RedLight),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .shadow(16.dp, CircleShape, ambientColor = main, spotColor = main)
                .clip(CircleShape)
                .background(
                    if (success) Toss.BrandGradient
                    else Brush.linearGradient(listOf(Toss.Red, Color(0xFFFF6B7A)))
                ),
            contentAlignment = Alignment.Center
        ) {
            if (success) {
                CheckGlyph(Modifier.size(76.dp), strokeWidth = 4.5.dp)
            } else {
                Text("!", style = TossType.Heading.copy(fontSize = 38.sp), color = Color.White)
            }
        }
    }
}
