/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceFragment;
import android.view.inputmethod.InputMethodSubtype;

import androidx.annotation.NonNull;

import org.dslul.openboard.inputmethod.latin.RichInputMethodManager;
import org.dslul.openboard.inputmethod.latin.RichInputMethodSubtype;

/**
 * Utility class for managing additional features settings.
 */
@SuppressWarnings("unused")
public class AdditionalFeaturesSettingUtils {
    public static final int ADDITIONAL_FEATURES_SETTINGS_SIZE = 0;

    private AdditionalFeaturesSettingUtils() {
        // This utility class is not publicly instantiable.
    }

    public static void addAdditionalFeaturesPreferences(
            final Context context, final PreferenceFragment settingsFragment) {
        // do nothing.
    }

    public static void readAdditionalFeaturesPreferencesIntoArray(final Context context,
            final SharedPreferences prefs, final int[] additionalFeaturesPreferences) {
        // do nothing.
    }

    @NonNull
    public static RichInputMethodSubtype createRichInputMethodSubtype(
            @NonNull final RichInputMethodManager imm,
            @NonNull final InputMethodSubtype subtype,
            final Context context) {
        return new RichInputMethodSubtype(subtype);
    }
}
