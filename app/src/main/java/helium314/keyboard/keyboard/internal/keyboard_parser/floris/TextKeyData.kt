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
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode.checkAndConvertCode
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode.convertFlorisLabel
import helium314.keyboard.keyboard.internal.keyboard_parser.rtlLabel
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.StringUtils

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
    val type: KeyType
    val code: Int
    val label: String
    val groupId: Int
    val popup: PopupSet<AbstractKeyData> // not nullable because can't add number otherwise
    val width: Float // in percent of keyboard width, 0 is default (depends on key), -1 is fill (like space bar)
    val labelFlags: Int

    // groups (currently) not supported
    companion object {
        /**
         * Constant for the default group. If not otherwise specified, any key is automatically
         * assigned to this group.
         */
        const val GROUP_DEFAULT: Int = 0

        /**
         * Constant for the Left modifier key group. Any key belonging to this group will get the
         * popups specified for "~left" in the popup mapping.
         */
        const val GROUP_LEFT: Int = 1

        /**
         * Constant for the right modifier key group. Any key belonging to this group will get the
         * popups specified for "~right" in the popup mapping.
         */
        const val GROUP_RIGHT: Int = 2

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
    }

    // make it non-nullable for simplicity, and to reflect current implementations
    override fun compute(params: KeyboardParams): KeyData {
        val newLabel = label.convertFlorisLabel()
        val newCode = code.checkAndConvertCode()

        // resolve currency keys
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
                    SimplePopups(currencyKey.second), width, labelFlags or Key.LABEL_FLAGS_FOLLOW_KEY_LETTER_RATIO)
            }
            val n = newLabel.substringAfter("$$$").toIntOrNull()
            if (n != null && n <= 5 && n > 0) {
                val finalLabel = currencyKey.second[n - 1] + currencyCodeAsString
                return TextKeyData(type, KeyCode.UNSPECIFIED, finalLabel, groupId, popup, width, labelFlags)
            }
        }
        if (newCode != code || newLabel != label)
            return TextKeyData(type, newCode, newLabel, groupId, popup, width, labelFlags).compute(params)
        return this
    }


    fun isSpaceKey(): Boolean {
        return type == KeyType.CHARACTER && (code == Constants.CODE_SPACE || code == KeyCode.CJK_SPACE
                || code == KeyCode.ZWNJ || code == KeyCode.KESHIDA)
    }

    // todo: width in units of keyboard width, or in percent? (currently the plan was percent, but actually fractions are used)
    fun toKeyParams(params: KeyboardParams, additionalLabelFlags: Int = 0): Key.KeyParams {
        // todo: remove checks here, do only when reading json layouts
        // numeric keys are assigned a higher width in number layouts (todo: not true any more, also maybe they could have width -1 instead?)
        if (type == KeyType.PLACEHOLDER) return Key.KeyParams.newSpacer(params, width)
        // todo: allow all types, but define / document what they do (probably only effect on background?)
        require(type == KeyType.CHARACTER || type == KeyType.NUMERIC || type == KeyType.ENTER_EDITING || type == KeyType.SYSTEM_GUI || type == KeyType.MODIFIER) { "only KeyType CHARACTER or NUMERIC is supported" }
        // allow GROUP_ENTER negative codes so original florisboard number layouts can be used, bu actually it's ignored
        require(groupId == GROUP_DEFAULT || groupId == GROUP_ENTER) { "currently only GROUP_DEFAULT or GROUP_ENTER is supported" }
        require(code != KeyCode.UNSPECIFIED || label.isNotEmpty()) { "key has no code and no label" }
        val actualWidth = if (width == 0f) getDefaultWidth(params) else width

        return if (code == KeyCode.UNSPECIFIED || code == KeyCode.MULTIPLE_CODE_POINTS) {
            // code will be determined from label if possible (i.e. label is single code point)
            // but also longer labels should work without issues, also for MultiTextKeyData
            if (this is MultiTextKeyData) {
                val outputText = String(codePoints, 0, codePoints.size)
                Key.KeyParams(
                    "$label|$outputText",
                    code,
                    params,
                    actualWidth,
                    labelFlags or additionalLabelFlags,
                    Key.BACKGROUND_TYPE_NORMAL, // todo (when supported): determine type
                    popup,
                )
            } else {
                Key.KeyParams(
                    label.rtlLabel(params), // todo (when supported): convert special labels to keySpec
                    params,
                    actualWidth,
                    labelFlags or additionalLabelFlags,
                    Key.BACKGROUND_TYPE_NORMAL, // todo (when supported): determine type
                    popup,
                )
            }
        } else {
            Key.KeyParams(
                label.ifEmpty { StringUtils.newSingleCodePointString(code) },
                code,
                params,
                actualWidth,
                labelFlags or additionalLabelFlags,
                Key.BACKGROUND_TYPE_NORMAL,
                popup,
            )
        }
    }

    // todo: only public for lazy workaround, make private again
    fun getDefaultWidth(params: KeyboardParams): Float {
        return if (label == "space" && params.mId.isAlphaOrSymbolKeyboard) -1f
        else if (type == KeyType.NUMERIC && params.mId.isNumberLayout) 0.17f
        else params.mDefaultKeyWidth
    }
}

/**
 * Data class which describes a single key and its attributes.
 *
 * @property type The type of the key. Some actions require both [code] and [type] to match in order
 *  to be successfully executed. Defaults to [KeyType.CHARACTER].
 * @property code The UTF-8 encoded code of the character. The code defined here is used as the
 *  data passed to the system. Defaults to 0.
 * @property label The string used to display the key in the UI. Is not used for the actual data
 *  passed to the system. Should normally be the exact same as the [code]. Defaults to an empty
 *  string.
 */
@Serializable
@SerialName("text_key")
class TextKeyData(
    override val type: KeyType = KeyType.CHARACTER,
    override val code: Int = KeyCode.UNSPECIFIED,
    override val label: String = "",
    override val groupId: Int = KeyData.GROUP_DEFAULT,
    override val popup: PopupSet<AbstractKeyData> = PopupSet(),
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

}

// AutoTextKeyData is just for converting case with shift, which HeliBoard always does anyway
// (maybe change later if there is a use case)
@Serializable
@SerialName("auto_text_key")
class AutoTextKeyData(
    override val type: KeyType = KeyType.CHARACTER,
    override val code: Int = KeyCode.UNSPECIFIED,
    override val label: String = "",
    override val groupId: Int = KeyData.GROUP_DEFAULT,
    override val popup: PopupSet<AbstractKeyData> = PopupSet(),
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
}

@Serializable
@SerialName("multi_text_key")
class MultiTextKeyData(
    override val type: KeyType = KeyType.CHARACTER,
    val codePoints: IntArray = intArrayOf(),
    override val label: String = "",
    override val groupId: Int = KeyData.GROUP_DEFAULT,
    override val popup: PopupSet<AbstractKeyData> = PopupSet(),
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
}

fun String.toTextKey(popupKeys: Collection<String>? = null, labelFlags: Int = 0): TextKeyData =
    TextKeyData(
        label = this,
        labelFlags = labelFlags,
        popup = SimplePopups(popupKeys)
    )
