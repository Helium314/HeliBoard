// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
package helium314.keyboard.latin

import android.app.Application
import helium314.keyboard.keyboard.emoji.SupportedEmojis
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.LayoutUtilsCustom
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SubtypeSettings
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class App : Application() {
    override fun onCreate() {
        try {
            val date = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Calendar.getInstance().time)
            val dir = DeviceProtectedUtils.getFilesDir(this)
            val filename = File(dir, "crash_report_startup.txt").absolutePath
            val fw = FileWriter(filename, true)
            fw.write("$date: app starting\n")
            fw.close()
        } catch (ioe: IOException) {
            System.err.println("IOException: " + ioe.message)
        }

        super.onCreate()
        DebugFlags.init(this)
        Settings.init(this)
        SubtypeSettings.init(this)
        RichInputMethodManager.init(this)

        AppUpgrade.checkVersionUpgrade(this)
        AppUpgrade.transferOldPinnedClips(this) // todo: remove in a few months, maybe mid 2026
        app = this
        Defaults.initDynamicDefaults(this)
        LayoutUtilsCustom.removeMissingLayouts(this) // only after version upgrade
        SupportedEmojis.load(this)

        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        @Suppress("DEPRECATION")
        Log.i(
            "startup", "Starting ${applicationInfo.processName} version ${packageInfo.versionName} (${
                packageInfo.versionCode
            }) on Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})"
        )
    }

    companion object {
        // used so JniUtils can access application once
        private var app: App? = null
        fun getApp(): App? {
            val application = app
            app = null
            return application
        }
    }
}
