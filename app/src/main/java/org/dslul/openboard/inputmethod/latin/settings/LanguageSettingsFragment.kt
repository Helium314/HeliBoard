package org.dslul.openboard.inputmethod.latin.settings

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.preference.TwoStatePreference
import android.view.inputmethod.InputMethodSubtype
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
        addPreferencesFromResource(R.xml.prefs_screen_languages);
        SubtypeLocaleUtils.init(activity)

        enabledSubtypes.addAll(getEnabledSubtypes(sharedPreferences))
        systemLocales.addAll(getSystemLocales())
        loadSubtypes()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String) {
        super.onSharedPreferenceChanged(prefs, key)
        if (key == Settings.PREF_USE_SYSTEM_LOCALES)
            loadSubtypes()
    }

    override fun onResume() {
        super.onResume()
        languageFilterListPreference.setSettingsFragment(this)
    }

    override fun onPause() {
        super.onPause()
        languageFilterListPreference.setSettingsFragment(null)
    }

    private fun loadSubtypes() {
        val systemOnly = (findPreference(Settings.PREF_USE_SYSTEM_LOCALES) as TwoStatePreference).isChecked
        sortedSubtypes.clear()
        // list of all subtypes, any subtype added to sortedSubtypes will be removed to avoid duplicates
        val allSubtypes = getAllAvailableSubtypes().toMutableList()
        // maybe make use of the map used by SubtypeSettings for performance reasons?
        fun List<Locale>.sortedAddToSubtypesAndRemoveFromAllSubtypes() {
            val subtypesToAdd = mutableListOf<SubtypeInfo>()
            forEach { locale ->
                // this could be rather slow with looping multiple times over all ~100 subtypes,
                //  but usually there aren't many locales to be checked, and usually the first loop already finds a match
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
                // try again, but with language only
                if (!added && locale.country.isNotEmpty()) {
                    val languageString = locale.language
                    val iter = allSubtypes.iterator()
                    while (iter.hasNext()) {
                        val subtype = iter.next()
                        if (subtype.locale == languageString) {
                            subtypesToAdd.add(subtype.toSubtypeInfo(LocaleUtils.constructLocaleFromString(languageString)))
                            iter.remove()
                            added = true
                        }
                    }
                }
                // special treatment for the known languages with _ZZ types
                // todo: later: make it a bit less weird... and probably faster
                //  consider that more _ZZ languages might be added (e.g. hinglish)
                if (!added && locale.language == "sr") {
                    val languageString = locale.language
                    val iter = allSubtypes.iterator()
                    while (iter.hasNext()) {
                        val subtype = iter.next()
                        if (subtype.locale.substringBefore("_") == languageString) {
                            subtypesToAdd.add(subtype.toSubtypeInfo(LocaleUtils.constructLocaleFromString(subtype.locale)))
                            iter.remove()
                        }
                    }
                }
            }
            subtypesToAdd.sortedBy { it.displayName }.addToSortedSubtypes()
        }

        // add enabled subtypes
        enabledSubtypes.map { it.toSubtypeInfo(LocaleUtils.constructLocaleFromString(it.locale), true) }
            .sortedBy { it.displayName }.addToSortedSubtypes()
        allSubtypes.removeAll(enabledSubtypes)

        if (systemOnly) { // don't add anything else
            languageFilterListPreference.setLanguages(sortedSubtypes.values, systemOnly)
            return
        }

        // add subtypes that have a dictionary
        val localesWithDictionary = DictionaryInfoUtils.getCachedDirectoryList(activity)?.mapNotNull { dir ->
            if (!dir.isDirectory)
                return@mapNotNull null
            if (dir.list()?.any { it.endsWith(USER_DICTIONARY_SUFFIX) } == true)
                LocaleUtils.constructLocaleFromString(dir.name)
            else null
        }
        localesWithDictionary?.sortedAddToSubtypesAndRemoveFromAllSubtypes()

        // add subtypes for device locales
        systemLocales.sortedAddToSubtypesAndRemoveFromAllSubtypes()

        // add the remaining ones
        allSubtypes.map { it.toSubtypeInfo(LocaleUtils.constructLocaleFromString(it.locale)) }
            .sortedBy { if (it.subtype.locale.equals("zz", true))
                    "zz" // "No language (Alphabet)" should be last
                else it.displayName
            }.addToSortedSubtypes()

        // set languages
        languageFilterListPreference.setLanguages(sortedSubtypes.values, systemOnly)
    }

    private fun InputMethodSubtype.toSubtypeInfo(locale: Locale, isEnabled: Boolean = false) =
        toSubtypeInfo(locale, resources, isEnabled)

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

fun InputMethodSubtype.toSubtypeInfo(locale: Locale, resources: Resources, isEnabled: Boolean): SubtypeInfo {
    val displayName = if (locale.toString().equals("zz", true)) // no language
            SubtypeLocaleUtils.getSubtypeLocaleDisplayNameInSystemLocale(locale.toString())
        else if (locale.toString().endsWith("zz", true)) // serbian (latin), maybe others in the future
            SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(this)
        else
            locale.getDisplayName(resources.configuration.locale)
    return SubtypeInfo(displayName, this, isEnabled)
}

private const val DICTIONARY_REQUEST_CODE = 96834
const val USER_DICTIONARY_SUFFIX = "user.dict"
