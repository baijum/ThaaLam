package com.baijum.thaalam

import android.app.Application
import com.baijum.thaalam.audio.MetronomeController

/** Initialises the application-scoped metronome controller. */
class ThaaLamApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MetronomeController.get(this)
    }
}
