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
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode.checkAndConvertCode
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyLabel.convertFlorisLabel
import helium314.keyboard.keyboard.internal.keyboard_parser.rtlLabel
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.settings.Settings
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator

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

        private fun getShiftLabel(params: KeyboardParams) = when (params.mId.mElementId) {
            KeyboardId.ELEMENT_SYMBOLS_SHIFTED -> params.mLocaleKeyboardInfos.labelSymbol
            KeyboardId.ELEMENT_SYMBOLS -> params.mLocaleKeyboardInfos.getShiftSymbolLabel(Settings.getInstance().isTablet)
            KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED, KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED,
            KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED, KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED -> "!icon/${KeyboardIconsSet.NAME_SHIFT_KEY_SHIFTED}"
            else -> "!icon/${KeyboardIconsSet.NAME_SHIFT_KEY}"
        }

        // todo (later): try avoiding this weirdness
        //  maybe just remove it and if users want it they can use custom functional layouts?
        //  but it has been like this "forever" and actually seems to make sense
        private fun getPeriodLabel(params: KeyboardParams): String {
            if (params.mId.isNumberLayout) return "."
            if (params.mId.isAlphabetKeyboard || params.mId.locale.language in listOf("ar", "fa"))
                return params.mLocaleKeyboardInfos.labelPeriod
            return "."
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
        require(groupId <= GROUP_ENTER) { "only groups up to GROUP_ENTER are supported" }
        require(label.isNotEmpty() || type == KeyType.PLACEHOLDER || code != KeyCode.UNSPECIFIED) { "non-placeholder key has no code and no label" }
        val newLabel = label.convertFlorisLabel()
        val newCode = code.checkAndConvertCode()

        if (newCode != code || newLabel != label)
            return copy(newCode = newCode, newLabel = newLabel)
        return this
    }


    fun isSpaceKey(): Boolean {
        return code == Constants.CODE_SPACE || code == KeyCode.CJK_SPACE || code == KeyCode.ZWNJ || code == KeyCode.KESHIDA
    }

    /** this expects that codes and labels are already converted from FlorisBoard values, usually through compute */
    fun toKeyParams(params: KeyboardParams, additionalLabelFlags: Int = 0): Key.KeyParams {
        if (type == KeyType.PLACEHOLDER) return Key.KeyParams.newSpacer(params, width)

        val newWidth = if (width == 0f) getDefaultWidth(params) else width
        val newCode: Int
        val newLabel: String
        if (code in KeyCode.Spec.CURRENCY) {
            // special treatment necessary, because we may need to encode it in the label
            // (currency is a string, so might have more than 1 codepoint)
            newCode = 0
            val l = processLabel(params)
            newLabel = when (code) {
                // consider currency codes for label
                KeyCode.CURRENCY_SLOT_1 -> "$l|${params.mLocaleKeyboardInfos.currencyKey.first}"
                KeyCode.CURRENCY_SLOT_2 -> "$l|${params.mLocaleKeyboardInfos.currencyKey.second[0]}"
                KeyCode.CURRENCY_SLOT_3 -> "$l|${params.mLocaleKeyboardInfos.currencyKey.second[1]}"
                KeyCode.CURRENCY_SLOT_4 -> "$l|${params.mLocaleKeyboardInfos.currencyKey.second[2]}"
                KeyCode.CURRENCY_SLOT_5 -> "$l|${params.mLocaleKeyboardInfos.currencyKey.second[3]}"
                KeyCode.CURRENCY_SLOT_6 -> "$l|${params.mLocaleKeyboardInfos.currencyKey.second[4]}"
                else -> throw IllegalStateException("code in currency range, but not in currency range?")
            }
        } else {
            newCode = processCode()
            newLabel = processLabel(params)
        }
        val newLabelFlags = labelFlags or additionalLabelFlags or getAdditionalLabelFlags(params)
        val newPopupKeys = popup.merge(getAdditionalPopupKeys(params))

        val background = when (type) {
            KeyType.CHARACTER, KeyType.NUMERIC -> Key.BACKGROUND_TYPE_NORMAL
            KeyType.FUNCTION, KeyType.MODIFIER, KeyType.SYSTEM_GUI -> Key.BACKGROUND_TYPE_FUNCTIONAL
            KeyType.PLACEHOLDER, KeyType.UNSPECIFIED -> Key.BACKGROUND_TYPE_EMPTY
            KeyType.NAVIGATION -> Key.BACKGROUND_TYPE_SPACEBAR
            KeyType.ENTER_EDITING -> Key.BACKGROUND_TYPE_ACTION
            KeyType.LOCK -> getShiftBackground(params)
            null -> getDefaultBackground(params)
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
                    newWidth,
                    newLabelFlags,
                    background,
                    newPopupKeys,
                )
            } else {
                Key.KeyParams(
                    newLabel.rtlLabel(params), // todo (when supported): convert special labels to keySpec
                    params,
                    newWidth,
                    newLabelFlags,
                    background,
                    newPopupKeys,
                )
            }
        } else {
            Key.KeyParams(
                newLabel.ifEmpty { StringUtils.newSingleCodePointString(newCode) },
                newCode,
                params,
                newWidth,
                newLabelFlags,
                background,
                newPopupKeys,
            )
        }
    }

    private fun getDefaultBackground(params: KeyboardParams): Int {
        // functional keys
        when (label) { // or use code?
            KeyLabel.SYMBOL_ALPHA, KeyLabel.SYMBOL, KeyLabel.ALPHA, KeyLabel.COMMA, KeyLabel.PERIOD, KeyLabel.DELETE,
            KeyLabel.EMOJI, KeyLabel.COM, KeyLabel.LANGUAGE_SWITCH, KeyLabel.NUMPAD -> return Key.BACKGROUND_TYPE_FUNCTIONAL
            KeyLabel.SPACE, KeyLabel.ZWNJ -> return Key.BACKGROUND_TYPE_SPACEBAR
            KeyLabel.ACTION -> return Key.BACKGROUND_TYPE_ACTION
            KeyLabel.SHIFT -> return getShiftBackground(params)
        }
        if (type == KeyType.PLACEHOLDER) return Key.BACKGROUND_TYPE_EMPTY
        return Key.BACKGROUND_TYPE_NORMAL
    }

    // todo (later): possibly the whole stickyOn/Off stuff can be removed, currently it should only have a very slight effect in holo
    //  but iirc there is some attempt in reviving the sticky thing, right?
    private fun getShiftBackground(params: KeyboardParams): Int {
        return if (params.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED
            || params.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED) Key.BACKGROUND_TYPE_STICKY_ON
        else Key.BACKGROUND_TYPE_STICKY_OFF
    }

    private fun getDefaultWidth(params: KeyboardParams): Float {
        return if (label == KeyLabel.SPACE && params.mId.isAlphaOrSymbolKeyboard) -1f
        else if (type == KeyType.NUMERIC && params.mId.isNumberLayout) 0.17f // todo (later) consider making this -1?
        else params.mDefaultKeyWidth
    }

    // todo (later): encoding the code in the label should be avoided, because we know it already
    private fun processLabel(params: KeyboardParams): String = when (label) {
        KeyLabel.SYMBOL_ALPHA -> if (params.mId.isAlphabetKeyboard) params.mLocaleKeyboardInfos.labelSymbol else params.mLocaleKeyboardInfos.labelAlphabet
        KeyLabel.SYMBOL -> params.mLocaleKeyboardInfos.labelSymbol
        KeyLabel.ALPHA -> params.mLocaleKeyboardInfos.labelAlphabet
        KeyLabel.COMMA -> params.mLocaleKeyboardInfos.labelComma
        KeyLabel.PERIOD -> getPeriodLabel(params)
        KeyLabel.SPACE -> getSpaceLabel(params)
//        KeyLabel.ACTION -> "${getActionKeyLabel(params)}|${getActionKeyCode(params)}" would need context
        KeyLabel.DELETE -> "!icon/delete_key|!code/key_delete"
        KeyLabel.SHIFT -> "${getShiftLabel(params)}|!code/key_shift"
        KeyLabel.EMOJI -> "!icon/emoji_normal_key|!code/key_emoji"
        // todo (later): label and popupKeys for .com should be in localeKeyTexts, handled similar to currency key
        KeyLabel.COM -> ".com"
        KeyLabel.LANGUAGE_SWITCH -> "!icon/language_switch_key|!code/key_language_switch"
        KeyLabel.NUMPAD -> "!icon/numpad_key|!code/key_numpad"
        KeyLabel.ZWNJ -> "!icon/zwnj_key|\u200C"
        KeyLabel.CURRENCY -> params.mLocaleKeyboardInfos.currencyKey.first
        KeyLabel.CURRENCY1 -> params.mLocaleKeyboardInfos.currencyKey.second[0]
        KeyLabel.CURRENCY2 -> params.mLocaleKeyboardInfos.currencyKey.second[1]
        KeyLabel.CURRENCY3 -> params.mLocaleKeyboardInfos.currencyKey.second[2]
        KeyLabel.CURRENCY4 -> params.mLocaleKeyboardInfos.currencyKey.second[3]
        KeyLabel.CURRENCY5 -> params.mLocaleKeyboardInfos.currencyKey.second[4]
        else -> label
    }

    private fun processCode(): Int {
        if (code != KeyCode.UNSPECIFIED) return code
        return when (label) {
            KeyLabel.SYMBOL_ALPHA -> KeyCode.SYMBOL_ALPHA
            KeyLabel.SYMBOL -> KeyCode.SYMBOL
            KeyLabel.ALPHA -> KeyCode.ALPHA
            else -> code
        }
    }

    // todo (later): add explanations / reasoning, often this is just taken from conversion from AOSP layouts
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
            KeyLabel.COM -> Key.LABEL_FLAGS_AUTO_X_SCALE or Key.LABEL_FLAGS_FONT_NORMAL or Key.LABEL_FLAGS_HAS_POPUP_HINT or Key.LABEL_FLAGS_PRESERVE_CASE
            KeyLabel.ZWNJ -> Key.LABEL_FLAGS_HAS_POPUP_HINT
            KeyLabel.CURRENCY -> Key.LABEL_FLAGS_FOLLOW_KEY_LETTER_RATIO
            else -> 0
        }
    }

    private fun getAdditionalPopupKeys(params: KeyboardParams): PopupSet<AbstractKeyData>? {
        if (groupId == GROUP_COMMA) return SimplePopups(getCommaPopupKeys(params))
        if (groupId == GROUP_PERIOD) return SimplePopups(getPunctuationPopupKeys(params))
//        if (groupId == GROUP_ENTER) return getActionKeyPopupKeys(params)?.let { SimplePopups(it) }
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
            KeyLabel.COM -> SimplePopups(listOf(Key.POPUP_KEYS_HAS_LABELS, ".net", ".org", ".gov", ".edu"))
            KeyLabel.ZWNJ -> SimplePopups(listOf("!icon/zwj_key|\u200D"))
            // only add currency popups if there are none defined on the key
            KeyLabel.CURRENCY -> if (popup.isEmpty()) SimplePopups(params.mLocaleKeyboardInfos.currencyKey.second) else null
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
