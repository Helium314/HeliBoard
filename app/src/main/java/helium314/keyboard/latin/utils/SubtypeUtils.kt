package helium314.keyboard.latin.utils

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.view.inputmethod.InputMethodSubtype
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Constants.Separators
import helium314.keyboard.latin.common.Constants.Subtype.ExtraValue
import helium314.keyboard.latin.common.Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET
import helium314.keyboard.latin.common.LocaleUtils
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.utils.ScriptUtils.script
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

/** Workaround for SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale ignoring custom layout names */
// todo (later): this should be done properly and in SubtypeLocaleUtils
fun InputMethodSubtype.displayName(context: Context): CharSequence {
    val layoutName = SubtypeLocaleUtils.getMainLayoutName(this)
    if (LayoutUtilsCustom.isCustomLayout(layoutName))
        return "${LocaleUtils.getLocaleDisplayNameInSystemLocale(locale(), context)} (${LayoutUtilsCustom.getDisplayName(layoutName)})"
    return SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(this)
}

data class SettingsSubtype(val locale: Locale, val extraValue: String) {

    fun toPref() = locale.toLanguageTag() + Separators.SET + extraValue

    /** Creates an additional subtype from the SettingsSubtype.
     *  Resulting InputMethodSubtypes are equal if SettingsSubtypes are equal */
    fun toAdditionalSubtype(): InputMethodSubtype? {
        val asciiCapable = locale.script() == ScriptUtils.SCRIPT_LATIN
        val subtype = SubtypeUtilsAdditional.createAdditionalSubtype(locale, extraValue, asciiCapable, true)
        if (subtype.nameResId == SubtypeLocaleUtils.UNKNOWN_KEYBOARD_LAYOUT && !LayoutUtilsCustom.isCustomLayout(mainLayoutName() ?: "qwerty")) {
            // Skip unknown keyboard layout subtype. This may happen when predefined keyboard
            // layout has been removed.
            Log.w(SettingsSubtype::class.simpleName, "unknown additional subtype $this")
            return null
        }
        return subtype
    }

    fun mainLayoutName() = LayoutType.getMainLayoutFromExtraValue(extraValue)

    companion object {
        fun String.toSettingsSubtype() =
            SettingsSubtype(substringBefore(Separators.SET).constructLocale(), substringAfter(Separators.SET))

        /** Creates a SettingsSubtype from the given InputMethodSubtype.
         *  Will strip some extra values that are set when creating the InputMethodSubtype from SettingsSubtype */
        fun InputMethodSubtype.toSettingsSubtype(): SettingsSubtype {
            if (DebugFlags.DEBUG_ENABLED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && locale().toLanguageTag() == "und") {
                @Suppress("deprecation") // it's debug logging, better get all information
                Log.e(SettingsSubtype::class.simpleName, "unknown language, should not happen ${locale}, $languageTag, $extraValue, ${hashCode()}, $nameResId")
            }
            val filteredExtraValue = extraValue.split(",").filterNot {
                it == ExtraValue.ASCII_CAPABLE
                        || it == ExtraValue.EMOJI_CAPABLE
                        || it == ExtraValue.IS_ADDITIONAL_SUBTYPE
                // todo: this is in "old" additional subtypes, but where was it set?
                //  must have been by app in 2.3, but not any more?
                //  anyway, a. we can easily create it again, and b. it may contain "bad" characters messing up the extra value
                        || it.startsWith(ExtraValue.UNTRANSLATABLE_STRING_IN_SUBTYPE_NAME)
            }.joinToString(",")
            require(!filteredExtraValue.contains(Separators.SETS) && !filteredExtraValue.contains(Separators.SET))
                { "extra value contains not allowed characters $filteredExtraValue" }
            return SettingsSubtype(locale(), filteredExtraValue)
        }
    }
}
