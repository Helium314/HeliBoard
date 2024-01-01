// SPDX-License-Identifier: GPL-3.0-only

package org.dslul.openboard.inputmethod.latin.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.os.Build
import android.view.inputmethod.InputMethodSubtype
import android.widget.Toast
import androidx.core.app.LocaleManagerCompat
import androidx.core.content.edit
import org.dslul.openboard.inputmethod.keyboard.KeyboardSwitcher
import org.dslul.openboard.inputmethod.latin.BuildConfig
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.RichInputMethodManager
import org.dslul.openboard.inputmethod.latin.define.DebugFlags
import org.dslul.openboard.inputmethod.latin.utils.AdditionalSubtypeUtils
import org.dslul.openboard.inputmethod.latin.utils.CUSTOM_LAYOUT_PREFIX
import org.dslul.openboard.inputmethod.latin.utils.DeviceProtectedUtils
import org.dslul.openboard.inputmethod.latin.utils.Log
import org.dslul.openboard.inputmethod.latin.utils.SubtypeLocaleUtils
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashMap

/** @return enabled subtypes. If no subtypes are enabled, but a contextForFallback is provided,
 *  subtypes for system locales will be returned, or en_US if none found. */
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

fun addEnabledSubtype(prefs: SharedPreferences, newSubtype: InputMethodSubtype) {
    require(initialized)
    val subtypeString = newSubtype.prefString()
    val oldSubtypeStrings = prefs.getString(Settings.PREF_ENABLED_INPUT_STYLES, "")!!.split(SUBTYPE_SEPARATOR)
    val newString = (oldSubtypeStrings + subtypeString).filter { it.isNotBlank() }.toSortedSet().joinToString(SUBTYPE_SEPARATOR)
    prefs.edit { putString(Settings.PREF_ENABLED_INPUT_STYLES, newString) }

    if (newSubtype !in enabledSubtypes) {
        enabledSubtypes.add(newSubtype)
        enabledSubtypes.sortBy { it.locale() } // for consistent order
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
    val subtypeString = prefs.getString(Settings.PREF_SELECTED_INPUT_STYLE, "")!!.split(LOCALE_LAYOUT_SEPARATOR)
    val subtypes = if (prefs.getBoolean(Settings.PREF_USE_SYSTEM_LOCALES, true)) getDefaultEnabledSubtypes()
        else enabledSubtypes
    val subtype = subtypes.firstOrNull { subtypeString.first() == it.locale() && subtypeString.last() == SubtypeLocaleUtils.getKeyboardLayoutSetName(it) }
        ?: subtypes.firstOrNull()
    if (subtype == null) {
        val defaultSubtypes = getDefaultEnabledSubtypes()
        return defaultSubtypes.firstOrNull { subtypeString.first() == it.locale() && subtypeString.last() == SubtypeLocaleUtils.getKeyboardLayoutSetName(it) }
            ?: defaultSubtypes.firstOrNull { subtypeString.first().substringBefore("_") == it.locale().substringBefore("_") && subtypeString.last() == SubtypeLocaleUtils.getKeyboardLayoutSetName(it) }
            ?: defaultSubtypes.first()
    }
    return subtype
}

fun setSelectedSubtype(prefs: SharedPreferences, subtype: InputMethodSubtype) {
    val subtypeString = subtype.prefString()
    if (subtype.locale().isEmpty() || prefs.getString(Settings.PREF_SELECTED_INPUT_STYLE, "") == subtypeString)
        return
    prefs.edit { putString(Settings.PREF_SELECTED_INPUT_STYLE, subtypeString) }
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

fun hasMatchingSubtypeForLocaleString(localeString: String): Boolean {
    require(initialized)
    return !resourceSubtypesByLocale[localeString].isNullOrEmpty()
}

fun getAvailableSubtypeLocaleStrings(): Collection<String> {
    require(initialized)
    return resourceSubtypesByLocale.keys
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
        val localeString = locale.toString()
        val subtypesOfLocale = resourceSubtypesByLocale[localeString]
            ?: resourceSubtypesByLocale[localeString.substringBefore("_")] // fall back to language matching the subtype
            ?: localeString.substringBefore("_").let { language -> // fall back to languages matching subtype language
                resourceSubtypesByLocale.firstNotNullOfOrNull {
                    if (it.key.substringBefore("_") == language)
                        it.value
                    else null
                }
            }
        subtypesOfLocale?.firstOrNull()
    }
    if (subtypes.isEmpty()) {
        // hardcoded fallback for weird cases
        systemSubtypes.add(resourceSubtypesByLocale["en_US"]!!.first())
    } else {
        systemSubtypes.addAll(subtypes)
    }
    return systemSubtypes
}

private fun InputMethodSubtype.prefString() =
    locale() + LOCALE_LAYOUT_SEPARATOR + SubtypeLocaleUtils.getKeyboardLayoutSetName(this)

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
            val locale = xml.getAttributeValue(namespace, "imeSubtypeLocale").intern()
            val languageTag = xml.getAttributeValue(namespace, "languageTag")
            val imeSubtypeMode = xml.getAttributeValue(namespace, "imeSubtypeMode")
            val imeSubtypeExtraValue = xml.getAttributeValue(namespace, "imeSubtypeExtraValue").intern()
            val isAsciiCapable = xml.getAttributeBooleanValue(namespace, "isAsciiCapable", false)
            val b = InputMethodSubtype.InputMethodSubtypeBuilder()
            b.setSubtypeIconResId(icon)
            b.setSubtypeNameResId(label)
            if (subtypeId != 0)
                b.setSubtypeId(subtypeId)
            b.setSubtypeLocale(locale)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && languageTag != null)
                b.setLanguageTag(languageTag)
            b.setSubtypeMode(imeSubtypeMode)
            b.setSubtypeExtraValue(imeSubtypeExtraValue)
            b.setIsAsciiCapable(isAsciiCapable)
            resourceSubtypesByLocale.getOrPut(locale) { ArrayList(2) }.add(b.build())
        }
        eventType = xml.next()
    }
}

private fun loadAdditionalSubtypes(context: Context) {
    val prefs = DeviceProtectedUtils.getSharedPreferences(context)
    val additionalSubtypeString = Settings.readPrefAdditionalSubtypes(prefs, context.resources)
    val subtypes = AdditionalSubtypeUtils.createAdditionalSubtypesArray(additionalSubtypeString)
    additionalSubtypes.addAll(subtypes)
}

// remove custom subtypes without a layout file
private fun removeInvalidCustomSubtypes(context: Context) {
    val prefs = DeviceProtectedUtils.getSharedPreferences(context)
    val additionalSubtypes = Settings.readPrefAdditionalSubtypes(prefs, context.resources).split(";")
    val customSubtypeFiles by lazy { File(context.filesDir, "layouts").list() }
    val subtypesToRemove = mutableListOf<String>()
    additionalSubtypes.forEach {
        val name = it.substringAfter(":").substringBefore(":")
        if (!name.startsWith(CUSTOM_LAYOUT_PREFIX)) return@forEach
        if (name !in customSubtypeFiles)
            subtypesToRemove.add(it)
    }
    if (subtypesToRemove.isEmpty()) return
    Log.w(TAG, "removing custom subtypes without files: $subtypesToRemove")
    Settings.writePrefAdditionalSubtypes(prefs, additionalSubtypes.filterNot { it in subtypesToRemove }.joinToString(";"))
}

// requires loadResourceSubtypes to be called before
private fun loadEnabledSubtypes(context: Context) {
    val prefs = DeviceProtectedUtils.getSharedPreferences(context)
    val subtypeStrings = prefs.getString(Settings.PREF_ENABLED_INPUT_STYLES, "")!!
        .split(SUBTYPE_SEPARATOR).filter { it.isNotEmpty() }.map { it.split(LOCALE_LAYOUT_SEPARATOR) }

    for (localeAndLayout in subtypeStrings) {
        require(localeAndLayout.size == 2)
        val subtypesForLocale = resourceSubtypesByLocale[localeAndLayout.first()]
        if (DebugFlags.DEBUG_ENABLED) // should not happen, but should not crash for normal user
            require(subtypesForLocale != null)
        else if (subtypesForLocale == null)
            continue

        val subtype = subtypesForLocale.firstOrNull { SubtypeLocaleUtils.getKeyboardLayoutSetName(it) == localeAndLayout.last() }
            ?: additionalSubtypes.firstOrNull { it.locale() == localeAndLayout.first() && SubtypeLocaleUtils.getKeyboardLayoutSetName(it) == localeAndLayout.last() }
        if (subtype == null) {
            val message = "subtype $localeAndLayout could not be loaded"
            Log.w(TAG, message)
            if (DebugFlags.DEBUG_ENABLED)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            else // don't remove in debug mode
                removeEnabledSubtype(prefs, localeAndLayout.joinToString(LOCALE_LAYOUT_SEPARATOR))
            continue
        }

        enabledSubtypes.add(subtype)
    }
}

private fun removeEnabledSubtype(prefs: SharedPreferences, subtypeString: String) {
    val oldSubtypeString = prefs.getString(Settings.PREF_ENABLED_INPUT_STYLES, "")!!
    val newString = (oldSubtypeString.split(SUBTYPE_SEPARATOR) - subtypeString).joinToString(SUBTYPE_SEPARATOR)
    if (newString == oldSubtypeString)
        return // already removed
    prefs.edit { putString(Settings.PREF_ENABLED_INPUT_STYLES, newString) }
    if (subtypeString == prefs.getString(Settings.PREF_SELECTED_INPUT_STYLE, "")) {
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
private val resourceSubtypesByLocale = LinkedHashMap<String, MutableList<InputMethodSubtype>>(100)
private val additionalSubtypes = mutableListOf<InputMethodSubtype>()
private val systemLocales = mutableListOf<Locale>()
private val systemSubtypes = mutableListOf<InputMethodSubtype>()

private const val SUBTYPE_SEPARATOR = ";"
private const val LOCALE_LAYOUT_SEPARATOR = ":"
private const val TAG = "SubtypeSettings"

@Suppress("deprecation") // it's deprecated, but no replacement for API < 24
// todo: subtypes should now have language tags -> use them for api >= 24
//  but only replace subtype-related usage, otherwise the api mess will be horrible
//  maybe rather return a locale instead of a string...
//   is this acceptable for performance? any place where there are many call to locale()?
//  see also InputMethodSubtypeCompatUtils
fun InputMethodSubtype.locale() = locale
