/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.settings;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;

import org.dslul.openboard.inputmethod.latin.R;

/**
 * "Gesture typing preferences" settings sub screen.
 *
 * This settings sub screen handles the following gesture typing preferences.
 * - Enable gesture typing
 * - Dynamic floating preview
 * - Show gesture trail
 * - Phrase gesture
 */
public final class GestureSettingsFragment extends SubScreenFragment {
    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_gesture);
        refreshSettingsEnablement();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        refreshSettingsEnablement();
    }

    private void refreshSettingsEnablement() {
        final SharedPreferences prefs = getSharedPreferences();
        final Resources res = getResources();
        setPreferenceVisible(Settings.PREF_GESTURE_PREVIEW_TRAIL,
                Settings.readGestureInputEnabled(prefs));
        setPreferenceVisible(Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT,
                Settings.readGestureInputEnabled(prefs));
        setPreferenceVisible(Settings.PREF_GESTURE_SPACE_AWARE,
                Settings.readGestureInputEnabled(prefs));
    }
}
