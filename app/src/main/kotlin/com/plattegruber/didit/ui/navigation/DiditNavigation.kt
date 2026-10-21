package com.plattegruber.didit.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.plattegruber.didit.core.domain.model.HabitId
import com.plattegruber.didit.ui.editor.EditorScreen
import com.plattegruber.didit.ui.editor.EditorViewModel
import com.plattegruber.didit.ui.history.HistoryScreen
import com.plattegruber.didit.ui.history.HistoryViewModel
import com.plattegruber.didit.ui.home.HomeScreen
import com.plattegruber.didit.widget.WidgetPinning
import kotlinx.coroutines.flow.Flow

/**
 * @param openHabitRequests habits to jump straight to, e.g. from the widget's log sheet.
 */
@Composable
fun DiditNavigation(openHabitRequests: Flow<HabitId>, modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(HomeRoute)
    val context = LocalContext.current
    val canPinWidgets = remember(context) { WidgetPinning.isSupported(context) }

    LaunchedEffect(openHabitRequests) {
        openHabitRequests.collect { id -> backStack.replaceAll(HomeRoute, HistoryRoute(id.value)) }
    }

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<HomeRoute> {
                    HomeScreen(
                        onOpenHistory = { backStack.add(HistoryRoute(it.value)) },
                        onCreateHabit = { backStack.add(EditorRoute()) },
                    )
                }
                entry<HistoryRoute> { route ->
                    val habitId = HabitId(route.habitId)
                    HistoryScreen(
                        viewModel =
                            hiltViewModel<HistoryViewModel, HistoryViewModel.Factory> {
                                it.create(route.habitId)
                            },
                        onBack = { backStack.removeLastOrNull() },
                        onEdit = { backStack.add(EditorRoute(route.habitId)) },
                        onAddWidget = if (canPinWidgets) ({ WidgetPinning.request(context, habitId) }) else null,
                    )
                }
                entry<EditorRoute> { route ->
                    EditorScreen(
                        viewModel =
                            hiltViewModel<EditorViewModel, EditorViewModel.Factory> {
                                it.create(route.habitId)
                            },
                        onClose = { backStack.removeLastOrNull() },
                        onCloseAfterDelete = { backStack.replaceAll(HomeRoute) },
                    )
                }
            },
    )
}

private fun NavBackStack<NavKey>.replaceAll(vararg keys: NavKey) {
    clear()
    addAll(keys)
}
