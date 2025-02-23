// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets.Type
import android.view.inputmethod.EditorInfo
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import helium314.keyboard.latin.utils.cleanUnusedMainDicts
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.dialogs.NewDictionaryDialog
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
// todo: consider viewModel, at least for LanguageScreen and ColorsScreen it might help making them less awkward and complicated
class SettingsActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val prefs by lazy { this.prefs() }
    val prefChanged = MutableStateFlow(0) // simple counter, as the only relevant information is that something changed
    private val dictUriFlow = MutableStateFlow<Uri?>(null)
    private val cachedDictionaryFile by lazy { File(this.cacheDir.path + File.separator + "temp_dict") }
    private val crashReportFiles = MutableStateFlow<List<File>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Settings.getInstance().current == null) {
            val inputAttributes = InputAttributes(EditorInfo(), false, packageName)
            Settings.getInstance().loadSettings(this, resources.configuration.locale(), inputAttributes)
        }
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute { cleanUnusedMainDicts(this) }
        if (BuildConfig.DEBUG || DebugFlags.DEBUG_ENABLED)
            crashReportFiles.value = findCrashReports()

        // with this the layout edit dialog is not covered by the keyboard
        //  alternative of Modifier.imePadding() and properties = DialogProperties(decorFitsSystemWindows = false) has other weird side effects
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView.rootView) { _, insets ->
            @Suppress("DEPRECATION")
            bottomInsets.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    insets.getInsets(Type.ime()).bottom
                else insets.systemWindowInsetBottom
            insets
        }

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
                    val dictUri by dictUriFlow.collectAsState()
                    val crashReports by crashReportFiles.collectAsState()
                    val crashFilePicker = filePicker { saveCrashReports(it) }
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
                    if (dictUri != null) {
                        NewDictionaryDialog(
                            onDismissRequest = { dictUriFlow.value = null },
                            cachedFile = cachedDictionaryFile,
                            mainLocale = null
                        )
                    }
                    if (crashReports.isNotEmpty()) {
                        ConfirmationDialog(
                            cancelButtonText = "ignore",
                            onDismissRequest = { crashReportFiles.value = emptyList() },
                            neutralButtonText = "delete",
                            onNeutral = { crashReports.forEach { it.delete() }; crashReportFiles.value = emptyList() },
                            confirmButtonText = "get",
                            onConfirmed = {
                                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                                intent.addCategory(Intent.CATEGORY_OPENABLE)
                                intent.putExtra(Intent.EXTRA_TITLE, "crash_reports.zip")
                                intent.setType("application/zip")
                                crashFilePicker.launch(intent)
                            },
                            text = { Text("Crash report files found") },
                        )
                    }
                }
            }
        }

        if (intent?.action == Intent.ACTION_VIEW) {
            intent?.data?.let {
                cachedDictionaryFile.delete()
                FileUtils.copyContentUriToNewFile(it, this, cachedDictionaryFile)
                dictUriFlow.value = it
            }
            intent = null
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
        if (forceNight != null || forceTheme != null) keyboardNeedsReload = true
        forceNight = false
        forceTheme = null
    }

    override fun onResume() {
        super.onResume()
        paused = false
    }

    private var paused = true
    fun setForceTheme(theme: String?, night: Boolean?) {
        if (paused) return
        if (forceTheme != theme || forceNight != night) {
            keyboardNeedsReload = true
        }
        forceTheme = theme
        forceNight = night
    }

    private fun findCrashReports(): List<File> {
        // find crash report files
        val dir: File = getExternalFilesDir(null) ?: return emptyList()
        val allFiles = dir.listFiles() ?: return emptyList()
        return allFiles.filter { it.name.startsWith("crash_report") }
    }

    private fun saveCrashReports(uri: Uri) {
        val files = findCrashReports()
        if (files.isEmpty()) return
        try {
            contentResolver.openOutputStream(uri)?.use {
                val bos = BufferedOutputStream(it)
                val z = ZipOutputStream(bos)
                for (file in files) {
                    val f = FileInputStream(file)
                    z.putNextEntry(ZipEntry(file.name))
                    FileUtils.copyStreamToOtherStream(f, z)
                    f.close()
                    z.closeEntry()
                }
                z.close()
                bos.close()
                for (file in files) {
                    file.delete()
                }
            }
        } catch (ignored: IOException) {
        }
    }

    companion object {
        // public write so compose previews can show the screens
        // having it in a companion object is not ideal as it will stay in memory even after settings are closed
        // but it's small enough to not care
        lateinit var settingsContainer: SettingsContainer

        var forceNight: Boolean? = null
        var forceTheme: String? = null

        // weird inset forwarding because otherwise layout dialog sometimes doesn't care about keyboard showing
        var bottomInsets = MutableStateFlow(0)
    }

    override fun onSharedPreferenceChanged(prefereces: SharedPreferences?, key: String?) {
        prefChanged.value++
    }
}

@JvmField
var keyboardNeedsReload = false
