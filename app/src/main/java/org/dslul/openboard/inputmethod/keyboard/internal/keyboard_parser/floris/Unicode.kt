/*
 * Copyright (C) 2021 Patrick Goldinger
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.floris

import android.icu.lang.UCharacter
import android.icu.lang.UCharacterCategory
import android.os.Build

// taken from FlorisBoard
//  unused parts removed, added fallback for api < 24
object Unicode {
    fun isNonSpacingMark(code: Int): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            UCharacter.getType(code).toByte() == UCharacterCategory.NON_SPACING_MARK
        } else {
            Character.getType(code).toByte() == Character.NON_SPACING_MARK
        }
    }
}
