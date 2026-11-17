package com.field.dial

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/* FieldText: thin wrapper over BasicText */
@Composable
fun FieldText(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Bold,
    color: Color = FieldInk,
    tracking: TextUnit = 0.sp,
    align: TextAlign? = null,
    lineHeight: TextUnit? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle.Default.merge(
            color = color,
            fontSize = size,
            fontWeight = weight,
            fontFamily = FontFamily.Monospace,
            letterSpacing = tracking,
            textAlign = align ?: TextAlign.Unspecified,
            lineHeight = lineHeight ?: TextUnit.Unspecified,
        ),
        maxLines = maxLines,
        overflow = overflow,
    )
}

/* LED: hard on/off blink. SLOW = 250/750, FAST = 100/400. */
@Composable
fun BlinkingLed(size: Dp = 8.dp, mode: LedMode, color: Color = FieldViolet) {
    var on by remember(mode) { mutableStateOf(true) }
    LaunchedEffect(mode) {
        when (mode) {
            LedMode.SOLID -> on = true
            LedMode.OFF -> on = false
            LedMode.FAST -> while (true) { on = true; delay(100); on = false; delay(400) }
            LedMode.SLOW -> while (true) { on = true; delay(250); on = false; delay(750) }
        }
    }
    Box(
        Modifier
            .size(size)
            .background(if (on) color else color.copy(alpha = 0.15f), CircleShape)
    )
}

/* Key: paper square, 3 dp radius, no ripple. Press = border weight. */
@Composable
fun FieldKey(
    label: String,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
    size: TextUnit = 15.sp,
    variant: KeyVariant = KeyVariant.PAPER,
    active: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg = when (variant) {
        KeyVariant.ACCENT -> FieldViolet
        KeyVariant.INK -> FieldInk
        KeyVariant.PAPER -> FieldPaper
    }
    val fg = when {
        variant == KeyVariant.ACCENT || variant == KeyVariant.INK -> FieldPaper
        active -> FieldViolet
        else -> FieldInk
    }
    val borderColor = when {
        variant == KeyVariant.INK -> FieldPaper
        active -> FieldViolet
        else -> FieldInk
    }
    val borderWidth = if (pressed || active) 2.dp else 1.5.dp
    Box(
        modifier
            .height(height)
            .background(bg, RoundedCornerShape(3.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(3.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onPress),
        contentAlignment = Alignment.Center,
    ) {
        FieldText(label, size, weight = FontWeight.Bold, color = fg, tracking = 1.sp)
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    FieldText(text, 10.sp, modifier, weight = FontWeight.Bold, color = FieldInk, tracking = 2.sp)
}

/* Toggle row: paper card, label + caption, square checkbox */
@Composable
fun FieldToggleRow(label: String, caption: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(FieldPaper, RoundedCornerShape(4.dp))
            .border(1.5.dp, FieldInk, RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            FieldText(label, 12.sp, color = FieldInk, tracking = 1.sp)
            FieldText(caption, 9.sp, Modifier.padding(top = 2.dp), color = FieldDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(20.dp).border(1.5.dp, FieldInk)) {
            if (checked) {
                Box(Modifier.padding(4.dp).size(12.dp).background(FieldViolet))
            }
        }
    }
}

/* Input: paper field with hint, foundation BasicTextField (skin EXT: INPUT ROW) */
@Composable
fun FieldInput(
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
) {
    Box(
        modifier
            .height(height)
            .background(FieldPaper, RoundedCornerShape(3.dp))
            .border(1.5.dp, FieldInk, RoundedCornerShape(3.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) FieldText(hint, 12.sp, color = FieldDim, tracking = 1.sp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            // single-line BasicTextField wraps to content width unless forced;
            // fillMaxWidth keeps the whole row tappable (empty field included)
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle.Default.merge(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = FieldInk,
            ),
            singleLine = true,
        )
    }
}

/* Dot grid texture: 26 dp pitch, computed once per size */
fun Modifier.fieldGrid(): Modifier = drawWithCache {
    val pitch = 26.dp.toPx()
    val dots = ArrayList<Offset>()
    var x = 0f
    while (x <= size.width) {
        var y = 0f
        while (y <= size.height) {
            dots.add(Offset(x, y))
            y += pitch
        }
        x += pitch
    }
    val r = 1.dp.toPx()
    onDrawBehind { for (c in dots) drawCircle(FieldGrid, radius = r, center = c) }
}
