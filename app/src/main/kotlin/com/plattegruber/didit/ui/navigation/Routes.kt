package com.plattegruber.didit.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute : NavKey

@Serializable
data class HistoryRoute(val habitId: Long) : NavKey

/** Creates a habit when [habitId] is null, edits it otherwise. */
@Serializable
data class EditorRoute(val habitId: Long? = null) : NavKey
