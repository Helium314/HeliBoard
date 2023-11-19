/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package org.dslul.openboard.inputmethod.latin.settings

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.dslul.openboard.inputmethod.dictionarypack.DictionaryPackConstants
import org.dslul.openboard.inputmethod.keyboard.KeyboardLayoutSet
import org.dslul.openboard.inputmethod.keyboard.KeyboardSwitcher
import org.dslul.openboard.inputmethod.latin.AudioAndHapticFeedbackManager
import org.dslul.openboard.inputmethod.latin.BuildConfig
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.SystemBroadcastReceiver
import org.dslul.openboard.inputmethod.latin.common.FileUtils
import org.dslul.openboard.inputmethod.latin.define.JniLibName
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.Writer
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream


/**
 * "Advanced" settings sub screen.
 *
 * This settings sub screen handles the following advanced preferences.
 * - Key popup dismiss delay
 * - Keypress vibration duration
 * - Keypress sound volume
 * - Show app icon
 * - Improve keyboard
 * - Debug settings
 */
class AdvancedSettingsFragment : SubScreenFragment() {
    private val TAG = this::class.simpleName
    private var libfile: File? = null
    private val backupFilePatterns by lazy { listOf(
        "blacklists/.*\\.txt".toRegex(),
        "dicts/.*/.*user\\.dict".toRegex(),
        "userunigram.*/userunigram.*\\.(body|header)".toRegex(),
        "UserHistoryDictionary.*/UserHistoryDictionary.*\\.(body|header)".toRegex(),
        "spellcheck_userunigram.*/spellcheck_userunigram.*\\.(body|header)".toRegex(),
    ) }

    private val libraryFilePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = it.data?.data ?: return@registerForActivityResult
        copyLibrary(uri)
    }

    private val backupFilePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = it.data?.data ?: return@registerForActivityResult
        backup(uri)
    }

    private val restoreFilePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = it.data?.data ?: return@registerForActivityResult
        restore(uri)
    }

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        addPreferencesFromResource(R.xml.prefs_screen_advanced)
        val context = requireContext()

        // When we are called from the Settings application but we are not already running, some
        // singleton and utility classes may not have been initialized.  We have to call
        // initialization method of these classes here. See {@link LatinIME#onCreate()}.
        AudioAndHapticFeedbackManager.init(context)
        if (!BuildConfig.DEBUG) {
            removePreference(Settings.SCREEN_DEBUG)
        }
        setupKeyLongpressTimeoutSettings()
        findPreference<Preference>("load_gesture_library")?.setOnPreferenceClickListener { onClickLoadLibrary() }
        findPreference<Preference>("pref_backup_restore")?.setOnPreferenceClickListener { showBackupRestoreDialog() }
    }

    private fun onClickLoadLibrary(): Boolean {
        // get architecture for telling user which file to use
        val abi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Build.SUPPORTED_ABIS[0]
        } else {
            Build.CPU_ABI
        }
        // show delete / add dialog
        val builder = AlertDialog.Builder(requireContext())
                .setTitle(R.string.load_gesture_library)
                .setMessage(requireContext().getString(R.string.load_gesture_library_message, abi))
                .setPositiveButton(R.string.load_gesture_library_button_load) { _, _ ->
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/octet-stream")
                    libraryFilePicker.launch(intent)
                }
                .setNegativeButton(android.R.string.cancel, null)
        libfile = File(requireContext().filesDir.absolutePath + File.separator + JniLibName.JNI_LIB_IMPORT_FILE_NAME)
        if (libfile?.exists() == true) {
            builder.setNeutralButton(R.string.load_gesture_library_button_delete) { _, _ ->
                libfile?.delete()
                Runtime.getRuntime().exit(0)
            }
        }
        builder.show()
        return true
    }

    private fun copyLibrary(uri: Uri) {
        if (libfile == null) return
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            FileUtils.copyStreamToNewFile(inputStream, libfile)
            Runtime.getRuntime().exit(0) // exit will restart the app, so library will be loaded
        } catch (e: IOException) {
            // should inform user, but probably the issues will only come when reading the library
        }
    }

    private fun showBackupRestoreDialog(): Boolean {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.backup_restore_title)
            .setMessage(R.string.backup_restore_message)
            .setNegativeButton(R.string.button_backup) { _, _ ->
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(
                        Intent.EXTRA_TITLE,
                        requireContext().getString(R.string.english_ime_name)
                            .replace(" ", "_") + "_backup.zip"
                    )
                    .setType("application/zip")
                backupFilePicker.launch(intent)
            }
            .setPositiveButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.button_restore) { _, _ ->
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/zip")
                restoreFilePicker.launch(intent)
            }
            .show()
        return true
    }

    private fun backup(uri: Uri) {
        // zip all files matching the backup patterns
        // essentially this is the typed words information, and user-added dictionaries
        val filesDir = requireContext().filesDir ?: return
        val filesPath = filesDir.path + File.separator
        val files = mutableListOf<File>()
        filesDir.walk().forEach { file ->
            val path = file.path.replace(filesPath, "")
            if (backupFilePatterns.any { path.matches(it) })
                files.add(file)
        }
        try {
            activity?.contentResolver?.openOutputStream(uri)?.use { os ->
                // write files to zip
                val zipStream = ZipOutputStream(os)
                files.forEach {
                    val fileStream = FileInputStream(it).buffered()
                    zipStream.putNextEntry(ZipEntry(it.path.replace(filesPath, "")))
                    fileStream.copyTo(zipStream, 1024)
                    fileStream.close()
                    zipStream.closeEntry()
                }
                zipStream.putNextEntry(ZipEntry(PREFS_FILE_NAME))
                zipStream.bufferedWriter().use { settingsToJsonStream(sharedPreferences.all, it) }
                zipStream.close()
            }
        } catch (t: Throwable) {
            // inform about every error
            Log.w(TAG, "error during backup", t)
            infoDialog(requireContext(), requireContext().getString(R.string.backup_error, t.message))
        }
    }

    private fun restore(uri: Uri) {
        try {
            activity?.contentResolver?.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zip ->
                    var entry: ZipEntry? = zip.nextEntry
                    val filesDir = requireContext().filesDir?.path ?: return
                    while (entry != null) {
                        if (backupFilePatterns.any { entry!!.name.matches(it) }) {
                            val file = File(filesDir, entry.name)
                            FileUtils.copyStreamToNewFile(zip, file)
                        } else if (entry.name == PREFS_FILE_NAME) {
                            val prefLines = String(zip.readBytes()).split("\n")
                            sharedPreferences.edit().clear().apply()
                            readJsonLinesToSettings(prefLines)
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            val newDictBroadcast = Intent(DictionaryPackConstants.NEW_DICTIONARY_INTENT_ACTION)
            activity?.sendBroadcast(newDictBroadcast)
            // reload current prefs screen
            preferenceScreen.removeAll()
            onCreate(null)
            KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(requireContext())
        } catch (t: Throwable) {
            // inform about every error
            Log.w(TAG, "error during restore", t)
            infoDialog(requireContext(), requireContext().getString(R.string.restore_error, t.message))
        }
    }

    private fun setupKeyLongpressTimeoutSettings() {
        val prefs = sharedPreferences
        findPreference<SeekBarDialogPreference>(Settings.PREF_KEY_LONGPRESS_TIMEOUT)?.setInterface(object :
            SeekBarDialogPreference.ValueProxy {
            override fun writeValue(value: Int, key: String) = prefs.edit().putInt(key, value).apply()

            override fun writeDefaultValue(key: String) = prefs.edit().remove(key).apply()

            override fun readValue(key: String) = Settings.readKeyLongpressTimeout(prefs, resources)

            override fun readDefaultValue(key: String) = Settings.readDefaultKeyLongpressTimeout(resources)

            override fun getValueText(value: Int) =
                resources.getString(R.string.abbreviation_unit_milliseconds, value.toString())

            override fun feedbackValue(value: Int) {}
        })
    }

    @Suppress("UNCHECKED_CAST") // it is checked... but whatever (except string set, because can't check for that))
    private fun settingsToJsonStream(settings: Map<String, Any?>, out: Writer) {
        val booleans = settings.filterValues { it is Boolean } as Map<String, Boolean>
        val ints = settings.filterValues { it is Int } as Map<String, Int>
        val longs = settings.filterValues { it is Long } as Map<String, Long>
        val floats = settings.filterValues { it is Float } as Map<String, Float>
        val strings = settings.filterValues { it is String } as Map<String, String>
        val stringSets = settings.filterValues { it is Set<*> } as Map<String, Set<String>>
        // now write
        out.appendLine("boolean settings")
        out.appendLine( Json.encodeToString(booleans))
        out.appendLine("int settings")
        out.appendLine( Json.encodeToString(ints))
        out.appendLine("long settings")
        out.appendLine( Json.encodeToString(longs))
        out.appendLine("float settings")
        out.appendLine( Json.encodeToString(floats))
        out.appendLine("string settings")
        out.appendLine( Json.encodeToString(strings))
        out.appendLine("string set settings")
        out.appendLine( Json.encodeToString(stringSets))
    }

    private fun readJsonLinesToSettings(list: List<String>): Boolean {
        val i = list.iterator()
        val e = sharedPreferences.edit()
        try {
            while (i.hasNext()) {
                when (i.next()) {
                    "boolean settings" -> Json.decodeFromString<Map<String, Boolean>>(i.next()).forEach { e.putBoolean(it.key, it.value) }
                    "int settings" -> Json.decodeFromString<Map<String, Int>>(i.next()).forEach { e.putInt(it.key, it.value) }
                    "long settings" -> Json.decodeFromString<Map<String, Long>>(i.next()).forEach { e.putLong(it.key, it.value) }
                    "float settings" -> Json.decodeFromString<Map<String, Float>>(i.next()).forEach { e.putFloat(it.key, it.value) }
                    "string settings" -> Json.decodeFromString<Map<String, String>>(i.next()).forEach { e.putString(it.key, it.value) }
                    "string set settings" -> Json.decodeFromString<Map<String, Set<String>>>(i.next()).forEach { e.putStringSet(it.key, it.value) }
                }
            }
            e.apply()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        when (key) {
            Settings.PREF_SHOW_SETUP_WIZARD_ICON -> SystemBroadcastReceiver.toggleAppIcon(requireContext())
            Settings.PREF_MORE_MORE_KEYS, Settings.PREF_USE_NEW_KEYBOARD_PARSING -> KeyboardLayoutSet.onSystemLocaleChanged()
        }
    }
}

private const val PREFS_FILE_NAME = "preferences.json"
