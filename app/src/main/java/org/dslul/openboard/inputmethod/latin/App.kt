package org.dslul.openboard.inputmethod.latin

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.util.*

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            CrashReportExceptionHandler(applicationContext).install()
        }
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
