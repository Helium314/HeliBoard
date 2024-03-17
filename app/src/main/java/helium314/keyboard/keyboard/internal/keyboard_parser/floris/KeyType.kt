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
    // todo: implement the effect on background
    //  also, how to get that specific space bar background?
    CHARACTER,      // default
    ENTER_EDITING,  // enter/insert/delete, gets functional key background (if not action key)
    FUNCTION,       // f1..., gets functional key background
    LOCK,           // scroll lock, num lock, caps lock, gets functional key background
    MODIFIER,       // alt, ctrl, shift, gets functional key background
    NAVIGATION,     // home, page up, page down, tab, arrows, geta default background
    SYSTEM_GUI,     // esc, print, pause, meta, (keyboard layout switch), geta functional background
    NUMERIC,        // numpad keys, get larger letter and larger width
    PLACEHOLDER,    // other keys go here, e.g. in shift, placeholder, delete the placeholder gets (typically) replaced by the bottom keyboard row
    UNSPECIFIED;    // treated like default

    override fun toString(): String {
        return super.toString().lowercase()
    }

    companion object {
        fun fromString(string: String): KeyType {
            return valueOf(string.uppercase())
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
