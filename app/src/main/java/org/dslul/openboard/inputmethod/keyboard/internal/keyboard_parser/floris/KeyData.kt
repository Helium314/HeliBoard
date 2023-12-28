/*
 * Copyright (C) 2021 Patrick Goldinger
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.floris

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.dslul.openboard.inputmethod.keyboard.Key
import org.dslul.openboard.inputmethod.keyboard.Key.KeyParams
import org.dslul.openboard.inputmethod.keyboard.KeyboardId
import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardParams
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.rtlLabel
import org.dslul.openboard.inputmethod.latin.common.StringUtils

// taken from FlorisBoard, small modifications
//  popup not nullable (maybe change back, but currently that's necessary for number keys)
//  added getLabel for creating moreKeys from popups (which may be abstract)
//  added toKeyParams for non-abstract KeyData
//  compute is using KeyboardParams (for shift state and variation)
//  char_width_selector and kana_selector throw an error (not yet supported)
//  added labelFlags to keyDate
//  added manualOrLocked for shift_state_selector
//  added date, time and datetime to VariationSelector
/**
 * Basic interface for a key data object. Base for all key data objects across the IME, such as text, emojis and
 * selectors. The implementation is as abstract as possible, as different features require different implementations.
 */
interface AbstractKeyData {
    /**
     * Computes a [KeyData] object for this key data. Returns null if no computation is possible or if the key is
     * not relevant based on the result of [params].
     *
     * @param params The KeyboardParams used to retrieve different states from the parent controller.
     *
     * @return A [KeyData] object or null if no computation is possible.
     */
    fun compute(params: KeyboardParams): KeyData?

    /**
     * Returns the data described by this key as a string.
     *
     * @param isForDisplay Specifies if the returned string is intended to be displayed in a UI label (=true) or if
     *  it should be computed to be sent to an input connection (=false).
     *
     * @return The computed string for the key data object. Note: some objects may return an empty string here, meaning
     *  it is always required to check for the string's length before attempting to directly retrieve the first char.
     */
    fun asString(isForDisplay: Boolean): String // todo: remove it? not used at all (better only later, maybe useful for getting display label in some languages)

    fun getLabel(params: KeyboardParams) = if (this is KeyData) label else compute(params)?.label ?: ""
}

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
interface KeyData : AbstractKeyData {
    val type: KeyType
    val code: Int
    val label: String
    val groupId: Int
    val popup: PopupSet<AbstractKeyData> // not nullable because can't add number otherwise
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
    override fun compute(params: KeyboardParams): KeyData

    fun isSpaceKey(): Boolean {
        return type == KeyType.CHARACTER && (code == KeyCode.SPACE || code == KeyCode.CJK_SPACE
                || code == KeyCode.HALF_SPACE || code == KeyCode.KESHIDA)
    }

    fun toKeyParams(params: KeyboardParams, width: Float = params.mDefaultRelativeKeyWidth, additionalLabelFlags: Int = 0): KeyParams {
        // numeric keys are assigned a higher width in number layouts
        require(type == KeyType.CHARACTER || type == KeyType.NUMERIC) { "only KeyType CHARACTER or NUMERIC is supported" }
        // allow GROUP_ENTER negative codes so original florisboard number layouts can be used, bu actually it's ignored
        require(groupId == GROUP_DEFAULT || groupId == GROUP_ENTER) { "currently only GROUP_DEFAULT or GROUP_ENTER is supported" }
        // allow some negative codes so original florisboard number layouts can be used, those codes are actually ignored
        require(code >= 0 || code == -7 || code == -201 || code == -202) { "functional code $code not (yet) supported" }
        require(code != KeyCode.UNSPECIFIED || label.isNotEmpty()) { "key has no code and no label" }

        return if (code == KeyCode.UNSPECIFIED || code == KeyCode.MULTIPLE_CODE_POINTS) {
            // code will be determined from label if possible (i.e. label is single code point)
            // but also longer labels should work without issues, also for MultiTextKeyData
            KeyParams(
                label.rtlLabel(params), // todo (when supported): convert special labels to keySpec
                params,
                width,
                labelFlags or additionalLabelFlags,
                Key.BACKGROUND_TYPE_NORMAL, // todo (when supported): determine type
                popup,
            )
        } else {
            KeyParams(
                label.ifEmpty { StringUtils.newSingleCodePointString(code) },
                code, // todo (when supported): convert codes < 0, because florisboard layouts should still be usable
                params,
                width,
                labelFlags or additionalLabelFlags,
                Key.BACKGROUND_TYPE_NORMAL,
                popup,
            )
        }
    }
}

/**
 * Allows to select an [AbstractKeyData] based on the current caps state. Note that this type of selector only really
 * makes sense in a text context, though technically speaking it can be used anywhere, so this implementation allows
 * for any [AbstractKeyData] to be used here. The JSON class identifier for this selector is `case_selector`.
 *
 * Example usage in a layout JSON file:
 * ```
 * { "$": "case_selector",
 *   "lower": { "code":   59, "label": ";" },
 *   "upper": { "code":   58, "label": ":" }
 * }
 * ```
 *
 * @property lower The key data to use if the current caps state is lowercase.
 * @property upper The key data to use if the current caps state is uppercase.
 */
@Serializable
@SerialName("case_selector")
class CaseSelector(
    val lower: AbstractKeyData,
    val upper: AbstractKeyData,
) : AbstractKeyData {
    override fun compute(params: KeyboardParams): KeyData? {
        return (if (params.mId.isAlphabetShifted) { upper } else { lower }).compute(params)
    }

    override fun asString(isForDisplay: Boolean): String {
        return ""
    }
}

/**
 * Allows to select an [AbstractKeyData] based on the current shift state. Note that this type of selector only really
 * makes sense in a text context, though technically speaking it can be used anywhere, so this implementation allows
 * for any [AbstractKeyData] to be used here. The JSON class identifier for this selector is `shift_state_selector`.
 *
 * Example usage in a layout JSON file:
 * ```
 * { "$": "shift_state_selector",
 *   "shiftedManual": { "code":   59, "label": ";" },
 *   "default": { "code":   58, "label": ":" }
 * }
 * ```
 *
 * @property unshifted The key data to use if the current shift state is unshifted, falling back to
 *  [default] if unspecified.
 * @property shifted The key data to use if the current shift state is either manual or
 *  automatic. Is overridden if [shiftedManual] or [shiftedAutomatic] is specified.
 * @property shiftedManual The key data to use if the current shift state is manual,
 *  falling back to [shifted] or [default] if unspecified.
 * @property shiftedAutomatic The key data to use if the current shift state is automatic,
 *  falling back to [shifted] or [default] if unspecified.
 * @property capsLock The key data to use if the current shift state is locked, falling back to
 *  [default] if unspecified.
 * @property default The key data to use if the current shift state is set to a value not specified by this selector.
 *  If a key data is provided for all shift states possible this key data will never be used.
 */
@Serializable
@SerialName("shift_state_selector")
class ShiftStateSelector(
    val unshifted: AbstractKeyData? = null,
    val shifted: AbstractKeyData? = null,
    val shiftedManual: AbstractKeyData? = null,
    val shiftedAutomatic: AbstractKeyData? = null,
    val capsLock: AbstractKeyData? = null,
    val default: AbstractKeyData? = null,
    val manualOrLocked: AbstractKeyData? = null,
) : AbstractKeyData {
    override fun compute(params: KeyboardParams): KeyData? {
        return when (params.mId.mElementId) {
            KeyboardId.ELEMENT_ALPHABET, KeyboardId.ELEMENT_SYMBOLS -> unshifted ?: default
            KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED -> shiftedManual ?: manualOrLocked ?: shifted ?: default
            KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED -> shiftedAutomatic ?: shifted ?: default
            KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED, KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED -> capsLock ?: manualOrLocked ?: shifted ?: default
            else -> default // or rather unshifted?
        }?.compute(params)
    }

    override fun asString(isForDisplay: Boolean): String {
        return ""
    }
}

/**
 * Allows to select an [AbstractKeyData] based on the current variation. Note that this type of selector only really
 * makes sense in a text context, though technically speaking it can be used anywhere, so this implementation allows
 * for any [AbstractKeyData] to be used here. The JSON class identifier for this selector is `variation_selector`.
 *
 * Example usage in a layout JSON file:
 * ```
 * { "$": "variation_selector",
 *   "default":  { "code":   44, "label": "," },
 *   "email":    { "code":   64, "label": "@" },
 *   "uri":      { "code":   47, "label": "/" }
 * }
 * ```
 *
 * @property default The default key data which should be used in case no key variation is known or for the current
 *  key variation no override key is defined. Can be null, in this case this may mean the variation selector hides
 *  the key if no direct match is present.
 * @property email The key data to use if [KeyboardId.MODE_EMAIL] is active. If this value is
 *  null, [default] will be used instead.
 * @property uri The key data to use if [KeyboardId.MODE_URL] is active. If this value is null,
 *  [default] will be used instead.
 * @property normal The key data to use when? Currently ignored... If this value is null,
 *  [default] will be used instead.
 * @property password The key data to use if [KeyboardId.passwordInput] return true. If this value is
 *  null, [default] will be used instead.
 */
@Serializable
@SerialName("variation_selector")
data class VariationSelector(
    val default: AbstractKeyData? = null,
    val email: AbstractKeyData? = null,
    val uri: AbstractKeyData? = null,
    val normal: AbstractKeyData? = null,
    val password: AbstractKeyData? = null,
    val date: AbstractKeyData? = null,
    val time: AbstractKeyData? = null,
    val datetime: AbstractKeyData? = null,
) : AbstractKeyData {
    override fun compute(params: KeyboardParams): KeyData? {
        return when {
            // todo: what is normal and all?
//            KeyVariation.ALL -> default
//            KeyVariation.NORMAL -> normal ?: default
            params.mId.passwordInput() -> password ?: default
            params.mId.mMode == KeyboardId.MODE_EMAIL -> email ?: default
            params.mId.mMode == KeyboardId.MODE_URL -> uri ?: default
            params.mId.mMode == KeyboardId.MODE_DATE -> date ?: default
            params.mId.mMode == KeyboardId.MODE_TIME -> time ?: default
            params.mId.mMode == KeyboardId.MODE_DATETIME -> datetime ?: default
            else -> default
        }?.compute(params)
    }

    override fun asString(isForDisplay: Boolean): String {
        return ""
    }
}

/**
 * Allows to select an [AbstractKeyData] based on the current layout direction. Note that this type of selector only
 * really makes sense in a text context, though technically speaking it can be used anywhere, so this implementation
 * allows for any [AbstractKeyData] to be used here. The JSON class identifier for this selector is
 * `layout_direction_selector`.
 *
 * Example usage in a layout JSON file:
 * ```
 * { "$": "layout_direction_selector",
 *   "ltr": { "code":   59, "label": ";" },
 *   "rtl": { "code":   58, "label": ":" }
 * }
 * ```
 *
 * @property ltr The key data to use if the current layout direction is LTR.
 * @property rtl The key data to use if the current layout direction is RTL.
 */
@Serializable
@SerialName("layout_direction_selector")
class LayoutDirectionSelector(
    val ltr: AbstractKeyData,
    val rtl: AbstractKeyData,
) : AbstractKeyData {
    override fun compute(params: KeyboardParams): KeyData? {
        return (if (params.mId.mSubtype.isRtlSubtype) { rtl } else { ltr }).compute(params)
    }

    override fun asString(isForDisplay: Boolean): String {
        return ""
    }
}

/**
 * Allows to select an [AbstractKeyData] based on the character's width. Note that this type of selector only really
 * makes sense in a text context, though technically speaking it can be used anywhere, so this implementation allows
 * for any [AbstractKeyData] to be used here. The JSON class identifier for this selector is `char_width_selector`.
 *
 * Example usage in a layout JSON file:
 * ```
 * { "$": "char_width_selector",
 *   "full": { "code": 12450, "label": "ア" },
 *   "half": { "code": 65393, "label": "ｱ" }
 * }
 * ```
 *
 * @property full The key data to use if the current character width is full.
 * @property half The key data to use if the current character width is half.
 */
@Serializable
@SerialName("char_width_selector")
class CharWidthSelector(
    val full: AbstractKeyData?,
    val half: AbstractKeyData?,
) : AbstractKeyData {
    override fun compute(params: KeyboardParams): KeyData? {
        throw UnsupportedOperationException("char_width_selector not (yet) supported")
//        val data = if (params.halfWidth) { half } else { full }
//        return data?.compute(params)
    }

    override fun asString(isForDisplay: Boolean): String {
        return ""
    }
}

/**
 * Allows to select an [AbstractKeyData] based on the kana state. Note that this type of selector only really
 * makes sense in a text context, though technically speaking it can be used anywhere, so this implementation allows
 * for any [AbstractKeyData] to be used here. The JSON class identifier for this selector is `kana_selector`.
 *
 * Example usage in a layout JSON file:
 * ```
 * { "$": "kana_selector",
 *   "hira": { "code": 12354, "label": "あ" },
 *   "kata": { "code": 12450, "label": "ア" }
 * }
 * ```
 *
 * @property hira The key data to use if the current kana state is hiragana.
 * @property kata The key data to use if the current kana state is katakana.
 */
@Serializable
@SerialName("kana_selector")
class KanaSelector(
    val hira: AbstractKeyData,
    val kata: AbstractKeyData,
) : AbstractKeyData {
    override fun compute(params: KeyboardParams): KeyData? {
        throw UnsupportedOperationException("kana_selector not (yet) supported")
//        val data = if (evaluator.state.isKanaKata) { kata } else { hira }
//        return data.compute(evaluator)
    }

    override fun asString(isForDisplay: Boolean): String {
        return ""
    }
}
