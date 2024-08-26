/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.samyarth.oskey.latin.settings;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;

import org.samyarth.oskey.R;


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
        setupGestureFastTypingCooldownPref();
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
        setPreferenceVisible(Settings.PREF_GESTURE_FAST_TYPING_COOLDOWN, Settings.readGestureInputEnabled(prefs));
    }

    private void setupGestureFastTypingCooldownPref() {
        final SeekBarDialogPreference pref = findPreference(
                Settings.PREF_GESTURE_FAST_TYPING_COOLDOWN);
        if (pref == null) return;
        final SharedPreferences prefs = getSharedPreferences();
        final Resources res = getResources();
        pref.setInterface(new SeekBarDialogPreference.ValueProxy() {
            @Override
            public void writeValue(final int value, final String key) {
                prefs.edit().putInt(key, value).apply();
            }

            @Override
            public void writeDefaultValue(final String key) {
                prefs.edit().remove(key).apply();
            }

            @Override
            public int readValue(final String key) {
                return Settings.readGestureFastTypingCooldown(prefs, res);
            }

            @Override
            public int readDefaultValue(final String key) {
                return Settings.readDefaultGestureFastTypingCooldown(res);
            }

            @Override
            public String getValueText(final int value) {
                if (value == 0) {
                    return res.getString(R.string.gesture_fast_typing_cooldown_instant);
                }
                return res.getString(R.string.abbreviation_unit_milliseconds, String.valueOf(value));
            }

            @Override
            public void feedbackValue(final int value) {
            }
        });
    }
}
