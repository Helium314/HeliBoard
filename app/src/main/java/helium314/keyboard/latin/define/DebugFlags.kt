/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.define

import android.content.Context
import android.os.Build
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.settings.DebugSettings
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Locale

object DebugFlags {
    @JvmField
    var DEBUG_ENABLED = false

    fun init(context: Context) {
        DEBUG_ENABLED = context.prefs().getBoolean(DebugSettings.PREF_DEBUG_MODE, Defaults.PREF_DEBUG_MODE)
        if (DEBUG_ENABLED || BuildConfig.DEBUG)
            CrashReportExceptionHandler(context.applicationContext).install()
    }
}

// basically copied from StreetComplete
private class CrashReportExceptionHandler(val appContext: Context) : Thread.UncaughtExceptionHandler {
    private var defaultUncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null

    fun install(): Boolean {
        val ueh = Thread.getDefaultUncaughtExceptionHandler()
        if (ueh is CrashReportExceptionHandler)
            return false
        defaultUncaughtExceptionHandler = ueh
        Thread.setDefaultUncaughtExceptionHandler(this)
        return true
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        val stackTrace = StringWriter()

        e.printStackTrace(PrintWriter(stackTrace))
        writeCrashReportToFile("""
Thread: ${t.name}
App version: ${BuildConfig.VERSION_NAME}
Device: ${Build.BRAND} ${Build.DEVICE}, Android ${Build.VERSION.RELEASE}
Locale: ${Locale.getDefault()}
Stack trace:
$stackTrace
Last log:
${Log.getLog(100).joinToString("\n")}
""")
        defaultUncaughtExceptionHandler!!.uncaughtException(t, e)
    }

    private fun writeCrashReportToFile(text: String) {
        try {
            val dir = appContext.getExternalFilesDir(null) ?: return
            val crashReportFile = File(dir, "crash_report_${System.currentTimeMillis()}.txt")
            crashReportFile.writeText(text)
        } catch (ignored: IOException) {
        }
    }
}
