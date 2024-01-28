package org.dslul.openboard.inputmethod.latin

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.dslul.openboard.inputmethod.latin.settings.Settings
import org.dslul.openboard.inputmethod.latin.settings.USER_DICTIONARY_SUFFIX
import org.dslul.openboard.inputmethod.latin.utils.DeviceProtectedUtils
import org.dslul.openboard.inputmethod.latin.utils.DictionaryInfoUtils
import org.dslul.openboard.inputmethod.latin.utils.upgradeToolbarPref

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        checkVersionUpgrade(this)
    }
}

fun checkVersionUpgrade(context: Context) {
    val prefs = DeviceProtectedUtils.getSharedPreferences(context)
    val oldVersion = prefs.getInt(Settings.PREF_VERSION_CODE, 0)
    if (oldVersion == BuildConfig.VERSION_CODE)
        return
    upgradeToolbarPref(prefs)
    // clear extracted dictionaries, in case updated version contains newer ones
    DictionaryInfoUtils.getCachedDirectoryList(context).forEach {
        if (!it.isDirectory) return@forEach
        val files = it.listFiles() ?: return@forEach
        for (file in files) {
            if (!file.name.endsWith(USER_DICTIONARY_SUFFIX))
                file.delete()
        }
    }
    if (oldVersion == 0) // new install or restoring settings from old app name
        prefUpgradesWhenComingFromOldAppName(prefs)
    prefs.edit { putInt(Settings.PREF_VERSION_CODE, BuildConfig.VERSION_CODE) }
}

// todo (later): remove it when most users probably have upgraded
private fun prefUpgradesWhenComingFromOldAppName(prefs: SharedPreferences) {
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
            when (val value = it.value) {
                is Boolean -> prefs.edit().putBoolean(it.key.substringAfter("pref_key_"), value).apply()
                is Int -> prefs.edit().putInt(it.key.substringAfter("pref_key_"), value).apply()
                is Long -> prefs.edit().putLong(it.key.substringAfter("pref_key_"), value).apply()
                is String -> prefs.edit().putString(it.key.substringAfter("pref_key_"), value).apply()
                is Float -> prefs.edit().putFloat(it.key.substringAfter("pref_key_"), value).apply()
            }
        } else if (it.key.startsWith("pref_")) {
            when (val value = it.value) {
                is Boolean -> prefs.edit().putBoolean(it.key.substringAfter("pref_"), value).apply()
                is Int -> prefs.edit().putInt(it.key.substringAfter("pref_"), value).apply()
                is Long -> prefs.edit().putLong(it.key.substringAfter("pref_"), value).apply()
                is String -> prefs.edit().putString(it.key.substringAfter("pref_"), value).apply()
                is Float -> prefs.edit().putFloat(it.key.substringAfter("pref_"), value).apply()
            }
        }
    }
}
