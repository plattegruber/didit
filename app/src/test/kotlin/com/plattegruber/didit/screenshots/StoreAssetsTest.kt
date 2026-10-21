package com.plattegruber.didit.screenshots

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.plattegruber.didit.R
import com.plattegruber.didit.testing.DesignSamples
import com.plattegruber.didit.ui.home.HabitRowsCard
import com.plattegruber.didit.ui.theme.DiditTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val GRAPHICS = "src/main/play/listings/en-US/graphics"

/**
 * The Play listing's icon and feature graphic, drawn from the app's own resources and UI so they
 * never drift from it. Phone screenshots come from [StoreScreenshotTest].
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreAssetsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "w512dp-h512dp-night-mdpi")
    fun icon() {
        compose.setContent {
            Image(
                painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().background(colorResource(R.color.launcher_background)),
            )
        }
        compose.onRoot().captureRoboImage("$GRAPHICS/icon/icon.png")
    }

    @Test
    @Config(qualifiers = "w1024dp-h500dp-night-mdpi")
    fun featureGraphic() {
        compose.setContent {
            DiditTheme(darkTheme = true) {
                Row(
                    Modifier.fillMaxSize().background(DiditTheme.colors.background).padding(horizontal = 64.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(56.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("didit", style = DiditTheme.type.wordmark.copy(fontSize = 88.sp, lineHeight = 88.sp))
                        Text(
                            "Habits, at a glance.",
                            style = DiditTheme.type.screenTitle.copy(fontSize = 30.sp),
                            color = DiditTheme.colors.subtle,
                        )
                    }
                    HabitRowsCard(
                        histories = DesignSamples.all,
                        today = DesignSamples.TODAY,
                        dayCount = 14,
                        onRowClick = {},
                        onRowLongClick = {},
                        modifier = Modifier
                            .width(460.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(DiditTheme.colors.surface),
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("$GRAPHICS/feature-graphic/feature-graphic.png")
    }
}
