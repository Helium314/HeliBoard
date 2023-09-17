/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
