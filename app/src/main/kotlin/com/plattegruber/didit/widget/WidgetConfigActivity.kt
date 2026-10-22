package com.plattegruber.didit.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.plattegruber.didit.MainActivity
import com.plattegruber.didit.R
import com.plattegruber.didit.core.domain.model.Habit
import com.plattegruber.didit.core.domain.model.Swatch
import com.plattegruber.didit.core.domain.repository.HabitRepository
import com.plattegruber.didit.core.domain.time.AppClock
import com.plattegruber.didit.core.domain.time.today
import com.plattegruber.didit.ui.components.DashedButton
import com.plattegruber.didit.ui.components.Dot
import com.plattegruber.didit.ui.components.Overline
import com.plattegruber.didit.ui.components.PrimaryButton
import com.plattegruber.didit.ui.components.RadioMark
import com.plattegruber.didit.ui.components.SelectableCard
import com.plattegruber.didit.ui.theme.DarkDiditColors
import com.plattegruber.didit.ui.theme.DiditTheme
import com.plattegruber.didit.ui.theme.SwatchColors
import com.plattegruber.didit.widget.WidgetConfig.Element
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class WidgetConfigViewModel
@Inject
constructor(
    savedState: SavedStateHandle,
    repository: HabitRepository,
    launcherTheme: LauncherThemeStore,
    clock: AppClock,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    val appWidgetId: Int = savedState[AppWidgetManager.EXTRA_APPWIDGET_ID] ?: AppWidgetManager.INVALID_APPWIDGET_ID

    val habits: StateFlow<List<Habit>?> =
        repository
            .observeHabits()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val launcher: StateFlow<LauncherTheme> =
        launcherTheme.theme.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            LauncherTheme.None,
        )

    var config by mutableStateOf(WidgetConfig())
        private set

    /** What the widget shows, for the preview; follows the habit being picked. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val model: StateFlow<WidgetModel> =
        snapshotFlow { config.target }
            .distinctUntilChanged()
            .flatMapLatest { WidgetModel.observe(repository, it, clock.today()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), WidgetModel.Loading)

    /** The widget's size on the home screen, in portrait; a typical 4 x 3 until the launcher says. */
    val size: DpSize =
        AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId).let { options ->
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            if (width > 0 && height > 0) DpSize(width.dp, height.dp) else DefaultSize
        }

    init {
        viewModelScope.launch {
            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
            config = WidgetConfig.from(getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId))
        }
    }

    fun update(change: (WidgetConfig) -> WidgetConfig) {
        config = change(config)
    }

    /** Saves the configuration and redraws the widget; [onSaved] runs once both are done. */
    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { preferences: Preferences ->
                preferences.toMutablePreferences().apply { config.writeTo(this) }
            }
            DiditWidget().update(context, glanceId)
            onSaved()
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        val DefaultSize = DpSize(366.dp, 290.dp)
    }
}

/** Chooses what a widget shows and how it looks, with a live preview. */
@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {
    private val viewModel: WidgetConfigViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (viewModel.appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        // The defaults are a fine widget, so backing out keeps it rather than removing it.
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, viewModel.appWidgetId))
        setContent {
            DiditTheme {
                val habits by viewModel.habits.collectAsStateWithLifecycle()
                val launcher by viewModel.launcher.collectAsStateWithLifecycle()
                val model by viewModel.model.collectAsStateWithLifecycle()
                WidgetConfigContent(
                    habits = habits,
                    config = viewModel.config,
                    launcher = launcher,
                    model = model,
                    widgetSize = viewModel.size,
                    onChange = viewModel::update,
                    onOpenApp = { startActivity(MainActivity.home(this)) },
                    onDone = { viewModel.save(::finish) },
                )
            }
        }
    }
}

@Composable
fun WidgetConfigContent(
    habits: List<Habit>?,
    config: WidgetConfig,
    launcher: LauncherTheme,
    model: WidgetModel,
    widgetSize: DpSize,
    onChange: ((WidgetConfig) -> WidgetConfig) -> Unit,
    onOpenApp: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    now: Instant = Instant.now(),
) {
    val colors = DiditTheme.colors
    Surface(modifier.fillMaxSize(), color = colors.background, contentColor = colors.ink) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding().imePadding()) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)) {
                Text(stringResource(R.string.widget_config_title), style = DiditTheme.type.screenTitle)
                Spacer(Modifier.height(16.dp))
                WidgetPreview(model = model, config = config, launcher = launcher, size = widgetSize)
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                HabitsSection(habits, config, onChange, onOpenApp)
                LayoutSection(config, onChange)
                TextSection(config, onChange)
                ColoursSection(config, launcher, now, onChange)
                PartsSection(config, onChange)
            }
            PrimaryButton(
                text = stringResource(R.string.widget_config_place),
                onClick = onDone,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp, top = 8.dp),
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Overline(title)
        content()
    }
}

@Composable
private fun HabitsSection(
    habits: List<Habit>?,
    config: WidgetConfig,
    onChange: ((WidgetConfig) -> WidgetConfig) -> Unit,
    onOpenApp: () -> Unit,
) {
    Section(stringResource(R.string.widget_config_show)) {
        if (habits != null && habits.isEmpty()) {
            Text(
                stringResource(R.string.widget_config_empty),
                style = DiditTheme.type.body,
                color = DiditTheme.colors.muted,
            )
            DashedButton(stringResource(R.string.widget_config_open_app), onOpenApp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TargetOption(
                    label = stringResource(R.string.widget_config_all),
                    selected = config.target == WidgetConfig.Target.AllHabits,
                    onClick = { onChange { it.copy(target = WidgetConfig.Target.AllHabits) } },
                )
                habits.orEmpty().forEach { habit ->
                    val target = WidgetConfig.Target.OneHabit(habit.id)
                    TargetOption(
                        label = habit.name,
                        selected = config.target == target,
                        onClick = { onChange { it.copy(target = target) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun LayoutSection(config: WidgetConfig, onChange: ((WidgetConfig) -> WidgetConfig) -> Unit) {
    Section(stringResource(R.string.widget_config_layout)) {
        Label(stringResource(R.string.widget_config_days))
        Chips(
            options = WidgetConfig.Days.entries,
            selected = config.days,
            label = { days -> days.count?.toString() ?: stringResource(R.string.widget_days_auto) },
            onSelect = { days -> onChange { it.copy(days = days) } },
        )
        ToggleRow(
            title = stringResource(R.string.widget_config_card),
            hint = stringResource(R.string.widget_config_card_hint),
            checked = config.card,
            onCheckedChange = { checked -> onChange { it.copy(card = checked) } },
        )
        if (config.card) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.widget_config_card_opacity),
                    style = DiditTheme.type.option,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.widget_config_percent, config.cardOpacity),
                    style = DiditTheme.type.heroLabel,
                    color = DiditTheme.colors.muted,
                )
            }
            Slider(
                value = config.cardOpacity.toFloat(),
                onValueChange = { value ->
                    onChange {
                        it.copy(
                            cardOpacity =
                                (value / OPACITY_STEP).roundToInt() * OPACITY_STEP,
                        )
                    }
                },
                valueRange = 0f..WidgetConfig.FULL_OPACITY.toFloat(),
                steps = WidgetConfig.FULL_OPACITY / OPACITY_STEP - 1,
                colors =
                    SliderDefaults.colors(
                        thumbColor = DiditTheme.colors.ink,
                        activeTrackColor = DiditTheme.colors.ink,
                        inactiveTrackColor = DiditTheme.colors.field,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent,
                    ),
            )
        }
    }
}

@Composable
private fun TextSection(config: WidgetConfig, onChange: ((WidgetConfig) -> WidgetConfig) -> Unit) {
    Section(stringResource(R.string.widget_config_text)) {
        Label(stringResource(R.string.widget_config_font))
        Chips(
            options = WidgetConfig.TextFont.entries,
            selected = config.font,
            label = { font -> fontName(font) },
            fontFamily = ::fontFamily,
            onSelect = { font -> onChange { it.copy(font = font) } },
        )
        Label(stringResource(R.string.widget_config_label_font))
        Chips(
            options = WidgetConfig.LabelFont.entries,
            selected = config.labelFont,
            label = { font -> labelFontName(font) },
            fontFamily = { font -> labelFontFamily(font, config.font) },
            onSelect = { font -> onChange { it.copy(labelFont = font) } },
        )
        Label(stringResource(R.string.widget_config_size))
        Chips(
            options = WidgetConfig.TextSize.entries,
            selected = config.textSize,
            label = { size ->
                stringResource(
                    when (size) {
                        WidgetConfig.TextSize.Small -> R.string.widget_size_small
                        WidgetConfig.TextSize.Medium -> R.string.widget_size_medium
                        WidgetConfig.TextSize.Large -> R.string.widget_size_large
                        WidgetConfig.TextSize.ExtraLarge -> R.string.widget_size_extra_large
                    },
                )
            },
            onSelect = { size -> onChange { it.copy(textSize = size) } },
        )
    }
}

@Composable
private fun ColoursSection(
    config: WidgetConfig,
    launcher: LauncherTheme,
    now: Instant,
    onChange: ((WidgetConfig) -> WidgetConfig) -> Unit,
) {
    Section(stringResource(R.string.widget_config_palette)) {
        Chips(
            options = WidgetConfig.Palette.entries,
            selected = config.palette,
            label = { palette ->
                stringResource(
                    when (palette) {
                        WidgetConfig.Palette.Didit -> R.string.widget_palette_didit
                        WidgetConfig.Palette.Launcher -> R.string.widget_palette_launcher
                        WidgetConfig.Palette.Custom -> R.string.widget_palette_custom
                    },
                )
            },
            onSelect = { palette -> onChange { it.copy(palette = palette) } },
        )
        if (config.palette == WidgetConfig.Palette.Launcher) LauncherStatus(launcher, now)
        if (config.palette == WidgetConfig.Palette.Custom) {
            val presets = colourPresets(launcher)
            ColourPicker(stringResource(R.string.widget_custom_text), config.custom.text, presets) { argb ->
                onChange { it.copy(custom = it.custom.copy(text = argb)) }
            }
            ColourPicker(stringResource(R.string.widget_custom_glyphs), config.custom.glyphs, presets) { argb ->
                onChange { it.copy(custom = it.custom.copy(glyphs = argb)) }
            }
            if (config.card) {
                ColourPicker(stringResource(R.string.widget_custom_card), config.custom.card, presets) { argb ->
                    onChange { it.copy(custom = it.custom.copy(card = argb)) }
                }
            }
        } else {
            Label(stringResource(R.string.widget_config_ink))
            Chips(
                options = WidgetConfig.Ink.entries,
                selected = config.ink,
                label = { ink ->
                    stringResource(
                        when (ink) {
                            WidgetConfig.Ink.Auto -> R.string.widget_ink_auto
                            WidgetConfig.Ink.Light -> R.string.widget_ink_light
                            WidgetConfig.Ink.Dark -> R.string.widget_ink_dark
                        },
                    )
                },
                onSelect = { ink -> onChange { it.copy(ink = ink) } },
            )
        }
    }
}

@Composable
private fun PartsSection(config: WidgetConfig, onChange: ((WidgetConfig) -> WidgetConfig) -> Unit) {
    Section(stringResource(R.string.widget_config_parts)) {
        Element.entries.forEach { element ->
            ToggleRow(
                title = stringResource(element.label()),
                checked = config.shows(element),
                onCheckedChange = { shown ->
                    onChange { it.copy(hidden = if (shown) it.hidden - element else it.hidden + element) }
                },
            )
        }
    }
}

private fun Element.label(): Int = when (this) {
    Element.Header -> R.string.widget_part_header
    Element.DateRange -> R.string.widget_part_dates
    Element.Names -> R.string.widget_part_names
    Element.Legend -> R.string.widget_part_legend
    Element.Totals -> R.string.widget_part_totals
    Element.Captions -> R.string.widget_part_captions
    Element.Mix -> R.string.widget_part_mix
    Element.Weekdays -> R.string.widget_part_weekdays
    Element.Highlights -> R.string.widget_part_highlights
    Element.EmptyDays -> R.string.widget_part_empty
}

@Composable
private fun fontName(font: WidgetConfig.TextFont): String = when (font) {
    WidgetConfig.TextFont.System -> stringResource(R.string.widget_font_system)
    WidgetConfig.TextFont.Serif -> stringResource(R.string.widget_font_serif)
    WidgetConfig.TextFont.Geist -> "Geist"
    WidgetConfig.TextFont.Inter -> "Inter"
    WidgetConfig.TextFont.SpaceGrotesk -> "Space Grotesk"
    WidgetConfig.TextFont.Manrope -> "Manrope"
    WidgetConfig.TextFont.Fraunces -> "Fraunces"
}

@Composable
private fun labelFontName(font: WidgetConfig.LabelFont): String = when (font) {
    WidgetConfig.LabelFont.GeistMono -> "Geist Mono"
    WidgetConfig.LabelFont.JetBrainsMono -> "JetBrains Mono"
    WidgetConfig.LabelFont.SystemMono -> stringResource(R.string.widget_font_system_mono)
    WidgetConfig.LabelFont.SameAsText -> stringResource(R.string.widget_font_same)
}

/** Each font's chip is set in that font, so the choice shows itself. */
private fun fontFamily(font: WidgetConfig.TextFont): FontFamily = when (font) {
    WidgetConfig.TextFont.System -> FontFamily.SansSerif
    WidgetConfig.TextFont.Serif -> FontFamily.Serif
    WidgetConfig.TextFont.Geist -> FontFamily(Font(R.font.geist))
    WidgetConfig.TextFont.Inter -> FontFamily(Font(R.font.inter))
    WidgetConfig.TextFont.SpaceGrotesk -> FontFamily(Font(R.font.space_grotesk))
    WidgetConfig.TextFont.Manrope -> FontFamily(Font(R.font.manrope))
    WidgetConfig.TextFont.Fraunces -> FontFamily(Font(R.font.fraunces))
}

private fun labelFontFamily(font: WidgetConfig.LabelFont, text: WidgetConfig.TextFont): FontFamily = when (font) {
    WidgetConfig.LabelFont.GeistMono -> FontFamily(Font(R.font.geist_mono))
    WidgetConfig.LabelFont.JetBrainsMono -> FontFamily(Font(R.font.jetbrains_mono))
    WidgetConfig.LabelFont.SystemMono -> FontFamily.Monospace
    WidgetConfig.LabelFont.SameAsText -> fontFamily(text)
}

/** Colours to pick from: Didit's own, its choice colours, plain white and black, and the launcher's. */
private fun colourPresets(launcher: LauncherTheme): List<Int> = (
    listOf(DarkDiditColors.ink, DarkDiditColors.surface, DarkDiditColors.background).map { it.toArgb() } +
        listOf(Swatch.Blue, Swatch.Amber, Swatch.Coral, Swatch.Lilac, Swatch.Sage).map(SwatchColors::argb) +
        listOf(WHITE, BLACK) +
        listOfNotNull(launcher.accent, launcher.text, launcher.surface, launcher.onSurface)
    ).distinct()

private const val WHITE = 0xFFFFFFFF.toInt()
private const val BLACK = 0xFF000000.toInt()
private const val OPACITY_STEP = 10
private const val RGB_MASK = 0xFFFFFF

@Composable
private fun ColourPicker(title: String, selected: Int, presets: List<Int>, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label(title)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            presets.forEach { argb ->
                val isSelected = argb == selected
                val name = stringResource(R.string.widget_custom_colour, "#%06X".format(argb and RGB_MASK))
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) DiditTheme.colors.ink else DiditTheme.colors.lineStrong,
                            shape = CircleShape,
                        ).selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(argb) })
                        .semantics { contentDescription = name },
                    contentAlignment = Alignment.Center,
                ) {
                    Dot(Color(argb), if (isSelected) 24.dp else 30.dp)
                }
            }
        }
    }
}

/** Whether Niagara's colours have reached Didit, and which they are. */
@Composable
private fun LauncherStatus(launcher: LauncherTheme, now: Instant, modifier: Modifier = Modifier) {
    val heardAt = launcher.heardAt
    val text =
        when {
            heardAt != null && !launcher.isEmpty -> {
                val ago =
                    DateUtils
                        .getRelativeTimeSpanString(
                            heardAt.toEpochMilli(),
                            now.toEpochMilli(),
                            DateUtils.MINUTE_IN_MILLIS,
                        )
                        .toString()
                stringResource(R.string.widget_palette_heard, ago)
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                stringResource(R.string.widget_palette_waiting_system)
            }

            else -> {
                stringResource(R.string.widget_palette_waiting_didit)
            }
        }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        listOfNotNull(launcher.accent, launcher.text, launcher.surface).forEach { argb ->
            Dot(Color(argb), 12.dp, Modifier.border(1.dp, DiditTheme.colors.lineStrong, CircleShape))
        }
        Text(text, style = DiditTheme.type.caption, color = DiditTheme.colors.muted, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = DiditTheme.type.option)
}

@Composable
private fun <T> Chips(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    fontFamily: ((T) -> FontFamily)? = null,
) {
    val colors = DiditTheme.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            val shape = RoundedCornerShape(14.dp)
            Box(
                Modifier
                    .clip(shape)
                    .border(1.5.dp, if (isSelected) colors.ink else colors.line, shape)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) })
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                val style = DiditTheme.type.chip
                Text(
                    label(option),
                    style = fontFamily?.let { style.copy(fontFamily = it(option)) } ?: style,
                    color = if (isSelected) colors.ink else colors.muted,
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, hint: String? = null) {
    val colors = DiditTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = DiditTheme.type.option)
            if (hint != null) Text(hint, style = DiditTheme.type.caption, color = colors.muted)
        }
        Spacer(Modifier.padding(start = 12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = colors.onInk,
                    checkedTrackColor = colors.ink,
                    uncheckedThumbColor = colors.muted,
                    uncheckedTrackColor = colors.field,
                    uncheckedBorderColor = colors.lineStrong,
                ),
        )
    }
}

@Composable
private fun TargetOption(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SelectableCard(selected = selected, onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.height(52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = DiditTheme.type.option, modifier = Modifier.weight(1f))
            RadioMark(selected)
        }
    }
}
