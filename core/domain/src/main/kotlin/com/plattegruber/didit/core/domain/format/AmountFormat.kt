package com.plattegruber.didit.core.domain.format

import java.math.RoundingMode
import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Locale

/** Formats amounts the way people write them: "12", "2.5", "1,250", never "12.0". */
object AmountFormat {
    private const val MAX_FRACTION_DIGITS = 2

    fun format(amount: Double, locale: Locale = Locale.getDefault()): String =
        NumberFormat
            .getNumberInstance(locale)
            .apply {
                maximumFractionDigits = MAX_FRACTION_DIGITS
                minimumFractionDigits = 0
                roundingMode = RoundingMode.HALF_UP
            }.format(amount)

    /** "12 pages", or just "12" for a unitless habit. */
    fun withUnit(amount: Double, unit: String, locale: Locale = Locale.getDefault()): String {
        val number = format(amount, locale)
        return if (unit.isBlank()) number else "$number $unit"
    }

    /** Parses user input in [locale]; null unless the whole text is one finite number. */
    fun parse(text: String, locale: Locale = Locale.getDefault()): Double? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val position = ParsePosition(0)
        val number = NumberFormat.getNumberInstance(locale).parse(trimmed, position) ?: return null
        if (position.index != trimmed.length) return null
        return number.toDouble().takeIf(Double::isFinite)
    }
}
