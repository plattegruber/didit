package com.plattegruber.didit.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.plattegruber.didit.core.domain.model.HabitId
import com.plattegruber.didit.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Asks the launcher to place a widget showing one habit, for launchers that allow it. */
object WidgetPinning {
    fun isSupported(context: Context): Boolean = AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    fun request(context: Context, habitId: HabitId) {
        // The launcher adds the new widget's id to this intent, so it must stay mutable.
        val callback =
            PendingIntent.getBroadcast(
                context,
                habitId.value.toInt(),
                Intent(
                    context,
                    PinnedWidgetReceiver::class.java,
                ).putExtra(PinnedWidgetReceiver.EXTRA_HABIT_ID, habitId.value),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
        AppWidgetManager
            .getInstance(context)
            .requestPinAppWidget(ComponentName(context, DiditWidgetReceiver::class.java), null, callback)
    }
}

/** Points a freshly pinned widget at the habit it was pinned from. */
@AndroidEntryPoint
class PinnedWidgetReceiver : BroadcastReceiver() {
    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val habitId = intent.getLongExtra(EXTRA_HABIT_ID, -1L)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || habitId < 0) return

        val pending = goAsync()
        scope.launch {
            try {
                val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { preferences: Preferences ->
                    preferences.toMutablePreferences().apply {
                        WidgetConfig(target = WidgetConfig.Target.OneHabit(HabitId(habitId))).writeTo(this)
                    }
                }
                DiditWidget().update(context, glanceId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_HABIT_ID = "com.plattegruber.didit.extra.PINNED_HABIT_ID"
    }
}
