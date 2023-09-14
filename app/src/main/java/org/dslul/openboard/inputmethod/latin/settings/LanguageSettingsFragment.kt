package org.dslul.openboard.inputmethod.latin.settings

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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.common.LocaleUtils
import org.dslul.openboard.inputmethod.latin.utils.DeviceProtectedUtils
import org.dslul.openboard.inputmethod.latin.utils.DictionaryInfoUtils
import org.dslul.openboard.inputmethod.latin.utils.SubtypeLocaleUtils
import org.dslul.openboard.inputmethod.latin.utils.getDictionaryLocales
import java.util.*

// not a SettingsFragment, because with androidx.preferences it's very complicated or
// impossible to have the languages RecyclerView scrollable (this way it works nicely out of the box)
class LanguageSettingsFragment : Fragment(R.layout.language_settings) {
    private val sortedSubtypes = LinkedHashMap<String, MutableList<SubtypeInfo>>()
    private val enabledSubtypes = mutableListOf<InputMethodSubtype>()
    private val systemLocales = mutableListOf<Locale>()
    private lateinit var languageFilterList: LanguageFilterListFakePreference
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var systemOnlySwitch: SwitchCompat
    private val dictionaryLocales by lazy { getDictionaryLocales(requireContext()).mapTo(HashSet()) { it.languageConsideringZZ() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = DeviceProtectedUtils.getSharedPreferences(requireContext())

        SubtypeLocaleUtils.init(requireContext())

        enabledSubtypes.addAll(getEnabledSubtypes(sharedPreferences))
        systemLocales.addAll(getSystemLocales())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState) ?: return null
        systemOnlySwitch = view.findViewById(R.id.language_switch)
        systemOnlySwitch.isChecked = sharedPreferences.getBoolean(Settings.PREF_USE_SYSTEM_LOCALES, true)
        systemOnlySwitch.setOnCheckedChangeListener { _, b ->
            sharedPreferences.edit { putBoolean(Settings.PREF_USE_SYSTEM_LOCALES, b) }
            enabledSubtypes.clear()
            enabledSubtypes.addAll(getEnabledSubtypes(sharedPreferences))
            loadSubtypes(b)
        }
        languageFilterList = LanguageFilterListFakePreference(view.findViewById(R.id.search_field), view.findViewById(R.id.language_list))
        loadSubtypes(systemOnlySwitch.isChecked)
        return view
    }

    override fun onResume() {
        super.onResume()
        languageFilterList.setSettingsFragment(this)
        val activity: Activity? = activity
        if (activity is AppCompatActivity) {
            val actionBar = activity.supportActionBar ?: return
            actionBar.setTitle(R.string.language_selection_title)
        }
    }

    override fun onPause() {
        super.onPause()
        languageFilterList.setSettingsFragment(null)
    }

    private fun loadSubtypes(systemOnly: Boolean) {
        sortedSubtypes.clear()
        // list of all subtypes, any subtype added to sortedSubtypes will be removed to avoid duplicates
        val allSubtypes = getAllAvailableSubtypes().toMutableList()
        // todo: re-write this, it's hard to understand
        //  also consider  that more _ZZ languages might be added
        fun List<Locale>.sortedAddToSubtypesAndRemoveFromAllSubtypes() {
            val subtypesToAdd = mutableListOf<SubtypeInfo>()
            forEach { locale ->
                val localeString = locale.toString()
                val iterator = allSubtypes.iterator()
                var added = false
                while (iterator.hasNext()) {
                    val subtype = iterator.next()
                    if (subtype.locale() == localeString) {
                        subtypesToAdd.add(subtype.toSubtypeInfo(locale))
                        iterator.remove()
                        added = true
                    }
                }
                // try again, but with language only
                if (!added && locale.country.isNotEmpty()) {
                    val languageString = locale.language
                    val iter = allSubtypes.iterator()
                    while (iter.hasNext()) {
                        val subtype = iter.next()
                        if (subtype.locale() == languageString) {
                            subtypesToAdd.add(subtype.toSubtypeInfo(LocaleUtils.constructLocaleFromString(languageString)))
                            iter.remove()
                            added = true
                        }
                    }
                }
                // special treatment for the known languages with _ZZ types
                if (!added && (locale.language == "sr" || locale.language == "hi")) {
                    val languageString = locale.language
                    val iter = allSubtypes.iterator()
                    while (iter.hasNext()) {
                        val subtype = iter.next()
                        if (subtype.locale().substringBefore("_") == languageString) {
                            subtypesToAdd.add(subtype.toSubtypeInfo(LocaleUtils.constructLocaleFromString(subtype.locale())))
                            iter.remove()
                        }
                    }
                }
            }
            subtypesToAdd.sortedBy { it.displayName }.addToSortedSubtypes()
        }

        // add enabled subtypes
        enabledSubtypes.map { it.toSubtypeInfo(LocaleUtils.constructLocaleFromString(it.locale()), true) }
            .sortedBy { it.displayName }.addToSortedSubtypes()
        allSubtypes.removeAll(enabledSubtypes)

        if (systemOnly) { // don't add anything else
            languageFilterList.setLanguages(sortedSubtypes.values, systemOnly)
            return
        }

        // add subtypes that have a dictionary
        val localesWithDictionary = DictionaryInfoUtils.getCachedDirectoryList(requireContext())?.mapNotNull { dir ->
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
        allSubtypes.map { it.toSubtypeInfo(LocaleUtils.constructLocaleFromString(it.locale())) }
            .sortedBy { if (it.subtype.locale().equals("zz", true))
                    "zz" // "No language (Alphabet)" should be last
                else it.displayName
            }.addToSortedSubtypes()

        // set languages
        languageFilterList.setLanguages(sortedSubtypes.values, systemOnly)
    }

    private fun InputMethodSubtype.toSubtypeInfo(locale: Locale, isEnabled: Boolean = false) =
        toSubtypeInfo(locale, requireContext(), isEnabled, dictionaryLocales.contains(locale.languageConsideringZZ()))

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

private fun Locale.languageConsideringZZ(): String {
    return if (country.equals("zz", false))
        "${language}_zz"
    else
        language
}

private const val DICTIONARY_REQUEST_CODE = 96834
const val USER_DICTIONARY_SUFFIX = "user.dict"
