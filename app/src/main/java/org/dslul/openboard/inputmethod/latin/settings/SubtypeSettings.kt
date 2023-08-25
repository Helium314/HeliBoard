package org.dslul.openboard.inputmethod.latin.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.os.Build
import android.view.inputmethod.InputMethodSubtype
import androidx.core.app.LocaleManagerCompat
import androidx.core.content.edit
import org.dslul.openboard.inputmethod.keyboard.KeyboardSwitcher
import org.dslul.openboard.inputmethod.latin.BuildConfig
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.RichInputMethodManager
import org.dslul.openboard.inputmethod.latin.utils.AdditionalSubtypeUtils
import org.dslul.openboard.inputmethod.latin.utils.DeviceProtectedUtils
import org.dslul.openboard.inputmethod.latin.utils.SubtypeLocaleUtils
import org.xmlpull.v1.XmlPullParser
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashMap

/** @return enabled subtypes. If no subtypes are enabled, but a contextForFallback is provided,
 *  subtypes for system locales will be returned, or en_US if none found. */
fun getEnabledSubtypes(prefs: SharedPreferences, fallback: Boolean = false): List<InputMethodSubtype> {
    require(initialized)
    if (prefs.getBoolean(Settings.PREF_USE_SYSTEM_LOCALES, true))
        return getDefaultEnabledSubtypes()
    return getExplicitlyEnabledSubtypes(fallback)
}

fun getExplicitlyEnabledSubtypes(fallback: Boolean = false): List<InputMethodSubtype> {
    require(initialized)
    if (fallback && enabledSubtypes.isEmpty())
        return getDefaultEnabledSubtypes()
    return enabledSubtypes
}

fun getAllAvailableSubtypes(): List<InputMethodSubtype> {
    require(initialized)
    return resourceSubtypesByLocale.values.flatten() + additionalSubtypes
}

fun addEnabledSubtype(prefs: SharedPreferences, subtype: InputMethodSubtype) {
    require(initialized)
    val subtypeString = subtype.prefString()
    val oldSubtypeStrings = prefs.getString(Settings.PREF_ENABLED_INPUT_STYLES, "")!!.split(SUBTYPE_SEPARATOR)
    val newString = (oldSubtypeStrings + subtypeString).filter { it.isNotBlank() }.toSortedSet().joinToString(SUBTYPE_SEPARATOR)
    prefs.edit { putString(Settings.PREF_ENABLED_INPUT_STYLES, newString) }

    if (subtype !in enabledSubtypes) {
        enabledSubtypes.add(subtype)
        enabledSubtypes.sortBy { it.locale } // for consistent order
    }
}

/** returns whether subtype was actually removed, does not remove last subtype */
fun removeEnabledSubtype(prefs: SharedPreferences, subtype: InputMethodSubtype) {
    require(initialized)
    val subtypeString = subtype.prefString()
    val oldSubtypeString = prefs.getString(Settings.PREF_ENABLED_INPUT_STYLES, "")!!
    val newString = (oldSubtypeString.split(SUBTYPE_SEPARATOR) - subtypeString).joinToString(SUBTYPE_SEPARATOR)
    if (newString == oldSubtypeString)
        return // already removed
    prefs.edit { putString(Settings.PREF_ENABLED_INPUT_STYLES, newString) }
    if (subtypeString == prefs.getString(Settings.PREF_SELECTED_INPUT_STYLE, "")) {
        // switch subtype if the currently used one has been disabled
        val nextSubtype = RichInputMethodManager.getInstance().getNextSubtypeInThisIme(true)
        if (subtypeString == nextSubtype?.prefString())
            KeyboardSwitcher.getInstance().switchToSubtype(getDefaultEnabledSubtypes().first())
        else
            KeyboardSwitcher.getInstance().switchToSubtype(nextSubtype)
    }
    enabledSubtypes.remove(subtype)
}

fun getSelectedSubtype(prefs: SharedPreferences): InputMethodSubtype {
    require(initialized)
    val subtypeString = prefs.getString(Settings.PREF_SELECTED_INPUT_STYLE, "")!!.split(LOCALE_LAYOUT_SEPARATOR)
    val subtype = enabledSubtypes.firstOrNull { subtypeString.first() == it.locale && subtypeString.last() == SubtypeLocaleUtils.getKeyboardLayoutSetName(it) }
        ?: enabledSubtypes.firstOrNull()
    if (subtype == null) {
        val defaultSubtypes = getDefaultEnabledSubtypes()
        return defaultSubtypes.firstOrNull { subtypeString.first() == it.locale && subtypeString.last() == SubtypeLocaleUtils.getKeyboardLayoutSetName(it) }
            ?: defaultSubtypes.firstOrNull { subtypeString.first().substringBefore("_") == it.locale.substringBefore("_") && subtypeString.last() == SubtypeLocaleUtils.getKeyboardLayoutSetName(it) }
            ?: defaultSubtypes.first()
    }
    return subtype
}

fun setSelectedSubtype(prefs: SharedPreferences, subtype: InputMethodSubtype) {
    val subtypeString = subtype.prefString()
    if (subtype.locale.isEmpty() || prefs.getString(Settings.PREF_SELECTED_INPUT_STYLE, "") == subtypeString)
        return
    prefs.edit { putString(Settings.PREF_SELECTED_INPUT_STYLE, subtypeString) }
}

fun isAdditionalSubtype(subtype: InputMethodSubtype): Boolean {
    return subtype in additionalSubtypes
}

fun updateAdditionalSubtypes(subtypes: Array<InputMethodSubtype>) {
    additionalSubtypes.clear()
    additionalSubtypes.addAll(subtypes)
}

fun reloadSystemLocales(context: Context) {
    systemLocales.clear()
    val localeList = LocaleManagerCompat.getSystemLocales(context)
    (0 until localeList.size()).forEach {
        val locale = localeList[it]
        if (locale != null) systemLocales.add(locale)
    }
}

fun getSystemLocales(): List<Locale> {
    require(initialized)
    return systemLocales
}

fun init(context: Context) {
    if (initialized) return
    SubtypeLocaleUtils.init(context) // necessary to get the correct getKeyboardLayoutSetName

    // necessary to set system locales at start, because for some weird reason (bug?)
    // LocaleManagerCompat.getSystemLocales(context) sometimes doesn't return all system locales
    reloadSystemLocales(context)

    loadResourceSubtypes(context.resources)
    loadAdditionalSubtypes(context)
    loadEnabledSubtypes(context)
    initialized = true
}

private fun getDefaultEnabledSubtypes(): List<InputMethodSubtype> {
    val inputMethodSubtypes = systemLocales.mapNotNull { locale ->
        val localeString = locale.toString()
        val subtypes = resourceSubtypesByLocale[localeString]
            ?: resourceSubtypesByLocale[localeString.substringBefore("_")] // fall back to language match
        subtypes?.firstOrNull() // todo: maybe set default for some languages with multiple resource subtypes?
    }
    if (inputMethodSubtypes.isEmpty())
        // hardcoded fallback for weird cases
        return listOf(resourceSubtypesByLocale["en_US"]!!.first())
    return inputMethodSubtypes
}

private fun InputMethodSubtype.prefString() =
    locale + LOCALE_LAYOUT_SEPARATOR + SubtypeLocaleUtils.getKeyboardLayoutSetName(this)

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

// requires loadResourceSubtypes to be called before
private fun loadEnabledSubtypes(context: Context) {
    val prefs = DeviceProtectedUtils.getSharedPreferences(context)
    val subtypeStrings = prefs.getString(Settings.PREF_ENABLED_INPUT_STYLES, "")!!
        .split(SUBTYPE_SEPARATOR).filter { it.isNotEmpty() }.map { it.split(LOCALE_LAYOUT_SEPARATOR) }

    for (localeAndLayout in subtypeStrings) {
        require(localeAndLayout.size == 2)
        val subtypesForLocale = resourceSubtypesByLocale[localeAndLayout.first()]
        if (BuildConfig.DEBUG) // should not happen, but should not crash for normal user
            require(subtypesForLocale != null)
        else if (subtypesForLocale == null)
            continue

        val subtype = subtypesForLocale.firstOrNull { SubtypeLocaleUtils.getKeyboardLayoutSetName(it) == localeAndLayout.last() }
            ?: additionalSubtypes.firstOrNull { it.locale == localeAndLayout.first() && SubtypeLocaleUtils.getKeyboardLayoutSetName(it) == localeAndLayout.last() }
        if (BuildConfig.DEBUG) // should not happen, but should not crash for normal user
            require(subtype != null)
        else if (subtype == null)
            continue

        enabledSubtypes.add(subtype)
    }
}

private var initialized = false
private val enabledSubtypes = mutableListOf<InputMethodSubtype>()
private val resourceSubtypesByLocale = LinkedHashMap<String, MutableList<InputMethodSubtype>>(100)
private val additionalSubtypes = mutableListOf<InputMethodSubtype>()
private val systemLocales = mutableListOf<Locale>()

private const val SUBTYPE_SEPARATOR = ";"
private const val LOCALE_LAYOUT_SEPARATOR = ":"
