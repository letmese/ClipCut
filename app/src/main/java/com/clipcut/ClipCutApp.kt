package com.clipcut

import android.app.Application
import com.clipcut.core.log.AppLog
import com.clipcut.data.db.ClipCutDatabase

/**
 * Application entrypoint. Initializes logging and the SQLite database singleton
 * before any Activity starts. AppLog.init must run first because every layer
 * (data, core, ui) calls AppLog.d/i/e during construction.
 */
class ClipCutApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        AppLog.init(this)
        AppLog.i(TAG, "ClipCutApp.onCreate — v1.2.31 (Build 42)")
        ClipCutDatabase.getInstance(this) // warm the SQLiteOpenHelper
    }

    companion object {
        private const val TAG = "ClipCutApp"
        @Volatile lateinit var instance: ClipCutApp
            private set
    }
}
