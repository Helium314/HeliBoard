// SPDX-License-Identifier: GPL-3.0-only

package com.majeur.inputmethod.tools.emoji.model

data class EmojiSpec(val codes: IntArray, val unicodeVer: Float, val name: String) {

    val variants by lazy { mutableListOf<EmojiSpec>() }

    override fun toString() = name

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EmojiSpec
        return codes contentEquals other.codes
    }

    val text get() = codes.joinToString("") { Character.toString(it) }

    override fun hashCode() = codes.contentHashCode()
}
