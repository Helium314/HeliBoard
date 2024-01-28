package org.dslul.openboard.inputmethod.latin

import android.app.Application
import android.content.Context
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
    if (prefs.getInt(Settings.PREF_VERSION_CODE, 0) == BuildConfig.VERSION_CODE)
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
    prefs.edit { putInt(Settings.PREF_VERSION_CODE, BuildConfig.VERSION_CODE) }
}
