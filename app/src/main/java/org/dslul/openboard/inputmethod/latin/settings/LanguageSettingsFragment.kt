package org.dslul.openboard.inputmethod.latin.settings

import android.os.Bundle
import android.preference.TwoStatePreference
import android.view.inputmethod.InputMethodSubtype
import androidx.core.app.LocaleManagerCompat
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.RichInputMethodManager
import org.dslul.openboard.inputmethod.latin.common.LocaleUtils
import org.dslul.openboard.inputmethod.latin.utils.DictionaryInfoUtils
import org.dslul.openboard.inputmethod.latin.utils.SubtypeLocaleUtils
import java.util.Locale


@Suppress("Deprecation") // yes everything here is deprecated, but only work on this if really necessary
class LanguageSettingsFragment : SubScreenFragment() {

    private val sortedSubtypes = mutableListOf<SubtypeInfo>()
    private val enabledSubtypes = mutableListOf<InputMethodSubtype>()
    private val systemLocales = mutableListOf<Locale>()

    // todo:
    //  * where / how to actually store changed language selection?
    //     probably simple keyboard can help here
    //     check out https://github.com/rkkr/simple-keyboard/pull/291/files
    //     most of the stuff is in RichInputMethodManager (not surprising)
    //  * need some default to fall back if nothing is enabled
    //     -> system locales, and if not existing en_US

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.prefs_screen_language);
        SubtypeLocaleUtils.init(activity)

//        enabledSubtypes.addAll(RichInputMethodManager.getInstance().getMyEnabledInputMethodSubtypeList(true))
        enabledSubtypes.addAll(Settings.getInstance().current.mEnabledSubtypes)
        systemLocales.addAll(getSystemLocales())
        (findPreference("pref_system_languages") as TwoStatePreference).setOnPreferenceChangeListener { _, b ->
            loadSubtypes(b as Boolean)
            true
        }
    }

    override fun onResume() {
        super.onResume()
        loadSubtypes((findPreference("pref_system_languages") as TwoStatePreference).isChecked)
    }

    // performance not great (0.1-0.2 s on S4 mini), but still acceptable
    // todo: less duplicate code?
    private fun loadSubtypes(systemOnly: Boolean) {
        sortedSubtypes.clear()
        val inputMethodInfo = RichInputMethodManager.getInstance().inputMethodInfoOfThisIme
        val l = mutableListOf<InputMethodSubtype>()
        for (i in 0 until inputMethodInfo.subtypeCount) {
            l.add(inputMethodInfo.getSubtypeAt(i))
            // subtype.locale is locale (deprecated, but working)
            // subtype.languageTag is often not set (in method.xml), and additionally requires api24
        }

        if (systemOnly) {
            systemLocales.mapNotNull { locale ->
                val localeString = locale.toString()
                val subtype = l.firstOrNull { it.locale == localeString }
                subtype?.toSubtypeInfo()
            }.sortedBy { it.displayName }.let { sortedSubtypes.addAll(it) }
            (findPreference("pref_language_filter") as LanguageFilterListPreference).setLanguages(sortedSubtypes, systemOnly)
            return
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

        // add subtypes for device locales
        systemLocales.filterNot { it.toString() in currentLocales }.mapNotNull { locale ->
            val localeString = locale.toString()
            val subtype = l.firstOrNull { it.locale == localeString } // only takes first, but multiple types should be rare here anyway
            l.remove(subtype)
            currentLocales.add(localeString)
            subtype?.toSubtypeInfo()
        }.sortedBy { it.displayName }.let { sortedSubtypes.addAll(it) }

        // add the remaining ones
        sortedSubtypes.addAll(l.map { it.toSubtypeInfo() }.sortedBy { it.displayName })

        // set languages
        (findPreference("pref_language_filter") as LanguageFilterListPreference).setLanguages(sortedSubtypes, systemOnly)
    }

    private fun InputMethodSubtype.toSubtypeInfo(isEnabled: Boolean = false) =
        SubtypeInfo(resources.getString(nameResId, SubtypeLocaleUtils.getSubtypeLocaleDisplayNameInSystemLocale(locale)), this, isEnabled)

    private fun getSystemLocales(): List<Locale> {
        val locales = LocaleManagerCompat.getSystemLocales(activity)
        return (0 until locales.size()).mapNotNull { locales[it] }
    }

}

data class SubtypeInfo(val displayName: String, val subtype: InputMethodSubtype, val isEnabled: Boolean)
