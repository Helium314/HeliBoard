/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.oscar.aikeyboard.latin.define

object ProductionFlags {
    const val IS_HARDWARE_KEYBOARD_SUPPORTED = false
    // todo: make it work
    //  was set to true in hangul branch (and there is the hangul hardware event decoder in latinIme)
    //  but disabled again because this breaks ctrl+c / ctrl+v, and most likely other things
    //  so it looks like the HardwareKeyboardEventDecoder needs some work before it's ready

    /**
     * Include all suggestions from all dictionaries in
     * [helium314.keyboard.latin.SuggestedWords.mRawSuggestions].
     */
    const val INCLUDE_RAW_SUGGESTIONS = false
}