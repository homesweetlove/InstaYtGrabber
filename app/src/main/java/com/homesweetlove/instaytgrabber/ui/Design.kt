package com.homesweetlove.instaytgrabber.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Toss / Apple inspired palette. */
object Toss {
    val Bg = Color(0xFFF2F4F6)
    val Card = Color.White
    val Text1 = Color(0xFF191F28)
    val Text2 = Color(0xFF4E5968)
    val Text3 = Color(0xFF8B95A1)
    val Text4 = Color(0xFFB0B8C1)
    val Line = Color(0xFFE5E8EB)
    val Grey = Color(0xFFF2F4F6)
    val Track = Color(0xFFE9ECEF)
    val RadioOff = Color(0xFFD1D6DB)
    val Blue = Color(0xFF3182F6)
    val BlueLight = Color(0xFFE8F3FF)
    val Indigo = Color(0xFF6D5DFC)
    val Red = Color(0xFFF04452)
    val RedLight = Color(0xFFFFEEF0)
    val Purple = Color(0xFF7B61FF)
    val PurpleLight = Color(0xFFF3EEFF)
    val Green = Color(0xFF12B76A)
    val GreenLight = Color(0xFFE9F9F0)
    val YouTubeRed = Color(0xFFFF0033)

    val BrandGradient = Brush.linearGradient(listOf(Blue, Indigo))
    val InstagramColors = listOf(
        Color(0xFFFEDA75),
        Color(0xFFFA7E1E),
        Color(0xFFD62976),
        Color(0xFF962FBF)
    )
}

object TossType {
    val Title = TextStyle(fontSize = 27.sp, lineHeight = 37.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
    val Heading = TextStyle(fontSize = 24.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
    val Name = TextStyle(fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
    val Section = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
    val Body = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.3).sp)
    val BodyRegular = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.3).sp)
    val Sub = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.2).sp)
    val Caption = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.2).sp)
    val Button = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
    val ButtonSmall = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)
    val Badge = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun GrabberTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Toss.Blue,
            onPrimary = Color.White,
            background = Toss.Bg,
            onBackground = Toss.Text1,
            surface = Color.White,
            onSurface = Toss.Text1,
            surfaceVariant = Toss.Grey,
            onSurfaceVariant = Toss.Text3,
            outline = Toss.Line
        ),
        typography = Typography(),
        content = content
    )
}

@Composable
fun TossCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(20.dp),
    spacing: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Toss.Card)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = TossType.Section, color = Toss.Text1)
}

enum class TossButtonStyle { Primary, Secondary, Grey }

@Composable
fun TossButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: TossButtonStyle = TossButtonStyle.Primary,
    compact: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.96f else 1f, label = "press")
    val (bg, fg) = when (style) {
        TossButtonStyle.Primary -> Toss.Blue to Color.White
        TossButtonStyle.Secondary -> Toss.BlueLight to Toss.Blue
        TossButtonStyle.Grey -> Toss.Grey to Toss.Text2
    }
    val shape = RoundedCornerShape(if (compact) 10.dp else 16.dp)
    Box(
        modifier = modifier
            .height(if (compact) 38.dp else 56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(if (enabled) bg else bg.copy(alpha = if (style == TossButtonStyle.Primary) 0.35f else 0.6f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = if (compact) 14.dp else 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = if (compact) TossType.ButtonSmall else TossType.Button,
            color = if (enabled || style == TossButtonStyle.Primary) fg else fg.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** iOS style segmented control. */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable RowScope.(option: T, selected: Boolean) -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Toss.Track)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            val on = option == selected
            val bg by animateColorAsState(if (on) Color.White else Color.Transparent, label = "segment")
            val shape = RoundedCornerShape(11.dp)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .then(if (on) Modifier.shadow(2.dp, shape) else Modifier)
                    .clip(shape)
                    .background(bg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(option) }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                label(option, on)
            }
        }
    }
}

@Composable
fun TossTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false
) {
    val clipboard = LocalClipboardManager.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        when {
            isError -> Toss.Red
            focused -> Toss.Blue
            else -> Color.Transparent
        },
        label = "border"
    )
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(Toss.Grey)
            .border(1.5.dp, borderColor, shape)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    style = TossType.BodyRegular,
                    color = Toss.Text4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = TossType.Body.copy(color = Toss.Text1),
                cursorBrush = SolidColor(Toss.Blue),
                interactionSource = interaction,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp)
            )
        }
        if (value.isEmpty()) {
            Text(
                "붙여넣기",
                style = TossType.ButtonSmall.copy(fontSize = 13.sp),
                color = Toss.Blue,
                modifier = Modifier
                    .shadow(1.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .clickable(enabled = enabled) {
                        clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let(onValueChange)
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        } else if (enabled) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { onValueChange("") },
                contentAlignment = Alignment.Center
            ) {
                ClearGlyph(Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun RadioMark(selected: Boolean, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(if (selected) Toss.Blue else Color.Transparent, label = "radio")
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(bg)
            .then(if (selected) Modifier else Modifier.border(2.dp, Toss.RadioOff, CircleShape)),
        contentAlignment = Alignment.Center
    ) {
        if (selected) CheckGlyph(Modifier.size(24.dp))
    }
}

@Composable
fun SelectRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        icon?.invoke()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = TossType.Body, color = Toss.Text1)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = TossType.Sub, color = Toss.Text3)
        }
        RadioMark(selected)
    }
}

@Composable
fun IconTile(
    background: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(background),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
fun Pill(text: String, color: Color, background: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = TossType.Badge,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** Fixed bottom call-to-action area that fades the content scrolling under it. */
@Composable
fun BottomCta(
    modifier: Modifier = Modifier,
    background: Color = Toss.Bg,
    caption: String? = null,
    captionColor: Color = Toss.Text3,
    content: @Composable RowScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to background.copy(alpha = 0f),
                    0.22f to background,
                    1f to background
                )
            )
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!caption.isNullOrBlank()) {
            Text(
                caption,
                style = TossType.Caption,
                color = captionColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

// ---------------------------------------------------------------- glyphs

@Composable
fun CheckGlyph(modifier: Modifier = Modifier, color: Color = Color.White, strokeWidth: Dp = 2.2.dp) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.29f, h * 0.51f)
            lineTo(w * 0.44f, h * 0.66f)
            lineTo(w * 0.72f, h * 0.36f)
        }
        drawPath(
            path,
            color,
            style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun ClearGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawCircle(Color(0xFFC4CAD1))
        val a = size.width * 0.34f
        val b = size.width * 0.66f
        val stroke = 1.8.dp.toPx()
        drawLine(Color.White, Offset(a, a), Offset(b, b), stroke, StrokeCap.Round)
        drawLine(Color.White, Offset(b, a), Offset(a, b), stroke, StrokeCap.Round)
    }
}

@Composable
fun ChevronGlyph(modifier: Modifier = Modifier, color: Color = Toss.Text4) {
    Canvas(modifier.size(7.dp, 12.dp)) {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, size.height / 2f)
            lineTo(0f, size.height)
        }
        drawPath(path, color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun InstagramGlyph(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        drawRoundRect(
            brush = Brush.linearGradient(Toss.InstagramColors, start = Offset(0f, w), end = Offset(w, 0f)),
            cornerRadius = CornerRadius(w * 0.3f)
        )
        drawCircle(Color.White, radius = w * 0.22f, style = Stroke(w * 0.09f))
        drawCircle(Color.White, radius = w * 0.055f, center = Offset(w * 0.74f, w * 0.26f))
    }
}

@Composable
fun YouTubeGlyph(height: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(height * 1.4f, height)) {
        val w = size.width
        val h = size.height
        drawRoundRect(Toss.YouTubeRed, cornerRadius = CornerRadius(h * 0.28f))
        val path = Path().apply {
            moveTo(w * 0.41f, h * 0.3f)
            lineTo(w * 0.64f, h * 0.5f)
            lineTo(w * 0.41f, h * 0.7f)
            close()
        }
        drawPath(path, Color.White)
    }
}

@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        drawRoundRect(Toss.BrandGradient, cornerRadius = CornerRadius(w * 0.32f))
        val stroke = Stroke(w * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val arrow = Path().apply {
            moveTo(w * 0.5f, w * 0.26f)
            lineTo(w * 0.5f, w * 0.66f)
            moveTo(w * 0.33f, w * 0.5f)
            lineTo(w * 0.5f, w * 0.67f)
            lineTo(w * 0.67f, w * 0.5f)
        }
        drawPath(arrow, Color.White, style = stroke)
    }
}

@Composable
fun PlayGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = 13.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val path = Path().apply {
            moveTo(w * 0.18f, w * 0.08f)
            lineTo(w * 0.92f, w * 0.5f)
            lineTo(w * 0.18f, w * 0.92f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
fun EqualizerGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp, 16.dp)) {
        val bar = size.width / 7f
        listOf(0.5f, 1f, 0.72f, 0.4f).forEachIndexed { i, ratio ->
            val h = size.height * ratio
            drawRoundRect(
                color,
                topLeft = Offset(i * bar * 1.9f, size.height - h),
                size = Size(bar, h),
                cornerRadius = CornerRadius(bar / 2f)
            )
        }
    }
}

@Composable
fun ImageGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(19.dp, 16.dp)) {
        val s = 1.8.dp.toPx()
        drawRoundRect(
            color,
            topLeft = Offset(s / 2, s / 2),
            size = Size(size.width - s, size.height - s),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(s)
        )
        val path = Path().apply {
            moveTo(size.width * 0.12f, size.height * 0.8f)
            lineTo(size.width * 0.4f, size.height * 0.48f)
            lineTo(size.width * 0.6f, size.height * 0.68f)
            lineTo(size.width * 0.72f, size.height * 0.56f)
            lineTo(size.width * 0.9f, size.height * 0.76f)
        }
        drawPath(path, color, style = Stroke(s, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, radius = size.width * 0.08f, center = Offset(size.width * 0.68f, size.height * 0.32f))
    }
}
