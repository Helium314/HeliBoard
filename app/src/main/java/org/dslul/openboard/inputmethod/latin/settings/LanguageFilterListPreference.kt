package org.dslul.openboard.inputmethod.latin.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.preference.Preference
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
    private val sortedSubtypes = mutableListOf<MutableList<SubtypeInfo>>() // todo: maybe better just use that map?

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
            view.findViewById<TextView>(R.id.language_name).text = infos.first().displayName
            // todo: get full display name including layout: resources.getString(nameResId, SubtypeLocaleUtils.getSubtypeLocaleDisplayNameInSystemLocale(locale))

//            view.findViewById<TextView>(R.id.language_details).text = // some short info, no more than 2 lines
            view.findViewById<LinearLayout>(R.id.language_text).setOnClickListener {
                // todo: click item dialog (better full screen with custom layout, not some alertDialog)
                //  secondary locale options similar to now
                //  add/remove dictionary thing, like now
                //   but also for secondary locales
                //  option to change / adjust layout (need to check how exactly)
                LocaleSubtypeSettingsDialog(view.context, infos, fragment).show()
            }
            view.findViewById<Switch>(R.id.language_switch).apply {
                // take care: isChecked changes if the language is scrolled out of view and comes back!
                // disable the change listener when setting the checked status on scroll
                // so it's only triggered on user interactions
                setOnCheckedChangeListener(null)
                isChecked = disableSwitches || infos.any { it.isEnabled }
                isEnabled = !disableSwitches
                var shouldBeChecked = isChecked
                // todo: change enablement, turn off screen and turn on again -> back to initial enablement
                setOnCheckedChangeListener { _, b ->
                    if (b == shouldBeChecked) return@setOnCheckedChangeListener // avoid the infinite circle...
                    if (b) {
                        if (infos.size == 1) {
                            addEnabledSubtype(prefs, infos.first().subtype)
                            shouldBeChecked = true
                            infos.single().isEnabled = true
                        } else {
                            shouldBeChecked = false
                            isChecked = false
                            LocaleSubtypeSettingsDialog(view.context, infos, fragment).show()
                        }
                    } else {
                        if (infos.size == 1) {
                            removeEnabledSubtype(prefs, infos.first().subtype)
                            shouldBeChecked = false
                            infos.single().isEnabled = false
                        } else {
                            shouldBeChecked = true
                            isChecked = true
                            LocaleSubtypeSettingsDialog(view.context, infos, fragment).show()
                        }
                    }
                }
            }
            // todo: set other text
        }
    }
}

// todo: some kind of contextThemeWrapper?
private class LocaleSubtypeSettingsDialog(
        context: Context, private val subtypes: MutableList<SubtypeInfo>, private val fragment: LanguageSettingsFragment?
    ) : AlertDialog(context), LanguageSettingsFragment.Listener {
    private val prefs = DeviceProtectedUtils.getSharedPreferences(context)!!
    private val view = LayoutInflater.from(context).inflate(R.layout.locale_settings_dialog, null)
    private val mainLocaleString = subtypes.first().subtype.locale
    private val mainLocale = mainLocaleString.toLocale()
    private val cachedDictionaryFile by lazy { File(context.cacheDir.path + File.separator + "temp_dict") }

    init {
        setTitle(subtypes.first().displayName)
        setView(ScrollView(context).apply { addView(view) })
        setButton(BUTTON_NEGATIVE, "close") { _, _ ->
            dismiss()
        }

        fillSubtypesView(view.findViewById(R.id.subtypes))
        fillSecondaryLocaleView(view.findViewById(R.id.secondary_languages))
        fillDictionariesView(view.findViewById(R.id.dictionaries))

        // todo:
        //  make it actually work
        //  refine dialog
        //   style for texts, and stuff
        //   padding/margins
        //   dividers
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
                        // todo: maybe enabled by default? why else would you add them?
                    .setTitle(R.string.keyboard_layout_set)
                    .setItems(displayNames.toTypedArray()) { di, i ->
                        di.dismiss()
                        val newSubtype = AdditionalSubtypeUtils.createAsciiEmojiCapableAdditionalSubtype(mainLocaleString, layouts[i])
                        addSubtypeToView(newSubtype.toSubtypeInfo(mainLocale, context.resources, false), subtypesView)
                        val oldAdditionalSubtypesString = Settings.readPrefAdditionalSubtypes(prefs, context.resources)
                        val oldAdditionalSubtypes = AdditionalSubtypeUtils.createAdditionalSubtypesArray(oldAdditionalSubtypesString).toHashSet()
                        val newAdditionalSubtypesString = AdditionalSubtypeUtils.createPrefSubtypes((oldAdditionalSubtypes + newSubtype).toTypedArray())
                        Settings.writePrefAdditionalSubtypes(prefs, newAdditionalSubtypesString)
                        subtypes.add(newSubtype.toSubtypeInfo(mainLocale, context.resources, false))
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
            subtype.subtype.getDisplayName(context, context.packageName, context.applicationInfo)
        row.findViewById<View>(R.id.language_details).isGone = true
        row.findViewById<Switch>(R.id.language_switch).apply {
            isChecked = subtype.isEnabled
            setOnCheckedChangeListener { _, b ->
                // todo: adjust base switch enabled state (dammit)
                if (b)
                    addEnabledSubtype(prefs, subtype.subtype)
                else
                    removeEnabledSubtype(prefs, subtype.subtype)
                subtype.isEnabled = b
            }
        }
        if (isAdditionalSubtype(subtype.subtype)) {
            row.findViewById<ImageView>(R.id.delete_button).apply {
                isVisible = true
                setOnClickListener {
                    confirmDialog(context, "really remove subtype ${subtype.subtype.getDisplayName(context, context.packageName, context.applicationInfo)}?", "remove") {
                        // todo: resources
                        subtypesView.removeView(row)
                        subtypes.remove(subtype)

                        val oldAdditionalSubtypesString = Settings.readPrefAdditionalSubtypes(prefs, context.resources)
                        val oldAdditionalSubtypes = AdditionalSubtypeUtils.createAdditionalSubtypesArray(oldAdditionalSubtypesString)
                        val newAdditionalSubtypes = oldAdditionalSubtypes.filter { it != subtype.subtype }
                        val newAdditionalSubtypesString = AdditionalSubtypeUtils.createPrefSubtypes(newAdditionalSubtypes.toTypedArray())
                        Settings.writePrefAdditionalSubtypes(prefs, newAdditionalSubtypesString)
                        removeEnabledSubtype(prefs, subtype.subtype)
                    }
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
                    val locales = (availableSecondaryLocales - Settings.getSecondaryLocales(prefs, mainLocaleString).map { it.toString() }).toList()
                    val localeNames = locales.map { it.toLocale().getDisplayName(context.resources.configuration.locale) }.toTypedArray()
                    Builder(context)
                        .setTitle(R.string.language_selection_title)
                        .setItems(localeNames) { di, i -> // todo: singleChoiceItems?
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
        // todo: manage dictionaries for that locale when clicking on language_texts?
        //  but essentially would require duplicating functionality in a different way
        secondaryLocalesView.addView(row)
    }

    private fun fillDictionariesView(dictionariesView: LinearLayout) {
        dictionariesView.findViewById<ImageView>(R.id.add_dictionary).setOnClickListener {
            fragment?.requestDictionary()
        }
        val (userDicts, hasInternalDict) = getUserAndInternalDictionaries(context, mainLocaleString)
        if (hasInternalDict) {
            dictionariesView.addView(TextView(context).apply {
                text = "main (internal)" // todo: resource
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
        Builder(context)
            .setTitle(R.string.replace_dictionary)
            .setMessage(context.resources.getString(R.string.replace_dictionary_message2, dictionaryType))
            .setCancelable(false)
            .setNegativeButton(R.string.cancel, ) { _,_ ->
                cachedDictionaryFile.delete()
            }
            .setPositiveButton(R.string.replace_dictionary) { _,_ ->
                moveDict(true)
            }
            .show()
    }

    private fun onDictionaryLoadingError(messageId: Int) = onDictionaryLoadingError(context.getString(messageId))

    private fun onDictionaryLoadingError(message: String) {
        // todo: maybe show toast instead?
        cachedDictionaryFile.delete()
        Builder(context)
            .setNegativeButton(android.R.string.ok, null)
            .setMessage(message)
            .show()
    }

    private fun addDictionaryToView(dictFile: File, dictionariesView: LinearLayout) {
        // todo: could load the dictionary headers to get some infos for display, or maybe show internal locale
        val dictType = dictFile.name.substringBefore("_${DictionarySettingsFragment.USER_DICTIONARY_SUFFIX}")
        val row = LayoutInflater.from(context).inflate(R.layout.language_list_item, null)
        row.findViewById<TextView>(R.id.language_name).text = dictType
        row.findViewById<View>(R.id.language_details).isGone = true
        row.findViewById<Switch>(R.id.language_switch).isGone = true
        row.findViewById<ImageView>(R.id.delete_button).apply {
            isVisible = true
            setOnClickListener {
                confirmDialog(context, "really delete user-added $dictType dictionary?", "delete") {
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
        .setTitle("confirm") // todo: resources
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
