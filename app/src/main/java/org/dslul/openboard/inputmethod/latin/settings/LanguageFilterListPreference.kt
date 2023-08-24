package org.dslul.openboard.inputmethod.latin.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.preference.Preference
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.doOnLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import org.dslul.openboard.inputmethod.dictionarypack.DictionaryPackConstants
import org.dslul.openboard.inputmethod.latin.BinaryDictionaryGetter
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.common.FileUtils
import org.dslul.openboard.inputmethod.latin.common.LocaleUtils
import org.dslul.openboard.inputmethod.latin.makedict.DictionaryHeader
import org.dslul.openboard.inputmethod.latin.utils.*
import java.io.File
import java.io.IOException
import java.util.Locale

class LanguageFilterListPreference(context: Context, attrs: AttributeSet) : Preference(context, attrs) {

    private var preferenceView: View? = null
    private val adapter = LanguageAdapter(emptyList(), context)
    private val sortedSubtypes = mutableListOf<MutableList<SubtypeInfo>>()

    fun setSettingsFragment(newFragment: LanguageSettingsFragment?) {
        adapter.fragment = newFragment
    }

    override fun onBindView(view: View?) {
        super.onBindView(view)
        preferenceView = view
        preferenceView?.findViewById<RecyclerView>(R.id.language_list)?.adapter = adapter
        val searchField = preferenceView?.findViewById<EditText>(R.id.search_field)!!
        searchField.doAfterTextChanged { text ->
            adapter.list = sortedSubtypes.filter { it.first().displayName.startsWith(text.toString(), ignoreCase = true) }
        }
        view?.doOnLayout {
            // set correct height for recycler view, so there is no scrolling of the outside view happening
            // not sure how, but probably this can be achieved in xml...
            val windowFrame = Rect()
            it.getWindowVisibleDisplayFrame(windowFrame) // rect the app has, we want the bottom (above screen bottom/navbar/keyboard)
            val globalRect = Rect()
            it.getGlobalVisibleRect(globalRect) // rect the view takes, we want the top (below the system language preference)
            val recycler = it.findViewById<RecyclerView>(R.id.language_list)

            val newHeight = windowFrame.bottom - globalRect.top - it.findViewById<View>(R.id.search_container).height
            recycler.layoutParams = recycler.layoutParams.apply { height = newHeight }
        }
    }

    fun setLanguages(list: Collection<MutableList<SubtypeInfo>>, disableSwitches: Boolean) {
        sortedSubtypes.clear()
        sortedSubtypes.addAll(list)
        adapter.disableSwitches = disableSwitches
        adapter.list = sortedSubtypes
    }

}

class LanguageAdapter(list: List<MutableList<SubtypeInfo>> = listOf(), context: Context) :
    RecyclerView.Adapter<LanguageAdapter.ViewHolder>() {
    var disableSwitches = false
    private val prefs = DeviceProtectedUtils.getSharedPreferences(context)
    var fragment: LanguageSettingsFragment? = null

    var list: List<MutableList<SubtypeInfo>> = list
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.onBind(list[position])
    }

    override fun getItemCount() = list.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageAdapter.ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.language_list_item, parent, false)
        return ViewHolder(v)
    }

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {

        fun onBind(infos: MutableList<SubtypeInfo>) {
            fun setupDetailsTextAndSwitch() {
                // this is unrelated -> rename it
                view.findViewById<TextView>(R.id.language_details).apply {
                    // input styles if more than one in infos
                    val sb = StringBuilder()
                    if (infos.size > 1) {
                        sb.append(infos.joinToString(", ") {// separator ok? because for some languages it might not be...
                            SubtypeLocaleUtils.getKeyboardLayoutSetDisplayName(it.subtype)
                                ?: SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(it.subtype)
                        })
                    }
                    val secondaryLocales = Settings.getSecondaryLocales(prefs, infos.first().subtype.locale)
                    if (secondaryLocales.isNotEmpty()) {
                        if (sb.isNotEmpty())
                            sb.append("\n")
                        sb.append(Settings.getSecondaryLocales(prefs, infos.first().subtype.locale)
                            .joinToString(", ") {
                                it.getDisplayName(context.resources.configuration.locale)
                            })
                    }
                    text = sb.toString()
                    if (text.isBlank()) isGone = true
                        else isVisible = true
                }

                view.findViewById<Switch>(R.id.language_switch).apply {
                    isEnabled = !disableSwitches && infos.size == 1
                    // take care: isChecked changes if the language is scrolled out of view and comes back!
                    // disable the change listener when setting the checked status on scroll
                    // so it's only triggered on user interactions
                    setOnCheckedChangeListener(null)
                    isChecked = disableSwitches || infos.any { it.isEnabled }
                    setOnCheckedChangeListener { _, b ->
                        if (b) {
                            if (infos.size == 1) {
                                addEnabledSubtype(prefs, infos.first().subtype)
                                infos.single().isEnabled = true
                            } else {
                                LocaleSubtypeSettingsDialog(view.context, infos, fragment, disableSwitches, { setupDetailsTextAndSwitch() }).show()
                            }
                        } else {
                            if (infos.size == 1) {
                                removeEnabledSubtype(prefs, infos.first().subtype)
                                infos.single().isEnabled = false
                            } else {
                                LocaleSubtypeSettingsDialog(view.context, infos, fragment, disableSwitches, { setupDetailsTextAndSwitch() }).show()
                            }
                        }
                    }
                }
            }

            view.findViewById<TextView>(R.id.language_name).text = infos.first().displayName
            view.findViewById<LinearLayout>(R.id.language_text).setOnClickListener {
                LocaleSubtypeSettingsDialog(view.context, infos, fragment, disableSwitches, { setupDetailsTextAndSwitch() }).show()
            }
            setupDetailsTextAndSwitch()
        }
    }
}

// todo: some kind of contextThemeWrapper necessary?
@Suppress("deprecation")
private class LocaleSubtypeSettingsDialog(
        context: Context,
        private val subtypes: MutableList<SubtypeInfo>,
        private val fragment: LanguageSettingsFragment?,
        private val disableSwitches: Boolean,
        private val onSubtypesChanged: () -> Unit
    ) : AlertDialog(context), LanguageSettingsFragment.Listener {
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
        val row = LayoutInflater.from(context).inflate(R.layout.language_list_item, null)
        row.findViewById<TextView>(R.id.language_name).text =
            SubtypeLocaleUtils.getKeyboardLayoutSetDisplayName(subtype.subtype)
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
        val row = LayoutInflater.from(context).inflate(R.layout.language_list_item, null)
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
            dictionariesView.addView(TextView(context).apply {
                setText(R.string.internal_dictionary_summary)
                isEnabled = userDicts.none { it.name == "${DictionaryInfoUtils.MAIN_DICT_PREFIX}${DictionarySettingsFragment.USER_DICTIONARY_SUFFIX}" }
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
                File.separator + dictionaryType + "_" + DictionarySettingsFragment.USER_DICTIONARY_SUFFIX
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
        confirmDialog(context, context.getString(R.string.replace_dictionary_message2, dictionaryType), context.getString(R.string.replace_dictionary)) {
            moveDict(true)
        }
    }

    private fun onDictionaryLoadingError(messageId: Int) = onDictionaryLoadingError(context.getString(messageId))

    private fun onDictionaryLoadingError(message: String) {
        cachedDictionaryFile.delete()
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private fun addDictionaryToView(dictFile: File, dictionariesView: LinearLayout) {
        val dictType = dictFile.name.substringBefore("_${DictionarySettingsFragment.USER_DICTIONARY_SUFFIX}")
        val row = LayoutInflater.from(context).inflate(R.layout.language_list_item, null)
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
                confirmDialog(context, context.getString(R.string.remove_dictionary_message2, dictType), context.getString(R.string.delete_dict)) {
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
//        .setTitle("confirm") // maybe looks ok without?
        .setMessage(message)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(confirmButton) { _, _ -> onConfirmed() }
        .show()
}

/** @return list of user dictionary files and whether an internal dictionary exists */
fun getUserAndInternalDictionaries(context: Context, localeString: String): Pair<List<File>, Boolean> {
    val localeString = localeString.lowercase() // files and folders always use lowercase
    val userDicts = mutableListOf<File>()
    var hasInternalDict = false
    val userLocaleDir = File(DictionaryInfoUtils.getWordListCacheDirectory(context), localeString)
    if (userLocaleDir.exists() && userLocaleDir.isDirectory) {
        userLocaleDir.listFiles()?.forEach {
            if (it.name.endsWith(DictionarySettingsFragment.USER_DICTIONARY_SUFFIX))
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
