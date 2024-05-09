/*
 * Copyright (C) 2020 Patrick Goldinger
 * modified
 * SPDX-License-Identifier: Apache-2.0
 */
package helium314.keyboard.keyboard.internal.keyboard_parser.floris

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// taken from FlorisBoard, not actually used (only CHARACTER allowed)
/**
 * Enum for declaring the type of the key.
 * List of possible key types:
 *  [Wikipedia](https://en.wikipedia.org/wiki/Keyboard_layout#Key_types)
 */
@Serializable(with = KeyTypeSerializer::class)
enum class KeyType {
    CHARACTER,      // default
    ENTER_EDITING,  // should be enter/insert/delete, but always gets action key background
    FUNCTION,       // f1..., gets functional key background
    LOCK,           // scroll lock, num lock, caps lock, gets sticky on/off background, which currently is the same as functional background
    MODIFIER,       // alt, ctrl, shift, gets functional key background
    NAVIGATION,     // home, page up, page down, tab, arrows, gets space background because it'S still the most suitable type
    SYSTEM_GUI,     // esc, print, pause, meta, (keyboard layout switch), gets functional background
    NUMERIC,        // numpad keys, get larger letter and larger width in number layouts, and default background
    PLACEHOLDER,    // spacer, or actual placeholder when used in functional key layouts
    UNSPECIFIED;    // empty background

    override fun toString(): String {
        return super.toString().lowercase()
    }

    companion object {
        fun fromString(string: String): KeyType {
            // resolve alternative names
            return when (string) {
                "space" -> NAVIGATION
                "action" -> ENTER_EDITING
                "shift" -> LOCK
                else -> valueOf(string.uppercase())
            }
        }
    }
}

class KeyTypeSerializer : KSerializer<KeyType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("KeyType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: KeyType) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): KeyType {
        return KeyType.fromString(decoder.decodeString())
    }
}
