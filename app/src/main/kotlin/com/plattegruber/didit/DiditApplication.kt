package com.plattegruber.didit

import android.app.Application
import com.plattegruber.didit.widget.WidgetSync
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DiditApplication : Application() {
    @Inject
    lateinit var widgetSync: WidgetSync

    override fun onCreate() {
        super.onCreate()
        widgetSync.start()
    }
}
