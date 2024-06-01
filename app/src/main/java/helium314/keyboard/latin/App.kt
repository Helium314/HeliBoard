// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
package helium314.keyboard.latin

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.USER_DICTIONARY_SUFFIX
import helium314.keyboard.latin.utils.CUSTOM_LAYOUT_PREFIX
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.defaultPinnedToolbarPref
import helium314.keyboard.latin.utils.getCustomLayoutFile
import helium314.keyboard.latin.utils.onCustomLayoutFileListChanged
import helium314.keyboard.latin.utils.upgradeToolbarPrefs
import java.io.File

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        checkVersionUpgrade(this)
        app = this
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

fun checkVersionUpgrade(context: Context) {
    val prefs = DeviceProtectedUtils.getSharedPreferences(context)
    val oldVersion = prefs.getInt(Settings.PREF_VERSION_CODE, 0)
    if (oldVersion == BuildConfig.VERSION_CODE)
        return
    // clear extracted dictionaries, in case updated version contains newer ones
    DictionaryInfoUtils.getCachedDirectoryList(context)?.forEach {
        if (!it.isDirectory) return@forEach
        val files = it.listFiles() ?: return@forEach
        for (file in files) {
            if (!file.name.endsWith(USER_DICTIONARY_SUFFIX))
                file.delete()
        }
    }
    if (oldVersion == 0) // new install or restoring settings from old app name
        upgradesWhenComingFromOldAppName(context)
    if (oldVersion <= 1000) { // upgrade old custom layouts name
        val oldShiftSymbolsFile = getCustomLayoutFile("${CUSTOM_LAYOUT_PREFIX}shift_symbols", context)
        if (oldShiftSymbolsFile.exists()) {
            oldShiftSymbolsFile.renameTo(getCustomLayoutFile("${CUSTOM_LAYOUT_PREFIX}symbols_shifted", context))
        }

        // rename subtype setting, and clean old subtypes that might remain in some cases
        val subtypesPref = prefs.getString("enabled_input_styles", "")!!
            .split(";").filter { it.isNotEmpty() }
            .map {
                val localeAndLayout = it.split(":").toMutableList()
                localeAndLayout[0] = localeAndLayout[0].constructLocale().toLanguageTag()
                localeAndLayout.joinToString(":")
            }.toSet().joinToString(";")
        val selectedSubtype = prefs.getString("selected_input_style", "")
        prefs.edit {
            remove("enabled_input_styles")
            putString(Settings.PREF_ENABLED_SUBTYPES, subtypesPref)
            remove("selected_input_style")
            putString(Settings.PREF_SELECTED_SUBTYPE, selectedSubtype)
        }
    }
    if (oldVersion <= 2000) {
        // upgrade pinned toolbar keys pref
        val oldPinnedKeysPref = prefs.getString(Settings.PREF_PINNED_TOOLBAR_KEYS, "")!!
        val pinnedKeys = oldPinnedKeysPref.split(";").mapNotNull {
            try {
                ToolbarKey.valueOf(it)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        val newPinnedKeysPref = (pinnedKeys.map { "${it.name},true" } + defaultPinnedToolbarPref.split(";"))
            .distinctBy { it.split(",").first() }
            .joinToString(";")
        prefs.edit { putString(Settings.PREF_PINNED_TOOLBAR_KEYS, newPinnedKeysPref) }
    }
    upgradeToolbarPrefs(prefs)
    onCustomLayoutFileListChanged() // just to be sure
    prefs.edit { putInt(Settings.PREF_VERSION_CODE, BuildConfig.VERSION_CODE) }
}

// todo (later): remove it when most users probably have upgraded
private fun upgradesWhenComingFromOldAppName(context: Context) {
    // move layout files
    try {
        File(context.filesDir, "layouts").listFiles()?.forEach {
            it.copyTo(getCustomLayoutFile(it.name, context), true)
            it.delete()
        }
    } catch (_: Exception) {}
    // move background images
    try {
        val bgDay = File(context.filesDir, "custom_background_image")
        if (bgDay.isFile) {
            bgDay.copyTo(Settings.getCustomBackgroundFile(context, false), true)
            bgDay.delete()
        }
        val bgNight = File(context.filesDir, "custom_background_image_night")
        if (bgNight.isFile) {
            bgNight.copyTo(Settings.getCustomBackgroundFile(context, true), true)
            bgNight.delete()
        }
    } catch (_: Exception) {}
    // upgrade prefs
    val prefs = DeviceProtectedUtils.getSharedPreferences(context)
    if (prefs.all.containsKey("theme_variant")) {
        prefs.edit().putString(Settings.PREF_THEME_COLORS, prefs.getString("theme_variant", "")).apply()
        prefs.edit().remove("theme_variant").apply()
    }
    if (prefs.all.containsKey("theme_variant_night")) {
        prefs.edit().putString(Settings.PREF_THEME_COLORS_NIGHT, prefs.getString("theme_variant_night", "")).apply()
        prefs.edit().remove("theme_variant_night").apply()
    }
    prefs.all.toMap().forEach {
        if (it.key.startsWith("pref_key_") && it.key != "pref_key_longpress_timeout") {
            var remove = true
            when (val value = it.value) {
                is Boolean -> prefs.edit().putBoolean(it.key.substringAfter("pref_key_"), value).apply()
                is Int -> prefs.edit().putInt(it.key.substringAfter("pref_key_"), value).apply()
                is Long -> prefs.edit().putLong(it.key.substringAfter("pref_key_"), value).apply()
                is String -> prefs.edit().putString(it.key.substringAfter("pref_key_"), value).apply()
                is Float -> prefs.edit().putFloat(it.key.substringAfter("pref_key_"), value).apply()
                else -> remove = false
            }
            if (remove)
                prefs.edit().remove(it.key).apply()
        } else if (it.key.startsWith("pref_")) {
            var remove = true
            when (val value = it.value) {
                is Boolean -> prefs.edit().putBoolean(it.key.substringAfter("pref_"), value).apply()
                is Int -> prefs.edit().putInt(it.key.substringAfter("pref_"), value).apply()
                is Long -> prefs.edit().putLong(it.key.substringAfter("pref_"), value).apply()
                is String -> prefs.edit().putString(it.key.substringAfter("pref_"), value).apply()
                is Float -> prefs.edit().putFloat(it.key.substringAfter("pref_"), value).apply()
                else -> remove = false
            }
            if (remove)
                prefs.edit().remove(it.key).apply()
        }
    }
    // change more_keys to popup_keys
    if (prefs.contains("more_keys_order")) {
        prefs.edit().putString(Settings.PREF_POPUP_KEYS_ORDER, prefs.getString("more_keys_order", "")?.replace("more_", "popup_")).apply()
        prefs.edit().remove("more_keys_order").apply()
    }
    if (prefs.contains("more_keys_labels_order")) {
        prefs.edit().putString(Settings.PREF_POPUP_KEYS_LABELS_ORDER, prefs.getString("more_keys_labels_order", "")?.replace("more_", "popup_")).apply()
        prefs.edit().remove("more_keys_labels_order").apply()
    }
    if (prefs.contains("more_more_keys")) {
        prefs.edit().putString(Settings.PREF_MORE_POPUP_KEYS, prefs.getString("more_more_keys", "")).apply()
        prefs.edit().remove("more_more_keys").apply()
    }
    if (prefs.contains("spellcheck_use_contacts")) {
        prefs.edit().putBoolean(Settings.PREF_USE_CONTACTS, prefs.getBoolean("spellcheck_use_contacts", false)).apply()
        prefs.edit().remove("spellcheck_use_contacts").apply()
    }
    // upgrade additional subtype locale strings
    val additionalSubtypes = mutableListOf<String>()
    Settings.readPrefAdditionalSubtypes(prefs, context.resources).split(";").forEach {
        val localeString = it.substringBefore(":")
        additionalSubtypes.add(it.replace(localeString, localeString.constructLocale().toLanguageTag()))
    }
    Settings.writePrefAdditionalSubtypes(prefs, additionalSubtypes.joinToString(";"))
    // move pinned clips to credential protected storage if device is not locked (should never happen)
    if (!prefs.contains(Settings.PREF_PINNED_CLIPS)) return
    try {
        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        defaultPrefs.edit { putString(Settings.PREF_PINNED_CLIPS, prefs.getString(Settings.PREF_PINNED_CLIPS, "")) }
        prefs.edit { remove(Settings.PREF_PINNED_CLIPS) }
    } catch (_: IllegalStateException) {
        // SharedPreferences in credential encrypted storage are not available until after user is unlocked
    }
}
