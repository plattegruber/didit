package com.plattegruber.didit.widget

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.plattegruber.didit.core.domain.model.HabitId

/** How one placed widget is set up. A fresh widget shows every habit, ink on the wallpaper. */
data class WidgetConfig(
    val target: Target = Target.AllHabits,
    val card: Boolean = false,
    val ink: Ink = Ink.Auto,
    val palette: Palette = Palette.Didit,
    val custom: CustomColors = CustomColors(),
    /** How opaque the card is, 0 to 100. */
    val cardOpacity: Int = FULL_OPACITY,
    val font: TextFont = TextFont.System,
    val labelFont: LabelFont = LabelFont.GeistMono,
    val textSize: TextSize = TextSize.Medium,
    val days: Days = Days.Auto,
    val hidden: Set<Element> = emptySet(),
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
     * colours; [Custom] uses [custom]. The colours given to a "one of" habit's choices never change.
     */
    enum class Palette(val key: String) {
        Didit("didit"),
        Launcher("launcher"),
        Custom("custom"),
    }

    /** Hand-picked colours, as ARGB. They start as Didit's own dark look. */
    data class CustomColors(
        val text: Int = 0xFFECE8DF.toInt(),
        val glyphs: Int = 0xFFECE8DF.toInt(),
        val card: Int = 0xFF1A1F26.toInt(),
    )

    /** The face for names, totals and the word mark. System follows the phone's own font. */
    enum class TextFont(val key: String) {
        System("system"),
        Geist("geist"),
        Inter("inter"),
        SpaceGrotesk("space_grotesk"),
        Manrope("manrope"),
        Fraunces("fraunces"),
        Serif("serif"),
    }

    /** The face for small labels: dates, weekday letters, units and choice names. */
    enum class LabelFont(val key: String) {
        GeistMono("geist_mono"),
        JetBrainsMono("jetbrains_mono"),
        SystemMono("system_mono"),
        SameAsText("same"),
    }

    /** Every text size is scaled by [scale], on top of the phone's own font size setting. */
    enum class TextSize(val key: String, val scale: Float) {
        Small("small", 0.85f),
        Medium("medium", 1f),
        Large("large", 1.15f),
        ExtraLarge("extra_large", 1.3f),
    }

    /** How many days the rows and strip show; [Auto] fits as many as the width allows. */
    enum class Days(val key: String, val count: Int?) {
        Auto("auto", null),
        OneWeek("7", 7),
        TwoWeeks("14", 14),
        ThreeWeeks("21", 21),
        FourWeeks("28", 28),
    }

    /** The parts of the widget that can be hidden. */
    enum class Element(val key: String) {
        /** The "didit" word mark, or a single habit's name. */
        Header("header"),

        /** The date range over the rows, or the number of weeks a single habit shows. */
        DateRange("dates"),
        Names("names"),

        /** The labels under a "one of" habit's name, in their choice colours. */
        Legend("legend"),
        Totals("totals"),

        /** The words under each total: the unit, "a day", "of 14". */
        Captions("captions"),

        /** The bar showing a "one of" habit's mix of choices. */
        Mix("mix"),

        /** Weekday letters, or the dates under a strip. */
        Weekdays("weekdays"),

        /** The highlight behind today and weekends. */
        Highlights("highlights"),

        /** The faint marks on days with nothing logged. */
        EmptyDays("empty"),
    }

    fun shows(element: Element): Boolean = element !in hidden

    fun writeTo(preferences: MutablePreferences) {
        when (target) {
            Target.AllHabits -> preferences.remove(HABIT_ID)
            is Target.OneHabit -> preferences[HABIT_ID] = target.id.value
        }
        preferences[CARD] = card
        preferences[INK] = ink.key
        preferences[PALETTE] = palette.key
        preferences[CUSTOM_TEXT] = custom.text
        preferences[CUSTOM_GLYPHS] = custom.glyphs
        preferences[CUSTOM_CARD] = custom.card
        preferences[CARD_OPACITY] = cardOpacity
        preferences[FONT] = font.key
        preferences[LABEL_FONT] = labelFont.key
        preferences[TEXT_SIZE] = textSize.key
        preferences[DAYS] = days.key
        preferences[HIDDEN] = hidden.map { it.key }.toSet()
    }

    companion object {
        const val FULL_OPACITY = 100

        private val HABIT_ID = longPreferencesKey("habit_id")
        private val CARD = booleanPreferencesKey("card")
        private val INK = stringPreferencesKey("ink")
        private val PALETTE = stringPreferencesKey("palette")
        private val CUSTOM_TEXT = intPreferencesKey("custom_text")
        private val CUSTOM_GLYPHS = intPreferencesKey("custom_glyphs")
        private val CUSTOM_CARD = intPreferencesKey("custom_card")
        private val CARD_OPACITY = intPreferencesKey("card_opacity")
        private val FONT = stringPreferencesKey("font")
        private val LABEL_FONT = stringPreferencesKey("label_font")
        private val TEXT_SIZE = stringPreferencesKey("text_size")
        private val DAYS = stringPreferencesKey("days")
        private val HIDDEN = stringSetPreferencesKey("hidden")

        private inline fun <reified T : Enum<T>> Preferences.choice(
            key: Preferences.Key<String>,
            fallback: T,
            keyOf: (T) -> String,
        ): T = enumValues<T>().firstOrNull { keyOf(it) == this[key] } ?: fallback

        fun from(preferences: Preferences): WidgetConfig {
            val defaults = WidgetConfig()
            return WidgetConfig(
                target = preferences[HABIT_ID]?.let { Target.OneHabit(HabitId(it)) } ?: Target.AllHabits,
                card = preferences[CARD] ?: defaults.card,
                ink = preferences.choice(INK, defaults.ink) { it.key },
                palette = preferences.choice(PALETTE, defaults.palette) { it.key },
                custom =
                    CustomColors(
                        text = preferences[CUSTOM_TEXT] ?: defaults.custom.text,
                        glyphs = preferences[CUSTOM_GLYPHS] ?: defaults.custom.glyphs,
                        card = preferences[CUSTOM_CARD] ?: defaults.custom.card,
                    ),
                cardOpacity = (preferences[CARD_OPACITY] ?: defaults.cardOpacity).coerceIn(0, FULL_OPACITY),
                font = preferences.choice(FONT, defaults.font) { it.key },
                labelFont = preferences.choice(LABEL_FONT, defaults.labelFont) { it.key },
                textSize = preferences.choice(TEXT_SIZE, defaults.textSize) { it.key },
                days = preferences.choice(DAYS, defaults.days) { it.key },
                hidden =
                    preferences[HIDDEN]
                        .orEmpty()
                        .mapNotNull { key -> Element.entries.firstOrNull { it.key == key } }
                        .toSet(),
            )
        }
    }
}
