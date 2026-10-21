package com.plattegruber.didit.widget

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.plattegruber.didit.core.domain.model.HabitId

/** How one placed widget is set up. A fresh widget shows every habit, ink on the wallpaper. */
data class WidgetConfig(val target: Target = Target.AllHabits, val card: Boolean = false, val ink: Ink = Ink.Auto) {
    sealed interface Target {
        data object AllHabits : Target

        data class OneHabit(val id: HabitId) : Target
    }

    /**
     * The colour of the ink itself: [Light] ink suits a dark wallpaper, [Dark] ink a light one.
     * Auto follows the system's dark theme.
     */
    enum class Ink(val key: String) {
        Auto("auto"),
        Light("light"),
        Dark("dark"),
    }

    fun writeTo(preferences: MutablePreferences) {
        when (target) {
            Target.AllHabits -> preferences.remove(HABIT_ID)
            is Target.OneHabit -> preferences[HABIT_ID] = target.id.value
        }
        preferences[CARD] = card
        preferences[INK] = ink.key
    }

    companion object {
        private val HABIT_ID = longPreferencesKey("habit_id")
        private val CARD = booleanPreferencesKey("card")
        private val INK = stringPreferencesKey("ink")

        fun from(preferences: Preferences): WidgetConfig =
            WidgetConfig(
                target = preferences[HABIT_ID]?.let { Target.OneHabit(HabitId(it)) } ?: Target.AllHabits,
                card = preferences[CARD] ?: false,
                ink = Ink.entries.firstOrNull { it.key == preferences[INK] } ?: Ink.Auto,
            )
    }
}
