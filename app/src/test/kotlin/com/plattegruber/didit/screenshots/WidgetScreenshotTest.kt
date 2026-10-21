package com.plattegruber.didit.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.plattegruber.didit.core.domain.model.Habit
import com.plattegruber.didit.testing.DesignSamples
import com.plattegruber.didit.widget.WidgetActions
import com.plattegruber.didit.widget.WidgetConfig
import com.plattegruber.didit.widget.WidgetContent
import com.plattegruber.didit.widget.WidgetModel
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private class NoopCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) = Unit
}

private object NoopActions : WidgetActions {
    override fun log(habit: Habit): Action = actionRunCallback<NoopCallback>()

    override fun openApp(): Action = actionRunCallback<NoopCallback>()

    override fun openHistory(habit: Habit): Action = actionRunCallback<NoopCallback>()

    override fun configure(): Action = actionRunCallback<NoopCallback>()
}

private class FixedWidget(private val model: WidgetModel, private val config: WidgetConfig) : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetContent(model, config, NoopActions) }
    }
}

/**
 * The widget as the launcher would inflate it, over the design's ground. Ink is pinned to light:
 * Robolectric doesn't resolve RemoteViews' day/night colours, so "Auto" can't be shown here. Glance composes to
 * RemoteViews, which are applied to a real view and drawn.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "night-xxhdpi")
class WidgetScreenshotTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private suspend fun render(model: WidgetModel, config: WidgetConfig, size: DpSize): Bitmap {
        val views = FixedWidget(model, config).compose(context, size = size)
        val density = context.resources.displayMetrics.density
        val width = (size.width.value * density).toInt()
        val height = (size.height.value * density).toInt()
        val host = FrameLayout(context)
        val view = views.apply(context, host)
        host.addView(view, FrameLayout.LayoutParams(width, height))
        host.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, width, height)
        return createBitmap(width, height).also { bitmap ->
            Canvas(bitmap).apply {
                drawColor(GROUND)
                host.draw(this)
            }
        }
    }

    @Test
    fun allHabits() = runTest {
        render(
            WidgetModel.AllHabits(DesignSamples.all, DesignSamples.TODAY),
            WidgetConfig(ink = WidgetConfig.Ink.Light),
            DpSize(366.dp, 290.dp),
        ).captureRoboImage()
    }

    @Test
    fun allHabitsCard() = runTest {
        render(
            WidgetModel.AllHabits(DesignSamples.all, DesignSamples.TODAY),
            WidgetConfig(card = true, ink = WidgetConfig.Ink.Light),
            DpSize(366.dp, 290.dp),
        ).captureRoboImage()
    }

    @Test
    fun oneHabitStrip() = runTest {
        render(
            WidgetModel.OneHabit(DesignSamples.pagesHistory, DesignSamples.TODAY),
            WidgetConfig(ink = WidgetConfig.Ink.Light),
            DpSize(350.dp, 112.dp),
        ).captureRoboImage()
    }

    @Test
    fun oneHabitCalendar() = runTest {
        render(
            WidgetModel.OneHabit(DesignSamples.mealsHistory, DesignSamples.TODAY),
            WidgetConfig(ink = WidgetConfig.Ink.Light),
            DpSize(178.dp, 178.dp),
        ).captureRoboImage()
    }

    @Test
    fun habitMissing() = runTest {
        render(
            WidgetModel.HabitMissing,
            WidgetConfig(ink = WidgetConfig.Ink.Light),
            DpSize(300.dp, 80.dp),
        ).captureRoboImage()
    }

    private companion object {
        /** The design's ground, standing in for a dark wallpaper. */
        const val GROUND = 0xFF111519.toInt()
    }
}
