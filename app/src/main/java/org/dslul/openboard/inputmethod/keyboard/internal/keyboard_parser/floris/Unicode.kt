/*
 * Copyright (C) 2021 Patrick Goldinger
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.floris

import android.icu.lang.UCharacter
import android.icu.lang.UCharacterCategory
import android.os.Build

// taken from FlorisBoard, small modifications
//  isNonSpacingMark has a fallback for api < 24
/**
 * Character codes and comments source:
 *  https://www.w3.org/International/questions/qa-bidi-unicode-controls#basedirection
 */
@Suppress("unused")
object UnicodeCtrlChar {
    /** Sets base direction to LTR and isolates the embedded content from the surrounding text */
    const val LeftToRightIsolate = "\u2066"

    /** Sets base direction to RTL and isolates the embedded content from the surrounding text */
    const val RightToLeftIsolate = "\u2067"

    /** Isolates the content and sets the direction according to the first strongly typed directional character */
    const val FirstStrongIsolate = "\u2068"

    /** Closes a previously opened isolated text block */
    const val PopDirectionalIsolate = "\u2069"

    val Matcher = """[$LeftToRightIsolate$RightToLeftIsolate$FirstStrongIsolate$PopDirectionalIsolate]""".toRegex()
}

fun String.stripUnicodeCtrlChars(): String {
    return this.replace(UnicodeCtrlChar.Matcher, "")
}

object Unicode {
    fun isNonSpacingMark(code: Int): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            UCharacter.getType(code).toByte() == UCharacterCategory.NON_SPACING_MARK
        } else {
            Character.getType(code).toByte() == Character.NON_SPACING_MARK
        }
    }
}
