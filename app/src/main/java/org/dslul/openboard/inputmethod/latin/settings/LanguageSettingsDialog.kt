package org.dslul.openboard.inputmethod.latin.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.size
import org.dslul.openboard.inputmethod.dictionarypack.DictionaryPackConstants
import org.dslul.openboard.inputmethod.latin.BinaryDictionaryGetter
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.common.LocaleUtils
import org.dslul.openboard.inputmethod.latin.utils.*
import java.io.File
import java.util.*

class LanguageSettingsDialog(
    context: Context,
    private val infos: MutableList<SubtypeInfo>,
    private val fragment: LanguageFakeSettingsFragment?,
    private val onlySystemLocales: Boolean,
    private val onSubtypesChanged: () -> Unit
) : AlertDialog(context), LanguageFakeSettingsFragment.Listener {
    private val prefs = DeviceProtectedUtils.getSharedPreferences(context)!!
    private val view = LayoutInflater.from(context).inflate(R.layout.locale_settings_dialog, null)
    private val mainLocaleString = infos.first().subtype.locale()
    private val mainLocale = mainLocaleString.toLocale()

    init {
        setTitle(infos.first().displayName)
        setView(ScrollView(context).apply { addView(view) })
        setButton(BUTTON_NEGATIVE, context.getString(R.string.dialog_close)) { _, _ ->
            dismiss()
        }

        if (onlySystemLocales)
            view.findViewById<View>(R.id.subtypes).isGone = true
        else
            fillSubtypesView(view.findViewById(R.id.subtypes))
        fillSecondaryLocaleView(view.findViewById(R.id.secondary_languages))
        fillDictionariesView(view.findViewById(R.id.dictionaries))
    }

    override fun onStart() {
        super.onStart()
        fragment?.setListener(this)
    }

    override fun onStop() {
        super.onStop()
        fragment?.setListener(null)
    }

    private fun fillSubtypesView(subtypesView: LinearLayout) {
        if (infos.any { it.subtype.isAsciiCapable }) { // currently can only add subtypes for latin keyboards
            subtypesView.findViewById<ImageView>(R.id.add_subtype).setOnClickListener {
                val layouts = context.resources.getStringArray(R.array.predefined_layouts)
                    .filterNot { layoutName -> infos.any { SubtypeLocaleUtils.getKeyboardLayoutSetName(it.subtype) == layoutName } }
                val displayNames = layouts.map { SubtypeLocaleUtils.getKeyboardLayoutSetDisplayName(it) }
                Builder(context)
                    .setTitle(R.string.keyboard_layout_set)
                    .setItems(displayNames.toTypedArray()) { di, i ->
                        di.dismiss()
                        val newSubtype = AdditionalSubtypeUtils.createAsciiEmojiCapableAdditionalSubtype(mainLocaleString, layouts[i])
                        val newSubtypeInfo = newSubtype.toSubtypeInfo(mainLocale, context, true, infos.first().hasDictionary) // enabled by default, because why else add them
                        addAdditionalSubtype(prefs, context.resources, newSubtype)
                        addEnabledSubtype(prefs, newSubtype)
                        addSubtypeToView(newSubtypeInfo, subtypesView)
                        infos.add(newSubtypeInfo)
                        onSubtypesChanged()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        } else
            subtypesView.findViewById<View>(R.id.add_subtype).isGone = true

        // add subtypes
        infos.sortedBy { it.displayName }.forEach {
            addSubtypeToView(it, subtypesView)
        }
    }

    private fun addSubtypeToView(subtype: SubtypeInfo, subtypesView: LinearLayout) {
        val row = LayoutInflater.from(context).inflate(R.layout.language_list_item, listView)
        row.findViewById<TextView>(R.id.language_name).text =
            SubtypeLocaleUtils.getKeyboardLayoutSetDisplayName(subtype.subtype)
                ?: SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(subtype.subtype)
        row.findViewById<View>(R.id.language_details).isGone = true
        row.findViewById<Switch>(R.id.language_switch).apply {
            isChecked = subtype.isEnabled
            isEnabled = !onlySystemLocales
            setOnCheckedChangeListener { _, b ->
                if (b) {
                    if (!infos.first().hasDictionary)
                        showMissingDictionaryDialog(context, mainLocale)
                    addEnabledSubtype(prefs, subtype.subtype)
                }
                else
                    removeEnabledSubtype(prefs, subtype.subtype)
                subtype.isEnabled = b
                onSubtypesChanged()
            }
        }
        if (isAdditionalSubtype(subtype.subtype)) {
            row.findViewById<Switch>(R.id.language_switch).isEnabled = true
            row.findViewById<ImageView>(R.id.delete_button).apply {
                isVisible = true
                setOnClickListener {
                    // can be re-added easily, no need for confirmation dialog
                    subtypesView.removeView(row)
                    infos.remove(subtype)

                    removeAdditionalSubtype(prefs, context.resources, subtype.subtype)
                    removeEnabledSubtype(prefs, subtype.subtype)
                    onSubtypesChanged()
                }
            }
        }
        subtypesView.addView(row)
    }

    private fun fillSecondaryLocaleView(secondaryLocalesView: LinearLayout) {
        // can only use multilingual typing if there is more than one dictionary available
        val availableSecondaryLocales = getAvailableSecondaryLocales(
            context,
            mainLocaleString,
            infos.first().subtype.isAsciiCapable
        )
        val selectedSecondaryLocales = Settings.getSecondaryLocales(prefs, mainLocaleString)
        selectedSecondaryLocales.forEach {
            addSecondaryLocaleView(it, secondaryLocalesView)
        }
        if (availableSecondaryLocales.isNotEmpty()) {
            secondaryLocalesView.findViewById<ImageView>(R.id.add_secondary_language).apply {
                isVisible = true
                setOnClickListener {
                    val locales = (availableSecondaryLocales - Settings.getSecondaryLocales(prefs, mainLocaleString)).sortedBy { it.displayName }
                    val localeNames = locales.map { LocaleUtils.getLocaleDisplayNameInSystemLocale(it, context) }.toTypedArray()
                    Builder(context)
                        .setTitle(R.string.language_selection_title)
                        .setItems(localeNames) { di, i ->
                            val locale = locales[i]
                            val localeStrings = Settings.getSecondaryLocales(prefs, mainLocaleString).map { it.toString() }
                            Settings.setSecondaryLocales(prefs, mainLocaleString, localeStrings + locale.toString())
                            addSecondaryLocaleView(locale, secondaryLocalesView)
                            di.dismiss()
                        }
                        .show()
                }
            }
        } else if (selectedSecondaryLocales.isEmpty())
            secondaryLocalesView.isGone = true
    }

    private fun addSecondaryLocaleView(locale: Locale, secondaryLocalesView: LinearLayout) {
        val row = LayoutInflater.from(context).inflate(R.layout.language_list_item, listView)
        row.findViewById<Switch>(R.id.language_switch).isGone = true
        row.findViewById<Switch>(R.id.language_details).isGone = true
        row.findViewById<TextView>(R.id.language_name).text = locale.displayName
        row.findViewById<ImageView>(R.id.delete_button).apply {
            isVisible = true
            setOnClickListener {
                val localeStrings = Settings.getSecondaryLocales(prefs, mainLocaleString).map { it.toString() }
                Settings.setSecondaryLocales(prefs, mainLocaleString, localeStrings - locale.toString())
                secondaryLocalesView.removeView(row)
            }
        }
        secondaryLocalesView.addView(row)
    }

    private fun fillDictionariesView(dictionariesView: LinearLayout) {
        dictionariesView.findViewById<ImageView>(R.id.add_dictionary).setOnClickListener {
            val link = "<a href='$DICTIONARY_URL'>" + context.getString(R.string.dictionary_link_text) + "</a>"
            val message = Html.fromHtml(context.getString(R.string.add_dictionary, link))
            val dialog = Builder(context)
                .setTitle(R.string.add_new_dictionary_title)
                .setMessage(message)
                .setPositiveButton(R.string.user_dict_settings_add_menu_title) { _, _ -> fragment?.requestDictionary() }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
            dialog.show()
            (dialog.findViewById<View>(android.R.id.message) as? TextView)?.movementMethod = LinkMovementMethod.getInstance()
        }
        val (userDicts, hasInternalDictForLanguage) = getUserAndInternalDictionaries(context, mainLocaleString)
        if (hasInternalDictForLanguage) {
            dictionariesView.addView(TextView(context, null, R.style.PreferenceCategoryTitleText).apply {
                setText(R.string.internal_dictionary_summary)
                textSize *= 0.8f
                setPadding((context.resources.displayMetrics.scaledDensity * 16).toInt(), 0, 0, 0)
                isEnabled = userDicts.none { it.name == "${DictionaryInfoUtils.MAIN_DICT_PREFIX}${USER_DICTIONARY_SUFFIX}" }
            })
        }
        userDicts.sorted().forEach {
            addDictionaryToView(it, dictionariesView)
        }
    }

    override fun onNewDictionary(uri: Uri?) {
        NewDictionaryAdder(context) { replaced, dictFile ->
            if (!replaced)
                addDictionaryToView(dictFile, view.findViewById(R.id.dictionaries))
        }.addDictionary(uri, mainLocale)
    }

    private fun addDictionaryToView(dictFile: File, dictionariesView: LinearLayout) {
        if (!infos.first().hasDictionary) {
            infos.forEach { it.hasDictionary = true }
        }
        val dictType = dictFile.name.substringBefore("_${USER_DICTIONARY_SUFFIX}")
        val row = LayoutInflater.from(context).inflate(R.layout.language_list_item, listView)
        row.findViewById<TextView>(R.id.language_name).text = dictType
        row.findViewById<TextView>(R.id.language_details).apply {
            val header = DictionaryInfoUtils.getDictionaryFileHeaderOrNull(dictFile, 0, dictFile.length())
            if (header?.description == null) {
                isGone = true
            } else {
                // what would potentially be interesting? locale? description? version? timestamp?
                text = header.description
            }
        }
        row.findViewById<Switch>(R.id.language_switch).isGone = true
        row.findViewById<ImageView>(R.id.delete_button).apply {
            isVisible = true
            setOnClickListener {
                confirmDialog(context, context.getString(R.string.remove_dictionary_message, dictType), context.getString(
                    R.string.delete_dict)) {
                    val parent = dictFile.parentFile
                    dictFile.delete()
                    if (parent?.list()?.isEmpty() == true)
                        parent.delete()
                    val newDictBroadcast = Intent(DictionaryPackConstants.NEW_DICTIONARY_INTENT_ACTION)
                    fragment?.activity?.sendBroadcast(newDictBroadcast)
                    dictionariesView.removeView(row)
                    if (dictionariesView.size < 2) { // first view is "Dictionaries"
                        infos.forEach { it.hasDictionary = false }
                    }

                }
            }
        }
        dictionariesView.addView(row)
    }
}

fun confirmDialog(context: Context, message: String, confirmButton: String, onConfirmed: (() -> Unit)) {
    AlertDialog.Builder(context)
        .setMessage(message)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(confirmButton) { _, _ -> onConfirmed() }
        .show()
}

/** @return list of user dictionary files and whether an internal dictionary exists */
fun getUserAndInternalDictionaries(context: Context, locale: String): Pair<List<File>, Boolean> {
    val localeString = locale.lowercase() // internal files and folders always use lowercase
    val userDicts = mutableListOf<File>()
    var hasInternalDict = false
    val userLocaleDir = File(DictionaryInfoUtils.getWordListCacheDirectory(context), localeString)
    if (userLocaleDir.exists() && userLocaleDir.isDirectory) {
        userLocaleDir.listFiles()?.forEach {
            if (it.name.endsWith(USER_DICTIONARY_SUFFIX))
                userDicts.add(it)
            else if (it.name.startsWith(DictionaryInfoUtils.MAIN_DICT_PREFIX))
                hasInternalDict = true
        }
    }
    if (hasInternalDict)
        return userDicts to true
    val language = localeString.languageConsideringZZ()
    BinaryDictionaryGetter.getAssetsDictionaryList(context)?.forEach { dictFile ->
        BinaryDictionaryGetter.extractLocaleFromAssetsDictionaryFile(dictFile)?.let {
            if (it == localeString || it.languageConsideringZZ() == language)
                return userDicts to true
        }
    }
    return userDicts to false
}

private fun String.languageConsideringZZ(): String {
    return if (endsWith("zz", false))
        this
    else
        substringBefore("_")
}

// get locales with same script as main locale, but different language
private fun getAvailableSecondaryLocales(context: Context, mainLocaleString: String, asciiCapable: Boolean): Set<Locale> {
    val mainLocale = mainLocaleString.toLocale()
    val locales = getDictionaryLocales(context)
    val mainScript = if (asciiCapable) ScriptUtils.SCRIPT_LATIN
    else ScriptUtils.getScriptFromSpellCheckerLocale(mainLocale)
    // ScriptUtils.getScriptFromSpellCheckerLocale may return latin when it should not
    //  e.g. for persian or chinese
    // workaround: don't allow secondary locales for these locales
    if (!asciiCapable && mainScript == ScriptUtils.SCRIPT_LATIN) return emptySet()

    locales.removeAll {
        it.language == mainLocale.language
                || ScriptUtils.getScriptFromSpellCheckerLocale(it) != mainScript
    }
    return locales
}
