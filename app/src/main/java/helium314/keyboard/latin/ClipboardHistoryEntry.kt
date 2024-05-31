// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin

import android.net.Uri
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class ClipboardHistoryEntry (
        var timeStamp: Long,
        @Serializable(with = CharSequenceStringSerializer::class)
        val text: CharSequence,
        @Serializable(with = UriSerializer::class)
        val imageUri: Uri?,
        var isPinned: Boolean = false
) : Comparable<ClipboardHistoryEntry> {
    override fun compareTo(other: ClipboardHistoryEntry): Int {
        val result = other.isPinned.compareTo(isPinned)
        return if (result != 0) result else other.timeStamp.compareTo(timeStamp)
    }
}

class CharSequenceStringSerializer : KSerializer<CharSequence> {
    override val descriptor = PrimitiveSerialDescriptor("CharSequence", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: CharSequence) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder) = decoder.decodeString()
}

class UriSerializer : KSerializer<Uri> {
    override val descriptor = PrimitiveSerialDescriptor("Uri", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Uri) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Uri {
        val uriString = decoder.decodeString()
        return Uri.parse(uriString)
    }
}