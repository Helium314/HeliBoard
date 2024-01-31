/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.spellcheck;

import android.Manifest;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.preference.SwitchPreference;

import helium314.keyboard.latin.R;
import helium314.keyboard.latin.permissions.PermissionsManager;
import helium314.keyboard.latin.permissions.PermissionsUtil;
import helium314.keyboard.latin.settings.SubScreenFragment;
import helium314.keyboard.latin.utils.ActivityThemeUtils;

/**
 * Preference screen.
 */
public final class SpellCheckerSettingsFragment extends SubScreenFragment
    implements SharedPreferences.OnSharedPreferenceChangeListener,
            PermissionsManager.PermissionsResultCallback {

    private SwitchPreference mLookupContactsPreference;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.spell_checker_settings);
        mLookupContactsPreference = findPreference(AndroidSpellCheckerService.PREF_USE_CONTACTS_KEY);
        turnOffLookupContactsIfNoPermission();

        ActivityThemeUtils.setActivityTheme(requireActivity());
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (!TextUtils.equals(key, AndroidSpellCheckerService.PREF_USE_CONTACTS_KEY) || sharedPreferences.getBoolean(key, false)) {
            return;
        }

        // Check for permissions.
        if (PermissionsUtil.checkAllPermissionsGranted(getContext(), Manifest.permission.READ_CONTACTS)) {
            return; // all permissions granted, no need to request permissions.
        }

        PermissionsManager.get(requireContext()).requestPermissions(this, getActivity(), Manifest.permission.READ_CONTACTS);
    }

    @Override
    public void onRequestPermissionsResult(boolean allGranted) {
        turnOffLookupContactsIfNoPermission();
    }

    private void turnOffLookupContactsIfNoPermission() {
        if (!PermissionsUtil.checkAllPermissionsGranted(
                getActivity(), Manifest.permission.READ_CONTACTS)) {
            mLookupContactsPreference.setChecked(false);
        }
    }
}
