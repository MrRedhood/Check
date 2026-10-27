package com.astracode.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astracode.app.ui.theme.AstraFonts

/**
 * Text with Tailwind-equivalent metrics: size in sp, line-height 1.5 (preflight default) or 1.333 for `text-xs`.
 * mono = JetBrains Mono slot (font-mono), otherwise Inter slot.
 */
@Composable
fun AText(
    text: String,
    color: Color,
    size: Float,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
    mono: Boolean = true,
    letterSpacing: Float = 0f,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    textAlign: TextAlign? = null,
    lineHeightMultiplier: Float = if (size == 12f) 1.3333f else 1.5f,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size.sp,
        fontWeight = weight,
        fontFamily = if (mono) AstraFonts.Mono else AstraFonts.Sans,
        letterSpacing = letterSpacing.sp,
        lineHeight = (size * lineHeightMultiplier).sp,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
        style = LocalTextStyle.current.copy(
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        ),
    )
}

/** Tailwind `animate-pulse`: opacity 1 -> 0.5 -> 1. Alpha is read in the graphics layer, so no per-frame recomposition. */
@Composable
fun PulseDot(color: Color, size: Dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha = transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    Box(
        modifier
            .size(size)
            .graphicsLayer { this.alpha = alpha.value }
            .background(color, CircleShape),
    )
}

/** Small monospace pill used all over the drawer (.rs, 12 mod, Ready, E0382 ...). */
@Composable
fun AstraBadge(
    text: String,
    fg: Color,
    bg: Color,
    border: Color?,
    modifier: Modifier = Modifier,
    size: Float = 9f,
    weight: FontWeight = FontWeight.SemiBold,
    hPad: Dp = 6.dp,
    vPad: Dp = 2.dp,
    radius: Dp = 4.dp,
) {
    AText(
        text = text,
        color = fg,
        size = size,
        weight = weight,
        modifier = modifier
            .panel(RoundedCornerShape(radius), bg, border)
            .padding(horizontal = hPad, vertical = vPad),
    )
}

/** clip + background + optional 1dp border in one call. */
fun Modifier.panel(shape: Shape, bg: Color, border: Color? = null, borderWidth: Dp = 1.dp): Modifier {
    var m = this.clip(shape).background(bg)
    if (border != null) m = m.border(borderWidth, border, shape)
    return m
}

/** Tailwind `active:scale-95`. State is read in the graphics layer. */
@Composable
fun Modifier.pressScale(source: InteractionSource, pressedScale: Float = 0.95f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) pressedScale else 1f, tween(90), label = "pressScale")
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

fun Modifier.borderTop(color: Color, width: Dp = 1.dp): Modifier =
    drawBehind { drawRect(color, Offset.Zero, Size(size.width, width.toPx())) }

fun Modifier.borderBottom(color: Color, width: Dp = 1.dp): Modifier =
    drawBehind { drawRect(color, Offset(0f, size.height - width.toPx()), Size(size.width, width.toPx())) }

fun Modifier.borderEnd(color: Color, width: Dp = 1.dp): Modifier =
    drawBehind { drawRect(color, Offset(size.width - width.toPx(), 0f), Size(width.toPx(), size.height)) }