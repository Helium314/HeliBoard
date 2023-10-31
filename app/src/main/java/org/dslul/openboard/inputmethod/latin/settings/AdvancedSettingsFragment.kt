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
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import org.dslul.openboard.inputmethod.dictionarypack.DictionaryPackConstants
import org.dslul.openboard.inputmethod.keyboard.KeyboardLayoutSet
import org.dslul.openboard.inputmethod.latin.AudioAndHapticFeedbackManager
import org.dslul.openboard.inputmethod.latin.BuildConfig
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.RichInputMethodManager
import org.dslul.openboard.inputmethod.latin.SystemBroadcastReceiver
import org.dslul.openboard.inputmethod.latin.common.FileUtils
import org.dslul.openboard.inputmethod.latin.define.JniLibName
import org.dslul.openboard.inputmethod.latin.settings.SeekBarDialogPreference.ValueProxy
import java.io.File
import java.io.FileInputStream
import java.io.IOException
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
    private var libfile: File? = null
    private val backupFilePatterns by lazy { listOf(
        "blacklists/.*\\.txt".toRegex(),
        "dicts/.*/.*user\\.dict".toRegex(),
        "userunigram.*/userunigram.*\\.(body|header)".toRegex(),
        "UserHistoryDictionary.*/UserHistoryDictionary.*\\.(body|header)".toRegex(),
        "spellcheck_userunigram.*/spellcheck_userunigram.*\\.(body|header)".toRegex(),
        // todo: found "b.<locale>.dict" folder, where does it come from?
        //  possibly some obfuscation thing that occurred after upgrading to gradle 8?
    ) }

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
                    startActivityForResult(intent, REQUEST_CODE_GESTURE_LIBRARY)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, result: Intent?) {
        val uri = result?.data
        if (resultCode != Activity.RESULT_OK || uri == null) return
        when (requestCode) {
            REQUEST_CODE_GESTURE_LIBRARY -> copyLibrary(uri)
            REQUEST_CODE_BACKUP -> backup(uri)
            REQUEST_CODE_RESTORE -> restore(uri)
        }
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
                startActivityForResult(intent, REQUEST_CODE_BACKUP)
            }
            .setPositiveButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.button_restore) { _, _ ->
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/zip")
                startActivityForResult(intent, REQUEST_CODE_RESTORE)
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
        if (files.isEmpty()) {
            infoDialog(requireContext(), R.string.backup_error)
            return
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
                zipStream.close()
            }
        } catch (t: Throwable) {
            // inform about every error
            infoDialog(requireContext(), R.string.backup_error)
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
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            val newDictBroadcast = Intent(DictionaryPackConstants.NEW_DICTIONARY_INTENT_ACTION)
            activity?.sendBroadcast(newDictBroadcast)
        } catch (t: Throwable) {
            // inform about every error
            infoDialog(requireContext(), requireContext().getString(R.string.restore_error, t.message))
        }
    }

    private fun setupKeyLongpressTimeoutSettings() {
        val prefs = sharedPreferences
        findPreference<SeekBarDialogPreference>(Settings.PREF_KEY_LONGPRESS_TIMEOUT)?.setInterface(object : ValueProxy {
            override fun writeValue(value: Int, key: String) = prefs.edit().putInt(key, value).apply()

            override fun writeDefaultValue(key: String) = prefs.edit().remove(key).apply()

            override fun readValue(key: String) = Settings.readKeyLongpressTimeout(prefs, resources)

            override fun readDefaultValue(key: String) = Settings.readDefaultKeyLongpressTimeout(resources)

            override fun getValueText(value: Int) =
                resources.getString(R.string.abbreviation_unit_milliseconds, value.toString())

            override fun feedbackValue(value: Int) {}
        })
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        if (Settings.PREF_SHOW_SETUP_WIZARD_ICON == key) {
            SystemBroadcastReceiver.toggleAppIcon(requireContext())
        } else if (Settings.PREF_SHOW_ALL_MORE_KEYS == key) {
            KeyboardLayoutSet.onKeyboardThemeChanged()
        }
    }
}

private const val REQUEST_CODE_GESTURE_LIBRARY = 570289
private const val REQUEST_CODE_BACKUP = 98665973
private const val REQUEST_CODE_RESTORE = 98665974
