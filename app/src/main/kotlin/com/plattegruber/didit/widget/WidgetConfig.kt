package com.plattegruber.didit.widget

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.plattegruber.didit.core.domain.model.HabitId

/** How one placed widget is set up. A fresh widget shows every habit, ink on the wallpaper. */
data class WidgetConfig(
    val target: Target = Target.AllHabits,
    val card: Boolean = false,
    val ink: Ink = Ink.Auto,
    val palette: Palette = Palette.Didit,
) {
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

    /**
     * Where the widget's own colours come from. [Launcher] takes the launcher's accent, text and
     * surface colours (Niagara's, when it has shared them), else the system's Material You
     * colours. The colours given to a "one of" habit's choices never change.
     */
    enum class Palette(val key: String) {
        Didit("didit"),
        Launcher("launcher"),
    }

    fun writeTo(preferences: MutablePreferences) {
        when (target) {
            Target.AllHabits -> preferences.remove(HABIT_ID)
            is Target.OneHabit -> preferences[HABIT_ID] = target.id.value
        }
        preferences[CARD] = card
        preferences[INK] = ink.key
        preferences[PALETTE] = palette.key
    }

    companion object {
        private val HABIT_ID = longPreferencesKey("habit_id")
        private val CARD = booleanPreferencesKey("card")
        private val INK = stringPreferencesKey("ink")
        private val PALETTE = stringPreferencesKey("palette")

        fun from(preferences: Preferences): WidgetConfig = WidgetConfig(
            target = preferences[HABIT_ID]?.let { Target.OneHabit(HabitId(it)) } ?: Target.AllHabits,
            card = preferences[CARD] ?: false,
            ink = Ink.entries.firstOrNull { it.key == preferences[INK] } ?: Ink.Auto,
            palette = Palette.entries.firstOrNull { it.key == preferences[PALETTE] } ?: Palette.Didit,
        )
    }
}
