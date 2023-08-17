package org.dslul.openboard.inputmethod.latin.settings

import android.os.Bundle
import android.view.inputmethod.InputMethodSubtype
import androidx.core.app.LocaleManagerCompat
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.RichInputMethodManager
import org.dslul.openboard.inputmethod.latin.common.LocaleUtils
import org.dslul.openboard.inputmethod.latin.utils.DictionaryInfoUtils
import org.dslul.openboard.inputmethod.latin.utils.SubtypeLocaleUtils
import java.util.Locale


class LanguageSettingsFragment : SubScreenFragment() {

    private val sortedSubtypes = mutableListOf<SubtypeInfo>()
    private val enabledSubtypes = mutableListOf<InputMethodSubtype>()
    private val systemLocales = mutableListOf<Locale>()

    // todo:
    //  * xml file
    //     that "system languages" switch on top
    //      if disabling, set those languages enabled like it would have been done manually
    //      where is the text coming from? can't find it in this repo, so probably system
    //     then the filter editText (with magnifying glass icon)
    //     then recyclerview with the (filtered) subtype list
    //      if "system languages" is enabled, only show those and don't allow disabling
    //      on disabling, update the list immediately? or don't yet re-order in case it was accidental?
    //  * click item dialog
    //     add/remove dictionary thing, like now
    //     secondary locale options like now, but also offer adding a dictionary (or even multiple? after all it's using stuff from another language...)
    //      idea: add some user-defined factor for each language? suggestion would be multiplied by this (probably should be between 0.8 and 1.2 to work reasonable)
    //     option to change / adjust layout (need to check how exactly)
    //  * where / how to actually store changed language selection?
    //     probably simple keyboard can help here

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.prefs_screen_language);
        SubtypeLocaleUtils.init(activity)

        enabledSubtypes.addAll(RichInputMethodManager.getInstance().getMyEnabledInputMethodSubtypeList(true))
        systemLocales.addAll(getSystemLocales())
        loadSubtypes()
        val thatPref = findPreference("language_filter") as LanguageFilterListPreference
        thatPref.setLanguages(sortedSubtypes)
    }

    // performance not great (0.1-0.2 s on S4 mini), but still acceptable
    // todo: different behavior if only showing system locales!
    private fun loadSubtypes() {
        val inputMethodInfo = RichInputMethodManager.getInstance().inputMethodInfoOfThisIme
        val l = mutableListOf<InputMethodSubtype>()
        for (i in 0 until inputMethodInfo.subtypeCount) {
            l.add(inputMethodInfo.getSubtypeAt(i))
            // subtype.locale is locale (deprecated, but working)
            // subtype.languageTag is often not set (in method.xml), and additionally requires api24
        }

        // add enabled subtypes
        sortedSubtypes.addAll(enabledSubtypes.map { it.toSubtypeInfo(true) }.sortedBy { it.displayName })
        l.removeAll(enabledSubtypes)

        // add subtypes that have a dictionary
        val localesWithDictionary = DictionaryInfoUtils.getCachedDirectoryList(activity)?.mapNotNull { dir ->
            if (!dir.isDirectory)
                return@mapNotNull null
            if (dir.list()?.any { it.endsWith(DictionarySettingsFragment.USER_DICTIONARY_SUFFIX) } == true)
                LocaleUtils.constructLocaleFromString(dir.name)
            else null
        }
        val currentLocales = sortedSubtypes.map { it.subtype.locale }.toHashSet() // remove the ones we already have, just for performance
        localesWithDictionary?.filterNot { it.toString() in currentLocales }?.mapNotNull { locale ->
            val localeString = locale.toString()
            val subtype = l.firstOrNull { it.locale == localeString } // only takes first, but multiple types should be rare here anyway
            l.remove(subtype)
            currentLocales.add(localeString)
            subtype?.toSubtypeInfo()
        }?.sortedBy { it.displayName }?.let { sortedSubtypes.addAll(it) }

        // add subtypes for device locales, todo: don't duplicate code
        systemLocales.filterNot { it.toString() in currentLocales }.mapNotNull { locale ->
            val localeString = locale.toString()
            val subtype = l.firstOrNull { it.locale == localeString } // only takes first, but multiple types should be rare here anyway
            l.remove(subtype)
            currentLocales.add(localeString)
            subtype?.toSubtypeInfo()
        }.sortedBy { it.displayName }.let { sortedSubtypes.addAll(it) }

        // add the remaining ones
        sortedSubtypes.addAll(l.map { it.toSubtypeInfo() }.sortedBy { it.displayName })
    }

    private fun InputMethodSubtype.toSubtypeInfo(isEnabled: Boolean = false) =
        SubtypeInfo(resources.getString(nameResId, SubtypeLocaleUtils.getSubtypeLocaleDisplayNameInSystemLocale(locale)), this, isEnabled)

    private fun getSystemLocales(): List<Locale> {
        val locales = LocaleManagerCompat.getSystemLocales(activity)
        return (0 until locales.size()).mapNotNull { locales[it] }
    }

}

data class SubtypeInfo(val displayName: String, val subtype: InputMethodSubtype, val isEnabled: Boolean)
