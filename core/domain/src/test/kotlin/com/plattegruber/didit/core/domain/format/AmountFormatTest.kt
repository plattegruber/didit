package com.plattegruber.didit.core.domain.format

import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test

class AmountFormatTest {
    @Test
    fun `whole numbers drop the decimal point`() {
        assertThat(AmountFormat.format(12.0, Locale.US)).isEqualTo("12")
    }

    @Test
    fun `fractions keep at most two digits`() {
        assertThat(AmountFormat.format(2.5, Locale.US)).isEqualTo("2.5")
        assertThat(AmountFormat.format(1.0 / 3, Locale.US)).isEqualTo("0.33")
        assertThat(AmountFormat.format(0.125, Locale.US)).isEqualTo("0.13")
    }

    @Test
    fun `grouping and decimal separators follow the locale`() {
        assertThat(AmountFormat.format(1250.5, Locale.US)).isEqualTo("1,250.5")
        assertThat(AmountFormat.format(1250.5, Locale.GERMANY)).isEqualTo("1.250,5")
    }

    @Test
    fun `units are appended only when present`() {
        assertThat(AmountFormat.withUnit(12.0, "pages", Locale.US)).isEqualTo("12 pages")
        assertThat(AmountFormat.withUnit(12.0, " ", Locale.US)).isEqualTo("12")
    }

    @Test
    fun `parse accepts locale numbers`() {
        assertThat(AmountFormat.parse(" 12 ", Locale.US)).isEqualTo(12.0)
        assertThat(AmountFormat.parse("2.5", Locale.US)).isEqualTo(2.5)
        assertThat(AmountFormat.parse("2,5", Locale.GERMANY)).isEqualTo(2.5)
    }

    @Test
    fun `parse rejects anything else`() {
        assertThat(AmountFormat.parse("", Locale.US)).isNull()
        assertThat(AmountFormat.parse("abc", Locale.US)).isNull()
        assertThat(AmountFormat.parse("12abc", Locale.US)).isNull()
    }
}
