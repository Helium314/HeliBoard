// SPDX-License-Identifier: GPL-3.0-only

package org.oscar.kb.latin.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodSubtype
import android.widget.Switch
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import org.oscar.kb.latin.settings.Settings
import org.oscar.kb.R
import org.oscar.kb.latin.common.LocaleUtils
import org.oscar.kb.latin.common.LocaleUtils.constructLocale
import org.oscar.kb.latin.utils.DeviceProtectedUtils
import org.oscar.kb.latin.utils.DictionaryInfoUtils
import org.oscar.kb.latin.utils.ScriptUtils.script
import org.oscar.kb.latin.utils.SubtypeLocaleUtils
import org.oscar.kb.latin.utils.getAllAvailableSubtypes
import org.oscar.kb.latin.utils.getDictionaryLocales
import org.oscar.kb.latin.utils.getEnabledSubtypes
import org.oscar.kb.latin.utils.getSystemLocales
import org.oscar.kb.latin.utils.locale
import java.util.*

// not a SettingsFragment, because with androidx.preferences it's very complicated or
// impossible to have the languages RecyclerView scrollable (this way it works nicely out of the box)
class LanguageSettingsFragment : Fragment(R.layout.language_settings) {
    private val sortedSubtypesByDisplayName = LinkedHashMap<String, MutableList<SubtypeInfo>>()
    private val enabledSubtypes = mutableListOf<InputMethodSubtype>()
    private val systemLocales = mutableListOf<Locale>()
    private lateinit var languageFilterList: LanguageFilterList
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var systemOnlySwitch: Switch
    private val dictionaryLocales by lazy { getDictionaryLocales(requireContext()) }

    private val dictionaryFilePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = it.data?.data ?: return@registerForActivityResult
        listener?.onNewDictionary(uri)
    }

    private val layoutFilePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = it.data?.data ?: return@registerForActivityResult
        listener?.onNewLayoutFile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = _root_ide_package_.org.oscar.kb.latin.utils.DeviceProtectedUtils.getSharedPreferences(requireContext())

        _root_ide_package_.org.oscar.kb.latin.utils.SubtypeLocaleUtils.init(requireContext())

        enabledSubtypes.addAll(getEnabledSubtypes(sharedPreferences))
        systemLocales.addAll(getSystemLocales())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState) ?: return null
        systemOnlySwitch = view.findViewById(R.id.language_switch)
        systemOnlySwitch.isChecked = sharedPreferences.getBoolean(_root_ide_package_.org.oscar.kb.latin.settings.Settings.PREF_USE_SYSTEM_LOCALES, true)
        systemOnlySwitch.setOnCheckedChangeListener { _, b ->
            sharedPreferences.edit { putBoolean(_root_ide_package_.org.oscar.kb.latin.settings.Settings.PREF_USE_SYSTEM_LOCALES, b) }
            enabledSubtypes.clear()
            enabledSubtypes.addAll(getEnabledSubtypes(sharedPreferences))
            loadSubtypes(b)
        }
        languageFilterList = LanguageFilterList(view.findViewById(R.id.search_field), view.findViewById(R.id.language_list))
        loadSubtypes(systemOnlySwitch.isChecked)
        return view
    }

    override fun onResume() {
        super.onResume()
        languageFilterList.setSettingsFragment(this)
        val activity: Activity? = activity
        if (activity is AppCompatActivity) {
            val actionBar = activity.supportActionBar ?: return
            actionBar.setTitle(R.string.language_and_layouts_title)
        }
    }

    override fun onPause() {
        super.onPause()
        languageFilterList.setSettingsFragment(null)
    }

    private fun loadSubtypes(systemOnly: Boolean) {
        sortedSubtypesByDisplayName.clear()
        // list of all subtypes, any subtype added to sortedSubtypes will be removed to avoid duplicates
        val allSubtypes = getAllAvailableSubtypes().toMutableList()
        fun List<Locale>.sortedAddToSubtypesAndRemoveFromAllSubtypes() {
            val subtypesToAdd = mutableListOf<SubtypeInfo>()
            forEach { locale ->
                val iterator = allSubtypes.iterator()
                var added = false
                while (iterator.hasNext()) {
                    val subtype = iterator.next()
                    if (subtype.locale() == locale) {
                        // add subtypes with matching locale
                        subtypesToAdd.add(subtype.toSubtypeInfo(locale))
                        iterator.remove()
                        added = true
                    }
                }
                // if locale has a country try again, but match language and script only
                if (!added && locale.country.isNotEmpty()) {
                    val language = locale.language
                    val script = locale.script()
                    val iter = allSubtypes.iterator()
                    while (iter.hasNext()) {
                        val subtype = iter.next()
                        val subtypeLocale = subtype.locale()
                        if (subtypeLocale.toLanguageTag() == subtypeLocale.language && subtypeLocale.language == language && script == subtypeLocale.script()) {
                            // add subtypes using the language only
                            subtypesToAdd.add(subtype.toSubtypeInfo(language.constructLocale()))
                            iter.remove()
                            added = true
                        }
                    }
                }
                // try again if script is not the default script, match language only
                if (!added && locale.script() != locale.language.constructLocale().script()) {
                    val language = locale.language
                    val iter = allSubtypes.iterator()
                    while (iter.hasNext()) {
                        val subtype = iter.next()
                        if (subtype.locale().language == language) {
                            subtypesToAdd.add(subtype.toSubtypeInfo(subtype.locale()))
                            iter.remove()
                        }
                    }
                }
            }
            subtypesToAdd.sortedBy { it.displayName }.addToSortedSubtypes()
        }

        // add enabled subtypes
        enabledSubtypes.map { it.toSubtypeInfo(it.locale(), true) }
            .sortedBy { it.displayName }.addToSortedSubtypes()
        allSubtypes.removeAll(enabledSubtypes)

        if (systemOnly) { // don't add anything else
            languageFilterList.setLanguages(sortedSubtypesByDisplayName.values, systemOnly)
            return
        }

        // add subtypes that have a dictionary
        val localesWithDictionary = _root_ide_package_.org.oscar.kb.latin.utils.DictionaryInfoUtils.getCachedDirectoryList(requireContext())?.mapNotNull { dir ->
            if (!dir.isDirectory)
                return@mapNotNull null
            if (dir.list()?.any { it.endsWith(USER_DICTIONARY_SUFFIX) } == true)
                dir.name.constructLocale()
            else null
        }
        localesWithDictionary?.sortedAddToSubtypesAndRemoveFromAllSubtypes()

        // add subtypes for device locales
        systemLocales.sortedAddToSubtypesAndRemoveFromAllSubtypes()

        // add the remaining ones
        allSubtypes.map { it.toSubtypeInfo(it.locale()) }
            .sortedBy { if (it.subtype.locale().toLanguageTag().equals(_root_ide_package_.org.oscar.kb.latin.utils.SubtypeLocaleUtils.NO_LANGUAGE, true))
                    _root_ide_package_.org.oscar.kb.latin.utils.SubtypeLocaleUtils.NO_LANGUAGE // "No language (Alphabet)" should be last
                else it.displayName
            }.addToSortedSubtypes()

        // set languages
        languageFilterList.setLanguages(sortedSubtypesByDisplayName.values, systemOnly)
    }

    private fun InputMethodSubtype.toSubtypeInfo(locale: Locale, isEnabled: Boolean = false) =
        toSubtypeInfo(locale, requireContext(), isEnabled, LocaleUtils.getBestMatch(locale, dictionaryLocales) {it} != null)

    private fun List<SubtypeInfo>.addToSortedSubtypes() {
        forEach {
            sortedSubtypesByDisplayName.getOrPut(it.displayName) { mutableListOf() }.add(it)
        }
    }

    interface Listener {
        fun onNewDictionary(uri: Uri?)
        fun onNewLayoutFile(uri: Uri?)
    }

    private var listener: Listener? = null

    fun setListener(newListener: Listener?) {
        listener = newListener
    }

    fun requestDictionary() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/octet-stream")
        dictionaryFilePicker.launch(intent)
    }

    fun requestLayoutFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            // todo: any working way to allow only json and text files?
            .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/*", "application/octet-stream", "application/json"))
            .setType("*/*")
        layoutFilePicker.launch(intent)
    }
}

class SubtypeInfo(val displayName: String, val subtype: InputMethodSubtype, var isEnabled: Boolean, var hasDictionary: Boolean) {
    override fun equals(other: Any?): Boolean {
        if (other !is SubtypeInfo) return false
        return subtype == other.subtype
    }

    override fun hashCode(): Int {
        return subtype.hashCode()
    }
}

fun InputMethodSubtype.toSubtypeInfo(locale: Locale, context: Context, isEnabled: Boolean, hasDictionary: Boolean): SubtypeInfo =
    SubtypeInfo(LocaleUtils.getLocaleDisplayNameInSystemLocale(locale, context), this, isEnabled, hasDictionary)

const val USER_DICTIONARY_SUFFIX = "user.dict"
