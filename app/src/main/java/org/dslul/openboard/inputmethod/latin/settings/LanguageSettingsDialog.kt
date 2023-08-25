package org.dslul.openboard.inputmethod.latin.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.core.view.isGone
import androidx.core.view.isVisible
import org.dslul.openboard.inputmethod.dictionarypack.DictionaryPackConstants
import org.dslul.openboard.inputmethod.latin.BinaryDictionaryGetter
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.common.FileUtils
import org.dslul.openboard.inputmethod.latin.common.LocaleUtils
import org.dslul.openboard.inputmethod.latin.makedict.DictionaryHeader
import org.dslul.openboard.inputmethod.latin.utils.*
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.collections.HashSet

@Suppress("deprecation")
class LanguageSettingsDialog(
    context: Context,
    private val subtypes: MutableList<SubtypeInfo>,
    private val fragment: LanguageSettingsFragment?,
    private val disableSwitches: Boolean,
    private val onSubtypesChanged: () -> Unit
) : AlertDialog(ContextThemeWrapper(context, R.style.platformDialogTheme)), LanguageSettingsFragment.Listener {
    private val context = ContextThemeWrapper(context, R.style.platformDialogTheme)
    private val prefs = DeviceProtectedUtils.getSharedPreferences(context)!!
    private val view = LayoutInflater.from(context).inflate(R.layout.locale_settings_dialog, null)
    private val mainLocaleString = subtypes.first().subtype.locale
    private val mainLocale = mainLocaleString.toLocale()
    private val cachedDictionaryFile by lazy { File(context.cacheDir.path + File.separator + "temp_dict") }

    init {
        setTitle(subtypes.first().displayName)
        setView(ScrollView(context).apply { addView(view) })
        setButton(BUTTON_NEGATIVE, context.getString(R.string.dialog_close)) { _, _ ->
            dismiss()
        }

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
        if (subtypes.any { it.subtype.isAsciiCapable }) { // currently can only add subtypes for latin keyboards
            subtypesView.findViewById<ImageView>(R.id.add_subtype).setOnClickListener {
                val layouts = context.resources.getStringArray(R.array.predefined_layouts)
                    .filterNot { layoutName -> subtypes.any { SubtypeLocaleUtils.getKeyboardLayoutSetName(it.subtype) == layoutName } }
                val displayNames = layouts.map { SubtypeLocaleUtils.getKeyboardLayoutSetDisplayName(it) }
                Builder(context)
                    .setTitle(R.string.keyboard_layout_set)
                    .setItems(displayNames.toTypedArray()) { di, i ->
                        di.dismiss()
                        val newSubtype = AdditionalSubtypeUtils.createAsciiEmojiCapableAdditionalSubtype(mainLocaleString, layouts[i])
                        val newSubtypeInfo = newSubtype.toSubtypeInfo(mainLocale, context.resources, true) // enabled by default, because why else add them
                        addSubtypeToView(newSubtypeInfo, subtypesView)
                        val oldAdditionalSubtypesString = Settings.readPrefAdditionalSubtypes(prefs, context.resources)
                        val oldAdditionalSubtypes = AdditionalSubtypeUtils.createAdditionalSubtypesArray(oldAdditionalSubtypesString).toHashSet()
                        val newAdditionalSubtypesString = AdditionalSubtypeUtils.createPrefSubtypes((oldAdditionalSubtypes + newSubtype).toTypedArray())
                        Settings.writePrefAdditionalSubtypes(prefs, newAdditionalSubtypesString)
                        addEnabledSubtype(prefs, newSubtype)
                        subtypes.add(newSubtypeInfo)
                        onSubtypesChanged()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        } else
            subtypesView.findViewById<View>(R.id.add_subtype).isGone = true

        // add subtypes
        subtypes.sortedBy { it.displayName }.forEach {
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
            isEnabled = !disableSwitches
            setOnCheckedChangeListener { _, b ->
                if (b)
                    addEnabledSubtype(prefs, subtype.subtype)
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
                    subtypes.remove(subtype)

                    val oldAdditionalSubtypesString = Settings.readPrefAdditionalSubtypes(prefs, context.resources)
                    val oldAdditionalSubtypes = AdditionalSubtypeUtils.createAdditionalSubtypesArray(oldAdditionalSubtypesString)
                    val newAdditionalSubtypes = oldAdditionalSubtypes.filter { it != subtype.subtype }
                    val newAdditionalSubtypesString = AdditionalSubtypeUtils.createPrefSubtypes(newAdditionalSubtypes.toTypedArray())
                    Settings.writePrefAdditionalSubtypes(prefs, newAdditionalSubtypesString)
                    removeEnabledSubtype(prefs, subtype.subtype)
                    onSubtypesChanged()
                }
            }
        }
        subtypesView.addView(row)
    }

    private fun fillSecondaryLocaleView(secondaryLocalesView: LinearLayout) {
        // can only use multilingual typing if there is more than one dictionary available
        val availableSecondaryLocales = getAvailableDictionaryLocales(
            context,
            mainLocaleString,
            subtypes.first().subtype.isAsciiCapable
        )
        val selectedSecondaryLocales = Settings.getSecondaryLocales(prefs, mainLocaleString)
        selectedSecondaryLocales.forEach {
            addSecondaryLocaleView(it, secondaryLocalesView)
        }
        if (availableSecondaryLocales.isNotEmpty()) {
            secondaryLocalesView.findViewById<ImageView>(R.id.add_secondary_language).apply {
                isVisible = true
                setOnClickListener {
                    val locales = (availableSecondaryLocales - Settings.getSecondaryLocales(prefs, mainLocaleString).map { it.toString() }).sorted()
                    val localeNames = locales.map { it.toLocale().getDisplayName(context.resources.configuration.locale) }.toTypedArray()
                    Builder(context)
                        .setTitle(R.string.language_selection_title)
                        .setItems(localeNames) { di, i ->
                            val locale = locales[i]
                            val localeStrings = Settings.getSecondaryLocales(prefs, mainLocaleString).map { it.toString() }
                            Settings.setSecondaryLocales(prefs, mainLocaleString, localeStrings + locale)
                            addSecondaryLocaleView(locale.toLocale(), secondaryLocalesView)
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
        val (userDicts, hasInternalDict) = getUserAndInternalDictionaries(context, mainLocaleString)
        if (hasInternalDict) {
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
        if (uri == null)
            return onDictionaryLoadingError(R.string.dictionary_load_error)

        cachedDictionaryFile.delete()
        try {
            FileUtils.copyStreamToNewFile(
                context.contentResolver.openInputStream(uri),
                cachedDictionaryFile
            )
        } catch (e: IOException) {
            return onDictionaryLoadingError(R.string.dictionary_load_error)
        }
        val newHeader = DictionaryInfoUtils.getDictionaryFileHeaderOrNull(cachedDictionaryFile, 0, cachedDictionaryFile.length())
            ?: return onDictionaryLoadingError(R.string.dictionary_file_error)

        val locale = newHeader.mLocaleString.toLocale()
        // ScriptUtils.getScriptFromSpellCheckerLocale may return latin when it should not,
        // e.g. for Persian or Chinese. But at least fail when dictionary certainly is incompatible
        if (ScriptUtils.getScriptFromSpellCheckerLocale(locale) != ScriptUtils.getScriptFromSpellCheckerLocale(mainLocale))
            return onDictionaryLoadingError(R.string.dictionary_file_wrong_script)

        if (locale != mainLocale) {
            val message = context.resources.getString(
                R.string.dictionary_file_wrong_locale,
                locale.getDisplayName(context.resources.configuration.locale),
                mainLocale.getDisplayName(context.resources.configuration.locale)
            )
            Builder(context)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel) { _, _ -> cachedDictionaryFile.delete() }
                .setPositiveButton(R.string.dictionary_file_wrong_locale_ok) { _, _ ->
                    addDictAndAskToReplace(newHeader)
                }
                .show()
            return
        }
        addDictAndAskToReplace(newHeader)
    }

    private fun addDictAndAskToReplace(header: DictionaryHeader) {
        val dictionaryType = header.mIdString.substringBefore(":")
        val dictFilename = DictionaryInfoUtils.getCacheDirectoryForLocale(mainLocaleString, context) +
                File.separator + dictionaryType + "_" + USER_DICTIONARY_SUFFIX
        val dictFile = File(dictFilename)

        fun moveDict(replaced: Boolean) {
            if (!cachedDictionaryFile.renameTo(dictFile)) {
                return onDictionaryLoadingError(R.string.dictionary_load_error)
            }
            if (dictionaryType == DictionaryInfoUtils.DEFAULT_MAIN_DICT) {
                // replaced main dict, remove the one created from internal data
                val internalMainDictFilename = DictionaryInfoUtils.getCacheDirectoryForLocale(this.toString(), context) +
                        File.separator + DictionaryInfoUtils.getMainDictFilename(this.toString())
                File(internalMainDictFilename).delete()
            }
            val newDictBroadcast = Intent(DictionaryPackConstants.NEW_DICTIONARY_INTENT_ACTION)
            fragment?.activity?.sendBroadcast(newDictBroadcast)
            if (!replaced)
                addDictionaryToView(dictFile, view.findViewById(R.id.dictionaries))
        }

        if (!dictFile.exists()) {
            return moveDict(false)
        }
        confirmDialog(context, context.getString(R.string.replace_dictionary_message, dictionaryType), context.getString(
            R.string.replace_dictionary)) {
            moveDict(true)
        }
    }

    private fun onDictionaryLoadingError(messageId: Int) = onDictionaryLoadingError(context.getString(messageId))

    private fun onDictionaryLoadingError(message: String) {
        cachedDictionaryFile.delete()
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private fun addDictionaryToView(dictFile: File, dictionariesView: LinearLayout) {
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
    BinaryDictionaryGetter.getAssetsDictionaryList(context)?.forEach { dictFile ->
        BinaryDictionaryGetter.extractLocaleFromAssetsDictionaryFile(dictFile)?.let {
            if (it == localeString)
                return userDicts to true
        }
    }
    return userDicts to false
}

// get locales with same script as main locale, but different language
private fun getAvailableDictionaryLocales(context: Context, mainLocaleString: String, asciiCapable: Boolean): Set<String> {
    val mainLocale = mainLocaleString.toLocale()
    val locales = HashSet<String>()
    val mainScript = if (asciiCapable) ScriptUtils.SCRIPT_LATIN
    else ScriptUtils.getScriptFromSpellCheckerLocale(mainLocale)
    // ScriptUtils.getScriptFromSpellCheckerLocale may return latin when it should not
    //  e.g. for persian or chinese
    // workaround: don't allow secondary locales for these locales
    if (!asciiCapable && mainScript == ScriptUtils.SCRIPT_LATIN) return locales

    // get cached dictionaries: extracted or user-added dictionaries
    val cachedDirectoryList = DictionaryInfoUtils.getCachedDirectoryList(context)
    if (cachedDirectoryList != null) {
        for (directory in cachedDirectoryList) {
            if (!directory.isDirectory) continue
            val dirLocale = DictionaryInfoUtils.getWordListIdFromFileName(directory.name)
            if (dirLocale == mainLocaleString) continue
            val locale = dirLocale.toLocale()
            if (locale.language == mainLocale.language) continue
            val localeScript = ScriptUtils.getScriptFromSpellCheckerLocale(locale)
            if (localeScript != mainScript) continue
            locales.add(locale.toString())
        }
    }
    // get assets dictionaries
    val assetsDictionaryList = BinaryDictionaryGetter.getAssetsDictionaryList(context)
    if (assetsDictionaryList != null) {
        for (dictionary in assetsDictionaryList) {
            val dictLocale =
                BinaryDictionaryGetter.extractLocaleFromAssetsDictionaryFile(dictionary)
                    ?: continue
            if (dictLocale == mainLocaleString) continue
            val locale = dictLocale.toLocale()
            if (locale.language == mainLocale.language) continue
            val localeScript = ScriptUtils.getScriptFromSpellCheckerLocale(locale)
            if (localeScript != mainScript) continue
            locales.add(locale.toString())
        }
    }
    return locales
}

private fun String.toLocale() = LocaleUtils.constructLocaleFromString(this)
private const val DICTIONARY_URL = "https://codeberg.org/Helium314/aosp-dictionaries"
