package com.plattegruber.didit.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.plattegruber.didit.MainActivity
import com.plattegruber.didit.core.domain.model.Habit
import com.plattegruber.didit.core.domain.model.HabitId
import com.plattegruber.didit.core.domain.model.HabitKind
import com.plattegruber.didit.core.domain.repository.HabitRepository
import com.plattegruber.didit.core.domain.time.AppClock
import com.plattegruber.didit.core.domain.time.today
import com.plattegruber.didit.quicklog.QuickLogActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** How the widget reaches the app graph; Glance creates widgets itself, outside of Hilt's reach. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun repository(): HabitRepository

    fun clock(): AppClock
}

internal fun Context.widgetEntryPoint(): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

class DiditWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = context.widgetEntryPoint()
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val actions = IntentActions(context, appWidgetId)
        provideContent {
            val config = WidgetConfig.from(currentState<Preferences>())
            // Read on every composition, so a refresh after midnight moves the widget on a day.
            val today = entryPoint.clock().today()
            val flow =
                remember(config.target, today) {
                    WidgetModel.observe(entryPoint.repository(), config.target, today)
                }
            val model by flow.collectAsState(WidgetModel.Loading)
            WidgetContent(model, config, actions)
        }
    }
}

private class IntentActions(private val context: Context, private val appWidgetId: Int) : WidgetActions {
    override fun log(habit: Habit): Action = if (habit.kind == HabitKind.Done) {
        actionRunCallback<ToggleDoneAction>(actionParametersOf(ToggleDoneAction.HabitKey to habit.id.value))
    } else {
        actionStartActivity(QuickLogActivity.intent(context, habit.id))
    }

    override fun openApp(): Action = actionStartActivity(MainActivity.home(context))

    override fun openHistory(habit: Habit): Action = actionStartActivity(MainActivity.openHabit(context, habit.id))

    override fun configure(): Action = actionStartActivity(
        Intent(context, WidgetConfigActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/** "One tap from the widget logs it": yes/no habits toggle today without opening anything. */
class ToggleDoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val habitId = parameters[HabitKey] ?: return
        val entryPoint = context.widgetEntryPoint()
        entryPoint.repository().toggleDone(HabitId(habitId), entryPoint.clock().today())
    }

    companion object {
        val HabitKey = ActionParameters.Key<Long>("habit_id")
    }
}

class DiditWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DiditWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        MidnightRefreshWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        MidnightRefreshWorker.cancel(context)
    }
}
