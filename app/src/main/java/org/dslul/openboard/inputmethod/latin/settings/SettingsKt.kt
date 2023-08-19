package org.dslul.openboard.inputmethod.latin.settings

import android.content.Context
import android.content.SharedPreferences
import android.view.inputmethod.InputMethodSubtype
import androidx.core.app.LocaleManagerCompat
import org.dslul.openboard.inputmethod.latin.RichInputMethodManager
import org.dslul.openboard.inputmethod.latin.utils.AdditionalSubtypeUtils

fun getEnabledSubtypes(prefs: SharedPreferences, context: Context): Array<InputMethodSubtype> {
    val inputMethodStrings = prefs.getStringSet(Settings.PREF_ENABLED_INPUT_STYLES, HashSet())!!
    if (inputMethodStrings.isEmpty()) return getDefaultEnabledSubtypes(context)

    val inputMethodSubtypes = inputMethodStrings.mapNotNull { AdditionalSubtypeUtils.createSubtypeFromString(it) }
    if (inputMethodSubtypes.isEmpty()) return getDefaultEnabledSubtypes(context)
    return inputMethodSubtypes.toTypedArray()
}

fun getDefaultEnabledSubtypes(context: Context): Array<InputMethodSubtype> {
    val inputMethodInfo = RichInputMethodManager.getInstance().inputMethodInfoOfThisIme
    val localeList = LocaleManagerCompat.getSystemLocales(context)
    val locales = (0 until localeList.size()).mapNotNull { localeList[it] }

    val inputMethodSubtypes = locales.mapNotNull { locale ->
        val localeString = locale.toString()
       (0 until localeList.size()).forEach {
           val subtype = inputMethodInfo.getSubtypeAt(it)
           if (localeString == subtype.locale)
               return@mapNotNull subtype
        }
        return@mapNotNull null
    }
    return inputMethodSubtypes.toTypedArray()
}
