/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.settings;

import android.os.Bundle;

import androidx.preference.Preference;

import org.dslul.openboard.inputmethod.latin.BuildConfig;
import org.dslul.openboard.inputmethod.latin.R;
 
/**
 * "About" sub screen.
 */
public final class AboutFragment extends SubScreenFragment {
    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_about);
        Preference versionPreference = findPreference("pref_key_version");
        versionPreference.setSummary(BuildConfig.VERSION_NAME);
    }
}
