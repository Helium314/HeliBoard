// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.get
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.size
import helium314.keyboard.compat.locale
import helium314.keyboard.dictionarypack.DictionaryPackConstants
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET
import helium314.keyboard.latin.common.LocaleUtils
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.databinding.LanguageListItemBinding
import helium314.keyboard.latin.databinding.LocaleSettingsDialogBinding
import helium314.keyboard.latin.utils.*
import helium314.keyboard.latin.utils.ScriptUtils.script
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
    private val mainLocale = infos.first().subtype.locale()
    private var hasInternalDictForLanguage = false
    private val userDicts = mutableSetOf<File>()

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
        if (infos.first().subtype.isAsciiCapable) {
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
        val newSubtype = AdditionalSubtypeUtils.createEmojiCapableAdditionalSubtype(mainLocale, name, infos.first().subtype.isAsciiCapable)
        val newSubtypeInfo = newSubtype.toSubtypeInfo(mainLocale, context, true, infos.first().hasDictionary) // enabled by default
        val displayName = SubtypeLocaleUtils.getKeyboardLayoutSetDisplayName(newSubtype)
        val old = infos.firstOrNull { isAdditionalSubtype(it.subtype) && displayName == SubtypeLocaleUtils.getKeyboardLayoutSetDisplayName(it.subtype) }
        if (old != null) {
            KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(context)
            reloadSetting()
            return
        }

        addAdditionalSubtype(prefs, context.resources, newSubtype)
        addEnabledSubtype(prefs, newSubtype)
        addSubtypeToView(newSubtypeInfo)
        KeyboardLayoutSet.onKeyboardThemeChanged()
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
            .setNeutralButton(R.string.button_copy_existing_layout) { _, _ -> copyLayout() }
            .setPositiveButton(R.string.button_load_custom) { _, _ -> fragment?.requestLayoutFile() }
            .create()
        dialog.show()
        (dialog.findViewById<View>(android.R.id.message) as? TextView)?.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun copyLayout() {
        val layouts = mutableListOf<String>()
        val displayNames = mutableListOf<String>()
        if (infos.first().subtype.isAsciiCapable) {
            layouts.addAll(context.resources.getStringArray(R.array.predefined_layouts))
            layouts.forEach { displayNames.add(SubtypeLocaleUtils.getKeyboardLayoutSetDisplayName(it) ?: it) }
        }
        infos.forEach {
            val layoutSetName = it.subtype.getExtraValueOf(KEYBOARD_LAYOUT_SET)
            if (layoutSetName?.startsWith(CUSTOM_LAYOUT_PREFIX) == false) { // don't allow copying custom layout (at least for now)
                layouts.add(layoutSetName)
                displayNames.add(SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(it.subtype))
            }
        }
        Builder(context)
            .setTitle(R.string.keyboard_layout_set)
            .setItems(displayNames.toTypedArray()) { di, i ->
                di.dismiss()
                val fileName = context.assets.list("layouts")!!.firstOrNull { it.startsWith(layouts[i]) } ?: return@setItems
                loadCustomLayout(context.assets.open("layouts${File.separator}$fileName").reader().readText(),
                    displayNames[i], mainLocale.toLanguageTag(), context) { addSubtype(it) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onNewLayoutFile(uri: Uri?) {
        loadCustomLayout(uri, mainLocale.toLanguageTag(), context) { addSubtype(it) }
    }

    private fun addSubtypeToView(subtype: SubtypeInfo) {
        val row = LayoutInflater.from(context).inflate(R.layout.language_list_item, listView)
        val layoutSetName: String? = subtype.subtype.getExtraValueOf(KEYBOARD_LAYOUT_SET)
        row.findViewById<TextView>(R.id.language_name).text =
            SubtypeLocaleUtils.getKeyboardLayoutSetDisplayName(subtype.subtype)
                ?: SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(subtype.subtype)
        if (layoutSetName?.startsWith(CUSTOM_LAYOUT_PREFIX) == true) {
            row.findViewById<TextView>(R.id.language_details).setText(R.string.edit_layout)
            row.findViewById<View>(R.id.language_text).setOnClickListener { editCustomLayout(layoutSetName, context) }
        } else {
            row.findViewById<View>(R.id.language_details).isGone = true
        }
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
                reloadSetting()
            }
        }
        if (isAdditionalSubtype(subtype.subtype)) {
            row.findViewById<Switch>(R.id.language_switch).isEnabled = true
            row.findViewById<ImageView>(R.id.delete_button).apply {
                isVisible = true
                setOnClickListener {
                    val isCustom = layoutSetName?.startsWith(CUSTOM_LAYOUT_PREFIX) == true
                    fun delete() {
                        binding.subtypes.removeView(row)
                        infos.remove(subtype)
                        if (isCustom)
                            removeCustomLayoutFile(layoutSetName!!, context)
                        removeAdditionalSubtype(prefs, context.resources, subtype.subtype)
                        removeEnabledSubtype(prefs, subtype.subtype)
                        reloadSetting()
                    }
                    if (isCustom) {
                        confirmDialog(context, context.getString(R.string.delete_layout, getLayoutDisplayName(layoutSetName!!)), context.getString(R.string.delete)) { delete() }
                    } else {
                        delete()
                    }
                }
            }
        }
        binding.subtypes.addView(row)
    }

    private fun fillSecondaryLocaleView() {
        // can only use multilingual typing if there is more than one dictionary available
        val availableSecondaryLocales = getAvailableSecondaryLocales(
            context,
            mainLocale,
            infos.first().subtype.isAsciiCapable
        )
        val selectedSecondaryLocales = Settings.getSecondaryLocales(prefs, mainLocale)
        selectedSecondaryLocales.forEach {
            addSecondaryLocaleView(it)
        }
        if (availableSecondaryLocales.isNotEmpty()) {
            binding.addSecondaryLanguage.apply {
                isVisible = true
                setOnClickListener {
                    val locales = (availableSecondaryLocales - Settings.getSecondaryLocales(prefs, mainLocale)).sortedBy { it.displayName }
                    val localeNames = locales.map { LocaleUtils.getLocaleDisplayNameInSystemLocale(it, context) }.toTypedArray()
                    Builder(context)
                        .setTitle(R.string.button_select_language)
                        .setItems(localeNames) { di, i ->
                            val locale = locales[i]
                            val currentSecondaryLocales = Settings.getSecondaryLocales(prefs, mainLocale)
                            Settings.setSecondaryLocales(prefs, mainLocale, currentSecondaryLocales + locale)
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
                val currentSecondaryLocales = Settings.getSecondaryLocales(prefs, mainLocale)
                Settings.setSecondaryLocales(prefs, mainLocale, currentSecondaryLocales - locale)
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
        val userDictsAndHasInternal = getUserAndInternalDictionaries(context, mainLocale)
        hasInternalDictForLanguage = userDictsAndHasInternal.second
        userDicts.addAll(userDictsAndHasInternal.first)
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
            val locale = context.resources.configuration.locale()
            Builder(context)
                .setMessage(header.info(locale))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        rowBinding.languageSwitch.isGone = true
        rowBinding.deleteButton.apply {
            isVisible = true
            setOnClickListener {
                confirmDialog(context, context.getString(R.string.remove_dictionary_message, dictType), context.getString(
                    R.string.delete)) {
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
            val popupKeyTypesDefault = prefs.getString(Settings.PREF_POPUP_KEYS_ORDER, POPUP_KEYS_ORDER_DEFAULT)!!
            reorderPopupKeysDialog(context, Settings.PREF_POPUP_KEYS_ORDER + "_" + mainLocale.toLanguageTag(), popupKeyTypesDefault, R.string.popup_order)
            KeyboardLayoutSet.onKeyboardThemeChanged()
        }
        binding.popupLabelPriority.setOnClickListener {
            val popupKeyTypesDefault = prefs.getString(Settings.PREF_POPUP_KEYS_LABELS_ORDER, POPUP_KEYS_LABEL_DEFAULT)!!
            reorderPopupKeysDialog(context, Settings.PREF_POPUP_KEYS_LABELS_ORDER + "_" + mainLocale.toLanguageTag(), popupKeyTypesDefault, R.string.hint_source)
            KeyboardLayoutSet.onKeyboardThemeChanged()
        }
    }

    private fun reloadDictionaries() = fragment?.activity?.sendBroadcast(Intent(DictionaryPackConstants.NEW_DICTIONARY_INTENT_ACTION))
}

/** @return list of user dictionary files and whether an internal dictionary exists */
fun getUserAndInternalDictionaries(context: Context, locale: Locale): Pair<List<File>, Boolean> {
    val userDicts = mutableListOf<File>()
    var hasInternalDict = false
    val userLocaleDir = File(DictionaryInfoUtils.getCacheDirectoryForLocale(locale, context))
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
    val internalDicts = DictionaryInfoUtils.getAssetsDictionaryList(context) ?: return userDicts to false
    val best = LocaleUtils.getBestMatch(locale, internalDicts.toList()) {
        DictionaryInfoUtils.extractLocaleFromAssetsDictionaryFile(it)?.constructLocale() ?: SubtypeLocaleUtils.NO_LANGUAGE.constructLocale()
    }
    return userDicts to (best != null)
}

// get locales with same script as main locale, but different language
private fun getAvailableSecondaryLocales(context: Context, mainLocale: Locale, asciiCapable: Boolean): Set<Locale> {
    val locales = getDictionaryLocales(context)
    val mainScript = if (asciiCapable) ScriptUtils.SCRIPT_LATIN
        else mainLocale.script()
    // script() extension function may return latin in case script cannot be determined
    // workaround: don't allow secondary locales for these locales
    if (!asciiCapable && mainScript == ScriptUtils.SCRIPT_LATIN) return emptySet()

    locales.removeAll {
        it.language == mainLocale.language || it.script() != mainScript
    }
    return locales
}

private const val LAYOUT_FORMAT_URL = "https://github.com/Helium314/openboard/blob/new/layouts.md"
