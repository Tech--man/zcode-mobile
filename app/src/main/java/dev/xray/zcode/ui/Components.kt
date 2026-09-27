package dev.xray.zcode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.window.Popup

@Composable
fun zcPalette(): ZcPalette = LocalZcPalette.current

@Composable
fun ZcText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = zcPalette().text,
    size: TextUnit = 14.sp,
    weight: FontWeight = FontWeight.Normal,
    mono: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) {
    val style = TextStyle(
        color = color,
        fontSize = size,
        fontWeight = weight,
        fontFamily = if (mono) FontFamily.Monospace else null,
        letterSpacing = letterSpacing,
    )
    BasicText(
        text = text,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 主按钮（sky 实心）与次按钮（描边），按压 alpha 反馈，无涟漪无阴影。 */
@Composable
fun ZcButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    icon: Int? = null,
    enabled: Boolean = true,
    height: androidx.compose.ui.unit.Dp = 46.dp,
) {
    val p = zcPalette()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val contentAlpha = if (!enabled) 0.4f else if (pressed) 0.72f else 1f
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(if (primary) p.brand else Color.Transparent)
            .border(1.dp, if (primary) Color.Transparent else p.border, shape)
            .alpha(contentAlpha)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Image(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    colorFilter = ColorFilter.tint(if (primary) p.onBrand else p.text),
                )
            }
            if (icon != null) Box(Modifier.size(6.dp))
            ZcText(label, color = if (primary) p.onBrand else p.text, weight = FontWeight.Medium)
        }
    }
}

@Composable
fun ZcIconButton(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = zcPalette().text,
    size: androidx.compose.ui.unit.Dp = 40.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .alpha(if (pressed) 0.55f else 1f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            colorFilter = ColorFilter.tint(tint),
        )
    }
}

/** 扁平输入框：细边框、等宽字体默认开。 */
@Composable
fun ZcField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    mono: Boolean = true,
) {
    val p = zcPalette()
    val shape = RoundedCornerShape(10.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = p.text,
            fontSize = 15.sp,
            fontFamily = if (mono) FontFamily.Monospace else null,
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.surfaceAlt)
            .border(1.dp, p.border, shape)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    ZcText(placeholder, color = p.textFaint, mono = mono)
                }
                inner()
            }
        },
    )
}

data class ZcMenuItem(val label: String, val checked: Boolean = false)

/** 轻量弹出菜单，扁平面板样式。 */
@Composable
fun ZcMenu(
    items: List<ZcMenuItem>,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val p = zcPalette()
    Popup(alignment = Alignment.TopEnd, offset = IntOffset(0, 4.dp.value.toInt()), onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(p.surfaceAlt)
                .border(1.dp, p.border, RoundedCornerShape(12.dp))
                .padding(vertical = 6.dp),
        ) {
            items.forEachIndexed { index, item ->
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (pressed) 0.6f else 1f)
                        .clickable(interactionSource = interaction, indication = null) { onSelect(index) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ZcText(item.label, color = p.text, modifier = Modifier.weight(1f))
                    if (item.checked) {
                        Image(
                            painter = painterResource(dev.xray.zcode.R.drawable.ic_check),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            colorFilter = ColorFilter.tint(p.brand),
                        )
                    }
                }
            }
        }
    }
}
