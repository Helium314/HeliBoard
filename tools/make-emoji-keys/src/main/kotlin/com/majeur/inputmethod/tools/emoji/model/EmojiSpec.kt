// SPDX-License-Identifier: GPL-3.0-only

package com.majeur.inputmethod.tools.emoji.model

import com.majeur.inputmethod.tools.emoji.model.EmojiData.Companion.CP_NUL

data class EmojiSpec(val codes: IntArray, val unicodeVer: Float, val name: String) {

    var component = CP_NUL

    val variants by lazy { mutableListOf<EmojiSpec>() }

    override fun toString() = name

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EmojiSpec
        return codes contentEquals other.codes
    }

    override fun hashCode() = codes.contentHashCode()
}