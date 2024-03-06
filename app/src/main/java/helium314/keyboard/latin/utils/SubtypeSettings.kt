// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.os.Build
import android.view.inputmethod.InputMethodSubtype
import android.widget.Toast
import androidx.core.app.LocaleManagerCompat
import androidx.core.content.edit
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.LocaleUtils
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ScriptUtils.script
import org.xmlpull.v1.XmlPullParser
import java.util.*

/** @return enabled subtypes. If no subtypes are enabled, but a contextForFallback is provided,
 *  subtypes for system locales will be returned, or en-US if none found. */
fun getEnabledSubtypes(prefs: SharedPreferences, fallback: Boolean = false): List<InputMethodSubtype> {
    require(initialized)
    if (prefs.getBoolean(Settings.PREF_USE_SYSTEM_LOCALES, true))
        return getDefaultEnabledSubtypes()
    if (fallback && enabledSubtypes.isEmpty())
        return getDefaultEnabledSubtypes()
    return enabledSubtypes
}

fun getAllAvailableSubtypes(): List<InputMethodSubtype> {
    require(initialized)
    return resourceSubtypesByLocale.values.flatten() + additionalSubtypes
}

fun getMatchingLayoutSetNameForLocale(locale: Locale): String {
    val subtypes = resourceSubtypesByLocale.values.flatten()
    val name = LocaleUtils.getBestMatch(locale, subtypes) { it.locale() }?.getExtraValueOf(Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET)
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
    require(initialized)
    val subtypeString = newSubtype.prefString()
    val oldSubtypeStrings = prefs.getString(Settings.PREF_ENABLED_SUBTYPES, "")!!.split(SUBTYPE_SEPARATOR)
    val newString = (oldSubtypeStrings + subtypeString).filter { it.isNotBlank() }.toSortedSet().joinToString(SUBTYPE_SEPARATOR)
    prefs.edit { putString(Settings.PREF_ENABLED_SUBTYPES, newString) }

    if (newSubtype !in enabledSubtypes) {
        enabledSubtypes.add(newSubtype)
        enabledSubtypes.sortBy { it.locale().toLanguageTag() } // for consistent order
        RichInputMethodManager.getInstance().refreshSubtypeCaches()
    }
}

/** returns whether subtype was actually removed, does not remove last subtype */
fun removeEnabledSubtype(prefs: SharedPreferences, subtype: InputMethodSubtype) {
    require(initialized)
    removeEnabledSubtype(prefs, subtype.prefString())
    enabledSubtypes.remove(subtype)
    RichInputMethodManager.getInstance().refreshSubtypeCaches()
}

fun addAdditionalSubtype(prefs: SharedPreferences, resources: Resources, subtype: InputMethodSubtype) {
    val oldAdditionalSubtypesString = Settings.readPrefAdditionalSubtypes(prefs, resources)
    val additionalSubtypes = AdditionalSubtypeUtils.createAdditionalSubtypesArray(oldAdditionalSubtypesString).toMutableSet()
    additionalSubtypes.add(subtype)
    val newAdditionalSubtypesString = AdditionalSubtypeUtils.createPrefSubtypes(additionalSubtypes.toTypedArray())
    Settings.writePrefAdditionalSubtypes(prefs, newAdditionalSubtypesString)
}

fun removeAdditionalSubtype(prefs: SharedPreferences, resources: Resources, subtype: InputMethodSubtype) {
    val oldAdditionalSubtypesString = Settings.readPrefAdditionalSubtypes(prefs, resources)
    val oldAdditionalSubtypes = AdditionalSubtypeUtils.createAdditionalSubtypesArray(oldAdditionalSubtypesString)
    val newAdditionalSubtypes = oldAdditionalSubtypes.filter { it != subtype }
    val newAdditionalSubtypesString = AdditionalSubtypeUtils.createPrefSubtypes(newAdditionalSubtypes.toTypedArray())
    Settings.writePrefAdditionalSubtypes(prefs, newAdditionalSubtypesString)
}

fun getSelectedSubtype(prefs: SharedPreferences): InputMethodSubtype {
    require(initialized)
    val localeAndLayout = prefs.getString(Settings.PREF_SELECTED_SUBTYPE, "")!!.toLocaleAndLayout()
    val subtypes = if (prefs.getBoolean(Settings.PREF_USE_SYSTEM_LOCALES, true)) getDefaultEnabledSubtypes()
        else enabledSubtypes
    val subtype = subtypes.firstOrNull { localeAndLayout.first == it.locale() && localeAndLayout.second == SubtypeLocaleUtils.getKeyboardLayoutSetName(it) }
    if (subtype != null) {
        return subtype
    } else {
        Log.w(TAG, "selected subtype $localeAndLayout not found")
    }
    if (subtypes.isNotEmpty())
        return subtypes.first()
    val defaultSubtypes = getDefaultEnabledSubtypes()
    return defaultSubtypes.firstOrNull { localeAndLayout.first == it.locale() && localeAndLayout.second == SubtypeLocaleUtils.getKeyboardLayoutSetName(it) }
        ?: defaultSubtypes.firstOrNull { localeAndLayout.first.language == it.locale().language && localeAndLayout.second == SubtypeLocaleUtils.getKeyboardLayoutSetName(it) }
        ?: defaultSubtypes.first()
}

fun setSelectedSubtype(prefs: SharedPreferences, subtype: InputMethodSubtype) {
    val subtypeString = subtype.prefString()
    if (subtype.locale().toLanguageTag().isEmpty() || prefs.getString(Settings.PREF_SELECTED_SUBTYPE, "") == subtypeString)
        return
    prefs.edit { putString(Settings.PREF_SELECTED_SUBTYPE, subtypeString) }
}

fun isAdditionalSubtype(subtype: InputMethodSubtype): Boolean {
    return subtype in additionalSubtypes
}

fun updateAdditionalSubtypes(subtypes: Array<InputMethodSubtype>) {
    additionalSubtypes.clear()
    additionalSubtypes.addAll(subtypes)
    RichInputMethodManager.getInstance().refreshSubtypeCaches()
}

fun reloadSystemLocales(context: Context) {
    systemLocales.clear()
    val localeList = LocaleManagerCompat.getSystemLocales(context)
    (0 until localeList.size()).forEach {
        val locale = localeList[it]
        if (locale != null) systemLocales.add(locale)
    }
    systemSubtypes.clear()
}

fun getSystemLocales(): List<Locale> {
    require(initialized)
    return systemLocales
}

fun hasMatchingSubtypeForLocale(locale: Locale): Boolean {
    require(initialized)
    return !resourceSubtypesByLocale[locale].isNullOrEmpty()
}

fun getAvailableSubtypeLocales(): Collection<Locale> {
    require(initialized)
    return resourceSubtypesByLocale.keys
}

fun reloadEnabledSubtypes(context: Context) {
    require(initialized)
    enabledSubtypes.clear()
    removeInvalidCustomSubtypes(context)
    loadEnabledSubtypes(context)
}

fun init(context: Context) {
    if (initialized) return
    SubtypeLocaleUtils.init(context) // necessary to get the correct getKeyboardLayoutSetName

    // necessary to set system locales at start, because for some weird reason (bug?)
    // LocaleManagerCompat.getSystemLocales(context) sometimes doesn't return all system locales
    reloadSystemLocales(context)

    loadResourceSubtypes(context.resources)
    removeInvalidCustomSubtypes(context)
    loadAdditionalSubtypes(context)
    loadEnabledSubtypes(context)
    initialized = true
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

/** string for for identifying a subtype, does not contain all necessary information to actually create it */
private fun InputMethodSubtype.prefString(): String {
    if (DebugFlags.DEBUG_ENABLED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && locale().toLanguageTag() == "und") {
        Log.e(TAG, "unknown language, should not happen ${locale}, $languageTag, $extraValue, ${hashCode()}, $nameResId")
    }
    return locale().toLanguageTag() + LOCALE_LAYOUT_SEPARATOR + SubtypeLocaleUtils.getKeyboardLayoutSetName(this)
}

private fun String.toLocaleAndLayout(): Pair<Locale, String> =
    substringBefore(LOCALE_LAYOUT_SEPARATOR).constructLocale() to substringAfter(LOCALE_LAYOUT_SEPARATOR)

private fun Pair<Locale, String>.prefString() =
    first.toLanguageTag() + LOCALE_LAYOUT_SEPARATOR + second

private fun loadResourceSubtypes(resources: Resources) {
    val xml = resources.getXml(R.xml.method)
    xml.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
    val namespace = "http://schemas.android.com/apk/res/android"
    var eventType = xml.eventType
    while (eventType != XmlPullParser.END_DOCUMENT) {
        if (eventType == XmlPullParser.START_TAG && xml.name == "subtype") {
            val icon = xml.getAttributeResourceValue(namespace, "icon", 0)
            val label = xml.getAttributeResourceValue(namespace, "label", 0)
            val subtypeId = xml.getAttributeIntValue(namespace, "subtypeId", 0)
            val localeString = xml.getAttributeValue(namespace, "imeSubtypeLocale").intern()
            val languageTag = xml.getAttributeValue(namespace, "languageTag").intern()
            val imeSubtypeMode = xml.getAttributeValue(namespace, "imeSubtypeMode")
            val imeSubtypeExtraValue = xml.getAttributeValue(namespace, "imeSubtypeExtraValue").intern()
            val isAsciiCapable = xml.getAttributeBooleanValue(namespace, "isAsciiCapable", false)
            val b = InputMethodSubtype.InputMethodSubtypeBuilder()
            b.setSubtypeIconResId(icon)
            b.setSubtypeNameResId(label)
            if (subtypeId != 0)
                b.setSubtypeId(subtypeId)
            b.setSubtypeLocale(localeString)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                b.setLanguageTag(languageTag)
            b.setSubtypeMode(imeSubtypeMode)
            b.setSubtypeExtraValue(imeSubtypeExtraValue)
            b.setIsAsciiCapable(isAsciiCapable)
            val locale = if (languageTag.isEmpty()) localeString.constructLocale()
                else languageTag.constructLocale()
            resourceSubtypesByLocale.getOrPut(locale) { ArrayList(2) }.add(b.build())
        }
        eventType = xml.next()
    }
}

// remove custom subtypes without a layout file
private fun removeInvalidCustomSubtypes(context: Context) {
    val prefs = DeviceProtectedUtils.getSharedPreferences(context)
    val additionalSubtypes = Settings.readPrefAdditionalSubtypes(prefs, context.resources).split(";")
    val customSubtypeFiles by lazy { Settings.getLayoutsDir(context).list() }
    val subtypesToRemove = mutableListOf<String>()
    additionalSubtypes.forEach {
        val name = it.substringAfter(":").substringBefore(":")
        if (!name.startsWith(CUSTOM_LAYOUT_PREFIX)) return@forEach
        if (customSubtypeFiles?.contains(name) != true)
            subtypesToRemove.add(it)
    }
    if (subtypesToRemove.isEmpty()) return
    Log.w(TAG, "removing custom subtypes without files: $subtypesToRemove")
    Settings.writePrefAdditionalSubtypes(prefs, additionalSubtypes.filterNot { it in subtypesToRemove }.joinToString(";"))
}

private fun loadAdditionalSubtypes(context: Context) {
    val prefs = DeviceProtectedUtils.getSharedPreferences(context)
    val additionalSubtypeString = Settings.readPrefAdditionalSubtypes(prefs, context.resources)
    val subtypes = AdditionalSubtypeUtils.createAdditionalSubtypesArray(additionalSubtypeString)
    additionalSubtypes.addAll(subtypes)
}

// requires loadResourceSubtypes to be called before
private fun loadEnabledSubtypes(context: Context) {
    val prefs = DeviceProtectedUtils.getSharedPreferences(context)
    val subtypeStrings = prefs.getString(Settings.PREF_ENABLED_SUBTYPES, "")!!
        .split(SUBTYPE_SEPARATOR).filter { it.isNotEmpty() }.map { it.toLocaleAndLayout() }

    for (localeAndLayout in subtypeStrings) {
        val subtypesForLocale = resourceSubtypesByLocale[localeAndLayout.first]
        if (subtypesForLocale == null) {
            val message = "no resource subtype for $localeAndLayout"
            Log.w(TAG, message)
            if (DebugFlags.DEBUG_ENABLED)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            else // don't remove in debug mode
                removeEnabledSubtype(prefs, localeAndLayout.prefString())
            continue
        }

        val subtype = subtypesForLocale.firstOrNull { SubtypeLocaleUtils.getKeyboardLayoutSetName(it) == localeAndLayout.second }
            ?: additionalSubtypes.firstOrNull { it.locale() == localeAndLayout.first && SubtypeLocaleUtils.getKeyboardLayoutSetName(it) == localeAndLayout.second }
        if (subtype == null) {
            val message = "subtype $localeAndLayout could not be loaded"
            Log.w(TAG, message)
            if (DebugFlags.DEBUG_ENABLED)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            else // don't remove in debug mode
                removeEnabledSubtype(prefs, localeAndLayout.prefString())
            continue
        }

        enabledSubtypes.add(subtype)
    }
}

private fun removeEnabledSubtype(prefs: SharedPreferences, subtypeString: String) {
    val oldSubtypeString = prefs.getString(Settings.PREF_ENABLED_SUBTYPES, "")!!
    val newString = (oldSubtypeString.split(SUBTYPE_SEPARATOR) - subtypeString).joinToString(SUBTYPE_SEPARATOR)
    if (newString == oldSubtypeString)
        return // already removed
    prefs.edit { putString(Settings.PREF_ENABLED_SUBTYPES, newString) }
    if (subtypeString == prefs.getString(Settings.PREF_SELECTED_SUBTYPE, "")) {
        // switch subtype if the currently used one has been disabled
        try {
            val nextSubtype = RichInputMethodManager.getInstance().getNextSubtypeInThisIme(true)
            if (subtypeString == nextSubtype?.prefString())
                KeyboardSwitcher.getInstance().switchToSubtype(getDefaultEnabledSubtypes().first())
            else
                KeyboardSwitcher.getInstance().switchToSubtype(nextSubtype)
        } catch (_: Exception) { } // do nothing if RichInputMethodManager isn't initialized
    }
}

var initialized = false
    private set
private val enabledSubtypes = mutableListOf<InputMethodSubtype>()
private val resourceSubtypesByLocale = LinkedHashMap<Locale, MutableList<InputMethodSubtype>>(100)
private val additionalSubtypes = mutableListOf<InputMethodSubtype>()
private val systemLocales = mutableListOf<Locale>()
private val systemSubtypes = mutableListOf<InputMethodSubtype>()

private const val SUBTYPE_SEPARATOR = ";"
private const val LOCALE_LAYOUT_SEPARATOR = ":"
private const val TAG = "SubtypeSettings"
