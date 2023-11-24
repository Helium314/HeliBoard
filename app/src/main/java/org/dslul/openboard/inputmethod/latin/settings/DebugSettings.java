/*
 * Copyright (C) 2010 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.settings;

/**
 * Debug settings for the application.
 *
 * Note: Even though these settings are stored in the default shared preferences file,
 * they shouldn't be restored across devices.
 * If a new key is added here, it should also be blacklisted for restore in
 * {@link LocalSettingsConstants}.
 */
public final class DebugSettings {
    public static final String PREF_DEBUG_MODE = "debug_mode";
    public static final String PREF_FORCE_NON_DISTINCT_MULTITOUCH = "force_non_distinct_multitouch";
    public static final String PREF_SHOULD_SHOW_LXX_SUGGESTION_UI =
            "pref_should_show_lxx_suggestion_ui";
    public static final String PREF_SLIDING_KEY_INPUT_PREVIEW = "pref_sliding_key_input_preview";

    private DebugSettings() {
        // This class is not publicly instantiable.
    }
}
