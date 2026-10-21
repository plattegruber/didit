package com.plattegruber.didit.ui.text

import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.plattegruber.didit.core.domain.model.Swatch
import com.plattegruber.didit.core.domain.viz.WindowSummary
import com.plattegruber.didit.testing.DesignSamples
import java.time.LocalDate
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS")
class DayTextTest {
    private val resources: Resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
    private val locale = Locale.US
    private val today = DesignSamples.TODAY

    @Test
    fun `summaries read like the design`() {
        val picks = WindowSummary.Picks(43, listOf(WindowSummary.Slice(Swatch.Ink, 43)))

        assertThat(DayText.summary(resources, picks, "meals", locale)).isEqualTo(SummaryText("43", "meals"))
        assertThat(DayText.summary(resources, WindowSummary.Total(320.0), "pages", locale))
            .isEqualTo(SummaryText("320", "pages"))
        assertThat(DayText.summary(resources, WindowSummary.PerDay(1.666), "cups", locale))
            .isEqualTo(SummaryText("1.7", "a day"))
        assertThat(DayText.summary(resources, WindowSummary.DaysDone(9, 14), "", locale))
            .isEqualTo(SummaryText("9", "of 14"))
    }

    @Test
    fun `sheet dates name today and yesterday`() {
        assertThat(DayText.sheetDate(resources, today, today, locale)).isEqualTo("Today · Tue, Oct 20")
        assertThat(DayText.sheetDate(resources, today.minusDays(1), today, locale))
            .isEqualTo("Yesterday · Mon, Oct 19")
        assertThat(DayText.sheetDate(resources, today.minusDays(2), today, locale)).isEqualTo("Sun, Oct 18")
    }

    @Test
    fun `ranges only repeat the month when it changes`() {
        assertThat(DayText.range(LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 20), locale))
            .isEqualTo("Oct 7 – 20")
        assertThat(
            DayText.range(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 11), locale),
        ).isEqualTo("Sep 28 – Oct 11")
    }
}
