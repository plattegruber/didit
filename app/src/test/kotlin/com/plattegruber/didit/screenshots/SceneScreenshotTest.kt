package com.plattegruber.didit.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.plattegruber.didit.testing.DesignSamples
import com.plattegruber.didit.ui.theme.DarkDiditColors
import com.plattegruber.didit.ui.theme.LightDiditColors
import com.plattegruber.didit.ui.viz.GlyphPainter
import com.plattegruber.didit.ui.viz.GlyphPalette
import com.plattegruber.didit.ui.viz.HabitScenes
import com.plattegruber.didit.ui.viz.labelTypeface
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.util.Locale

/** The painter on its own, drawing each scene from the design's data at 3x. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xxhdpi")
class SceneScreenshotTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val unit = 3f

    private fun render(
        widthDp: Float,
        heightDp: Float,
        dark: Boolean = true,
        draw: GlyphPainter.(Canvas, Float, Float) -> Unit,
    ): Bitmap {
        val colors = if (dark) DarkDiditColors else LightDiditColors
        val bitmap = createBitmap((widthDp * unit).toInt(), (heightDp * unit).toInt())
        val canvas = Canvas(bitmap)
        canvas.drawColor(colors.background.toArgb())
        GlyphPainter(GlyphPalette.full(colors.ink.toArgb()), unit, labelTypeface(context))
            .draw(canvas, bitmap.width.toFloat(), bitmap.height.toFloat())
        return bitmap
    }

    @Test
    fun rows() {
        val scene = HabitScenes.rows(DesignSamples.all, DesignSamples.TODAY, 14, Locale.US)
        render(206f, scene.height) { canvas, w, _ -> drawRows(canvas, scene, w) }.captureRoboImage()
    }

    @Test
    fun rowsLight() {
        val scene = HabitScenes.rows(DesignSamples.all, DesignSamples.TODAY, 14, Locale.US)
        render(206f, scene.height, dark = false) { canvas, w, _ -> drawRows(canvas, scene, w) }.captureRoboImage()
    }

    @Test
    fun strip() {
        val scene = HabitScenes.strip(DesignSamples.pagesHistory, DesignSamples.TODAY, 28, Locale.US)
        render(350f, 78f) { canvas, w, h -> drawStrip(canvas, scene, w, h) }.captureRoboImage()
    }

    @Test
    fun calendar() {
        val scene =
            HabitScenes.lastWeeks(
                history = DesignSamples.mealsHistory,
                today = DesignSamples.TODAY,
                weeks = 4,
                weekStart = DayOfWeek.MONDAY,
                locale = Locale.US,
            )
        render(170f, scene.height) { canvas, w, _ -> drawCalendar(canvas, scene, w) }.captureRoboImage()
    }
}
