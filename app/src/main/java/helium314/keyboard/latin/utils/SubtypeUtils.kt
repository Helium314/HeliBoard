package helium314.keyboard.latin.utils

import android.content.SharedPreferences
import android.content.res.Resources
import android.os.Build
import android.view.inputmethod.InputMethodSubtype
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Constants.Separators
import helium314.keyboard.latin.common.Constants.Subtype.ExtraValue
import helium314.keyboard.latin.common.Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsSubtype.Companion.getExtraValueOf
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

fun InputMethodSubtype.locale(): Locale {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        if (languageTag.isNotEmpty())
            return languageTag.constructLocale()
    }
    @Suppress("deprecation") return locale.constructLocale()
}

fun InputMethodSubtype.mainLayoutName(): String? {
    val map = LayoutType.getLayoutMap(getExtraValueOf(KEYBOARD_LAYOUT_SET) ?: "")
    return map[LayoutType.MAIN]
}

fun InputMethodSubtype.mainLayoutNameOrQwerty(): String = mainLayoutName() ?: SubtypeLocaleUtils.QWERTY

fun getResourceSubtypes(resources: Resources): List<InputMethodSubtype> {
    val subtypes = mutableListOf<InputMethodSubtype>()
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
            subtypes.add(b.build())
        }
        eventType = xml.next()
    }
    return subtypes
}

fun getHasLocalizedNumberRow(subtype: InputMethodSubtype, prefs: SharedPreferences): Boolean =
    subtype.getExtraValueOf(ExtraValue.LOCALIZED_NUMBER_ROW)?.toBoolean()
        ?: prefs.getBoolean(Settings.PREF_LOCALIZED_NUMBER_ROW, Defaults.PREF_LOCALIZED_NUMBER_ROW)

fun getPopupKeyTypes(subtype: InputMethodSubtype, prefs: SharedPreferences): List<String> {
    val string = subtype.getExtraValueOf(ExtraValue.POPUP_ORDER)
        ?: prefs.getString(Settings.PREF_POPUP_KEYS_ORDER, Defaults.PREF_POPUP_KEYS_ORDER)!!
    return getEnabledPopupKeys(string)
}

fun getPopupKeyLabelSources(subtype: InputMethodSubtype, prefs: SharedPreferences): List<String> {
    val string = subtype.getExtraValueOf(ExtraValue.HINT_ORDER)
        ?: prefs.getString(Settings.PREF_POPUP_KEYS_LABELS_ORDER, Defaults.PREF_POPUP_KEYS_LABELS_ORDER)!!
    return getEnabledPopupKeys(string)
}

fun getMoreKeys(subtype: InputMethodSubtype, prefs: SharedPreferences): String =
    subtype.getExtraValueOf(ExtraValue.MORE_POPUPS)
        ?: prefs.getString(Settings.PREF_MORE_POPUP_KEYS, Defaults.PREF_MORE_POPUP_KEYS)!!

fun getSecondaryLocales(extraValues: String): List<Locale> =
    extraValues.getExtraValueOf(ExtraValue.SECONDARY_LOCALES)
        ?.split(Separators.KV)?.map { it.constructLocale() }.orEmpty()
