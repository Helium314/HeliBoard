// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
package helium314.keyboard.latin

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import helium314.keyboard.keyboard.ColorSetting
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode.checkAndConvertCode
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Constants.Separators
import helium314.keyboard.latin.common.Constants.Subtype.ExtraValue
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.common.encodeBase36
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsSubtype
import helium314.keyboard.latin.settings.SettingsSubtype.Companion.toSettingsSubtype
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.DictionaryInfoUtils.USER_DICTIONARY_SUFFIX
import helium314.keyboard.latin.utils.LayoutType
import helium314.keyboard.latin.utils.LayoutType.Companion.folder
import helium314.keyboard.latin.utils.LayoutUtilsCustom
import helium314.keyboard.latin.utils.ScriptUtils.SCRIPT_LATIN
import helium314.keyboard.latin.utils.ScriptUtils.script
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.SubtypeUtilsAdditional
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.defaultPinnedToolbarPref
import helium314.keyboard.latin.utils.getResourceSubtypes
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.mainLayoutName
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.protectedPrefs
import helium314.keyboard.latin.utils.upgradeToolbarPrefs
import helium314.keyboard.latin.utils.writeCustomKeyCodes
import helium314.keyboard.settings.screens.colorPrefsAndResIds
import java.io.File
import java.util.EnumMap

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        DebugFlags.init(this)
        SubtypeSettings.init(this)
        RichInputMethodManager.init(this)

        checkVersionUpgrade(this)
        app = this
        Defaults.initDynamicDefaults(this)
    }

    companion object {
        // used so JniUtils can access application once
        private var app: App? = null
        fun getApp(): App? {
            val application = app
            app = null
            return application
        }
    }
}

// old variant for old folder structure
private fun getCustomLayoutFile(layoutName: String, context: Context): File =
    File(File(DeviceProtectedUtils.getFilesDir(context), "layouts"), layoutName)

fun checkVersionUpgrade(context: Context) {
    val prefs = context.prefs()
    val oldVersion = prefs.getInt(Settings.PREF_VERSION_CODE, 0)
    if (oldVersion == BuildConfig.VERSION_CODE)
        return
    // clear extracted dictionaries, in case updated version contains newer ones
    DictionaryInfoUtils.getCachedDirectoryList(context)?.forEach {
        if (!it.isDirectory) return@forEach
        val files = it.listFiles() ?: return@forEach
        for (file in files) {
            if (!file.name.endsWith(USER_DICTIONARY_SUFFIX))
                file.delete()
        }
    }
    if (oldVersion == 0) // new install or restoring settings from old app name
        upgradesWhenComingFromOldAppName(context)
    if (oldVersion <= 1000) { // upgrade old custom layouts name
        val oldShiftSymbolsFile = getCustomLayoutFile("custom.shift_symbols", context)
        if (oldShiftSymbolsFile.exists()) {
            oldShiftSymbolsFile.renameTo(getCustomLayoutFile("custom.symbols_shifted", context))
        }
        if (prefs.contains("enabled_input_styles")) {
            // rename subtype setting, and clean old subtypes that might remain in some cases
            val subtypesPref = prefs.getString("enabled_input_styles", "")!!
                .split(";").filter { it.isNotEmpty() }
                .map {
                    val localeAndLayout = it.split(":").toMutableList()
                    localeAndLayout[0] = localeAndLayout[0].constructLocale().toLanguageTag()
                    localeAndLayout.joinToString(":")
                }.toSet().joinToString(";")
            prefs.edit().remove("enabled_input_styles").putString(Settings.PREF_ENABLED_SUBTYPES, subtypesPref).apply()
        }
        if (prefs.contains("selected_input_style")) {
            val selectedSubtype = prefs.getString("selected_input_style", "")
            prefs.edit().remove("selected_input_style").putString(Settings.PREF_SELECTED_SUBTYPE, selectedSubtype).apply()
        }
    }
    if (oldVersion <= 2000) {
        // upgrade pinned toolbar keys pref
        val oldPinnedKeysPref = prefs.getString(Settings.PREF_PINNED_TOOLBAR_KEYS, "")!!
        val pinnedKeys = oldPinnedKeysPref.split(";").mapNotNull {
            try {
                ToolbarKey.valueOf(it)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        val newPinnedKeysPref = (pinnedKeys.map { "${it.name},true" } + defaultPinnedToolbarPref.split(";"))
            .distinctBy { it.split(",").first() }
            .joinToString(";")
        prefs.edit { putString(Settings.PREF_PINNED_TOOLBAR_KEYS, newPinnedKeysPref) }

        // enable language switch key if it was enabled previously
        if (prefs.contains(Settings.PREF_LANGUAGE_SWITCH_KEY) && prefs.getString(Settings.PREF_LANGUAGE_SWITCH_KEY, "") != "off")
            prefs.edit { putBoolean(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, true) }
    }
    if (oldVersion <= 2100) {
        if (prefs.contains("show_more_colors")) {
            val moreColors = prefs.getInt("show_more_colors", 0)
            prefs.edit {
                putInt("theme_color_show_more_colors", moreColors)
                if (prefs.getBoolean(Settings.PREF_THEME_DAY_NIGHT, false))
                    putInt("theme_dark_color_show_more_colors", moreColors)
                remove("show_more_colors")
            }
        }
    }
    if (oldVersion <= 2201) {
        val additionalSubtypeString = prefs.getString(Settings.PREF_ADDITIONAL_SUBTYPES, Defaults.PREF_ADDITIONAL_SUBTYPES)!!
        if (additionalSubtypeString.contains(".")) { // means there are custom layouts
            val subtypeStrings = additionalSubtypeString.split(";")
            val newSubtypeStrings = subtypeStrings.mapNotNull {
                if ("." !in it) // not a custom subtype, nothing to do
                    return@mapNotNull it
                val split = it.split(":").toMutableList()
                if (split.size < 2) return@mapNotNull null // should never happen
                val oldName = split[1]
                val newName = oldName.substringBeforeLast(".") + "."
                if (oldName == newName) return@mapNotNull split.joinToString(":") // should never happen
                val oldFile = getCustomLayoutFile(oldName, context)
                val newFile = getCustomLayoutFile(newName, context)
                if (!oldFile.exists()) return@mapNotNull null // should never happen
                if (newFile.exists()) newFile.delete() // should never happen
                oldFile.renameTo(newFile)
                val enabledSubtypes = prefs.getString(Settings.PREF_ENABLED_SUBTYPES, "")!!
                if (enabledSubtypes.contains(oldName))
                    prefs.edit { putString(Settings.PREF_ENABLED_SUBTYPES, enabledSubtypes.replace(oldName, newName)) }
                val selectedSubtype = prefs.getString(Settings.PREF_SELECTED_SUBTYPE, "")!!
                if (selectedSubtype.contains(oldName))
                    prefs.edit { putString(Settings.PREF_SELECTED_SUBTYPE, selectedSubtype.replace(oldName, newName)) }
                split[1] = newName
                split.joinToString(":")
            }
            prefs.edit().putString(Settings.PREF_ADDITIONAL_SUBTYPES, newSubtypeStrings.joinToString(";")).apply()
        }
        // rename other custom layouts
        LayoutUtilsCustom.onLayoutFileChanged()
        File(DeviceProtectedUtils.getFilesDir(context), "layouts").listFiles()?.forEach {
            val newFile = getCustomLayoutFile(it.name.substringBeforeLast(".") + ".", context)
            if (newFile.name == it.name) return@forEach
            if (newFile.exists()) newFile.delete() // should never happen
            it.renameTo(newFile)
        }
    }
    if (oldVersion <= 2301) {
        // upgrade and remove old color prefs
        fun readAllColorsMap(isNight: Boolean): EnumMap<ColorType, Int> {
            val prefPrefix = if (isNight) "theme_dark_color_" else "theme_color_"
            val colorsString = prefs.getString(prefPrefix + "all_colors", "") ?: ""
            val colorMap = EnumMap<ColorType, Int>(ColorType::class.java)
            colorsString.split(";").forEach {
                val ct = try {
                    ColorType.valueOf(it.substringBefore(",").uppercase())
                } catch (_: Exception) {
                    return@forEach
                }
                val i = it.substringAfter(",").toIntOrNull() ?: return@forEach
                colorMap[ct] = i
            }
            return colorMap
        }
        // day colors
        val themeNameDay = context.getString(R.string.theme_name_user)
        val colorsDay = colorPrefsAndResIds.map {
            val pref = "theme_color_" + it.first
            val color = if (prefs.contains(pref)) prefs.getInt(pref, 0) else null
            val result = ColorSetting(it.first, prefs.getBoolean(pref + "_auto", true), color)
            prefs.edit().remove(pref).remove(pref + "_auto").apply()
            result
        }
        if (colorsDay.any { it.color != null }) {
            KeyboardTheme.writeUserColors(prefs, themeNameDay, colorsDay)
        }
        if (prefs.contains("theme_color_show_more_colors")) {
            val moreColorsDay = prefs.getInt("theme_color_show_more_colors", 0)
            prefs.edit().remove("theme_color_show_more_colors").apply()
            KeyboardTheme.writeUserMoreColors(prefs, themeNameDay, moreColorsDay)
        }
        if (prefs.contains("theme_color_all_colors")) {
            val allColorsDay = readAllColorsMap(false)
            prefs.edit().remove("theme_color_all_colors").apply()
            KeyboardTheme.writeUserAllColors(prefs, themeNameDay, allColorsDay)
        }
        if (prefs.getString(Settings.PREF_THEME_COLORS, Defaults.PREF_THEME_COLORS) == "user")
            prefs.edit().putString(Settings.PREF_THEME_COLORS, themeNameDay).apply()

        // same for night colors
        val themeNameNight = context.getString(R.string.theme_name_user_night)
        val colorsNight = colorPrefsAndResIds.map {
            val pref = "theme_dark_color_" + it.first
            val color = if (prefs.contains(pref)) prefs.getInt(pref, 0) else null
            val result = ColorSetting(it.first, prefs.getBoolean(pref + "_auto", true), color)
            prefs.edit().remove(pref).remove(pref + "_auto").apply()
            result
        }
        if (colorsNight.any { it.color!= null }) {
            KeyboardTheme.writeUserColors(prefs, themeNameNight, colorsNight)
        }
        if (prefs.contains("theme_dark_color_show_more_colors")) {
            val moreColorsNight = prefs.getInt("theme_dark_color_show_more_colors", 0)
            prefs.edit().remove("theme_dark_color_show_more_colors").apply()
            KeyboardTheme.writeUserMoreColors(prefs, themeNameNight, moreColorsNight)
        }
        if (prefs.contains("theme_dark_color_all_colors")) {
            val allColorsNight = readAllColorsMap(true)
            prefs.edit().remove("theme_dark_color_all_colors").apply()
            KeyboardTheme.writeUserAllColors(prefs, themeNameNight, allColorsNight)
        }
        if (prefs.getString(Settings.PREF_THEME_COLORS_NIGHT, Defaults.PREF_THEME_COLORS_NIGHT) == "user_night")
            prefs.edit().putString(Settings.PREF_THEME_COLORS_NIGHT, themeNameNight).apply()
    }
    if (oldVersion <= 2302) {
        fun readCustomKeyCodes(setting: String) =
            prefs.getString(setting, "")!!
                .split(";").filter { it.isNotEmpty()}.associate {
                    val code = runCatching { it.substringAfter(",").toIntOrNull()?.checkAndConvertCode() }.getOrNull()
                    it.substringBefore(",") to code
                }
        val customCodes = readCustomKeyCodes("toolbar_custom_key_codes")
        val customLongpressCodes = readCustomKeyCodes("toolbar_custom_longpress_codes")
        prefs.edit().remove("toolbar_custom_longpress_codes").remove("toolbar_custom_key_codes").apply()
        val combined = EnumMap<ToolbarKey, Pair<Int?, Int?>>(ToolbarKey::class.java)
        customCodes.forEach { runCatching {
            val key = ToolbarKey.valueOf(it.key)
            combined[key] = (combined[key] ?: (null to null)).copy(first = it.value)
        } }
        customLongpressCodes.forEach { runCatching {
            val key = ToolbarKey.valueOf(it.key)
            combined[key] = (combined[key] ?: (null to null)).copy(second = it.value)
        } }
        writeCustomKeyCodes(prefs, combined)
    }
    if (oldVersion <= 2303) {
        File(DeviceProtectedUtils.getFilesDir(context), "layouts").listFiles()?.forEach { file ->
            val folder = DeviceProtectedUtils.getFilesDir(context)
            if (file.isDirectory) return@forEach
            when (file.name) {
                "custom.symbols." -> {
                    val dir = File(folder, LayoutType.SYMBOLS.folder)
                    dir.mkdirs()
                    val name = "custom.${encodeBase36("symbols")}."
                    file.renameTo(File(dir, name))
                    prefs.edit().putString(Settings.PREF_LAYOUT_PREFIX + LayoutType.SYMBOLS.name, name).apply()
                }
                "custom.symbols_shifted." -> {
                    val dir = File(folder, LayoutType.MORE_SYMBOLS.folder)
                    dir.mkdirs()
                    val name = "custom.${encodeBase36("symbols_shifted")}."
                    file.renameTo(File(dir, name))
                    prefs.edit().putString(Settings.PREF_LAYOUT_PREFIX + LayoutType.MORE_SYMBOLS.name, name).apply()
                }
                "custom.symbols_arabic." -> {
                    val dir = File(folder, LayoutType.SYMBOLS.folder)
                    dir.mkdirs()
                    val name = "custom.${encodeBase36("symbols_arabic")}."
                    file.renameTo(File(dir, name))
                }
                "custom.numpad." -> {
                    val dir = File(folder, LayoutType.NUMPAD.folder)
                    dir.mkdirs()
                    val name = "custom.${encodeBase36("numpad")}."
                    file.renameTo(File(dir, name))
                    prefs.edit().putString(Settings.PREF_LAYOUT_PREFIX + LayoutType.NUMPAD.name, name).apply()
                }
                "custom.numpad_landscape." -> {
                    val dir = File(folder, LayoutType.NUMPAD_LANDSCAPE.folder)
                    dir.mkdirs()
                    val name = "custom.${encodeBase36("numpad_landscape")}."
                    file.renameTo(File(dir, name))
                    prefs.edit().putString(Settings.PREF_LAYOUT_PREFIX + LayoutType.NUMPAD_LANDSCAPE.name, name).apply()
                }
                "custom.number." -> {
                    val dir = File(folder, LayoutType.NUMBER.folder)
                    dir.mkdirs()
                    val name = "custom.${encodeBase36("number")}."
                    file.renameTo(File(dir, name))
                    prefs.edit().putString(Settings.PREF_LAYOUT_PREFIX + LayoutType.NUMBER.name, name).apply()
                }
                "custom.phone." -> {
                    val dir = File(folder, LayoutType.PHONE.folder)
                    dir.mkdirs()
                    val name = "custom.${encodeBase36("phone")}."
                    file.renameTo(File(dir, name))
                    prefs.edit().putString(Settings.PREF_LAYOUT_PREFIX + LayoutType.PHONE.name, name).apply()
                }
                "custom.phone_symbols." -> {
                    val dir = File(folder, LayoutType.PHONE_SYMBOLS.folder)
                    dir.mkdirs()
                    val name = "custom.${encodeBase36("phone_symbols")}."
                    file.renameTo(File(dir, name))
                    prefs.edit().putString(Settings.PREF_LAYOUT_PREFIX + LayoutType.PHONE_SYMBOLS.name, name).apply()
                }
                "custom.number_row." -> {
                    val dir = File(folder, LayoutType.NUMBER_ROW.folder)
                    dir.mkdirs()
                    val name = "custom.${encodeBase36("number_row")}."
                    file.renameTo(File(dir, name))
                    prefs.edit().putString(Settings.PREF_LAYOUT_PREFIX + LayoutType.NUMBER_ROW.name, name).apply()
                }
                "custom.emoji_bottom_row." -> {
                    val dir = File(folder, LayoutType.EMOJI_BOTTOM.folder)
                    dir.mkdirs()
                    val name = "custom.${encodeBase36("emoji_bottom_row")}."
                    file.renameTo(File(dir, name))
                    prefs.edit().putString(Settings.PREF_LAYOUT_PREFIX + LayoutType.EMOJI_BOTTOM.name, name).apply()
                }
                "custom.clip_bottom_row." -> {
                    val dir = File(folder, LayoutType.CLIPBOARD_BOTTOM.folder)
                    dir.mkdirs()
                    val name = "custom.${encodeBase36("clip_bottom_row")}."
                    file.renameTo(File(dir, name))
                    prefs.edit().putString(Settings.PREF_LAYOUT_PREFIX + LayoutType.CLIPBOARD_BOTTOM.name, name).apply()
                }
                "custom.functional_keys." -> {
                    val dir = File(folder, LayoutType.FUNCTIONAL.folder)
                    dir.mkdirs()
                    val name = "custom.${encodeBase36("functional_keys")}."
                    file.renameTo(File(dir, name))
                    prefs.edit().putString(Settings.PREF_LAYOUT_PREFIX + LayoutType.FUNCTIONAL.name, name).apply()
                }
                "custom.functional_keys_symbols." -> {
                    val dir = File(folder, LayoutType.FUNCTIONAL.folder)
                    dir.mkdirs()
                    val name = "custom.${encodeBase36("functional_keys_symbols")}."
                    file.renameTo(File(dir, name))
                }
                "custom.functional_keys_symbols_shifted." -> {
                    val dir = File(folder, LayoutType.FUNCTIONAL.folder)
                    dir.mkdirs()
                    val name = "custom.${encodeBase36("functional_keys_symbols_shifted")}."
                    file.renameTo(File(dir, name))
                }
                else -> {
                    // main layouts
                    val dir = File(folder, LayoutType.MAIN.folder)
                    dir.mkdirs()
                    file.renameTo(File(dir, file.name))
                }
            }
        }
        if (prefs.contains(Settings.PREF_ADDITIONAL_SUBTYPES))
            prefs.edit().putString(Settings.PREF_ADDITIONAL_SUBTYPES, prefs.getString(Settings.PREF_ADDITIONAL_SUBTYPES, "")!!
                .replace(":", Separators.SET)).apply()
    }
    if (oldVersion <= 2304) {
        // rename layout files for latin scripts, and adjust layouts stored in prefs accordingly
        LayoutUtilsCustom.getLayoutFiles(LayoutType.MAIN, context).forEach {
            val locale = it.name.substringAfter("custom.").substringBefore(".").constructLocale()
            if (locale.script() != SCRIPT_LATIN) return@forEach
            // change language tag to SCRIPT_LATIN, but
            //  avoid overwriting if 2 layouts have a different language tag, but the same name
            val layoutDisplayName = LayoutUtilsCustom.getDisplayName(it.name)
            var newFile = File(it.parentFile!!, LayoutUtilsCustom.getLayoutName(layoutDisplayName, LayoutType.MAIN, locale))
            var i = 1
            while (newFile.exists()) // make sure name is not already in use, e.g. custom.en.abcd. and custom.it.abcd. would both be custom.Latn.abcd
                newFile = File(it.parentFile!!, LayoutUtilsCustom.getLayoutName(layoutDisplayName + i++, LayoutType.MAIN, locale))
            it.renameTo(newFile)
            // modify prefs
            listOf(Settings.PREF_ENABLED_SUBTYPES, Settings.PREF_SELECTED_SUBTYPE, Settings.PREF_ADDITIONAL_SUBTYPES).forEach { key ->
                val value = prefs.getString(key, "")!!
                if (it.name in value)
                    prefs.edit().putString(key, value.replace(it.name, newFile.name)).apply()
            }
            LayoutUtilsCustom.onLayoutFileChanged()
        }
    }
    if (oldVersion <= 2305) {
        (prefs.all.keys.filter { it.startsWith(Settings.PREF_POPUP_KEYS_ORDER) || it.startsWith(Settings.PREF_POPUP_KEYS_LABELS_ORDER) } +
                listOf(Settings.PREF_TOOLBAR_KEYS, Settings.PREF_PINNED_TOOLBAR_KEYS, Settings.PREF_CLIPBOARD_TOOLBAR_KEYS)).forEach {
            if (!prefs.contains(it)) return@forEach
            val newValue = prefs.getString(it, "")!!.replace(",", Separators.KV).replace(";", Separators.ENTRY)
            prefs.edit().putString(it, newValue).apply()
        }
        listOf(Settings.PREF_ENABLED_SUBTYPES, Settings.PREF_SELECTED_SUBTYPE, Settings.PREF_ADDITIONAL_SUBTYPES).forEach {
            if (!prefs.contains(it)) return@forEach
            val value = prefs.getString(it, "")!!.replace(":", Separators.SET)
            prefs.edit().putString(it, value).apply()
        }
        prefs.all.keys.filter { it.startsWith("secondary_locales_") }.forEach {
            val newValue = prefs.getString(it, "")!!.replace(";", Separators.KV)
            prefs.edit().putString(it, newValue).apply()
        }
    }
    if (oldVersion <= 2306) {
        // upgrade additional, enabled, and selected subtypes to same format of locale and (filtered) extra value
        if (prefs.contains(Settings.PREF_ADDITIONAL_SUBTYPES)) {
            val new = prefs.getString(Settings.PREF_ADDITIONAL_SUBTYPES, "")!!.split(Separators.SETS).filter { it.isNotEmpty() }.mapNotNull { pref ->
                val oldSplit = pref.split(Separators.SET)
                val languageTag = oldSplit[0]
                val mainLayoutName = oldSplit[1]
                val extraValue = oldSplit[2]
                SettingsSubtype(
                    languageTag.constructLocale(),
                    ExtraValue.KEYBOARD_LAYOUT_SET + "=MAIN" + Separators.KV + mainLayoutName + "," + extraValue
                ).toAdditionalSubtype()?.let { it.toSettingsSubtype().toPref() }
            }.joinToString(Separators.SETS)
            prefs.edit().putString(Settings.PREF_ADDITIONAL_SUBTYPES, new).apply()
        }
        listOf(Settings.PREF_ENABLED_SUBTYPES, Settings.PREF_SELECTED_SUBTYPE).forEach { key ->
            if (!prefs.contains(key)) return@forEach
            val resourceSubtypes = getResourceSubtypes(context.resources)
            val additionalSubtypeString = prefs.getString(Settings.PREF_ADDITIONAL_SUBTYPES, Defaults.PREF_ADDITIONAL_SUBTYPES)!!
            val additionalSubtypes = SubtypeUtilsAdditional.createAdditionalSubtypes(additionalSubtypeString)
            val new = prefs.getString(key, "")!!.split(Separators.SETS).filter { it.isNotEmpty() }.joinToString(Separators.SETS) { pref ->
                val oldSplit = pref.split(Separators.SET)
                val languageTag = oldSplit[0]
                val mainLayoutName = oldSplit[1]
                // we now need more information than just locale and main layout name, get it from existing subtypes
                val filtered = additionalSubtypes.filter {
                    it.locale().toLanguageTag() == languageTag && (it.mainLayoutName() ?: "qwerty") == mainLayoutName
                }
                if (filtered.isNotEmpty())
                    return@joinToString filtered.first().toSettingsSubtype().toPref()
                // find best matching resource subtype
                val goodMatch = resourceSubtypes.filter {
                    it.locale().toLanguageTag() == languageTag && (it.mainLayoutName() ?: "qwerty") == mainLayoutName
                }
                if (goodMatch.isNotEmpty())
                    return@joinToString goodMatch.first().toSettingsSubtype().toPref()
                // not sure how we can get here, but better deal with it
                val okMatch = resourceSubtypes.filter {
                    it.locale().language == languageTag.constructLocale().language && (it.mainLayoutName() ?: "qwerty") == mainLayoutName
                }
                if (okMatch.isNotEmpty())
                    okMatch.first().toSettingsSubtype().toPref()
                else resourceSubtypes.first { it.locale().language == languageTag.constructLocale().language }
                    .toSettingsSubtype().toPref()
            }
            prefs.edit().putString(key, new).apply()
        }
    }
    if (oldVersion <= 2307) {
        prefs.all.keys.forEach {
            if (!it.startsWith(Settings.PREF_POPUP_KEYS_ORDER) && !it.startsWith(Settings.PREF_POPUP_KEYS_LABELS_ORDER))
                return@forEach
            prefs.edit().putString(it, prefs.getString(it, "")!!.replace("popup_keys_", "")).apply()
        }
    }
    if (oldVersion <= 2308) {
        SubtypeSettings.reloadEnabledSubtypes(context)
        prefs.all.keys.toList().forEach { key ->
            if (key.startsWith(Settings.PREF_POPUP_KEYS_ORDER+"_")) {
                val locale = key.substringAfter(Settings.PREF_POPUP_KEYS_ORDER+"_").constructLocale()
                SubtypeSettings.getEnabledSubtypes().forEach {
                    if (it.locale() == locale && !SubtypeSettings.isAdditionalSubtype(it)) {
                        SubtypeUtilsAdditional.changeAdditionalSubtype(it.toSettingsSubtype(), it.toSettingsSubtype(), context)
                    }
                }
                val additional = prefs.getString(Settings.PREF_ADDITIONAL_SUBTYPES, "")!!
                additional.split(Separators.SETS).filter { it.isNotEmpty() }.forEach inner@{
                    val subtype = it.toSettingsSubtype()
                    if (subtype.locale != locale) return@inner
                    val newSubtype = subtype.with(ExtraValue.POPUP_ORDER, prefs.getString(key, ""))
                    SubtypeUtilsAdditional.changeAdditionalSubtype(subtype, newSubtype, context)
                }
                prefs.edit().remove(key).apply()
            }
            if (key.startsWith(Settings.PREF_POPUP_KEYS_LABELS_ORDER+"_")) {
                val locale = key.substringAfter(Settings.PREF_POPUP_KEYS_LABELS_ORDER+"_").constructLocale()
                SubtypeSettings.getEnabledSubtypes().forEach {
                    if (it.locale() == locale && !SubtypeSettings.isAdditionalSubtype(it)) {
                        SubtypeUtilsAdditional.changeAdditionalSubtype(it.toSettingsSubtype(), it.toSettingsSubtype(), context)
                    }
                }
                val additional = prefs.getString(Settings.PREF_ADDITIONAL_SUBTYPES, "")!!
                additional.split(Separators.SETS).filter { it.isNotEmpty() }.forEach inner@{
                    val subtype = it.toSettingsSubtype()
                    if (subtype.locale != locale) return@inner
                    val newSubtype = subtype.with(ExtraValue.HINT_ORDER, prefs.getString(key, ""))
                    SubtypeUtilsAdditional.changeAdditionalSubtype(subtype, newSubtype, context)
                }
                prefs.edit().remove(key).apply()
            }
            if (key.startsWith("secondary_locales_")) {
                val locale = key.substringAfter("secondary_locales_").constructLocale()
                SubtypeSettings.getEnabledSubtypes().forEach {
                    if (it.locale() == locale && !SubtypeSettings.isAdditionalSubtype(it)) {
                        SubtypeUtilsAdditional.changeAdditionalSubtype(it.toSettingsSubtype(), it.toSettingsSubtype(), context)
                    }
                }
                val additional = prefs.getString(Settings.PREF_ADDITIONAL_SUBTYPES, "")!!
                val secondaryLocales = prefs.getString(key, "")!!.split(Separators.KV).filter { it.isNotBlank() }.joinToString(Separators.KV)
                additional.split(Separators.SETS).filter { it.isNotEmpty() }.forEach inner@{
                    val subtype = it.toSettingsSubtype()
                    if (subtype.locale != locale) return@inner
                    val newSubtype = subtype.with(ExtraValue.SECONDARY_LOCALES, secondaryLocales)
                    SubtypeUtilsAdditional.changeAdditionalSubtype(subtype, newSubtype, context)
                }
                prefs.edit().remove(key).apply()
            }
        }
    }
    if (oldVersion <= 2309) {
        if (prefs.contains("auto_correction_confidence")) {
            val value = when (prefs.getString("auto_correction_confidence", "0")) {
                "1" -> 0.067f
                "2" -> -1f
                else -> 0.185f
            }
            prefs.edit().remove("auto_correction_confidence").putFloat(Settings.PREF_AUTO_CORRECT_THRESHOLD, value).apply()
        }
    }
   if (oldVersion <= 2310) {
    listOf(
        Settings.PREF_ENABLED_SUBTYPES,
        Settings.PREF_SELECTED_SUBTYPE,
        Settings.PREF_ADDITIONAL_SUBTYPES
    ).forEach { key ->
        val value = prefs.getString(key, "")!!
        if ("bengali," in value) {
            prefs.edit().putString(key, value.replace("bengali,", "bengali_inscript,")).apply()
        }
    }
}
    upgradeToolbarPrefs(prefs)
    LayoutUtilsCustom.onLayoutFileChanged() // just to be sure
    prefs.edit { putInt(Settings.PREF_VERSION_CODE, BuildConfig.VERSION_CODE) }
}

// todo (later): remove it when most users probably have upgraded
private fun upgradesWhenComingFromOldAppName(context: Context) {
    // move layout files
    try {
        File(context.filesDir, "layouts").listFiles()?.forEach {
            it.copyTo(getCustomLayoutFile(it.name, context), true)
            it.delete()
        }
    } catch (_: Exception) {}
    // move background images
    try {
        val bgDay = File(context.filesDir, "custom_background_image")
        if (bgDay.isFile) {
            bgDay.copyTo(Settings.getCustomBackgroundFile(context, false, false), true)
            bgDay.delete()
        }
        val bgNight = File(context.filesDir, "custom_background_image_night")
        if (bgNight.isFile) {
            bgNight.copyTo(Settings.getCustomBackgroundFile(context, true, false), true)
            bgNight.delete()
        }
    } catch (_: Exception) {}
    // upgrade prefs
    val prefs = context.prefs()
    if (prefs.all.containsKey("theme_variant")) {
        prefs.edit().putString(Settings.PREF_THEME_COLORS, prefs.getString("theme_variant", "")).apply()
        prefs.edit().remove("theme_variant").apply()
    }
    if (prefs.all.containsKey("theme_variant_night")) {
        prefs.edit().putString(Settings.PREF_THEME_COLORS_NIGHT, prefs.getString("theme_variant_night", "")).apply()
        prefs.edit().remove("theme_variant_night").apply()
    }
    prefs.all.toMap().forEach {
        if (it.key.startsWith("pref_key_") && it.key != "pref_key_longpress_timeout") {
            var remove = true
            when (val value = it.value) {
                is Boolean -> prefs.edit().putBoolean(it.key.substringAfter("pref_key_"), value).apply()
                is Int -> prefs.edit().putInt(it.key.substringAfter("pref_key_"), value).apply()
                is Long -> prefs.edit().putLong(it.key.substringAfter("pref_key_"), value).apply()
                is String -> prefs.edit().putString(it.key.substringAfter("pref_key_"), value).apply()
                is Float -> prefs.edit().putFloat(it.key.substringAfter("pref_key_"), value).apply()
                else -> remove = false
            }
            if (remove)
                prefs.edit().remove(it.key).apply()
        } else if (it.key.startsWith("pref_")) {
            var remove = true
            when (val value = it.value) {
                is Boolean -> prefs.edit().putBoolean(it.key.substringAfter("pref_"), value).apply()
                is Int -> prefs.edit().putInt(it.key.substringAfter("pref_"), value).apply()
                is Long -> prefs.edit().putLong(it.key.substringAfter("pref_"), value).apply()
                is String -> prefs.edit().putString(it.key.substringAfter("pref_"), value).apply()
                is Float -> prefs.edit().putFloat(it.key.substringAfter("pref_"), value).apply()
                else -> remove = false
            }
            if (remove)
                prefs.edit().remove(it.key).apply()
        }
    }
    // change more_keys to popup_keys
    if (prefs.contains("more_keys_order")) {
        prefs.edit().putString(Settings.PREF_POPUP_KEYS_ORDER, prefs.getString("more_keys_order", "")?.replace("more_", "popup_")).apply()
        prefs.edit().remove("more_keys_order").apply()
    }
    if (prefs.contains("more_keys_labels_order")) {
        prefs.edit().putString(Settings.PREF_POPUP_KEYS_LABELS_ORDER, prefs.getString("more_keys_labels_order", "")?.replace("more_", "popup_")).apply()
        prefs.edit().remove("more_keys_labels_order").apply()
    }
    if (prefs.contains("more_more_keys")) {
        prefs.edit().putString(Settings.PREF_MORE_POPUP_KEYS, prefs.getString("more_more_keys", "")).apply()
        prefs.edit().remove("more_more_keys").apply()
    }
    if (prefs.contains("spellcheck_use_contacts")) {
        prefs.edit().putBoolean(Settings.PREF_USE_CONTACTS, prefs.getBoolean("spellcheck_use_contacts", false)).apply()
        prefs.edit().remove("spellcheck_use_contacts").apply()
    }
    // upgrade additional subtype locale strings
    if (prefs.contains(Settings.PREF_ADDITIONAL_SUBTYPES)) {
        val additionalSubtypes = mutableListOf<String>()
        prefs.getString(Settings.PREF_ADDITIONAL_SUBTYPES, "")!!.split(";").forEach {
            val localeString = it.substringBefore(":")
            additionalSubtypes.add(it.replace(localeString, localeString.constructLocale().toLanguageTag()))
        }
        prefs.edit().putString(Settings.PREF_ADDITIONAL_SUBTYPES, additionalSubtypes.joinToString(";")).apply()
    }
    // move pinned clips to credential protected storage if device is not locked (should never happen)
    if (!prefs.contains(Settings.PREF_PINNED_CLIPS)) return
    try {
        val defaultProtectedPrefs = context.protectedPrefs()
        defaultProtectedPrefs.edit { putString(Settings.PREF_PINNED_CLIPS, prefs.getString(Settings.PREF_PINNED_CLIPS, "")) }
        prefs.edit { remove(Settings.PREF_PINNED_CLIPS) }
    } catch (_: IllegalStateException) {
        // SharedPreferences in credential encrypted storage are not available until after user is unlocked
    }
}
