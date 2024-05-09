/*
 * Copyright (C) 2021 Patrick Goldinger
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package helium314.keyboard.keyboard.internal.keyboard_parser.floris

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode.checkAndConvertCode
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyLabel.convertFlorisLabel
import helium314.keyboard.keyboard.internal.keyboard_parser.rtlLabel
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.settings.Settings

// taken from FlorisBoard, small modifications (see also KeyData)
//  internal keys removed (currently no plan to support them)
//  added String.toTextKey
//  currency key handling (see todo below...)

/**
 * Interface describing a basic key which can carry a character, an emoji, a special function etc. while being as
 * abstract as possible.
 *
 * @property type The type of the key.
 * @property code The Unicode code point of this key, or a special code from [KeyCode].
 * @property label The label of the key. This should always be a representative string for [code].
 * @property groupId The group which this key belongs to (currently only allows [GROUP_DEFAULT]).
 * @property popup The popups for ths key. Can also dynamically be provided via popup extensions.
 */
sealed interface KeyData : AbstractKeyData {
    val type: KeyType?
    val code: Int
    val label: String
    val groupId: Int
    val popup: PopupSet<out AbstractKeyData> // not nullable because can't add number otherwise
    val width: Float // in percent of keyboard width, 0 is default (depends on key), -1 is fill (like space bar)
    val labelFlags: Int

    fun copy(newType: KeyType? = type, newCode: Int = code, newLabel: String = label, newGroupId: Int = groupId,
             newPopup: PopupSet<out AbstractKeyData> = popup, newWidth: Float = width, newLabelFlags: Int = labelFlags): KeyData

    // groups (currently) not supported
    companion object {
        /**
         * Constant for the default group. If not otherwise specified, any key is automatically
         * assigned to this group.
         */
        const val GROUP_DEFAULT: Int = 0

        /**
         * Constant for the Left modifier key group. Any key belonging to this group will get the
         * popups specified for the comma key.
         */
        const val GROUP_COMMA: Int = 1

        /**
         * Constant for the right modifier key group. Any key belonging to this group will get the
         * popups specified for the period key.
         */
        const val GROUP_PERIOD: Int = 2

        /**
         * Constant for the enter modifier key group. Any key belonging to this group will get the
         * popups specified for "~enter" in the popup mapping.
         */
        const val GROUP_ENTER: Int = 3

        /**
         * Constant for the enter modifier key group. Any key belonging to this group will get the
         * popups specified for "~kana" in the popup mapping.
         */
        const val GROUP_KANA: Int = 97

        private fun getToSymbolLabel(params: KeyboardParams) =
            if (params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS || params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED)
                params.mLocaleKeyboardInfos.labelAlphabet
            else params.mLocaleKeyboardInfos.labelSymbol

        private fun getShiftLabel(params: KeyboardParams): String {
            val elementId = params.mId.mElementId
            if (elementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED)
                return params.mLocaleKeyboardInfos.labelSymbol
            if (elementId == KeyboardId.ELEMENT_SYMBOLS)
                return params.mLocaleKeyboardInfos.getShiftSymbolLabel(Settings.getInstance().isTablet)
            if (elementId == KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED || elementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED
                || elementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED || elementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED)
                return "!icon/shift_key_shifted"
            return "!icon/shift_key"
        }

        private fun getPeriodLabel(params: KeyboardParams): String {
            if (params.mId.isNumberLayout) return "."
            if (params.mId.isAlphabetKeyboard || params.mId.locale.language in listOf("ar", "fa")) // todo: this exception is not so great...
                return params.mLocaleKeyboardInfos.labelPeriod
            return "."
        }

        private fun getCommaLabel(params: KeyboardParams): String {
            if (params.mId.mMode == KeyboardId.MODE_URL && params.mId.isAlphabetKeyboard)
                return "/"
            if (params.mId.mMode == KeyboardId.MODE_EMAIL && params.mId.isAlphabetKeyboard)
                return "\\@"
            if (params.mId.isNumberLayout)
                return ","
            return params.mLocaleKeyboardInfos.labelComma
        }

        private fun getSpaceLabel(params: KeyboardParams): String =
            if (params.mId.mElementId <= KeyboardId.ELEMENT_SYMBOLS_SHIFTED)
                "!icon/space_key|!code/key_space"
            else "!icon/space_key_for_number_layout|!code/key_space"

        private fun getCommaPopupKeys(params: KeyboardParams): List<String> {
            val keys = mutableListOf<String>()
            if (!params.mId.mDeviceLocked)
                keys.add("!icon/clipboard_normal_key|!code/key_clipboard")
            if (!params.mId.mEmojiKeyEnabled && !params.mId.isNumberLayout)
                keys.add("!icon/emoji_normal_key|!code/key_emoji")
            if (!params.mId.mLanguageSwitchKeyEnabled)
                keys.add("!icon/language_switch_key|!code/key_language_switch")
            if (!params.mId.mOneHandedModeEnabled)
                keys.add("!icon/start_onehanded_mode_key|!code/key_start_onehanded")
            if (!params.mId.mDeviceLocked)
                keys.add("!icon/settings_key|!code/key_settings")
            return keys
        }

        private fun getPunctuationPopupKeys(params: KeyboardParams): List<String> {
            if (params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS || params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED)
                return listOf("…")
            if (params.mId.isNumberLayout)
                return listOf(":", "…", ";", "∞", "π", "√", "°", "^")
            val popupKeys = params.mLocaleKeyboardInfos.getPopupKeys("punctuation")!!.toMutableList()
            if (params.mId.mSubtype.isRtlSubtype) {
                for (i in popupKeys.indices)
                    popupKeys[i] = popupKeys[i].rtlLabel(params) // for parentheses
            }
            if (Settings.getInstance().isTablet && popupKeys.contains("!") && popupKeys.contains("?")) {
                // remove ! and ? keys and reduce number in autoColumnOrder
                // this makes use of removal of empty popupKeys in PopupKeySpec.insertAdditionalPopupKeys
                popupKeys[popupKeys.indexOf("!")] = ""
                popupKeys[popupKeys.indexOf("?")] = ""
                val columns = popupKeys[0].substringAfter(Key.POPUP_KEYS_AUTO_COLUMN_ORDER).toIntOrNull()
                if (columns != null)
                    popupKeys[0] = "${Key.POPUP_KEYS_AUTO_COLUMN_ORDER}${columns - 1}"
            }
            return popupKeys
        }
    }

    // make it non-nullable for simplicity, and to reflect current implementations
    override fun compute(params: KeyboardParams): KeyData {
        val newLabel = label.convertFlorisLabel()
        val newCode = code.checkAndConvertCode()

        // resolve currency keys
        // todo: see whether we can handle this in toKeyParams (or is there a reason it's here instead? if so, write it down)
        if (newLabel.startsWith("$$$") || newCode in KeyCode.Spec.CURRENCY) {
            val currencyKey = params.mLocaleKeyboardInfos.currencyKey
            val currencyCodeAsString = if (newCode in KeyCode.Spec.CURRENCY) {
                when (newCode) {
                    KeyCode.CURRENCY_SLOT_1 -> "|" + currencyKey.first
                    KeyCode.CURRENCY_SLOT_2 -> "|" + currencyKey.second[0]
                    KeyCode.CURRENCY_SLOT_3 -> "|" + currencyKey.second[1]
                    KeyCode.CURRENCY_SLOT_4 -> "|" + currencyKey.second[2]
                    KeyCode.CURRENCY_SLOT_5 -> "|" + currencyKey.second[3]
                    KeyCode.CURRENCY_SLOT_6 -> "|" + currencyKey.second[4]
                    else -> ""
                }
            } else ""
            if (newLabel == "$$$") {
                val finalLabel = currencyKey.first + currencyCodeAsString
                // the flag is to match old parser, but why is it there for main currency key and not for others?
                return TextKeyData(type, KeyCode.UNSPECIFIED, finalLabel, groupId,
                    popup.merge(SimplePopups(currencyKey.second)), width, labelFlags or Key.LABEL_FLAGS_FOLLOW_KEY_LETTER_RATIO)
            }
            val n = newLabel.substringAfter("$$$").toIntOrNull()
            if (n != null && n <= 5 && n > 0) {
                val finalLabel = currencyKey.second[n - 1] + currencyCodeAsString
                return copy(newCode = KeyCode.UNSPECIFIED, newLabel = finalLabel)
            }
        }
        if (newCode != code || newLabel != label)
            return copy(newCode = newCode, newLabel = newLabel)
        return this
    }


    fun isSpaceKey(): Boolean {
        return type == KeyType.CHARACTER && (code == Constants.CODE_SPACE || code == KeyCode.CJK_SPACE
                || code == KeyCode.ZWNJ || code == KeyCode.KESHIDA)
    }

    /** this expects that codes and labels are already converted from FlorisBoard values, usually through compute */
    // todo: width in units of keyboard width, or in percent? (currently the plan was percent, but actually fractions are used)
    fun toKeyParams(params: KeyboardParams, additionalLabelFlags: Int = 0): Key.KeyParams {
        // todo: remove checks here, do only when reading json layouts
        // numeric keys are assigned a higher width in number layouts (todo: not true any more, also maybe they could have width -1 instead?)
        if (type == KeyType.PLACEHOLDER) return Key.KeyParams.newSpacer(params, width)
        require(groupId <= GROUP_ENTER) { "only groups up to GROUP_ENTER are supported" }
        require(code != KeyCode.UNSPECIFIED || label.isNotEmpty()) { "key has no code and no label" }
        val actualWidth = if (width == 0f) getDefaultWidth(params) else width

        val newLabel = processLabel(params)
        val newCode = processCode()
        val newLabelFlags = labelFlags or getAdditionalLabelFlags(params)
        val newPopupKeys = popup.merge(getAdditionalPopupKeys(params))

        val background = when (type) {
            KeyType.CHARACTER, KeyType.NUMERIC -> Key.BACKGROUND_TYPE_NORMAL
            KeyType.FUNCTION, KeyType.MODIFIER, KeyType.SYSTEM_GUI -> Key.BACKGROUND_TYPE_FUNCTIONAL
            KeyType.PLACEHOLDER, KeyType.UNSPECIFIED -> Key.BACKGROUND_TYPE_EMPTY
            KeyType.NAVIGATION -> Key.BACKGROUND_TYPE_SPACEBAR
            KeyType.ENTER_EDITING -> Key.BACKGROUND_TYPE_ACTION
            KeyType.LOCK -> determineShiftBackground(params)
            null -> defaultBackground(params)
        }

        return if (newCode == KeyCode.UNSPECIFIED || newCode == KeyCode.MULTIPLE_CODE_POINTS) {
            // code will be determined from label if possible (i.e. label is single code point)
            // but also longer labels should work without issues, also for MultiTextKeyData
            if (this is MultiTextKeyData) {
                val outputText = String(codePoints, 0, codePoints.size)
                Key.KeyParams(
                    "$newLabel|$outputText",
                    newCode,
                    params,
                    actualWidth,
                    newLabelFlags or additionalLabelFlags,
                    background,
                    newPopupKeys,
                )
            } else {
                Key.KeyParams(
                    newLabel.rtlLabel(params), // todo (when supported): convert special labels to keySpec
                    params,
                    actualWidth,
                    newLabelFlags or additionalLabelFlags,
                    background,
                    newPopupKeys,
                )
            }
        } else {
            Key.KeyParams(
                newLabel.ifEmpty { StringUtils.newSingleCodePointString(newCode) },
                newCode,
                params,
                actualWidth,
                newLabelFlags or additionalLabelFlags,
                background,
                newPopupKeys,
            )
        }
    }

    private fun defaultBackground(params: KeyboardParams): Int {
        // functional keys
        when (label) { // or use code?
            KeyLabel.SYMBOL_ALPHA, KeyLabel.SYMBOL, KeyLabel.ALPHA, KeyLabel.COMMA, KeyLabel.PERIOD, KeyLabel.DELETE,
            KeyLabel.EMOJI, KeyLabel.COM, KeyLabel.EMOJI_COM, KeyLabel.LANGUAGE_SWITCH, KeyLabel.NUMPAD -> return Key.BACKGROUND_TYPE_FUNCTIONAL
            KeyLabel.SPACE, KeyLabel.ZWNJ -> return Key.BACKGROUND_TYPE_SPACEBAR
            KeyLabel.ACTION -> return Key.BACKGROUND_TYPE_ACTION
            // todo (later): possibly the whole stickyOn/Off stuff can be removed, currently it should only have a very slight effect in holo
            //  but iirc there is some attempt in reviving the sticky thing, right?
            KeyLabel.SHIFT -> return determineShiftBackground(params)
        }
        // todo (later): this is more like a workaround, should be improved
        if (params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED && (groupId == GROUP_COMMA || groupId == GROUP_PERIOD))
            return Key.BACKGROUND_TYPE_FUNCTIONAL
        if (type == KeyType.PLACEHOLDER) return Key.BACKGROUND_TYPE_EMPTY
        return Key.BACKGROUND_TYPE_NORMAL
    }

    private fun determineShiftBackground(params: KeyboardParams): Int {
        return if (params.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED
            || params.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED) Key.BACKGROUND_TYPE_STICKY_ON
        else Key.BACKGROUND_TYPE_STICKY_OFF
    }

    // todo: only public for lazy workaround, make private again
    fun getDefaultWidth(params: KeyboardParams): Float {
        return if (label == KeyLabel.SPACE && params.mId.isAlphaOrSymbolKeyboard) -1f
        else if (type == KeyType.NUMERIC && params.mId.isNumberLayout) 0.17f // todo (later) consider making this -1?
        else params.mDefaultKeyWidth
    }

    // todo (later): encoding the code in the label should not be done
    private fun processLabel(params: KeyboardParams): String {
        return when (label) {
            KeyLabel.SYMBOL_ALPHA -> if (params.mId.isAlphabetKeyboard) getToSymbolLabel(params) else params.mLocaleKeyboardInfos.labelAlphabet
            KeyLabel.SYMBOL -> getToSymbolLabel(params)
            KeyLabel.ALPHA -> params.mLocaleKeyboardInfos.labelAlphabet
            KeyLabel.COMMA -> getCommaLabel(params)
            KeyLabel.PERIOD -> getPeriodLabel(params)
            KeyLabel.SPACE -> getSpaceLabel(params)
//            KeyLabel.ACTION -> "${getActionKeyLabel(params)}|${getActionKeyCode(params)}"
            KeyLabel.DELETE -> "!icon/delete_key|!code/key_delete"
            KeyLabel.SHIFT -> "${getShiftLabel(params)}|!code/key_shift"
            KeyLabel.EMOJI -> "!icon/emoji_normal_key|!code/key_emoji"
            KeyLabel.EMOJI_COM -> {
                if (params.mId.mMode == KeyboardId.MODE_URL || params.mId.mMode == KeyboardId.MODE_EMAIL) ".com"
                else "!icon/emoji_normal_key|!code/key_emoji"
            } // todo...
            // todo (later): label and popupKeys for .com could be in localeKeyTexts, handled similar to currency key
            //  better not in the text files, because it should be handled per country
            KeyLabel.COM -> ".com"
            KeyLabel.LANGUAGE_SWITCH -> "!icon/language_switch_key|!code/key_language_switch"
            KeyLabel.NUMPAD -> "!icon/numpad_key|!code/key_numpad"
            KeyLabel.ZWNJ -> "!icon/zwnj_key|\u200C"
            else -> label
        }
    }

    private fun processCode(): Int {
        if (code != KeyCode.UNSPECIFIED) return code
        return when (label) {
            KeyLabel.SYMBOL_ALPHA -> KeyCode.ALPHA_SYMBOL
            KeyLabel.SYMBOL -> KeyCode.SYMBOL
            KeyLabel.ALPHA -> KeyCode.ALPHA
            else -> code
        }
    }

    private fun getAdditionalLabelFlags(params: KeyboardParams): Int {
        return when (label) {
            KeyLabel.ALPHA, KeyLabel.SYMBOL_ALPHA, KeyLabel.SYMBOL -> Key.LABEL_FLAGS_PRESERVE_CASE or Key.LABEL_FLAGS_FOLLOW_FUNCTIONAL_TEXT_COLOR
            KeyLabel.PERIOD, KeyLabel.COMMA -> Key.LABEL_FLAGS_HAS_POPUP_HINT // todo: period also has defaultLabelFlags -> when is this relevant?
            KeyLabel.ACTION -> {
                Key.LABEL_FLAGS_PRESERVE_CASE or Key.LABEL_FLAGS_AUTO_X_SCALE or
                        Key.LABEL_FLAGS_FOLLOW_KEY_LABEL_RATIO or Key.LABEL_FLAGS_FOLLOW_FUNCTIONAL_TEXT_COLOR or
                        Key.LABEL_FLAGS_HAS_POPUP_HINT or KeyboardTheme.getThemeActionAndEmojiKeyLabelFlags(params.mThemeId)
            }
            KeyLabel.SPACE -> if (params.mId.isNumberLayout) Key.LABEL_FLAGS_ALIGN_ICON_TO_BOTTOM else 0
            KeyLabel.SHIFT -> Key.LABEL_FLAGS_PRESERVE_CASE or if (!params.mId.isAlphabetKeyboard) Key.LABEL_FLAGS_FOLLOW_FUNCTIONAL_TEXT_COLOR else 0
            KeyLabel.EMOJI -> KeyboardTheme.getThemeActionAndEmojiKeyLabelFlags(params.mThemeId)
            KeyLabel.EMOJI_COM -> {
                if (params.mId.mMode == KeyboardId.MODE_URL || params.mId.mMode == KeyboardId.MODE_EMAIL) {
                    Key.LABEL_FLAGS_AUTO_X_SCALE or Key.LABEL_FLAGS_FONT_NORMAL or Key.LABEL_FLAGS_HAS_POPUP_HINT or Key.LABEL_FLAGS_PRESERVE_CASE
                } else KeyboardTheme.getThemeActionAndEmojiKeyLabelFlags(params.mThemeId)
            }
            KeyLabel.COM -> Key.LABEL_FLAGS_AUTO_X_SCALE or Key.LABEL_FLAGS_FONT_NORMAL or Key.LABEL_FLAGS_HAS_POPUP_HINT or Key.LABEL_FLAGS_PRESERVE_CASE
            KeyLabel.ZWNJ -> Key.LABEL_FLAGS_HAS_POPUP_HINT
            else -> 0
        }
    }

    // todo: popup keys should be merged with existing keys!
    private fun getAdditionalPopupKeys(params: KeyboardParams): PopupSet<AbstractKeyData>? {
        if (groupId == GROUP_COMMA) return SimplePopups(getCommaPopupKeys(params))
        if (groupId == GROUP_PERIOD) return SimplePopups(getPunctuationPopupKeys(params))
        return when (label) {
            KeyLabel.COMMA -> SimplePopups(getCommaPopupKeys(params))
            KeyLabel.PERIOD -> SimplePopups(getPunctuationPopupKeys(params))
//            KeyLabel.ACTION -> getActionKeyPopupKeys(params)?.let { SimplePopups(it) }
            KeyLabel.SHIFT -> {
                if (params.mId.isAlphabetKeyboard) SimplePopups(
                    listOf(
                        "!noPanelAutoPopupKey!",
                        " |!code/key_capslock"
                    )
                ) else null // why the alphabet popup keys actually?
            }
            KeyLabel.EMOJI_COM -> {
                if (params.mId.mMode == KeyboardId.MODE_URL || params.mId.mMode == KeyboardId.MODE_EMAIL)
                    SimplePopups(listOf(Key.POPUP_KEYS_HAS_LABELS, ".net", ".org", ".gov", ".edu"))
                else null
            }
            KeyLabel.COM -> SimplePopups(listOf(Key.POPUP_KEYS_HAS_LABELS, ".net", ".org", ".gov", ".edu"))
            KeyLabel.ZWNJ -> SimplePopups(listOf("!icon/zwj_key|\u200D"))
            else -> null
        }
    }
}

/**
 * Data class which describes a single key and its attributes.
 *
 * @property type The type of the key. Some actions require both [code] and [type] to match in order
 *  to be successfully executed. Defaults to null.
 * @property code The UTF-8 encoded code of the character. The code defined here is used as the
 *  data passed to the system. Defaults to 0.
 * @property label The string used to display the key in the UI. Is not used for the actual data
 *  passed to the system. Should normally be the exact same as the [code]. Defaults to an empty
 *  string.
 */
@Serializable
@SerialName("text_key")
class TextKeyData(
    override val type: KeyType? = null,
    override val code: Int = KeyCode.UNSPECIFIED,
    override val label: String = "",
    override val groupId: Int = KeyData.GROUP_DEFAULT,
    override val popup: PopupSet<out AbstractKeyData> = SimplePopups(null),
    override val width: Float = 0f,
    override val labelFlags: Int = 0
) : KeyData {
    override fun asString(isForDisplay: Boolean): String {
        return buildString {
            if (isForDisplay || code == KeyCode.URI_COMPONENT_TLD || code < Constants.CODE_SPACE) {
                if (Unicode.isNonSpacingMark(code) && !label.startsWith("◌")) {
                    append("◌")
                }
                append(label)
            } else {
                try { appendCodePoint(code) } catch (_: Throwable) { }
            }
        }
    }

    override fun toString(): String {
        return "${TextKeyData::class.simpleName} { type=$type code=$code label=\"$label\" groupId=$groupId }"
    }

    override fun copy(
        newType: KeyType?,
        newCode: Int,
        newLabel: String,
        newGroupId: Int,
        newPopup: PopupSet<out AbstractKeyData>,
        newWidth: Float,
        newLabelFlags: Int
    ) = TextKeyData(newType, newCode, newLabel, newGroupId, newPopup, newWidth, newLabelFlags)

}

// AutoTextKeyData is just for converting case with shift, which HeliBoard always does anyway
// (maybe change later if there is a use case)
@Serializable
@SerialName("auto_text_key")
class AutoTextKeyData(
    override val type: KeyType? = null,
    override val code: Int = KeyCode.UNSPECIFIED,
    override val label: String = "",
    override val groupId: Int = KeyData.GROUP_DEFAULT,
    override val popup: PopupSet<out AbstractKeyData> = SimplePopups(null),
    override val width: Float = 0f,
    override val labelFlags: Int = 0
) : KeyData {

    override fun asString(isForDisplay: Boolean): String {
        return buildString {
            if (isForDisplay || code == KeyCode.URI_COMPONENT_TLD || code < Constants.CODE_SPACE) {
                if (Unicode.isNonSpacingMark(code) && !label.startsWith("◌")) {
                    append("◌")
                }
                append(label)
            } else {
                try { appendCodePoint(code) } catch (_: Throwable) { }
            }
        }
    }

    override fun toString(): String {
        return "${AutoTextKeyData::class.simpleName} { type=$type code=$code label=\"$label\" groupId=$groupId }"
    }

    override fun copy(
        newType: KeyType?,
        newCode: Int,
        newLabel: String,
        newGroupId: Int,
        newPopup: PopupSet<out AbstractKeyData>,
        newWidth: Float,
        newLabelFlags: Int
    ) = AutoTextKeyData(newType, newCode, newLabel, newGroupId, newPopup, newWidth, newLabelFlags)

}

@Serializable
@SerialName("multi_text_key")
class MultiTextKeyData(
    override val type: KeyType? = null,
    val codePoints: IntArray = intArrayOf(),
    override val label: String = "",
    override val groupId: Int = KeyData.GROUP_DEFAULT,
    override val popup: PopupSet<out AbstractKeyData> = SimplePopups(null),
    override val width: Float = 0f,
    override val labelFlags: Int = 0
) : KeyData {
    @Transient override val code: Int = KeyCode.MULTIPLE_CODE_POINTS

    override fun compute(params: KeyboardParams): KeyData {
        // todo: does this work? maybe convert label to | style?
        //  but if i allow negative codes, ctrl+z could be on a single key (but floris doesn't support this anyway)
        return this
    }

    override fun asString(isForDisplay: Boolean): String {
        return buildString {
            if (isForDisplay) {
                append(label)
            } else {
                for (codePoint in codePoints) {
                    try { appendCodePoint(codePoint) } catch (_: Throwable) { }
                }
            }
        }
    }

    override fun toString(): String {
        return "${MultiTextKeyData::class.simpleName} { type=$type code=$code label=\"$label\" groupId=$groupId }"
    }

    override fun copy(
        newType: KeyType?,
        newCode: Int,
        newLabel: String,
        newGroupId: Int,
        newPopup: PopupSet<out AbstractKeyData>,
        newWidth: Float,
        newLabelFlags: Int
    ) = MultiTextKeyData(newType, codePoints, newLabel, newGroupId, newPopup, newWidth, newLabelFlags)

}

fun String.toTextKey(popupKeys: Collection<String>? = null, labelFlags: Int = 0): TextKeyData =
    TextKeyData(
        label = this,
        labelFlags = labelFlags,
        popup = SimplePopups(popupKeys)
    )
