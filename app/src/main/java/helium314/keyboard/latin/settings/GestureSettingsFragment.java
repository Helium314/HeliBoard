/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.settings;

import android.content.SharedPreferences;
import android.os.Bundle;

import helium314.keyboard.latin.R;

/**
 * "Gesture typing preferences" settings sub screen.
 * <p>
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
        setPreferenceVisible(Settings.PREF_GESTURE_PREVIEW_TRAIL, Settings.readGestureInputEnabled(prefs));
        setPreferenceVisible(Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT, Settings.readGestureInputEnabled(prefs));
        setPreferenceVisible(Settings.PREF_GESTURE_SPACE_AWARE, Settings.readGestureInputEnabled(prefs));
        setPreferenceVisible(Settings.PREF_GESTURE_ALWAYS_START, Settings.readGestureInputEnabled(prefs));
    }
}
