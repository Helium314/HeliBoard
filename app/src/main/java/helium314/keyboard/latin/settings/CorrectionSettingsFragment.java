/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.settings;

import static helium314.keyboard.latin.permissions.PermissionsManager.get;

import android.Manifest;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.SwitchPreference;
import androidx.preference.TwoStatePreference;

import helium314.keyboard.latin.R;
import helium314.keyboard.latin.permissions.PermissionsManager;
import helium314.keyboard.latin.permissions.PermissionsUtil;

public final class CorrectionSettingsFragment extends SubScreenFragment
    implements SharedPreferences.OnSharedPreferenceChangeListener,
        PermissionsManager.PermissionsResultCallback {

    private SwitchPreference mLookupContactsPreference;

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_correction);

        mLookupContactsPreference = findPreference(Settings.PREF_USE_CONTACTS);

        refreshEnabledSettings();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        if (Settings.PREF_USE_CONTACTS.equals(key)
                && prefs.getBoolean(key, false)
                && !PermissionsUtil.checkAllPermissionsGranted(getActivity(), Manifest.permission.READ_CONTACTS)
        ) {
            get(requireContext()).requestPermissions(this, getActivity(), Manifest.permission.READ_CONTACTS);
        } else if ((Settings.PREF_KEY_USE_PERSONALIZED_DICTS.equals(key) && prefs.getBoolean(Settings.PREF_SHOW_SUGGESTIONS, true)
                || Settings.PREF_SHOW_SUGGESTIONS.equals(key)) && !prefs.getBoolean(key, true)) {
            new AlertDialog.Builder(requireContext())
                    .setMessage(R.string.disable_personalized_dicts_message)
                    .setNegativeButton(android.R.string.cancel, (dialogInterface, i) -> ((TwoStatePreference) findPreference(key)).setChecked(true))
                    .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> refreshEnabledSettings())
                    .setOnCancelListener(dialogInterface -> ((TwoStatePreference) findPreference(key)).setChecked(true))
                    .show();
        } else refreshEnabledSettings();
    }

    // contacts and permission stuff from SpellCheckerSettingsFragment
    @Override
    public void onRequestPermissionsResult(boolean allGranted) {
        turnOffLookupContactsIfNoPermission();
        if (allGranted)
            mLookupContactsPreference.setChecked(true);
    }

    private void turnOffLookupContactsIfNoPermission() {
        if (!PermissionsUtil.checkAllPermissionsGranted(
                getActivity(), Manifest.permission.READ_CONTACTS)) {
            mLookupContactsPreference.setChecked(false);
        }
    }

    private void refreshEnabledSettings() {
        final boolean showSuggestions = getSharedPreferences().getBoolean(Settings.PREF_SHOW_SUGGESTIONS, true);
        setPreferenceVisible(Settings.PREF_AUTO_CORRECTION_CONFIDENCE, Settings.readAutoCorrectEnabled(getSharedPreferences()));
        setPreferenceVisible(Settings.PREF_MORE_AUTO_CORRECTION, Settings.readAutoCorrectEnabled(getSharedPreferences()));
        setPreferenceVisible(Settings.PREF_ADD_TO_PERSONAL_DICTIONARY, showSuggestions
                && getSharedPreferences().getBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, true));
        setPreferenceVisible(Settings.PREF_ALWAYS_SHOW_SUGGESTIONS, showSuggestions);
        setPreferenceVisible(Settings.PREF_BIGRAM_PREDICTIONS, showSuggestions);
        setPreferenceVisible(Settings.PREF_SUGGEST_CLIPBOARD_CONTENT, showSuggestions);
        setPreferenceVisible(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, showSuggestions);
        setPreferenceVisible(Settings.PREF_USE_CONTACTS, showSuggestions);
        turnOffLookupContactsIfNoPermission();
    }

}
