// SPDX-License-Identifier: GPL-3.0-only
package org.dslul.openboard.inputmethod.latin.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.get
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.size
import org.dslul.openboard.inputmethod.dictionarypack.DictionaryPackConstants
import org.dslul.openboard.inputmethod.keyboard.KeyboardLayoutSet
import org.dslul.openboard.inputmethod.latin.BinaryDictionaryGetter
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.common.Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET
import org.dslul.openboard.inputmethod.latin.common.LocaleUtils
import org.dslul.openboard.inputmethod.latin.databinding.LanguageListItemBinding
import org.dslul.openboard.inputmethod.latin.databinding.LocaleSettingsDialogBinding
import org.dslul.openboard.inputmethod.latin.utils.*
import java.io.File
import java.util.*

class LanguageSettingsDialog(
    context: Context,
    private val infos: MutableList<SubtypeInfo>,
    private val fragment: LanguageSettingsFragment?,
    private val onlySystemLocales: Boolean,
    private val reloadSetting: () -> Unit
) : AlertDialog(context), LanguageSettingsFragment.Listener {
    private val prefs = DeviceProtectedUtils.getSharedPreferences(context)!!
    private val binding = LocaleSettingsDialogBinding.inflate(LayoutInflater.from(context))
    private val mainLocaleString = infos.first().subtype.locale()
    private val mainLocale = mainLocaleString.toLocale()
    private var hasInternalDictForLanguage = false
    private lateinit var userDicts: MutableSet<File>

    init {
        setTitle(infos.first().displayName)
        setView(ScrollView(context).apply { addView(binding.root) })
        setButton(BUTTON_NEGATIVE, context.getString(R.string.dialog_close)) { _, _ ->
            dismiss()
        }

        if (onlySystemLocales)
            binding.subtypes.isGone = true
        else
            fillSubtypesView()
        fillSecondaryLocaleView()
        fillDictionariesView()
        setupPopupSettings()
    }

    override fun onStart() {
        super.onStart()
        fragment?.setListener(this)
    }

    override fun onStop() {
        super.onStop()
        fragment?.setListener(null)
    }

    private fun fillSubtypesView() {
        if (infos.any { it.subtype.isAsciiCapable }) {
            binding.addSubtype.setOnClickListener {
                val layouts = context.resources.getStringArray(R.array.predefined_layouts)
                    .filterNot { layoutName -> infos.any { SubtypeLocaleUtils.getKeyboardLayoutSetName(it.subtype) == layoutName } }
                val displayNames = layouts.map { SubtypeLocaleUtils.getKeyboardLayoutSetDisplayName(it) }
                Builder(context)
                    .setTitle(R.string.keyboard_layout_set)
                    .setItems(displayNames.toTypedArray()) { di, i ->
                        di.dismiss()
                        addSubtype(layouts[i])
                    }
                    .setNeutralButton(R.string.button_title_add_custom_layout) { _, _ -> onClickAddCustomSubtype() }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        } else
            binding.addSubtype.setOnClickListener { onClickAddCustomSubtype() }

        // add subtypes
        infos.sortedBy { it.displayName }.forEach {
            addSubtypeToView(it)
        }
    }

    private fun addSubtype(name: String) {
        val newSubtype = AdditionalSubtypeUtils.createAsciiEmojiCapableAdditionalSubtype(mainLocaleString, name)
        val newSubtypeInfo = newSubtype.toSubtypeInfo(mainLocale, context, true, infos.first().hasDictionary) // enabled by default, because why else add them
        val old = infos.firstOrNull { isAdditionalSubtype(it.subtype) && it.displayName == newSubtypeInfo.displayName }
        if (old != null) {
            KeyboardLayoutSet.onKeyboardThemeChanged()
            reloadSetting()
            return
        }
        addAdditionalSubtype(prefs, context.resources, newSubtype)
        addEnabledSubtype(prefs, newSubtype)
        addSubtypeToView(newSubtypeInfo)
        infos.add(newSubtypeInfo)
        reloadSetting()
    }

    private fun onClickAddCustomSubtype() {
        val link = "<a href='$LAYOUT_FORMAT_URL'>" + context.getString(R.string.dictionary_link_text) + "</a>"
        val message = SpannableStringUtils.fromHtml(context.getString(R.string.message_add_custom_layout, link))
        val dialog = Builder(context)
            .setTitle(R.string.button_title_add_custom_layout)
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.button_load_custom_layout) { _, _ -> fragment?.requestLayoutFile() }
            .create()
        dialog.show()
        (dialog.findViewById<View>(android.R.id.message) as? TextView)?.movementMethod = LinkMovementMethod.getInstance()
    }

    override fun onNewLayoutFile(uri: Uri?) {
        loadCustomLayout(uri, mainLocaleString, context) { addSubtype(it) }
    }

    private fun addSubtypeToView(subtype: SubtypeInfo) {
        val row = LayoutInflater.from(context).inflate(R.layout.language_list_item, listView)
        row.findViewById<TextView>(R.id.language_name).text =
            SubtypeLocaleUtils.getKeyboardLayoutSetDisplayName(subtype.subtype)
                ?: SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(subtype.subtype)
        row.findViewById<View>(R.id.language_details).isGone = true
        row.findViewById<SwitchCompat>(R.id.language_switch).apply {
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
                reloadSetting()
            }
        }
        if (isAdditionalSubtype(subtype.subtype)) {
            row.findViewById<SwitchCompat>(R.id.language_switch).isEnabled = true
            row.findViewById<ImageView>(R.id.delete_button).apply {
                isVisible = true
                setOnClickListener {
                    // can be re-added easily, no need for confirmation dialog
                    binding.subtypes.removeView(row)
                    infos.remove(subtype)
                    val layoutSetName: String? = subtype.subtype.getExtraValueOf(KEYBOARD_LAYOUT_SET)
                    if (layoutSetName?.startsWith(CUSTOM_LAYOUT_PREFIX) == true)
                        removeCustomLayoutFile(layoutSetName, context)

                    removeAdditionalSubtype(prefs, context.resources, subtype.subtype)
                    removeEnabledSubtype(prefs, subtype.subtype)
                    reloadSetting()
                }
            }
        }
        binding.subtypes.addView(row)
    }

    private fun fillSecondaryLocaleView() {
        // can only use multilingual typing if there is more than one dictionary available
        val availableSecondaryLocales = getAvailableSecondaryLocales(
            context,
            mainLocaleString,
            infos.first().subtype.isAsciiCapable
        )
        val selectedSecondaryLocales = Settings.getSecondaryLocales(prefs, mainLocaleString)
        selectedSecondaryLocales.forEach {
            addSecondaryLocaleView(it)
        }
        if (availableSecondaryLocales.isNotEmpty()) {
            binding.addSecondaryLanguage.apply {
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
                            addSecondaryLocaleView(locale)
                            di.dismiss()
                            reloadSetting()
                            reloadDictionaries()
                            KeyboardLayoutSet.onSystemLocaleChanged()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            }
        } else if (selectedSecondaryLocales.isEmpty())
            binding.secondaryLocales.isGone = true
    }

    private fun addSecondaryLocaleView(locale: Locale) {
        val rowBinding = LanguageListItemBinding.inflate(LayoutInflater.from(context), listView, false)
        rowBinding.languageSwitch.isGone = true
        rowBinding.languageDetails.isGone = true
        rowBinding.languageName.text = locale.displayName
        rowBinding.deleteButton.apply {
            isVisible = true
            setOnClickListener {
                val localeStrings = Settings.getSecondaryLocales(prefs, mainLocaleString).map { it.toString() }
                Settings.setSecondaryLocales(prefs, mainLocaleString, localeStrings - locale.toString())
                binding.secondaryLocales.removeView(rowBinding.root)
                reloadSetting()
                reloadDictionaries()
                KeyboardLayoutSet.onSystemLocaleChanged()
            }
        }
        binding.secondaryLocales.addView(rowBinding.root)
    }

    private fun fillDictionariesView() {
        binding.addDictionary.setOnClickListener {
            val link = "<a href='$DICTIONARY_URL'>" + context.getString(R.string.dictionary_link_text) + "</a>"
            val message = SpannableStringUtils.fromHtml(context.getString(R.string.add_dictionary, link))
            val dialog = Builder(context)
                .setTitle(R.string.add_new_dictionary_title)
                .setMessage(message)
                .setPositiveButton(R.string.user_dict_settings_add_menu_title) { _, _ -> fragment?.requestDictionary() }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
            dialog.show()
            (dialog.findViewById<View>(android.R.id.message) as? TextView)?.movementMethod = LinkMovementMethod.getInstance()
        }
        val (_userDicts, _hasInternalDictForLanguage) = getUserAndInternalDictionaries(context, mainLocaleString)
        userDicts = _userDicts.toMutableSet()
        hasInternalDictForLanguage = _hasInternalDictForLanguage
        if (hasInternalDictForLanguage) {
            binding.dictionaries.addView(TextView(context, null, R.style.PreferenceCategoryTitleText).apply {
                setText(R.string.internal_dictionary_summary)
                // just setting a text size can be complicated...
                val attrs = context.obtainStyledAttributes(R.style.PreferenceSubtitleText, intArrayOf(android.R.attr.textSize))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, attrs.getDimension(0, 20f))
                attrs.recycle()
                setPadding((context.resources.displayMetrics.scaledDensity * 16).toInt(), 0, 0, 0)
                isEnabled = userDicts.none { it.name == "${DictionaryInfoUtils.MAIN_DICT_PREFIX}${USER_DICTIONARY_SUFFIX}" }
            })
        }
        userDicts.sorted().forEach {
            addDictionaryToView(it)
        }
    }

    override fun onNewDictionary(uri: Uri?) {
        NewDictionaryAdder(context) { replaced, dictFile ->
            if (!replaced) {
                addDictionaryToView(dictFile)
                userDicts.add(dictFile)
                if (hasInternalDictForLanguage) {
                    binding.dictionaries[1].isEnabled =
                        userDicts.none { it.name == "${DictionaryInfoUtils.MAIN_DICT_PREFIX}${USER_DICTIONARY_SUFFIX}" }
                }
            }
        }.addDictionary(uri, mainLocale)
    }

    private fun addDictionaryToView(dictFile: File) {
        if (!infos.first().hasDictionary) {
            infos.forEach { it.hasDictionary = true }
        }
        val dictType = dictFile.name.substringBefore("_${USER_DICTIONARY_SUFFIX}")
        val rowBinding = LanguageListItemBinding.inflate(LayoutInflater.from(context), listView, false)
        val header = DictionaryInfoUtils.getDictionaryFileHeaderOrNull(dictFile, 0, dictFile.length())
        rowBinding.languageName.text = dictType
        rowBinding.languageDetails.apply {
            if (header?.description == null) {
                isGone = true
            } else {
                // what would potentially be interesting? locale? description? version? timestamp?
                text = header.description
            }
        }
        rowBinding.languageText.setOnClickListener {
            if (header == null) return@setOnClickListener
            Builder(context)
                .setMessage(header.info(context.resources.configuration.locale))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        rowBinding.languageSwitch.isGone = true
        rowBinding.deleteButton.apply {
            isVisible = true
            setOnClickListener {
                confirmDialog(context, context.getString(R.string.remove_dictionary_message, dictType), context.getString(
                    R.string.delete_dict)) {
                    val parent = dictFile.parentFile
                    dictFile.delete()
                    if (parent?.list()?.isEmpty() == true)
                        parent.delete()
                    reloadDictionaries()
                    binding.dictionaries.removeView(rowBinding.root)
                    if (binding.dictionaries.size < 2) { // first view is "Dictionaries"
                        infos.forEach { it.hasDictionary = false }
                    }
                    userDicts.remove(dictFile)
                    if (hasInternalDictForLanguage) {
                        binding.dictionaries[1].isEnabled =
                            userDicts.none { it.name == "${DictionaryInfoUtils.MAIN_DICT_PREFIX}${USER_DICTIONARY_SUFFIX}" }
                    }
                }
            }
        }
        binding.dictionaries.addView(rowBinding.root)
    }

    private fun setupPopupSettings() {
        binding.popupOrder.setOnClickListener {
            val moreKeyTypesDefault = prefs.getString(Settings.PREF_MORE_KEYS_ORDER, MORE_KEYS_ORDER_DEFAULT)!!
            reorderMoreKeysDialog(context, Settings.PREF_MORE_KEYS_ORDER + "_" + mainLocaleString, moreKeyTypesDefault, R.string.popup_order)
            KeyboardLayoutSet.onKeyboardThemeChanged()
        }
        binding.popupLabelPriority.setOnClickListener {
            val moreKeyTypesDefault = prefs.getString(Settings.PREF_MORE_KEYS_LABELS_ORDER, MORE_KEYS_LABEL_DEFAULT)!!
            reorderMoreKeysDialog(context, Settings.PREF_MORE_KEYS_LABELS_ORDER + "_" + mainLocaleString, moreKeyTypesDefault, R.string.hint_source)
            KeyboardLayoutSet.onKeyboardThemeChanged()
        }
    }

    private fun reloadDictionaries() = fragment?.activity?.sendBroadcast(Intent(DictionaryPackConstants.NEW_DICTIONARY_INTENT_ACTION))
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

private const val LAYOUT_FORMAT_URL = "https://github.com/Helium314/openboard/blob/new/layouts.md"
