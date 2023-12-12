/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.settings;

import static org.dslul.openboard.inputmethod.latin.permissions.PermissionsManager.get;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;
import androidx.preference.TwoStatePreference;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.permissions.PermissionsManager;
import org.dslul.openboard.inputmethod.latin.permissions.PermissionsUtil;
import org.dslul.openboard.inputmethod.latin.spellcheck.AndroidSpellCheckerService;
import org.dslul.openboard.inputmethod.latin.userdictionary.UserDictionaryList;
import org.dslul.openboard.inputmethod.latin.userdictionary.UserDictionarySettings;

import java.util.TreeSet;

/**
 * "Text correction" settings sub screen.
 *
 * This settings sub screen handles the following text correction preferences.
 * - Personal dictionary
 * - Add-on dictionaries
 * - Block offensive words
 * - Auto-correction
 * - Auto-correction confidence
 * - Show correction suggestions
 * - Personalized suggestions
 * - Suggest Contact names
 * - Next-word suggestions
 */
public final class CorrectionSettingsFragment extends SubScreenFragment
    implements SharedPreferences.OnSharedPreferenceChangeListener,
        PermissionsManager.PermissionsResultCallback {

    private static final boolean OPENBOARD_PERSONAL_DICTIONARY_SETTINGS = true;
    private boolean SYSTEM_PERSONAL_DICTIONARY_SETTINGS() {
        final SettingsValues settingsValues = Settings.getInstance().getCurrent();
        if (settingsValues.mOpenboardPersonalDictionary) {
            return OPENBOARD_PERSONAL_DICTIONARY_SETTINGS;
        }
        return false;
    }

    private SwitchPreferenceCompat mLookupContactsPreference;

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_correction);

        final PackageManager pm = requireContext().getPackageManager();

        final Preference editPersonalDictionary =
                findPreference(Settings.PREF_EDIT_PERSONAL_DICTIONARY);
        final Intent editPersonalDictionaryIntent = editPersonalDictionary.getIntent();
        final ResolveInfo ri = SYSTEM_PERSONAL_DICTIONARY_SETTINGS() ? null
                : pm.resolveActivity(editPersonalDictionaryIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (ri == null) {
            overwriteUserDictionaryPreference(editPersonalDictionary);
        }
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
                    .show();
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
        setPreferenceVisible(Settings.PREF_AUTO_CORRECTION_CONFIDENCE,
                Settings.readAutoCorrectEnabled(getSharedPreferences()));
        setPreferenceVisible(Settings.PREF_ADD_TO_PERSONAL_DICTIONARY, getSharedPreferences().getBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, true));
        turnOffLookupContactsIfNoPermission();
    }

    private void overwriteUserDictionaryPreference(final Preference userDictionaryPreference) {
        final TreeSet<String> localeList = UserDictionaryList.getUserDictionaryLocalesSet(requireActivity());
        if (null == localeList) {
            // The locale list is null if and only if the user dictionary service is
            // not present or disabled. In this case we need to remove the preference.
            getPreferenceScreen().removePreference(userDictionaryPreference);
        } else if (localeList.size() <= 1) {
            userDictionaryPreference.setFragment(UserDictionarySettings.class.getName());
            // If the size of localeList is 0, we don't set the locale parameter in the
            // extras. This will be interpreted by the UserDictionarySettings class as
            // meaning "the current locale".
            // Note that with the current code for UserDictionaryList#getUserDictionaryLocalesSet()
            // the locale list always has at least one element, since it always includes the current
            // locale explicitly. @see UserDictionaryList.getUserDictionaryLocalesSet().
            if (localeList.size() == 1) {
                final String locale = (String)localeList.toArray()[0];
                userDictionaryPreference.getExtras().putString("locale", locale);
            }
        } else {
            userDictionaryPreference.setFragment(UserDictionaryList.class.getName());
        }
    }

}
