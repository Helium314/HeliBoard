package org.dslul.openboard.inputmethod.latin.settings

import android.app.Activity
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.preference.TwoStatePreference
import android.view.inputmethod.InputMethodSubtype
import androidx.core.app.LocaleManagerCompat
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.common.LocaleUtils
import org.dslul.openboard.inputmethod.latin.utils.DictionaryInfoUtils
import org.dslul.openboard.inputmethod.latin.utils.SubtypeLocaleUtils
import java.util.Locale


@Suppress("Deprecation") // yes everything here is deprecated, but only work on this if really necessary
class LanguageSettingsFragment : SubScreenFragment() {

    private val sortedSubtypes = LinkedHashMap<String, MutableList<SubtypeInfo>>()
    private val enabledSubtypes = mutableListOf<InputMethodSubtype>()
    private val systemLocales = mutableListOf<Locale>()
    private val languageFilterListPreference by lazy { findPreference("pref_language_filter") as LanguageFilterListPreference }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.prefs_screen_language);
        SubtypeLocaleUtils.init(activity)

        enabledSubtypes.addAll(getEnabledSubtypes())
        systemLocales.addAll(getSystemLocales())
        (findPreference(Settings.PREF_USE_SYSTEM_LOCALES) as TwoStatePreference).setOnPreferenceChangeListener { _, b ->
            loadSubtypes(b as Boolean)
            true
        }
    }

    override fun onResume() {
        super.onResume()
        loadSubtypes((findPreference(Settings.PREF_USE_SYSTEM_LOCALES) as TwoStatePreference).isChecked)
        languageFilterListPreference.setSettingsFragment(this)
    }

    override fun onPause() {
        super.onPause()
        languageFilterListPreference.setSettingsFragment(null)
    }

    // todo: filter *_zz locales, should be added as subtype of their languages
    //  and ignore zz?
    private fun loadSubtypes(systemOnly: Boolean) {
        sortedSubtypes.clear()
        val allSubtypes = getAllAvailableSubtypes().toMutableList() // todo: replace? use the map to have it by locale?

        // todo: make use of the map for performance reasons
        fun List<Locale>.sortedAddToSubtypesAndRemoveFromAllSubtypes() {
            val subtypesToAdd = mutableListOf<SubtypeInfo>()
            forEach { locale ->
                val localeString = locale.toString()
                val iter = allSubtypes.iterator()
                var added = false
                while (iter.hasNext()) {
                    val subtype = iter.next()
                    if (subtype.locale == localeString) {
                        subtypesToAdd.add(subtype.toSubtypeInfo(locale))
                        iter.remove()
                        added = true
                    }
                }
                if (!added) {
                    // try again, but with language only
                    val languageString = locale.language
                    val iter = allSubtypes.iterator()
                    while (iter.hasNext()) {
                        val subtype = iter.next()
                        if (subtype.locale == languageString) {
                            subtypesToAdd.add(subtype.toSubtypeInfo(LocaleUtils.constructLocaleFromString(languageString)))
                            iter.remove()
                        }
                    }
                }
            }
            subtypesToAdd.sortedBy { it.displayName }.addToSortedSubtypes()
        }

        if (systemOnly) {
            systemLocales.sortedAddToSubtypesAndRemoveFromAllSubtypes()
            // todo: make sure these locales are all actually enabled in settingsValues
            //  but not here, rather in LatinIME onCreate
            //  the switch only changes enablement
            //  though it does use richIMM... we'll see
            languageFilterListPreference.setLanguages(sortedSubtypes.values, systemOnly)
            return
        }

        // add enabled subtypes
        enabledSubtypes.map { it.toSubtypeInfo(LocaleUtils.constructLocaleFromString(it.locale), true) }
            .sortedBy { it.displayName }.addToSortedSubtypes()
        allSubtypes.removeAll(enabledSubtypes)

        // add subtypes that have a dictionary
        val localesWithDictionary = DictionaryInfoUtils.getCachedDirectoryList(activity)?.mapNotNull { dir ->
            if (!dir.isDirectory)
                return@mapNotNull null
            if (dir.list()?.any { it.endsWith(DictionarySettingsFragment.USER_DICTIONARY_SUFFIX) } == true)
                LocaleUtils.constructLocaleFromString(dir.name)
            else null
        }
        localesWithDictionary?.sortedAddToSubtypesAndRemoveFromAllSubtypes()

        // add subtypes for device locales
        systemLocales.sortedAddToSubtypesAndRemoveFromAllSubtypes()

        // add the remaining ones
        allSubtypes.map { it.toSubtypeInfo(LocaleUtils.constructLocaleFromString(it.locale)) }
            .sortedBy { it.displayName }.addToSortedSubtypes()

        // set languages
        languageFilterListPreference.setLanguages(sortedSubtypes.values, systemOnly)
    }

    private fun InputMethodSubtype.toSubtypeInfo(locale: Locale, isEnabled: Boolean = false) =
        toSubtypeInfo(locale, resources, isEnabled)

    private fun getSystemLocales(): List<Locale> {
        val locales = LocaleManagerCompat.getSystemLocales(activity)
        return (0 until locales.size()).mapNotNull { locales[it] }
    }

    private fun List<SubtypeInfo>.addToSortedSubtypes() {
        forEach {
            sortedSubtypes.getOrPut(it.displayName) { mutableListOf() }.add(it)
        }
    }

    interface Listener {
        fun onNewDictionary(uri: Uri?)
    }

    private var listener: Listener? = null

    fun setListener(newListener: Listener?) {
        listener = newListener
    }

    fun requestDictionary() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/octet-stream")
        startActivityForResult(intent, DICTIONARY_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (resultCode == Activity.RESULT_OK && requestCode == DICTIONARY_REQUEST_CODE)
            listener?.onNewDictionary(resultData?.data)
    }

}

class SubtypeInfo(val displayName: String, val subtype: InputMethodSubtype, var isEnabled: Boolean) {
    override fun equals(other: Any?): Boolean {
        if (other !is SubtypeInfo) return false
        return subtype == other.subtype
    }

    override fun hashCode(): Int {
        return subtype.hashCode()
    }
}

fun InputMethodSubtype.toSubtypeInfo(locale: Locale, resources: Resources, isEnabled: Boolean) =
    SubtypeInfo(locale.getDisplayName(resources.configuration.locale), this, isEnabled)

private const val DICTIONARY_REQUEST_CODE = 96834
