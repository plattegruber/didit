package com.plattegruber.didit.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.plattegruber.didit.core.domain.repository.HabitRepository
import com.plattegruber.didit.ui.components.DashedButton
import com.plattegruber.didit.ui.components.Overline
import com.plattegruber.didit.ui.components.PrimaryButton
import com.plattegruber.didit.ui.components.RadioMark
import com.plattegruber.didit.ui.components.SelectableCard
import com.plattegruber.didit.ui.theme.DiditTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WidgetConfigViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        repository: HabitRepository,
        @param:ApplicationContext private val context: Context,
    ) : ViewModel() {
        val appWidgetId: Int = savedState[AppWidgetManager.EXTRA_APPWIDGET_ID] ?: AppWidgetManager.INVALID_APPWIDGET_ID

        val habits: StateFlow<List<Habit>?> =
            repository
                .observeHabits()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

        var config by mutableStateOf(WidgetConfig())
            private set

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

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/** Chooses what a widget shows and how it sits on the home screen. */
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
                WidgetConfigContent(
                    habits = habits,
                    config = viewModel.config,
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
    onChange: ((WidgetConfig) -> WidgetConfig) -> Unit,
    onOpenApp: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DiditTheme.colors
    Surface(modifier.fillMaxSize(), color = colors.background, contentColor = colors.ink) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding().imePadding()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Text(stringResource(R.string.widget_config_title), style = DiditTheme.type.screenTitle)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Overline(stringResource(R.string.widget_config_show))
                    if (habits != null && habits.isEmpty()) {
                        Text(
                            stringResource(R.string.widget_config_empty),
                            style = DiditTheme.type.body,
                            color = colors.muted,
                        )
                        DashedButton(stringResource(R.string.widget_config_open_app), onOpenApp)
                    } else {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.widget_config_card), style = DiditTheme.type.option)
                        Text(
                            stringResource(R.string.widget_config_card_hint),
                            style = DiditTheme.type.caption,
                            color = colors.muted,
                        )
                    }
                    Spacer(Modifier.padding(start = 12.dp))
                    Switch(
                        checked = config.card,
                        onCheckedChange = { checked -> onChange { it.copy(card = checked) } },
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
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Overline(stringResource(R.string.widget_config_ink))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WidgetConfig.Ink.entries.forEach { ink ->
                            val label =
                                when (ink) {
                                    WidgetConfig.Ink.Auto -> R.string.widget_ink_auto
                                    WidgetConfig.Ink.Light -> R.string.widget_ink_light
                                    WidgetConfig.Ink.Dark -> R.string.widget_ink_dark
                                }
                            TargetOption(
                                label = stringResource(label),
                                selected = config.ink == ink,
                                onClick = { onChange { it.copy(ink = ink) } },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
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
private fun TargetOption(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SelectableCard(selected = selected, onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.height(52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = DiditTheme.type.option, modifier = Modifier.weight(1f))
            RadioMark(selected)
        }
    }
}
