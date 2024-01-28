/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.settings;

import static org.dslul.openboard.inputmethod.latin.permissions.PermissionsManager.get;

import android.Manifest;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.SwitchPreferenceCompat;
import androidx.preference.TwoStatePreference;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.permissions.PermissionsManager;
import org.dslul.openboard.inputmethod.latin.permissions.PermissionsUtil;
import org.dslul.openboard.inputmethod.latin.spellcheck.AndroidSpellCheckerService;

public final class CorrectionSettingsFragment extends SubScreenFragment
    implements SharedPreferences.OnSharedPreferenceChangeListener,
        PermissionsManager.PermissionsResultCallback {

    private SwitchPreferenceCompat mLookupContactsPreference;

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_correction);

        mLookupContactsPreference = findPreference(AndroidSpellCheckerService.PREF_USE_CONTACTS_KEY);

        refreshEnabledSettings();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        if (AndroidSpellCheckerService.PREF_USE_CONTACTS_KEY.equals(key)
                && prefs.getBoolean(key, false)
                && !PermissionsUtil.checkAllPermissionsGranted(getActivity(), Manifest.permission.READ_CONTACTS)
        ) {
            get(requireContext()).requestPermissions(this, getActivity(), Manifest.permission.READ_CONTACTS);
        } else if (Settings.PREF_KEY_USE_PERSONALIZED_DICTS.equals(key) && !prefs.getBoolean(key, true)) {
            new AlertDialog.Builder(requireContext())
                    .setMessage(R.string.disable_personalized_dicts_message)
                    .setNegativeButton(android.R.string.cancel, (dialogInterface, i) -> ((TwoStatePreference) findPreference(key)).setChecked(true))
                    .setPositiveButton(android.R.string.ok, null)
                    .setOnCancelListener(dialogInterface -> ((TwoStatePreference) findPreference(key)).setChecked(true))
                    .show();
        } else if (Settings.PREF_SHOW_SUGGESTIONS.equals(key) && !prefs.getBoolean(key, true)) {
            ((TwoStatePreference)findPreference(Settings.PREF_ALWAYS_SHOW_SUGGESTIONS)).setChecked(false);
        }
        refreshEnabledSettings();
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
        setPreferenceVisible(Settings.PREF_AUTO_CORRECTION_CONFIDENCE, Settings.readAutoCorrectEnabled(getSharedPreferences()));
        setPreferenceVisible(Settings.PREF_MORE_AUTO_CORRECTION, Settings.readAutoCorrectEnabled(getSharedPreferences()));
        setPreferenceVisible(Settings.PREF_ADD_TO_PERSONAL_DICTIONARY, getSharedPreferences().getBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, true));
        setPreferenceVisible(Settings.PREF_ALWAYS_SHOW_SUGGESTIONS, getSharedPreferences().getBoolean(Settings.PREF_SHOW_SUGGESTIONS, true));
        turnOffLookupContactsIfNoPermission();
    }

}
