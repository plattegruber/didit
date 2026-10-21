package com.plattegruber.didit.ui.text

import android.content.res.Resources
import com.plattegruber.didit.R
import com.plattegruber.didit.core.domain.draft.DraftError
import com.plattegruber.didit.core.domain.draft.HabitLimits
import com.plattegruber.didit.core.domain.format.AmountFormat
import com.plattegruber.didit.core.domain.viz.WindowSummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A summary as the design shows it: a number, and the small label under it. */
data class SummaryText(val value: String, val label: String)

/** User-facing words for days and values. Plain functions so the widget can share them. */
object DayText {
    fun summary(
        resources: Resources,
        summary: WindowSummary,
        unit: String,
        locale: Locale = Locale.getDefault(),
    ): SummaryText = when (summary) {
        is WindowSummary.Picks -> {
            SummaryText(AmountFormat.format(summary.total.toDouble(), locale), unit)
        }

        is WindowSummary.Total -> {
            SummaryText(AmountFormat.format(summary.total, locale), unit)
        }

        is WindowSummary.PerDay -> {
            SummaryText(
                String.format(locale, "%.1f", summary.average),
                resources.getString(R.string.summary_per_day),
            )
        }

        is WindowSummary.DaysDone -> {
            SummaryText(
                AmountFormat.format(summary.done.toDouble(), locale),
                resources.getString(R.string.summary_of_days, summary.of),
            )
        }
    }

    /** "Today · Tue, Oct 20", "Yesterday · Mon, Oct 19" or just "Sun, Oct 18". */
    fun sheetDate(
        resources: Resources,
        date: LocalDate,
        today: LocalDate,
        locale: Locale = Locale.getDefault(),
    ): String {
        val short = shortDate(date, locale)
        return when (date) {
            today -> resources.getString(R.string.date_today, short)
            today.minusDays(1) -> resources.getString(R.string.date_yesterday, short)
            else -> short
        }
    }

    fun shortDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(DateTimeFormatter.ofPattern("EEE, MMM d", locale))

    /** "Oct 7 – 20", or "Sep 28 – Oct 11" across a month boundary. */
    fun range(from: LocalDate, to: LocalDate, locale: Locale = Locale.getDefault()): String {
        val start = from.format(DateTimeFormatter.ofPattern("MMM d", locale))
        val end = to.format(DateTimeFormatter.ofPattern(if (from.month == to.month) "d" else "MMM d", locale))
        return "$start – $end"
    }

    fun month(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))

    fun draftError(resources: Resources, error: DraftError): String = when (error) {
        DraftError.NameBlank -> {
            resources.getString(R.string.error_name_blank)
        }

        DraftError.NameTooLong -> {
            resources.getQuantityString(
                R.plurals.error_name_too_long,
                HabitLimits.MAX_NAME_LENGTH,
                HabitLimits.MAX_NAME_LENGTH,
            )
        }

        DraftError.UnitTooLong -> {
            resources.getQuantityString(
                R.plurals.error_unit_too_long,
                HabitLimits.MAX_UNIT_LENGTH,
                HabitLimits.MAX_UNIT_LENGTH,
            )
        }

        DraftError.TooFewOptions -> {
            resources.getQuantityString(
                R.plurals.error_too_few_options,
                HabitLimits.MIN_OPTIONS,
                HabitLimits.MIN_OPTIONS,
            )
        }

        DraftError.TooManyOptions -> {
            resources.getQuantityString(
                R.plurals.error_too_many_options,
                HabitLimits.MAX_OPTIONS,
                HabitLimits.MAX_OPTIONS,
            )
        }

        DraftError.OptionLabelBlank -> {
            resources.getString(R.string.error_option_blank)
        }

        DraftError.OptionLabelTooLong -> {
            resources.getQuantityString(
                R.plurals.error_option_too_long,
                HabitLimits.MAX_OPTION_LABEL_LENGTH,
                HabitLimits.MAX_OPTION_LABEL_LENGTH,
            )
        }

        DraftError.DuplicateOptionLabels -> {
            resources.getString(R.string.error_option_duplicate)
        }
    }
}
