package com.plattegruber.didit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.plattegruber.didit.core.domain.model.HabitId
import com.plattegruber.didit.ui.navigation.DiditNavigation
import com.plattegruber.didit.ui.theme.DiditTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val openHabitRequests = Channel<HabitId>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // After a configuration change the back stack is restored, so the request is already honoured.
        if (savedInstanceState == null) handle(intent)
        setContent {
            DiditTheme {
                DiditNavigation(openHabitRequests.receiveAsFlow())
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        val id = intent.getLongExtra(EXTRA_HABIT_ID, NO_HABIT)
        if (id != NO_HABIT) openHabitRequests.trySend(HabitId(id))
    }

    companion object {
        private const val EXTRA_HABIT_ID = "com.plattegruber.didit.extra.HABIT_ID"
        private const val NO_HABIT = -1L

        fun home(context: Context): Intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** Opens the app on [habitId]'s history. */
        fun openHabit(context: Context, habitId: HabitId): Intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_HABIT_ID, habitId.value)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
