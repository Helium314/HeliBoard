// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.RelativeLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.isGone
import helium314.keyboard.compat.locale
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ExecutorUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.cleanUnusedMainDicts
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// todo: with compose, app startup is slower and UI needs some "warmup" time to be snappy
//  maybe baseline profiles help?
//  https://developer.android.com/codelabs/android-baseline-profiles-improve
//  https://developer.android.com/codelabs/jetpack-compose-performance#2
//  https://developer.android.com/topic/performance/baselineprofiles/overview
class SettingsActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val prefs by lazy { this.prefs() }
    val prefChanged = MutableStateFlow(0) // simple counter, as the only relevant information is that something changed

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Settings.getInstance().current == null) {
            val inputAttributes = InputAttributes(EditorInfo(), false, packageName)
            Settings.getInstance().loadSettings(this, resources.configuration.locale(), inputAttributes)
        }
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute { cleanUnusedMainDicts(this) }
        if (BuildConfig.DEBUG || DebugFlags.DEBUG_ENABLED)
            askAboutCrashReports()

        // with this the layout edit dialog is not covered by the keyboard
        //  alterative of Modifier.imePadding() and properties = DialogProperties(decorFitsSystemWindows = false) has other weird side effects
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView.rootView) { _, insets -> insets }

        settingsContainer = SettingsContainer(this)

        val spellchecker = intent?.getBooleanExtra("spellchecker", false) ?: false

        // todo: when removing old settings completely, remove settings_activity.xml and supportFragmentManager stuff
//        val cv = ComposeView(context = this)
//        setContentView(cv)
        setContentView(R.layout.settings_activity)
        supportFragmentManager.addOnBackStackChangedListener {
            updateContainerVisibility()
        }
//        cv.setContent { // todo: when removing old settings
        findViewById<ComposeView>(R.id.navHost).setContent {
            Theme {
                Surface {
                    if (spellchecker)
                        Column { // lazy way of implementing spell checker settings
                            settingsContainer[Settings.PREF_USE_CONTACTS]!!.Preference()
                            settingsContainer[Settings.PREF_BLOCK_POTENTIALLY_OFFENSIVE]!!.Preference()
                        }
                    else
                    SettingsNavHost(
                            onClickBack = {
//                                this.finish() // todo: when removing old settings
                                if (supportFragmentManager.findFragmentById(R.id.settingsFragmentContainer) == null)
                                    this.finish()
                                else supportFragmentManager.popBackStack()
                            }
                        )
                }
            }
        }
    }

    private fun updateContainerVisibility() { // todo: remove when removing old settings
        findViewById<RelativeLayout>(R.id.settingsFragmentContainer).isGone = supportFragmentManager.findFragmentById(R.id.settingsFragmentContainer) == null
    }

    override fun onStart() {
        super.onStart()
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

    override fun onPause() {
        super.onPause()
        paused = true
        if (forceOppositeTheme) keyboardNeedsReload = true
        forceOppositeTheme = false
    }

    override fun onResume() {
        super.onResume()
        paused = false
    }

    private var paused = true
    fun setForceOppositeTheme(opposite: Boolean) {
        if (paused) return
        if (forceOppositeTheme != opposite) {
            keyboardNeedsReload = true
        }
        forceOppositeTheme = opposite
    }

    // todo: crash report stuff just just taken from old SettingsFragment and kotlinized
    //  it should be updated to use compose, and maybe move to MainSettingsScreen
    private val crashReportFiles = mutableListOf<File>()
    private fun askAboutCrashReports() {
        // find crash report files
        val dir: File = getExternalFilesDir(null) ?: return
        val allFiles = dir.listFiles() ?: return
        crashReportFiles.clear()
        for (file in allFiles) {
            if (file.name.startsWith("crash_report")) crashReportFiles.add(file)
        }
        if (crashReportFiles.isEmpty()) return
        AlertDialog.Builder(this)
            .setMessage("Crash report files found")
            .setPositiveButton("get") { _, _ ->
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.putExtra(Intent.EXTRA_TITLE, "crash_reports.zip")
                intent.setType("application/zip")
                crashReportFilePicker.launch(intent)
            }
            .setNeutralButton("delete") { _, _ ->
                for (file in crashReportFiles) {
                    file.delete() // don't care whether it fails, though user will complain
                }
            }
            .setNegativeButton("ignore", null)
            .show()
    }
    private val crashReportFilePicker: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode != RESULT_OK || it.data == null) return@registerForActivityResult
        val uri = it.data!!.data
        if (uri != null) ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute { saveCrashReport(uri) }
    }
    private fun saveCrashReport(uri: Uri?) {
        if (uri == null || crashReportFiles.isEmpty()) return
        try {
            contentResolver.openOutputStream(uri)?.use {
                val bos = BufferedOutputStream(it)
                val z = ZipOutputStream(bos)
                for (file in crashReportFiles) {
                    val f = FileInputStream(file)
                    z.putNextEntry(ZipEntry(file.name))
                    FileUtils.copyStreamToOtherStream(f, z)
                    f.close()
                    z.closeEntry()
                }
                z.close()
                bos.close()
                for (file in crashReportFiles) {
                    file.delete()
                }
                crashReportFiles.clear()
            }
        } catch (ignored: IOException) {
        }
    }

    companion object {
        // public write so compose previews can show the screens
        // having it in a companion object is not ideal as it will stay in memory even after settings are closed
        // but it's small enough to not care
        lateinit var settingsContainer: SettingsContainer

        var forceOppositeTheme = false
    }

    override fun onSharedPreferenceChanged(prefereces: SharedPreferences?, key: String?) {
        prefChanged.value++
    }
}

@JvmField
var keyboardNeedsReload = false
