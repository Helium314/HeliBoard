/*
 * Copyright (C) 2021 Patrick Goldinger
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.floris

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.dslul.openboard.inputmethod.keyboard.Key
import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardParams

// taken from FlorisBoard, small modifications (see also KeyData)
//  internal keys removed (currently no plan to support them)
//  added String.toTextKey
//  currency key handling (see todo below...)
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
    override val labelFlags: Int = 0
) : KeyData {
    override fun compute(params: KeyboardParams): KeyData {
//        if (evaluator.isSlot(this)) { // todo: currency key stuff probably should be taken from florisboard too
//            return evaluator.slotData(this)?.let { data ->
//                TextKeyData(type, data.code, data.label, groupId, popup).compute(params)
//            }
//        }
        if (label.startsWith("$$$")) { // currency key
            if (label == "$$$")
                return params.mLocaleKeyTexts.currencyKey
                    .let { it.first.toTextKey(it.second.toList(), labelFlags = Key.LABEL_FLAGS_FOLLOW_KEY_LETTER_RATIO) } // the flag is to match old parser, but why for main currency key, but not for others?
            val n = label.substringAfter("$$$").toIntOrNull()
            if (n != null && n <= 4 && n > 0)
                return params.mLocaleKeyTexts.currencyKey.second[n - 1].toTextKey()
        }
        return this
    }

    override fun asString(isForDisplay: Boolean): String {
        return buildString {
            if (isForDisplay || code == KeyCode.URI_COMPONENT_TLD || code < KeyCode.SPACE) {
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

@Serializable
@SerialName("auto_text_key")
class AutoTextKeyData(
    override val type: KeyType = KeyType.CHARACTER,
    override val code: Int = KeyCode.UNSPECIFIED,
    override val label: String = "",
    override val groupId: Int = KeyData.GROUP_DEFAULT,
    override val popup: PopupSet<AbstractKeyData> = PopupSet(),
    override val labelFlags: Int = 0
) : KeyData {
    // state and recompute not needed, as upcasing is done when creating KeyParams

    override fun compute(params: KeyboardParams): KeyData {
//        if (evaluator.isSlot(this)) { // todo: see above
//            return evaluator.slotData(this)?.let { data ->
//                TextKeyData(type, data.code, data.label, groupId, popup).compute(evaluator)
//            }
//        }
        return this
    }

    override fun asString(isForDisplay: Boolean): String {
        return buildString {
            if (isForDisplay || code == KeyCode.URI_COMPONENT_TLD || code < KeyCode.SPACE) {
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
    override val labelFlags: Int = 0
) : KeyData {
    @Transient override val code: Int = KeyCode.MULTIPLE_CODE_POINTS

    override fun compute(params: KeyboardParams): KeyData {
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

fun String.toTextKey(moreKeys: Collection<String>? = null, labelFlags: Int = 0): TextKeyData =
    TextKeyData(
        label = this,
        labelFlags = labelFlags,
        popup = SimplePopups(moreKeys)
    )
