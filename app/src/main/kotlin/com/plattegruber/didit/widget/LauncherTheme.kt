package com.plattegruber.didit.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.updateAll
import com.plattegruber.didit.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The colours the home screen launcher has published, as far as Didit has heard them. Niagara
 * shares its theme with Kustom (KWGT/KLWP) through Kustom's variable broadcast; Didit listens in
 * on the same broadcast. Any colour may be missing: nothing heard yet, or never sent.
 */
data class LauncherTheme(
    val accent: Int? = null,
    val text: Int? = null,
    val surface: Int? = null,
    val onSurface: Int? = null,
    val heardAt: Instant? = null,
) {
    val isEmpty: Boolean get() = accent == null && text == null && surface == null && onSurface == null

    companion object {
        val None = LauncherTheme()
    }
}

/**
 * Kustom's "send variable" broadcast, as documented at
 * https://docs.kustom.rocks/docs/developers/send_variables_broadcast/.
 */
internal object KustomBroadcast {
    const val ACTION = "org.kustom.action.SEND_VAR"
    const val EXTRA_SENDER = "org.kustom.action.EXT_NAME"
    const val EXTRA_NAME = "org.kustom.action.VAR_NAME"
    const val EXTRA_VALUE = "org.kustom.action.VAR_VALUE"
    const val EXTRA_NAMES = "org.kustom.action.VAR_NAME_ARRAY"
    const val EXTRA_VALUES = "org.kustom.action.VAR_VALUE_ARRAY"

    /** The sender name Niagara uses, as in `$br(niagara-launcher, accent-color)$`. */
    const val NIAGARA = "niagara-launcher"

    /** The variables in one broadcast, single or array form, ignoring malformed pairs. */
    fun variables(name: String?, value: String?, names: Array<String>?, values: Array<String>?): Map<String, String> =
        buildMap {
            if (name != null && value != null) put(name, value)
            if (names != null && values != null && names.size == values.size) putAll(names.zip(values))
        }
}

/** The launcher variables Didit keeps, by their Niagara names. */
internal enum class LauncherColor(val variable: String) {
    Accent("accent-color"),
    Text("text-color"),
    Surface("surface-color"),
    OnSurface("on-surface-color"),
}

/**
 * Reads a colour however a launcher writes it: `#RRGGBB` or `#AARRGGBB`, with or without the
 * `#`, or a signed ARGB integer in decimal, as Android's `Color` ints print (opaque ones are
 * negative, which is what tells the two apart).
 */
internal fun parseLauncherColor(raw: String): Int? {
    val value = raw.trim()
    if (value.startsWith("-")) return value.toIntOrNull()
    val hex = value.removePrefix("#")
    val argb = hex.toLongOrNull(HEX) ?: return null
    return when (hex.length) {
        RGB_DIGITS -> (OPAQUE or argb).toInt()
        ARGB_DIGITS -> argb.toInt()
        else -> null
    }
}

private const val HEX = 16
private const val RGB_DIGITS = 6
private const val ARGB_DIGITS = 8
private const val OPAQUE = 0xFF000000L

/** Folds what was stored into a theme; unreadable values count as not sent. */
internal fun launcherTheme(raw: Map<LauncherColor, String>, heardAt: Instant?): LauncherTheme = LauncherTheme(
    accent = raw[LauncherColor.Accent]?.let(::parseLauncherColor),
    text = raw[LauncherColor.Text]?.let(::parseLauncherColor),
    surface = raw[LauncherColor.Surface]?.let(::parseLauncherColor),
    onSurface = raw[LauncherColor.OnSurface]?.let(::parseLauncherColor),
    heardAt = heardAt,
)

/** Marks the store the launcher's colours are kept in. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LauncherThemeData

/** Keeps the last launcher colours heard, and redraws widgets when they change. */
@Singleton
class LauncherThemeStore
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    @param:LauncherThemeData private val store: DataStore<Preferences>,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val keys = LauncherColor.entries.associateWith { stringPreferencesKey(it.variable) }
    private val heardAtKey = longPreferencesKey("heard_at")

    val theme: Flow<LauncherTheme> =
        store.data
            .map { preferences ->
                val raw = keys.mapNotNull { (color, key) -> preferences[key]?.let { color to it } }.toMap()
                launcherTheme(raw, preferences[heardAtKey]?.let(Instant::ofEpochMilli))
            }.distinctUntilChanged()

    /** Starts listening. Only works while Didit's process is alive, as Android allows for this. */
    fun listen() {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.getStringExtra(KustomBroadcast.EXTRA_SENDER) != KustomBroadcast.NIAGARA) return
                    val variables =
                        KustomBroadcast.variables(
                            name = intent.getStringExtra(KustomBroadcast.EXTRA_NAME),
                            value = intent.getStringExtra(KustomBroadcast.EXTRA_VALUE),
                            names = intent.getStringArrayExtra(KustomBroadcast.EXTRA_NAMES),
                            values = intent.getStringArrayExtra(KustomBroadcast.EXTRA_VALUES),
                        )
                    scope.launch { record(variables) }
                }
            }
        // Exported: the whole point is hearing another app. The worst a forged broadcast can do
        // is recolour the widget.
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(KustomBroadcast.ACTION),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    internal suspend fun record(variables: Map<String, String>) {
        val colors = LauncherColor.entries.mapNotNull { color -> variables[color.variable]?.let { color to it } }
        if (colors.isEmpty()) return
        var changed = false
        store.edit { preferences ->
            for ((color, value) in colors) {
                val key = keys.getValue(color)
                if (preferences[key] != value) {
                    preferences[key] = value
                    changed = true
                }
            }
            preferences[heardAtKey] = Instant.now().toEpochMilli()
        }
        if (changed) DiditWidget().updateAll(context)
    }
}
