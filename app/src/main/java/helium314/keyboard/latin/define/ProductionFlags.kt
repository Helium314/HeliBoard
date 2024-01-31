/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.define

object ProductionFlags {
    const val IS_HARDWARE_KEYBOARD_SUPPORTED = true // was set to true in hangul branch
    // todo: test whether there are issues
    //  hangul dev apparently did use it at least the hangul hardware event decoder in latinIme suggests it

    /**
     * Include all suggestions from all dictionaries in
     * [helium314.keyboard.latin.SuggestedWords.mRawSuggestions].
     */
    const val INCLUDE_RAW_SUGGESTIONS = false
}