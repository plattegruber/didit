package com.plattegruber.didit.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.plattegruber.didit.R
import kotlin.math.min

private val CardRadius = 24.dp
private val CardPadding = 12.dp
private val ClearPadding = 4.dp
private val BackdropPadding = 16.dp

/** A stand-in wallpaper, so a clear widget has something to sit on. */
private val NightBackdrop = Brush.verticalGradient(listOf(Color(0xFF2B323A), Color(0xFF12161B)))
private val DayBackdrop = Brush.verticalGradient(listOf(Color(0xFFD9D2C6), Color(0xFFF4F0E9)))

/**
 * The widget as the launcher will draw it, from the same layout, layers and colours, shrunk to
 * fit the screen if need be. [size] is the widget's own size on the home screen.
 */
@Composable
fun WidgetPreview(
    model: WidgetModel,
    config: WidgetConfig,
    launcher: LauncherTheme,
    size: DpSize,
    modifier: Modifier = Modifier,
    night: Boolean = isSystemInDarkTheme(),
) {
    val context = LocalContext.current
    val palette = remember(config, launcher) { WidgetPalette.resolve(config, launcher, SystemColors.of(context)) }
    val padding = if (config.card) CardPadding else ClearPadding
    val inner = DpSize(size.width - padding * 2, size.height - padding * 2)
    val type = remember(config) { WidgetType(context, config) }
    val drawing =
        remember(model, config, inner) {
            widgetLayout(context, config, type).layout(model, inner.width.value, inner.height.value)
        }
    val layers = remember(drawing, type) { LayerRenderer(context, config, type).render(drawing) }
    val label = stringResource(R.string.widget_config_preview)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(if (night) NightBackdrop else DayBackdrop)
            .padding(BackdropPadding),
        contentAlignment = Alignment.Center,
    ) {
        val fit = min(1f, maxWidth / size.width)
        val card = palette.card
        Box(
            Modifier
                .size(size * fit)
                .then(
                    if (card != null) {
                        Modifier.clip(RoundedCornerShape(CardRadius * fit)).background(Color(card.pick(night)))
                    } else {
                        Modifier
                    },
                ).padding(padding * fit)
                .semantics { contentDescription = "$label: ${drawing.description}" },
        ) {
            Mask(layers.text, Color(palette.text.pick(night)))
            Mask(layers.glyphs, Color(palette.glyphs.pick(night)))
            layers.accents?.let {
                Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            }
        }
    }
}

@Composable
private fun Mask(mask: android.graphics.Bitmap, color: Color) {
    Image(
        bitmap = mask.asImageBitmap(),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.FillBounds,
        colorFilter = ColorFilter.tint(color),
    )
}
