/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.define

import android.content.SharedPreferences
import org.dslul.openboard.inputmethod.latin.BuildConfig
import org.dslul.openboard.inputmethod.latin.settings.DebugSettings

object DebugFlags {
    @JvmField
    var DEBUG_ENABLED = false

    @JvmStatic
    fun init(prefs: SharedPreferences) {
        DEBUG_ENABLED = BuildConfig.DEBUG && prefs.getBoolean(DebugSettings.PREF_DEBUG_MODE, false)
    }
}
