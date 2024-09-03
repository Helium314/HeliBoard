/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.oscar.kb.latin.define

import android.content.Context
import android.os.Build
import org.oscar.kb.BuildConfig
import org.oscar.kb.latin.settings.DebugSettings
import org.oscar.kb.latin.utils.DeviceProtectedUtils
import org.oscar.kb.latin.utils.Log
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Locale

object DebugFlags {
    @JvmField
    var DEBUG_ENABLED = false

    @JvmStatic
    fun init(context: Context) {
        val prefs = _root_ide_package_.org.oscar.kb.latin.utils.DeviceProtectedUtils.getSharedPreferences(context)
        DEBUG_ENABLED = prefs.getBoolean(_root_ide_package_.org.oscar.kb.latin.settings.DebugSettings.PREF_DEBUG_MODE, false)
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
