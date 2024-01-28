package org.dslul.openboard.inputmethod.latin

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.dslul.openboard.inputmethod.latin.common.LocaleUtils.constructLocale
import org.dslul.openboard.inputmethod.latin.settings.Settings
import org.dslul.openboard.inputmethod.latin.settings.USER_DICTIONARY_SUFFIX
import org.dslul.openboard.inputmethod.latin.utils.DeviceProtectedUtils
import org.dslul.openboard.inputmethod.latin.utils.DictionaryInfoUtils
import org.dslul.openboard.inputmethod.latin.utils.Log
import org.dslul.openboard.inputmethod.latin.utils.upgradeToolbarPref
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
    Log.i("test", "old version $oldVersion")
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
        upgradesWhenComingFromOldAppName(context)
    prefs.edit { putInt(Settings.PREF_VERSION_CODE, BuildConfig.VERSION_CODE) }
}

// todo (later): remove it when most users probably have upgraded
private fun upgradesWhenComingFromOldAppName(context: Context) {
    // move layout files
    try {
        val layoutsDir = Settings.getLayoutsDir(context)
        File(context.filesDir, "layouts").listFiles()?.forEach {
            it.copyTo(File(layoutsDir, it.name), true)
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
    // upgrade additional subtype locale strings
    val additionalSubtypes = mutableListOf<String>()
    Settings.readPrefAdditionalSubtypes(prefs, context.resources).split(";").forEach {
        val localeString = it.substringBefore(":")
        additionalSubtypes.add(it.replace(localeString, localeString.constructLocale().toLanguageTag()))
    }
    Settings.writePrefAdditionalSubtypes(prefs, additionalSubtypes.joinToString(";"))
    // move pinned clips to credential protected storage
    if (!prefs.contains(Settings.PREF_PINNED_CLIPS)) return
    val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)
    defaultPrefs.edit { putString(Settings.PREF_PINNED_CLIPS, prefs.getString(Settings.PREF_PINNED_CLIPS, "")) }
    prefs.edit { remove(Settings.PREF_PINNED_CLIPS) }
}
