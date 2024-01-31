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
    CHARACTER,
    ENTER_EDITING,
    FUNCTION,
    LOCK,
    MODIFIER,
    NAVIGATION,
    SYSTEM_GUI,
    NUMERIC,
    PLACEHOLDER,
    UNSPECIFIED;

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
