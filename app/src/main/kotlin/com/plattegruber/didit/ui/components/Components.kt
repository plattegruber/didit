package com.plattegruber.didit.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plattegruber.didit.R
import com.plattegruber.didit.ui.theme.DiditTheme

/** A solid circle, the design's basic mark. */
@Composable
fun Dot(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(color, CircleShape))
}

/** Upper-case monospaced section label: "NAME", "SHOW IT AS". */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = DiditTheme.colors.muted) {
    Text(text.uppercase(), style = DiditTheme.type.overline, color = color, modifier = modifier)
}

/** The filled ink button that ends a screen: "Add to widget". */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = DiditTheme.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (enabled) colors.ink else colors.raised,
        contentColor = if (enabled) colors.onInk else colors.muted,
    ) {
        Box(contentAlignment = Alignment.Center) { Text(text, style = DiditTheme.type.button) }
    }
}

/** A quiet outlined button, such as the log sheet's "+10". */
@Composable
fun OutlineButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        color = Color.Transparent,
        contentColor = DiditTheme.colors.ink,
        border = BorderStroke(1.5.dp, DiditTheme.colors.lineStrong),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/** A 44dp round icon button. [filled] gives it the sheet's raised background, as on "Close". */
@Composable
fun RoundIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(size).semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        color = if (filled) DiditTheme.colors.raised else Color.Transparent,
        contentColor = if (enabled) DiditTheme.colors.ink else DiditTheme.colors.lineStrong,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(iconSize))
        }
    }
}

/**
 * The design's text field: 52dp tall, softly bordered, the border brightening to ink on focus.
 * [leading] sits inside the field before the text, as a choice's colour dot does.
 */
@Composable
fun DiditTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    textStyle: TextStyle = DiditTheme.type.body,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = DiditTheme.colors
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val borderColor =
        when {
            isError -> colors.danger
            focused -> colors.ink
            else -> colors.line
        }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            modifier
                .height(52.dp)
                .semantics { if (label != null) contentDescription = label },
        singleLine = true,
        textStyle = textStyle.copy(color = colors.ink),
        cursorBrush = SolidColor(colors.ink),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactions,
        decorationBox = { field ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.field)
                    .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    leading()
                    Spacer(Modifier.width(2.dp))
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = textStyle, color = colors.subtle)
                    field()
                }
            }
        },
    )
}

/** A thin bar split in proportion to [parts]: the at-a-glance mix of a choice habit. */
@Composable
fun MixBar(parts: List<Pair<Color, Int>>, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(2.dp)), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        parts.filter { it.second > 0 }.forEach { (color, weight) ->
            Box(Modifier.weight(weight.toFloat()).height(4.dp).background(color))
        }
    }
}

/** A clickable surface used where a whole card is the control, with the right role for TalkBack. */
@Composable
fun SelectableCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = DiditTheme.colors
    Surface(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics { role = Role.RadioButton },
        shape = RoundedCornerShape(18.dp),
        color = if (selected) colors.surface else Color.Transparent,
        contentColor = colors.ink,
        border = BorderStroke(1.5.dp, if (selected) colors.ink else colors.line),
        content = content,
    )
}

/** The radio mark in a [SelectableCard]'s corner. */
@Composable
fun RadioMark(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = DiditTheme.colors
    Box(
        modifier
            .size(16.dp)
            .border(1.5.dp, if (selected) colors.ink else colors.lineStrong, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Dot(colors.ink, 8.dp)
    }
}

/** The design's dashed "add" button. */
@Composable
fun DashedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DiditTheme.colors
    Surface(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .height(52.dp)
                .drawBehind {
                    drawRoundRect(
                        color = colors.lineStrong,
                        cornerRadius = CornerRadius(14.dp.toPx()),
                        style =
                            Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                            ),
                    )
                },
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        contentColor = colors.inkSoft,
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_plus), contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = DiditTheme.type.chip)
        }
    }
}
