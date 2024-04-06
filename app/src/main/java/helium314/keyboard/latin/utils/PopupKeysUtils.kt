// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.SharedPreferences
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.internal.KeySpecParser
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.PopupSet
import helium314.keyboard.keyboard.internal.keyboard_parser.rtlLabel

const val POPUP_KEYS_NUMBER = "popup_keys_number"
private const val POPUP_KEYS_LANGUAGE_PRIORITY = "popup_keys_language_priority"
const val POPUP_KEYS_LAYOUT = "popup_keys_layout"
private const val POPUP_KEYS_SYMBOLS = "popup_keys_symbols"
private const val POPUP_KEYS_LANGUAGE = "popup_keys_language"
const val POPUP_KEYS_LABEL_DEFAULT = "$POPUP_KEYS_NUMBER,true;$POPUP_KEYS_LANGUAGE_PRIORITY,false;$POPUP_KEYS_LAYOUT,true;$POPUP_KEYS_SYMBOLS,true;$POPUP_KEYS_LANGUAGE,false"
const val POPUP_KEYS_ORDER_DEFAULT = "$POPUP_KEYS_LANGUAGE_PRIORITY,true;$POPUP_KEYS_NUMBER,true;$POPUP_KEYS_SYMBOLS,true;$POPUP_KEYS_LAYOUT,true;$POPUP_KEYS_LANGUAGE,true"

private val allPopupKeyTypes = listOf(POPUP_KEYS_NUMBER, POPUP_KEYS_LAYOUT, POPUP_KEYS_SYMBOLS, POPUP_KEYS_LANGUAGE, POPUP_KEYS_LANGUAGE_PRIORITY)

fun createPopupKeysArray(popupSet: PopupSet<*>?, params: KeyboardParams, label: String): Array<String>? {
    // often PopupKeys are empty, so we want to avoid unnecessarily creating sets
    val popupKeysDelegate = lazy { mutableSetOf<String>() }
    val popupKeys by popupKeysDelegate
    val types = if (params.mId.isAlphabetKeyboard) params.mPopupKeyTypes else allPopupKeyTypes
    types.forEach { type ->
        when (type) {
            POPUP_KEYS_NUMBER -> params.mLocaleKeyboardInfos.getNumberLabel(popupSet?.numberIndex)?.let { popupKeys.add(it) }
            POPUP_KEYS_LAYOUT -> popupSet?.getPopupKeyLabels(params)?.let { popupKeys.addAll(it) }
            POPUP_KEYS_SYMBOLS -> popupSet?.symbol?.let { popupKeys.add(it) }
            POPUP_KEYS_LANGUAGE -> params.mLocaleKeyboardInfos.getPopupKeys(label)?.let { popupKeys.addAll(it) }
            POPUP_KEYS_LANGUAGE_PRIORITY -> params.mLocaleKeyboardInfos.getPriorityPopupKeys(label)?.let { popupKeys.addAll(it) }
        }
    }
    if (!popupKeysDelegate.isInitialized() || popupKeys.isEmpty())
        return null
    val fco = popupKeys.firstOrNull { it.startsWith(Key.POPUP_KEYS_FIXED_COLUMN_ORDER) }
    if (fco != null && fco.substringAfter(Key.POPUP_KEYS_FIXED_COLUMN_ORDER).toIntOrNull() != popupKeys.size - 1) {
        val fcoExpected = popupKeys.size - popupKeys.count { it.startsWith("!") && it.endsWith("!") } - 1
        if (fco.substringAfter(Key.POPUP_KEYS_FIXED_COLUMN_ORDER).toIntOrNull() != fcoExpected)
            popupKeys.remove(fco) // maybe rather adjust the number instead of remove?
    }
    if (popupKeys.size > 1 && (label == "(" || label == ")")) { // add fixed column order for that case (typically other variants of brackets / parentheses
        // not really fast, but no other way to add first in a LinkedHashSet
        val tmp = popupKeys.toList()
        popupKeys.clear()
        popupKeys.add("${Key.POPUP_KEYS_FIXED_COLUMN_ORDER}${tmp.size}")
        popupKeys.addAll(tmp)
    }
    // autoColumnOrder should be fine

    val array = popupKeys.toTypedArray()
    for (i in array.indices) {
        array[i] = transformLabel(array[i], params)
    }
    return array
}

fun getHintLabel(popupSet: PopupSet<*>?, params: KeyboardParams, label: String): String? {
    var hintLabel: String? = null
    for (type in params.mPopupKeyLabelSources) {
        when (type) {
            POPUP_KEYS_NUMBER -> params.mLocaleKeyboardInfos.getNumberLabel(popupSet?.numberIndex)?.let { hintLabel = it }
            POPUP_KEYS_LAYOUT -> popupSet?.getPopupKeyLabels(params)?.let { hintLabel = it.firstOrNull() }
            POPUP_KEYS_SYMBOLS -> popupSet?.symbol?.let { hintLabel = it }
            POPUP_KEYS_LANGUAGE -> params.mLocaleKeyboardInfos.getPopupKeys(label)?.let { hintLabel = it.firstOrNull() }
            POPUP_KEYS_LANGUAGE_PRIORITY -> params.mLocaleKeyboardInfos.getPriorityPopupKeys(label)?.let { hintLabel = it.firstOrNull() }
        }
        if (hintLabel != null) break
    }

    return hintLabel?.let { KeySpecParser.getLabel(transformLabel(it, params)) }
        // avoid e.g. !autoColumnOrder! as label
        //  this will avoid having labels on comma and period keys
        ?.takeIf { !it.startsWith("!") || it.count { it == '!' } != 2 } // excluding the special labels
}

private fun transformLabel(label: String, params: KeyboardParams): String =
    if (label.startsWith("$$$")) { // currency keys, todo: handing is similar to textKeyData, could it be merged?
        if (label == "$$$") {
            if (params.mId.passwordInput()) "$"
            else params.mLocaleKeyboardInfos.currencyKey.first
        } else {
            val index = label.substringAfter("$$$").toIntOrNull()
            if (index != null && index in 1..5)
                params.mLocaleKeyboardInfos.currencyKey.second[index - 1]
            else label
        }
    } else if (params.mId.mSubtype.isRtlSubtype) {
        label.rtlLabel(params)
    } else label

/** returns a list of enabled popup keys for pref [key] */
fun getEnabledPopupKeys(prefs: SharedPreferences, key: String, defaultSetting: String): List<String> {
    return prefs.getString(key, defaultSetting)?.split(";")?.mapNotNull {
        val split = it.split(",")
        if (split.last() == "true") split.first() else null
    } ?: emptyList()
}
