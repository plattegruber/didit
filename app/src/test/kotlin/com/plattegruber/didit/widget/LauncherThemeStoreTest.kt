package com.plattegruber.didit.widget

import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** Niagara's broadcast, heard by the real receiver and kept in the real store. */
@RunWith(AndroidJUnit4::class)
class LauncherThemeStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store by lazy {
        val data = PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("launcher_theme.preferences_pb") }
        LauncherThemeStore(context, data, scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun send(sender: String, names: Array<String>, values: Array<String>) {
        context.sendBroadcast(
            Intent(KustomBroadcast.ACTION)
                .putExtra(KustomBroadcast.EXTRA_SENDER, sender)
                .putExtra(KustomBroadcast.EXTRA_NAMES, names)
                .putExtra(KustomBroadcast.EXTRA_VALUES, values),
        )
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `niagara's colours are kept, and other senders ignored`() = runBlocking {
        store.listen()

        send("someone-else", arrayOf("accent-color"), arrayOf("#FFFF0000"))
        send(
            KustomBroadcast.NIAGARA,
            arrayOf("screen", "accent-color", "text-color", "surface-color", "on-surface-color"),
            arrayOf("0", "#FF8AB4F8", "#FFFFFFFF", "#FF1F1F23", "#FFE6E1E5"),
        )

        val theme = withTimeout(TIMEOUT_MILLIS) { store.theme.first { !it.isEmpty } }
        assertThat(theme.accent).isEqualTo(0xFF8AB4F8.toInt())
        assertThat(theme.text).isEqualTo(0xFFFFFFFF.toInt())
        assertThat(theme.surface).isEqualTo(0xFF1F1F23.toInt())
        assertThat(theme.onSurface).isEqualTo(0xFFE6E1E5.toInt())
        assertThat(theme.heardAt).isNotNull()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
