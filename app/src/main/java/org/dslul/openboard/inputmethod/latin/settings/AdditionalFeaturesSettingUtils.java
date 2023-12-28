/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
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
