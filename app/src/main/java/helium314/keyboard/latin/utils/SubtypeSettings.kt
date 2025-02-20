// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.view.inputmethod.InputMethodSubtype
import android.widget.Toast
import androidx.core.app.LocaleManagerCompat
import androidx.core.content.edit
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.common.Constants.Separators
import helium314.keyboard.latin.common.LocaleUtils
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ScriptUtils.script
import helium314.keyboard.latin.utils.SettingsSubtype.Companion.toSettingsSubtype
import java.util.Locale

object SubtypeSettings {
    /** @return enabled subtypes. If no subtypes are enabled, but a contextForFallback is provided,
     *  subtypes for system locales will be returned, or en-US if none found. */
    fun getEnabledSubtypes(prefs: SharedPreferences, fallback: Boolean = false): List<InputMethodSubtype> {
        if (prefs.getBoolean(Settings.PREF_USE_SYSTEM_LOCALES, Defaults.PREF_USE_SYSTEM_LOCALES))
            return getDefaultEnabledSubtypes()
        if (fallback && enabledSubtypes.isEmpty())
            return getDefaultEnabledSubtypes()
        return enabledSubtypes.toList()
    }

    fun getAllAvailableSubtypes(): List<InputMethodSubtype> =
        resourceSubtypesByLocale.values.flatten() + additionalSubtypes

    fun getMatchingMainLayoutNameForLocale(locale: Locale): String {
        val subtypes = resourceSubtypesByLocale.values.flatten()
        val name = LocaleUtils.getBestMatch(locale, subtypes) { it.locale() }?.mainLayoutName()
        if (name != null) return name
        return when (locale.script()) {
            ScriptUtils.SCRIPT_LATIN -> "qwerty"
            ScriptUtils.SCRIPT_ARMENIAN -> "armenian_phonetic"
            ScriptUtils.SCRIPT_CYRILLIC -> "ru"
            ScriptUtils.SCRIPT_GREEK -> "greek"
            ScriptUtils.SCRIPT_HEBREW -> "hebrew"
            ScriptUtils.SCRIPT_GEORGIAN -> "georgian"
            ScriptUtils.SCRIPT_BENGALI -> "bengali_unijoy"
            else -> throw RuntimeException("Wrong script supplied: ${locale.script()}")
        }
    }

    fun addEnabledSubtype(prefs: SharedPreferences, newSubtype: InputMethodSubtype) {
        val subtypeString = newSubtype.toSettingsSubtype().toPref()
        val oldSubtypeStrings = prefs.getString(Settings.PREF_ENABLED_SUBTYPES, Defaults.PREF_ENABLED_SUBTYPES)!!.split(Separators.SETS)
        val newString = (oldSubtypeStrings + subtypeString).filter { it.isNotBlank() }.toSortedSet().joinToString(Separators.SETS)
        prefs.edit { putString(Settings.PREF_ENABLED_SUBTYPES, newString) }

        if (newSubtype !in enabledSubtypes) {
            enabledSubtypes.add(newSubtype)
            enabledSubtypes.sortBy { it.locale().toLanguageTag() } // for consistent order
            RichInputMethodManager.getInstance().refreshSubtypeCaches()
        }
    }

    /** @return whether subtype was actually removed */
    fun removeEnabledSubtype(context: Context, subtype: InputMethodSubtype): Boolean {
        if (!removeEnabledSubtype(context.prefs(), subtype.toSettingsSubtype().toPref())) return false
        if (!enabledSubtypes.remove(subtype)) reloadEnabledSubtypes(context)
        else RichInputMethodManager.getInstance().refreshSubtypeCaches()
        return true
    }

    fun getSelectedSubtype(prefs: SharedPreferences): InputMethodSubtype {
        val selectedSubtype = prefs.getString(Settings.PREF_SELECTED_SUBTYPE, Defaults.PREF_SELECTED_SUBTYPE)!!.toSettingsSubtype()
        if (selectedSubtype.isAdditionalSubtype(prefs)) {
            val selectedAdditionalSubtype = selectedSubtype.toAdditionalSubtype()
            if (selectedAdditionalSubtype != null) return selectedAdditionalSubtype
        }
        // no additional subtype, must be a resource subtype
        val subtypes = if (prefs.getBoolean(Settings.PREF_USE_SYSTEM_LOCALES, Defaults.PREF_USE_SYSTEM_LOCALES)) getDefaultEnabledSubtypes()
        else enabledSubtypes

        val subtype = subtypes.firstOrNull { it.toSettingsSubtype() == selectedSubtype }
        if (subtype != null) {
            return subtype
        } else {
            Log.w(TAG, "selected subtype $selectedSubtype / ${prefs.getString(Settings.PREF_SELECTED_SUBTYPE, Defaults.PREF_SELECTED_SUBTYPE)} not found")
        }
        if (subtypes.isNotEmpty())
            return subtypes.first()
        val defaultSubtypes = getDefaultEnabledSubtypes()
        return defaultSubtypes.firstOrNull { it.locale() == selectedSubtype.locale && it.mainLayoutName() == it.mainLayoutName() }
            ?: defaultSubtypes.firstOrNull { it.locale().language == selectedSubtype.locale.language }
            ?: defaultSubtypes.first()
    }

    fun setSelectedSubtype(prefs: SharedPreferences, subtype: InputMethodSubtype) {
        val subtypeString = subtype.toSettingsSubtype().toPref()
        if (subtype.locale().toLanguageTag().isEmpty() || prefs.getString(Settings.PREF_SELECTED_SUBTYPE, Defaults.PREF_SELECTED_SUBTYPE) == subtypeString)
            return
        prefs.edit { putString(Settings.PREF_SELECTED_SUBTYPE, subtypeString) }
    }

    // todo: use this or the version in SubtypeUtilsAdditional?
    fun isAdditionalSubtype(subtype: InputMethodSubtype): Boolean = subtype in additionalSubtypes

    fun getAdditionalSubtypes(): List<InputMethodSubtype> = additionalSubtypes.toList()

    fun reloadSystemLocales(context: Context) {
        systemLocales.clear()
        val localeList = LocaleManagerCompat.getSystemLocales(context)
        (0 until localeList.size()).forEach {
            val locale = localeList[it]
            if (locale != null) systemLocales.add(locale)
        }
        systemSubtypes.clear()
    }

    fun getSystemLocales(): List<Locale> = systemLocales.toList()

    fun hasMatchingSubtypeForLocale(locale: Locale): Boolean = !resourceSubtypesByLocale[locale].isNullOrEmpty()

    fun getResourceSubtypesForLocale(locale: Locale): List<InputMethodSubtype> = resourceSubtypesByLocale[locale].orEmpty()

    fun getAvailableSubtypeLocales(): List<Locale> = resourceSubtypesByLocale.keys.toList()

    fun onRenameLayout(type: LayoutType, from: String, to: String, context: Context) {
        val prefs = context.prefs()
        listOf(
            Settings.PREF_ADDITIONAL_SUBTYPES to Defaults.PREF_ADDITIONAL_SUBTYPES,
            Settings.PREF_ENABLED_SUBTYPES to Defaults.PREF_ENABLED_SUBTYPES,
            Settings.PREF_SELECTED_SUBTYPE to Defaults.PREF_SELECTED_SUBTYPE
        ).forEach { (key, default) ->
            val new = prefs.getString(key, default)!!.split(Separators.SETS).joinToString(Separators.SETS) {
                val subtype = it.toSettingsSubtype()
                if (subtype.layoutName(type) == from) subtype.withLayout(type, to).toPref()
                else subtype.toPref()
            }
            prefs.edit().putString(key, new).apply()
        }
        if (Settings.readDefaultLayoutName(type, prefs) == from)
            Settings.writeDefaultLayoutName(to, type, prefs)
        reloadEnabledSubtypes(context)
    }

    fun reloadEnabledSubtypes(context: Context) {
        enabledSubtypes.clear()
        removeInvalidCustomSubtypes(context)
        loadAdditionalSubtypes(context.prefs())
        loadEnabledSubtypes(context)
        RichInputMethodManager.getInstance().refreshSubtypeCaches()
    }

    fun init(context: Context) {
        SubtypeLocaleUtils.init(context) // necessary to get the correct getKeyboardLayoutSetName

        // necessary to set system locales at start, because for some weird reason (bug?)
        // LocaleManagerCompat.getSystemLocales(context) sometimes doesn't return all system locales
        reloadSystemLocales(context)

        loadResourceSubtypes(context.resources)
        removeInvalidCustomSubtypes(context)
        loadAdditionalSubtypes(context.prefs())
        loadEnabledSubtypes(context)
    }

    private fun getDefaultEnabledSubtypes(): List<InputMethodSubtype> {
        if (systemSubtypes.isNotEmpty()) return systemSubtypes
        val subtypes = systemLocales.mapNotNull { locale ->
            val subtypesOfLocale = resourceSubtypesByLocale[locale]
            // get best match
                ?: LocaleUtils.getBestMatch(locale, resourceSubtypesByLocale.keys) {it}?.let { resourceSubtypesByLocale[it] }
            subtypesOfLocale?.firstOrNull()
        }
        if (subtypes.isEmpty()) {
            // hardcoded fallback to en-US for weird cases
            systemSubtypes.add(resourceSubtypesByLocale[Locale.US]!!.first())
        } else {
            systemSubtypes.addAll(subtypes)
        }
        return systemSubtypes
    }

    private fun loadResourceSubtypes(resources: Resources) {
        getResourceSubtypes(resources).forEach {
            resourceSubtypesByLocale.getOrPut(it.locale()) { ArrayList(2) }.add(it)
        }
    }

    // remove custom subtypes without a layout file
    private fun removeInvalidCustomSubtypes(context: Context) {
        val prefs = context.prefs()
        val additionalSubtypes = prefs.getString(Settings.PREF_ADDITIONAL_SUBTYPES, Defaults.PREF_ADDITIONAL_SUBTYPES)!!.split(Separators.SETS)
        val customLayoutFiles by lazy { LayoutUtilsCustom.getLayoutFiles(LayoutType.MAIN, context).map { it.name } }
        val subtypesToRemove = mutableListOf<String>()
        additionalSubtypes.forEach {
            val name = it.toSettingsSubtype().mainLayoutName() ?: SubtypeLocaleUtils.QWERTY
            if (!LayoutUtilsCustom.isCustomLayout(name)) return@forEach
            if (name !in customLayoutFiles)
                subtypesToRemove.add(it)
        }
        if (subtypesToRemove.isEmpty()) return
        Log.w(TAG, "removing custom subtypes without main layout files: $subtypesToRemove")
        Settings.writePrefAdditionalSubtypes(prefs, additionalSubtypes.filterNot { it in subtypesToRemove }.joinToString(Separators.SETS))
    }

    private fun loadAdditionalSubtypes(prefs: SharedPreferences) {
        additionalSubtypes.clear()
        val additionalSubtypeString = prefs.getString(Settings.PREF_ADDITIONAL_SUBTYPES, Defaults.PREF_ADDITIONAL_SUBTYPES)!!
        val subtypes = SubtypeUtilsAdditional.createAdditionalSubtypes(additionalSubtypeString)
        additionalSubtypes.addAll(subtypes)
    }

    // requires loadResourceSubtypes to be called before
    private fun loadEnabledSubtypes(context: Context) {
        val prefs = context.prefs()
        val settingsSubtypes = prefs.getString(Settings.PREF_ENABLED_SUBTYPES, Defaults.PREF_ENABLED_SUBTYPES)!!
            .split(Separators.SETS).filter { it.isNotEmpty() }.map { it.toSettingsSubtype() }
        for (settingsSubtype in settingsSubtypes) {
            if (settingsSubtype.isAdditionalSubtype(prefs)) {
                val additionalSubtype = settingsSubtype.toAdditionalSubtype()
                if (additionalSubtype != null) {
                    enabledSubtypes.add(additionalSubtype)
                    continue
                }
            }
            val subtypesForLocale = resourceSubtypesByLocale[settingsSubtype.locale]
            if (subtypesForLocale == null) {
                val message = "no resource subtype for $settingsSubtype"
                Log.w(TAG, message)
                if (DebugFlags.DEBUG_ENABLED)
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                else // don't remove in debug mode
                    removeEnabledSubtype(prefs, settingsSubtype.toPref())
                continue
            }

            val subtype = subtypesForLocale.firstOrNull { SubtypeLocaleUtils.getMainLayoutName(it) == (settingsSubtype.mainLayoutName() ?: SubtypeLocaleUtils.QWERTY) }
            if (subtype == null) {
                val message = "subtype $settingsSubtype could not be loaded"
                Log.w(TAG, message)
                if (DebugFlags.DEBUG_ENABLED)
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                else // don't remove in debug mode
                    removeEnabledSubtype(prefs, settingsSubtype.toPref())
                continue
            }

            enabledSubtypes.add(subtype)
        }
    }

    /** @return whether pref was changed */
    private fun removeEnabledSubtype(prefs: SharedPreferences, subtypeString: String): Boolean {
        val oldSubtypeString = prefs.getString(Settings.PREF_ENABLED_SUBTYPES, Defaults.PREF_ENABLED_SUBTYPES)!!
        val newString = (oldSubtypeString.split(Separators.SETS) - subtypeString).joinToString(Separators.SETS)
        if (newString == oldSubtypeString)
            return false// already removed
        prefs.edit { putString(Settings.PREF_ENABLED_SUBTYPES, newString) }
        if (subtypeString == prefs.getString(Settings.PREF_SELECTED_SUBTYPE, Defaults.PREF_SELECTED_SUBTYPE)) {
            // switch subtype if the currently used one has been disabled
            try {
                val nextSubtype = RichInputMethodManager.getInstance().getNextSubtypeInThisIme(true)
                if (subtypeString == nextSubtype?.toSettingsSubtype()?.toPref())
                    KeyboardSwitcher.getInstance().switchToSubtype(getDefaultEnabledSubtypes().first())
                else
                    KeyboardSwitcher.getInstance().switchToSubtype(nextSubtype)
            } catch (_: Exception) { } // do nothing if RichInputMethodManager isn't initialized
        }
        return true
    }

    private val enabledSubtypes = mutableListOf<InputMethodSubtype>()
    private val resourceSubtypesByLocale = LinkedHashMap<Locale, MutableList<InputMethodSubtype>>(100)
    private val additionalSubtypes = mutableListOf<InputMethodSubtype>()
    private val systemLocales = mutableListOf<Locale>()
    private val systemSubtypes = mutableListOf<InputMethodSubtype>()
    private val TAG = SubtypeSettings::class.simpleName
}
