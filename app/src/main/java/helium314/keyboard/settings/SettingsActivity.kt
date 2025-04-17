// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets.Type
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import helium314.keyboard.compat.locale
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ExecutorUtils
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.UncachedInputMethodManagerUtils
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
class SettingsActivity : ComponentActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val prefs by lazy { this.prefs() }
    val prefChanged = MutableStateFlow(0) // simple counter, as the only relevant information is that something changed
    fun prefChanged() = prefChanged.value++
    private val dictUriFlow = MutableStateFlow<Uri?>(null)
    private val cachedDictionaryFile by lazy { File(this.cacheDir.path + File.separator + "temp_dict") }
    private val crashReportFiles = MutableStateFlow<List<File>>(emptyList())
    private var paused = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Settings.getValues() == null) {
            val inputAttributes = InputAttributes(EditorInfo(), false, packageName)
            Settings.getInstance().loadSettings(this, resources.configuration.locale(), inputAttributes)
        }
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute { cleanUnusedMainDicts(this) }
        if (BuildConfig.DEBUG || DebugFlags.DEBUG_ENABLED)
            crashReportFiles.value = findCrashReports()
        setSystemBarIconColor()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

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

        val cv = ComposeView(context = this)
        setContentView(cv)
        cv.setContent {
            Theme {
                Surface {
                    val dictUri by dictUriFlow.collectAsState()
                    val crashReports by crashReportFiles.collectAsState()
                    val crashFilePicker = filePicker { saveCrashReports(it) }
                    var showWelcomeWizard by rememberSaveable { mutableStateOf(
                        !UncachedInputMethodManagerUtils.isThisImeCurrent(this, imm)
                                || !UncachedInputMethodManagerUtils.isThisImeEnabled(this, imm)
                    ) }
                    if (spellchecker)
                        Scaffold { innerPadding ->
                            Column(Modifier.padding(innerPadding)) { // lazy way of implementing spell checker settings
                                settingsContainer[Settings.PREF_USE_CONTACTS]!!.Preference()
                                settingsContainer[Settings.PREF_BLOCK_POTENTIALLY_OFFENSIVE]!!.Preference()
                            }
                        }
                    else
                        SettingsNavHost(onClickBack = { this.finish() })
                    if (dictUri != null) {
                        NewDictionaryDialog(
                            onDismissRequest = { dictUriFlow.value = null },
                            cachedFile = cachedDictionaryFile,
                            mainLocale = null
                        )
                    }
                    if (!showWelcomeWizard && !spellchecker && crashReports.isNotEmpty()) {
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
                            content = { Text("Crash report files found") },
                        )
                    }
                    if (!spellchecker && showWelcomeWizard) {
                        WelcomeWizard(close = { showWelcomeWizard = false }, finish = this::finish)
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
        setForceTheme(null, null)
        paused = true
    }

    override fun onResume() {
        super.onResume()
        paused = false
    }

    fun setForceTheme(theme: String?, night: Boolean?) {
        if (paused) return
        if (forceTheme == theme && forceNight == night)
            return
        forceTheme = theme
        forceNight = night
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
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

    // deprecated but works... ideally it would be done automatically like it worked before switching to compose
    private fun setSystemBarIconColor() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val view = window.decorView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (ResourceUtils.isNight(resources))
                view.systemUiVisibility = view.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv() and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            else
                view.systemUiVisibility = view.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        } else {
            if (ResourceUtils.isNight(resources))
                view.systemUiVisibility = view.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            else
                view.systemUiVisibility = view.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
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
        prefChanged()
    }
}
