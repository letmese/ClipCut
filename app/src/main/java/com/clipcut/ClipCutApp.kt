package com.clipcut

import android.app.Application
import com.clipcut.core.log.AppLog
import com.clipcut.data.db.ClipCutDatabase

class ClipCutApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize logging first so all crashes and init steps are captured
        AppLog.init(this)
        AppLog.i("ClipCutApp", "Application starting...")

        // Warm up Room DB
        try {
            ClipCutDatabase.getInstance(this)
            AppLog.i("ClipCutApp", "Database initialized successfully.")
        } catch (e: Exception) {
            AppLog.e("ClipCutApp", "Failed to initialize database", e)
        }
    }
}
