/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.settings;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.preference.Preference;

import org.dslul.openboard.inputmethod.latin.BuildConfig;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.utils.SpannableStringUtils;

/**
 * "About" sub screen.
 */
public final class AboutFragment extends SubScreenFragment {
    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_about);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // need to set icon tint because old android versions don't use the vector drawables
            for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
                final Preference p = getPreferenceScreen().getPreference(0);
                final Drawable icon = p.getIcon();
                if (icon != null)
                    DrawableCompat.setTint(icon, Color.WHITE);
            }
        }

        setupHiddenFeatures();
        setupVersionPref();
    }

    private void setupHiddenFeatures() {
        Preference hiddenFeaturesPreference = findPreference("hidden_features");
        hiddenFeaturesPreference.setOnPreferenceClickListener(preference -> {
            final String link = "<a href=\"https://developer.android.com/reference/android/content/Context#createDeviceProtectedStorageContext()\">"
                    + getString(R.string.hidden_features_text) + "</a>";
            final String message = requireContext().getString(R.string.hidden_features_message, link);
            final Spanned dialogMessage = SpannableStringUtils.fromHtml(message);

            final AlertDialog builder = new AlertDialog.Builder(requireContext())
                    .setIcon(R.drawable.ic_settings_about_hidden_features)
                    .setTitle(R.string.hidden_features_title)
                    .setMessage(dialogMessage)
                    .setPositiveButton(R.string.dialog_close, null)
                    .create();
            builder.show();
            ((TextView) builder.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
            return true;
        });
    }

    private void setupVersionPref() {
        Preference versionPreference = findPreference("pref_key_version");
        versionPreference.setSummary(BuildConfig.VERSION_NAME);
        if (BuildConfig.DEBUG) return;
        int[] count = new int[] {0};
        versionPreference.setOnPreferenceClickListener((pref) -> {
            if (getSharedPreferences().getBoolean(DebugSettings.PREF_SHOW_DEBUG_SETTINGS, false))
                return true;
            count[0]++;
            if (count[0] < 5) return true;
            getSharedPreferences().edit().putBoolean(DebugSettings.PREF_SHOW_DEBUG_SETTINGS, true).apply();
            Toast.makeText(requireContext(), R.string.prefs_debug_settings_enabled, Toast.LENGTH_LONG).show();
            return true;
        });
    }
}
