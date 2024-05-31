/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package helium314.keyboard.latin.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import helium314.keyboard.latin.utils.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import helium314.keyboard.dictionarypack.DictionaryPackConstants
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.latin.utils.ChecksumCalculator
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.keyboard_parser.LAYOUT_NUMBER
import helium314.keyboard.keyboard.internal.keyboard_parser.LAYOUT_NUMPAD
import helium314.keyboard.keyboard.internal.keyboard_parser.LAYOUT_NUMPAD_LANDSCAPE
import helium314.keyboard.keyboard.internal.keyboard_parser.LAYOUT_PHONE
import helium314.keyboard.keyboard.internal.keyboard_parser.LAYOUT_PHONE_SYMBOLS
import helium314.keyboard.keyboard.internal.keyboard_parser.LAYOUT_SYMBOLS
import helium314.keyboard.keyboard.internal.keyboard_parser.LAYOUT_SYMBOLS_ARABIC
import helium314.keyboard.keyboard.internal.keyboard_parser.LAYOUT_SYMBOLS_SHIFTED
import helium314.keyboard.keyboard.internal.keyboard_parser.RawKeyboardParser
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.SystemBroadcastReceiver
import helium314.keyboard.latin.checkVersionUpgrade
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.settings.SeekBarDialogPreference.ValueProxy
import helium314.keyboard.latin.utils.AdditionalSubtypeUtils
import helium314.keyboard.latin.utils.CUSTOM_FUNCTIONAL_LAYOUT_NORMAL
import helium314.keyboard.latin.utils.CUSTOM_FUNCTIONAL_LAYOUT_SYMBOLS
import helium314.keyboard.latin.utils.CUSTOM_FUNCTIONAL_LAYOUT_SYMBOLS_SHIFTED
import helium314.keyboard.latin.utils.CUSTOM_LAYOUT_PREFIX
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.ExecutorUtils
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.editCustomLayout
import helium314.keyboard.latin.utils.getCustomLayoutFiles
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.infoDialog
import helium314.keyboard.latin.utils.reloadEnabledSubtypes
import helium314.keyboard.latin.utils.updateAdditionalSubtypes
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
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
@Suppress("KotlinConstantConditions") // build type might be a constant, but depends on... build type!
class AdvancedSettingsFragment : SubScreenFragment() {
    private val libfile by lazy { File(requireContext().filesDir.absolutePath + File.separator + JniUtils.JNI_LIB_IMPORT_FILE_NAME) }
    private val backupFilePatterns by lazy { listOf(
        "blacklists/.*\\.txt".toRegex(),
        "layouts/.*.(txt|json)".toRegex(),
        "dicts/.*/.*user\\.dict".toRegex(),
        "UserHistoryDictionary.*/UserHistoryDictionary.*\\.(body|header)".toRegex(),
        "custom_background_image.*".toRegex(),
    ) }

    // is there any way to get additional information into the ActivityResult? would remove the need for 5 times the (almost) same code
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupPreferences()
    }

    private fun setupPreferences() {
        addPreferencesFromResource(R.xml.prefs_screen_advanced)
        setDebugPrefVisibility()
        val context = requireContext()

        // When we are called from the Settings application but we are not already running, some
        // singleton and utility classes may not have been initialized.  We have to call
        // initialization method of these classes here. See {@link LatinIME#onCreate()}.
        AudioAndHapticFeedbackManager.init(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            removePreference(Settings.PREF_SHOW_SETUP_WIZARD_ICON)
        }
        if (BuildConfig.BUILD_TYPE == "nouserlib") {
            removePreference("load_gesture_library")
        }
        setupKeyLongpressTimeoutSettings()
        setupLanguageSwipeDistanceSettings()
        updateLangSwipeDistanceVisibility(sharedPreferences)
        findPreference<Preference>("load_gesture_library")?.setOnPreferenceClickListener { onClickLoadLibrary() }
        findPreference<Preference>("backup_restore")?.setOnPreferenceClickListener { showBackupRestoreDialog() }

        findPreference<Preference>("custom_symbols_number_layouts")?.setOnPreferenceClickListener {
            showCustomizeSymbolNumberLayoutsDialog()
            true
        }
        findPreference<Preference>("custom_functional_key_layouts")?.setOnPreferenceClickListener {
            showCustomizeFunctionalKeyLayoutsDialog()
            true
        }
    }

    override fun onStart() {
        super.onStart()
        // Remove debug preference. This is already done in onCreate, but if we come back from
        // debug prefs and have just disabled debug settings, they should disappear.
        setDebugPrefVisibility()
    }

    private fun setDebugPrefVisibility() {
        if (!BuildConfig.DEBUG && !sharedPreferences.getBoolean(DebugSettings.PREF_SHOW_DEBUG_SETTINGS, false)) {
            removePreference(Settings.SCREEN_DEBUG)
        }
    }

    private fun showCustomizeSymbolNumberLayoutsDialog() {
        val layoutNames = RawKeyboardParser.symbolAndNumberLayouts.map { it.getStringResourceOrName("layout_", requireContext()) }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.customize_symbols_number_layouts)
            .setItems(layoutNames) { di, i ->
                di.dismiss()
                customizeSymbolNumberLayout(RawKeyboardParser.symbolAndNumberLayouts[i])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun customizeSymbolNumberLayout(layoutName: String) {
        val customLayoutName = getCustomLayoutFiles(requireContext()).map { it.name }
            .firstOrNull { it.startsWith("$CUSTOM_LAYOUT_PREFIX$layoutName.") }
        val originalLayout = if (customLayoutName != null) null
            else {
                requireContext().assets.list("layouts")?.firstOrNull { it.startsWith("$layoutName.") }
                    ?.let { requireContext().assets.open("layouts" + File.separator + it).reader().readText() }
            }
        val displayName = layoutName.getStringResourceOrName("layout_", requireContext())
        editCustomLayout(customLayoutName ?: "$CUSTOM_LAYOUT_PREFIX$layoutName.txt", requireContext(), originalLayout, displayName)
    }

    private fun showCustomizeFunctionalKeyLayoutsDialog() {
        val list = listOf(CUSTOM_FUNCTIONAL_LAYOUT_NORMAL, CUSTOM_FUNCTIONAL_LAYOUT_SYMBOLS, CUSTOM_FUNCTIONAL_LAYOUT_SYMBOLS_SHIFTED)
            .map { it.substringBeforeLast(".") }
        val layoutNames = list.map { it.substringAfter(CUSTOM_LAYOUT_PREFIX).getStringResourceOrName("layout_", requireContext()) }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.customize_functional_key_layouts)
            .setItems(layoutNames) { di, i ->
                di.dismiss()
                customizeFunctionalKeysLayout(list[i])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun customizeFunctionalKeysLayout(layoutName: String) {
        val customLayoutName = getCustomLayoutFiles(requireContext()).map { it.name }
            .firstOrNull { it.startsWith("$layoutName.") }
        val originalLayout = if (customLayoutName != null) null
            else {
                val defaultLayoutName = if (Settings.getInstance().isTablet) "functional_keys_tablet.json" else "functional_keys.json"
                requireContext().assets.open("layouts" + File.separator + defaultLayoutName).reader().readText()
            }
        val displayName = layoutName.substringAfter(CUSTOM_LAYOUT_PREFIX).getStringResourceOrName("layout_", requireContext())
        editCustomLayout(customLayoutName ?: "$layoutName.json", requireContext(), originalLayout, displayName)
    }

    @SuppressLint("ApplySharedPref")
    private fun onClickLoadLibrary(): Boolean {
        // get architecture for telling user which file to use
        val abi = Build.SUPPORTED_ABIS[0]
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
        if (libfile.exists()) {
            builder.setNeutralButton(R.string.load_gesture_library_button_delete) { _, _ ->
                libfile.delete()
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit().remove(Settings.PREF_LIBRARY_CHECKSUM).commit()
                Runtime.getRuntime().exit(0)
            }
        }
        builder.show()
        return true
    }

    private fun copyLibrary(uri: Uri) {
        val tmpfile = File(requireContext().filesDir.absolutePath + File.separator + "tmplib")
        try {
            val otherTemporaryFile = File(requireContext().filesDir.absolutePath + File.separator + "tmpfile")
            FileUtils.copyContentUriToNewFile(uri, requireContext(), otherTemporaryFile)
            val inputStream = FileInputStream(otherTemporaryFile)
            val outputStream = FileOutputStream(tmpfile)
            outputStream.use {
                tmpfile.setReadOnly() // as per recommendations in https://developer.android.com/about/versions/14/behavior-changes-14#safer-dynamic-code-loading
                FileUtils.copyStreamToOtherStream(inputStream, it)
            }
            otherTemporaryFile.delete()

            val checksum = ChecksumCalculator.checksum(tmpfile.inputStream()) ?: ""
            if (checksum == JniUtils.expectedDefaultChecksum()) {
                renameToLibfileAndRestart(tmpfile, checksum)
            } else {
                val abi = Build.SUPPORTED_ABIS[0]
                AlertDialog.Builder(requireContext())
                    .setMessage(getString(R.string.checksum_mismatch_message, abi))
                    .setPositiveButton(android.R.string.ok) { _, _ -> renameToLibfileAndRestart(tmpfile, checksum) }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> tmpfile.delete() }
                    .show()
            }
        } catch (e: IOException) {
            tmpfile.delete()
            // should inform user, but probably the issues will only come when reading the library
        }
    }

    @SuppressLint("ApplySharedPref")
    private fun renameToLibfileAndRestart(file: File, checksum: String) {
        libfile.delete()
        // store checksum in default preferences (soo JniUtils)
        PreferenceManager.getDefaultSharedPreferences(requireContext()).edit().putString(Settings.PREF_LIBRARY_CHECKSUM, checksum).commit()
        file.renameTo(libfile)
        Runtime.getRuntime().exit(0) // exit will restart the app, so library will be loaded
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
        val protectedFilesDir = DeviceProtectedUtils.getFilesDir(requireContext())
        val protectedFilesPath = protectedFilesDir.path + File.separator
        val protectedFiles = mutableListOf<File>()
        protectedFilesDir.walk().forEach { file ->
            val path = file.path.replace(protectedFilesPath, "")
            if (backupFilePatterns.any { path.matches(it) })
                protectedFiles.add(file)
        }
        var error: String? = ""
        val wait = CountDownLatch(1)
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
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
                    protectedFiles.forEach {
                        val fileStream = FileInputStream(it).buffered()
                        zipStream.putNextEntry(ZipEntry(it.path.replace(protectedFilesDir.path, "unprotected")))
                        fileStream.copyTo(zipStream, 1024)
                        fileStream.close()
                        zipStream.closeEntry()
                    }
                    zipStream.putNextEntry(ZipEntry(PREFS_FILE_NAME))
                    settingsToJsonStream(sharedPreferences.all, zipStream)
                    zipStream.closeEntry()
                    zipStream.putNextEntry(ZipEntry(PROTECTED_PREFS_FILE_NAME))
                    settingsToJsonStream(PreferenceManager.getDefaultSharedPreferences(requireContext()).all, zipStream)
                    zipStream.closeEntry()
                    zipStream.close()
                }
            } catch (t: Throwable) {
                error = t.message
                Log.w(TAG, "error during backup", t)
            } finally {
                wait.countDown()
            }
        }
        wait.await()
        if (!error.isNullOrBlank()) {
            // inform about every error
            infoDialog(requireContext(), requireContext().getString(R.string.backup_error, error))
        }
    }

    private fun restore(uri: Uri) {
        var error: String? = ""
        val wait = CountDownLatch(1)
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
            try {
                activity?.contentResolver?.openInputStream(uri)?.use { inputStream ->
                    ZipInputStream(inputStream).use { zip ->
                        var entry: ZipEntry? = zip.nextEntry
                        val filesDir = requireContext().filesDir?.path ?: return@execute
                        val deviceProtectedFilesDir = DeviceProtectedUtils.getFilesDir(requireContext()).path
                        Settings.getInstance().stopListener()
                        while (entry != null) {
                            if (entry.name.startsWith("unprotected${File.separator}")) {
                                val adjustedName = entry.name.substringAfter("unprotected${File.separator}")
                                if (backupFilePatterns.any { adjustedName.matches(it) }) {
                                    val targetFileName = upgradeFileNames(adjustedName)
                                    val file = File(deviceProtectedFilesDir, targetFileName)
                                    FileUtils.copyStreamToNewFile(zip, file)
                                }
                            } else if (backupFilePatterns.any { entry!!.name.matches(it) }) {
                                val targetFileName = upgradeFileNames(entry.name)
                                val file = File(filesDir, targetFileName)
                                FileUtils.copyStreamToNewFile(zip, file)
                            } else if (entry.name == PREFS_FILE_NAME) {
                                val prefLines = String(zip.readBytes()).split("\n")
                                sharedPreferences.edit().clear().apply()
                                readJsonLinesToSettings(prefLines, sharedPreferences)
                            } else if (entry.name == PROTECTED_PREFS_FILE_NAME) {
                                val prefLines = String(zip.readBytes()).split("\n")
                                val protectedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
                                protectedPrefs.edit().clear().apply()
                                readJsonLinesToSettings(prefLines, protectedPrefs)
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                }
            } catch (t: Throwable) {
                error = t.message
                Log.w(TAG, "error during restore", t)
            } finally {
                wait.countDown()
            }
        }
        wait.await()
        if (!error.isNullOrBlank()) {
            // inform about every error
            infoDialog(requireContext(), requireContext().getString(R.string.restore_error, error))
        }
        checkVersionUpgrade(requireContext())
        Settings.getInstance().startListener()
        val additionalSubtypes = Settings.readPrefAdditionalSubtypes(sharedPreferences, resources)
        updateAdditionalSubtypes(AdditionalSubtypeUtils.createAdditionalSubtypesArray(additionalSubtypes))
        reloadEnabledSubtypes(requireContext())
        val newDictBroadcast = Intent(DictionaryPackConstants.NEW_DICTIONARY_INTENT_ACTION)
        activity?.sendBroadcast(newDictBroadcast)
        // reload current prefs screen
        preferenceScreen.removeAll()
        setupPreferences()
        KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(requireContext())
    }

    // todo (later): remove this when new package name has been in use for long enough, this is only for migrating from old openboard name
    private fun upgradeFileNames(originalName: String): String {
        return when {
            originalName.endsWith(USER_DICTIONARY_SUFFIX) -> {
                // replace directory after switch to language tag
                val dirName = originalName.substringAfter(File.separator).substringBefore(File.separator)
                originalName.replace(dirName, dirName.constructLocale().toLanguageTag())
            }
            originalName.startsWith("blacklists") -> {
                // replace file name after switch to language tag
                val fileName = originalName.substringAfter("blacklists${File.separator}").substringBefore(".txt")
                originalName.replace(fileName, fileName.constructLocale().toLanguageTag())
            }
            originalName.startsWith("layouts") -> {
                // replace file name after switch to language tag, but only if it's not a layout
                val localeString = originalName.substringAfter(".").substringBefore(".")
                if (localeString in listOf(LAYOUT_SYMBOLS, LAYOUT_SYMBOLS_SHIFTED, LAYOUT_SYMBOLS_ARABIC, LAYOUT_NUMBER, LAYOUT_NUMPAD, LAYOUT_NUMPAD_LANDSCAPE, LAYOUT_PHONE, LAYOUT_PHONE_SYMBOLS))
                    return originalName // it's a layout!
                val locale = localeString.constructLocale()
                if (locale.toLanguageTag() != "und")
                    originalName.replace(localeString, locale.toLanguageTag())
                else
                    originalName // no valid locale -> must be symbols layout, don't change
            }
            originalName.startsWith("UserHistoryDictionary") -> {
                val localeString = originalName.substringAfter(".").substringBefore(".")
                val locale = localeString.constructLocale()
                originalName.replace(localeString, locale.toLanguageTag())
            }
            else -> originalName
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

    private fun setupLanguageSwipeDistanceSettings() {
        val prefs = sharedPreferences
        findPreference<SeekBarDialogPreference>(Settings.PREF_LANGUAGE_SWIPE_DISTANCE)?.setInterface(object : ValueProxy {
            override fun writeValue(value: Int, key: String) = prefs.edit().putInt(key, value).apply()

            override fun writeDefaultValue(key: String) = prefs.edit().remove(key).apply()

            override fun readValue(key: String) = Settings.readLanguageSwipeDistance(prefs, resources)

            override fun readDefaultValue(key: String) = Settings.readDefaultLanguageSwipeDistance(resources)

            override fun getValueText(value: Int) = value.toString()

            override fun feedbackValue(value: Int) {}
        })
    }

    private fun updateLangSwipeDistanceVisibility(prefs: SharedPreferences) {
        val horizontalSpaceSwipe = Settings.readHorizontalSpaceSwipe(prefs)
        val verticalSpaceSwipe = Settings.readVerticalSpaceSwipe(prefs)
        val visibility = horizontalSpaceSwipe == KeyboardActionListener.SWIPE_SWITCH_LANGUAGE
                || verticalSpaceSwipe == KeyboardActionListener.SWIPE_SWITCH_LANGUAGE
        setPreferenceVisible(Settings.PREF_LANGUAGE_SWIPE_DISTANCE, visibility)
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        when (key) {
            Settings.PREF_SHOW_SETUP_WIZARD_ICON -> SystemBroadcastReceiver.toggleAppIcon(requireContext())
            Settings.PREF_MORE_POPUP_KEYS -> KeyboardLayoutSet.onSystemLocaleChanged()
            Settings.PREF_SPACE_HORIZONTAL_SWIPE -> updateLangSwipeDistanceVisibility(prefs)
            Settings.PREF_SPACE_VERTICAL_SWIPE -> updateLangSwipeDistanceVisibility(prefs)
        }
    }

    companion object {
        @Suppress("UNCHECKED_CAST") // it is checked... but whatever (except string set, because can't check for that))
        private fun settingsToJsonStream(settings: Map<String, Any?>, out: OutputStream) {
            val booleans = settings.filterValues { it is Boolean } as Map<String, Boolean>
            val ints = settings.filterValues { it is Int } as Map<String, Int>
            val longs = settings.filterValues { it is Long } as Map<String, Long>
            val floats = settings.filterValues { it is Float } as Map<String, Float>
            val strings = settings.filterValues { it is String } as Map<String, String>
            val stringSets = settings.filterValues { it is Set<*> } as Map<String, Set<String>>
            // now write
            out.write("boolean settings\n".toByteArray())
            out.write(Json.encodeToString(booleans).toByteArray())
            out.write("\nint settings\n".toByteArray())
            out.write(Json.encodeToString(ints).toByteArray())
            out.write("\nlong settings\n".toByteArray())
            out.write(Json.encodeToString(longs).toByteArray())
            out.write("\nfloat settings\n".toByteArray())
            out.write(Json.encodeToString(floats).toByteArray())
            out.write("\nstring settings\n".toByteArray())
            out.write(Json.encodeToString(strings).toByteArray())
            out.write("\nstring set settings\n".toByteArray())
            out.write(Json.encodeToString(stringSets).toByteArray())
        }

        private fun readJsonLinesToSettings(list: List<String>, prefs: SharedPreferences): Boolean {
            val i = list.iterator()
            val e = prefs.edit()
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
    }
}

private const val PREFS_FILE_NAME = "preferences.json"
private const val PROTECTED_PREFS_FILE_NAME = "protected_preferences.json"
private const val TAG = "AdvancedSettingsFragment"
