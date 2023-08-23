package org.dslul.openboard.inputmethod.latin.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.os.Build
import android.view.inputmethod.InputMethodSubtype
import androidx.core.app.LocaleManagerCompat
import androidx.core.content.edit
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.utils.AdditionalSubtypeUtils
import org.dslul.openboard.inputmethod.latin.utils.DeviceProtectedUtils
import org.dslul.openboard.inputmethod.latin.utils.SubtypeLocaleUtils
import org.xmlpull.v1.XmlPullParser

fun getEnabledSubtypes(): List<InputMethodSubtype> {
    require(initialized)
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

    if (subtype !in enabledSubtypes)
        enabledSubtypes.add(subtype) // todo: order? maybe sorted set? is it relevant? sorting also done in pref string, probably should match
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
//    if (subtypeString == prefs.getString(Settings.PREF_SELECTED_INPUT_STYLE, ""))
//        RichInputMethodManager.getInstance().switchToNextInputMethod(, true) // todo: needs to be handled by latinIME actually...

    if (subtype in enabledSubtypes)
        enabledSubtypes.remove(subtype)
}

// selected subtype is mostly managed by system, but sometimes this here is necessary
fun getSelectedSubtype(prefs: SharedPreferences): InputMethodSubtype {
    require(initialized)
    val subtypeString = prefs.getString(Settings.PREF_SELECTED_INPUT_STYLE, "")!!.split(LOCALE_LAYOUT_SEPARATOR)
    val subtype = enabledSubtypes.firstOrNull { subtypeString.first() == it.locale && subtypeString.last() == SubtypeLocaleUtils.getKeyboardLayoutSetName(it) }
    return subtype ?: enabledSubtypes.first()
}

fun setSelectedSubtype(prefs: SharedPreferences, subtype: InputMethodSubtype) {
    val subtypeString = subtype.prefString()
    if (prefs.getString(Settings.PREF_SELECTED_INPUT_STYLE, "") == subtypeString)
        return
    prefs.edit { putString(Settings.PREF_SELECTED_INPUT_STYLE, subtypeString) }
}

fun isAdditionalSubtype(subtype: InputMethodSubtype): Boolean {
    return subtype in additionalSubtypes // todo: maybe use a set?
}

fun updateAdditionalSubtypes(subtypes: Array<InputMethodSubtype>) {
    additionalSubtypes.clear()
    additionalSubtypes.addAll(subtypes)
}

fun init(context: Context) {
    if (initialized) return
    SubtypeLocaleUtils.init(context) // necessary to get the correct getKeyboardLayoutSetName
    loadResourceSubtypes(context.resources)
    loadAdditionalSubtypes(context)
    loadEnabledSubtypes(context)
    initialized = true
}

// requires loadResourceSubtypes to be called before
private fun getDefaultEnabledSubtypes(context: Context): List<InputMethodSubtype> {
    val localeList = LocaleManagerCompat.getSystemLocales(context)
    val locales = (0 until localeList.size()).mapNotNull { localeList[it] }

    val inputMethodSubtypes = locales.mapNotNull { locale ->
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
            val locale = xml.getAttributeValue(namespace, "imeSubtypeLocale")
            val languageTag = xml.getAttributeValue(namespace, "languageTag")
            val imeSubtypeMode = xml.getAttributeValue(namespace, "imeSubtypeMode")
            val imeSubtypeExtraValue = xml.getAttributeValue(namespace, "imeSubtypeExtraValue")
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
        require(subtypesForLocale != null) // todo: remove in the end, even weird things should never crash

        val subtype = subtypesForLocale.firstOrNull { SubtypeLocaleUtils.getKeyboardLayoutSetName(it) == localeAndLayout.last() }
            ?: additionalSubtypes.firstOrNull { it.locale == localeAndLayout.first() && SubtypeLocaleUtils.getKeyboardLayoutSetName(it) == localeAndLayout.last() }
        require(subtype != null) // todo: this will be null if additional subtype is removed!

        enabledSubtypes.add(subtype)
    }

    if (enabledSubtypes.isEmpty()) {
        enabledSubtypes.addAll(getDefaultEnabledSubtypes(context))
    }
}

private var initialized = false
private val enabledSubtypes = mutableListOf<InputMethodSubtype>()
private val resourceSubtypesByLocale = LinkedHashMap<String, MutableList<InputMethodSubtype>>(100)
private val additionalSubtypes = mutableListOf<InputMethodSubtype>()

private const val SUBTYPE_SEPARATOR = ";"
private const val LOCALE_LAYOUT_SEPARATOR = ":"
