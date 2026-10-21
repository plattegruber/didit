package com.plattegruber.didit.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.plattegruber.didit.core.domain.repository.HabitRepository
import com.plattegruber.didit.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/** Keeps placed widgets in step with the data, wherever the change came from. */
@Singleton
class WidgetSync
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: HabitRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            // Bursts of edits (stepping a total up five times) collapse into one redraw.
            repository.observeChanges().debounce(DEBOUNCE_MILLIS).collect { DiditWidget().updateAll(context) }
        }
        scope.launch {
            if (hasWidgets(context)) MidnightRefreshWorker.schedule(context)
        }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 250L
    }
}

internal suspend fun hasWidgets(context: Context): Boolean =
    GlanceAppWidgetManager(context).getGlanceIds(DiditWidget::class.java).isNotEmpty()

/** Redraws widgets just after midnight, so "today" moves on even if nothing is logged. */
class MidnightRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        DiditWidget().updateAll(applicationContext)
        if (hasWidgets(applicationContext)) schedule(applicationContext, ExistingWorkPolicy.REPLACE)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "midnight-refresh"

        /** A minute's grace so the system clock has certainly ticked over. */
        private val GRACE: Duration = Duration.ofMinutes(1)

        fun schedule(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
            val now = ZonedDateTime.now()
            val nextMidnight = LocalDate.now(now.zone).plusDays(1).atStartOfDay(now.zone)
            val delay = Duration.between(now, nextMidnight).plus(GRACE)
            val request =
                OneTimeWorkRequestBuilder<MidnightRefreshWorker>()
                    .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
