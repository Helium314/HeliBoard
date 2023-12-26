/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.userdictionary;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.provider.UserDictionary;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.common.LocaleUtils;

import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

// Caveat: This class is basically taken from
// packages/apps/Settings/src/com/android/settings/inputmethod/UserDictionaryList.java
// in order to deal with some devices that have issues with the user dictionary handling

public class UserDictionaryList extends PreferenceFragmentCompat {

    public static final String USER_DICTIONARY_SETTINGS_INTENT_ACTION =
            "android.settings.USER_DICTIONARY_SETTINGS";

    @Override
    public void onCreatePreferences(@Nullable Bundle bundle, @Nullable String s) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            getPreferenceManager().setStorageDeviceProtected();
        }
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(requireContext()));
    }

    public static TreeSet<String> getUserDictionaryLocalesSet(final Activity activity) {
        final Cursor cursor = activity.getContentResolver().query(UserDictionary.Words.CONTENT_URI,
                new String[] { UserDictionary.Words.LOCALE },
                null, null, null);
        final TreeSet<String> localeSet = new TreeSet<>();
        if (null == cursor) {
            // The user dictionary service is not present or disabled. Return null.
            return null;
        }
        try {
            if (cursor.moveToFirst()) {
                final int columnIndex = cursor.getColumnIndex(UserDictionary.Words.LOCALE);
                do {
                    final String locale = cursor.getString(columnIndex);
                    localeSet.add(null != locale ? locale : "");
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }
        if (!UserDictionarySettings.IS_SHORTCUT_API_SUPPORTED) {
            // For ICS, we need to show "For all languages" in case that the keyboard locale
            // is different from the system locale
            localeSet.add("");
        }

        final InputMethodManager imm =
                (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        final List<InputMethodInfo> imis = imm.getEnabledInputMethodList();
        for (final InputMethodInfo imi : imis) {
            final List<InputMethodSubtype> subtypes =
                    imm.getEnabledInputMethodSubtypeList(
                            imi, true /* allowsImplicitlySelectedSubtypes */);
            for (InputMethodSubtype subtype : subtypes) {
                final String locale = subtype.getLocale();
                if (!TextUtils.isEmpty(locale)) {
                    localeSet.add(locale);
                }
            }
        }

        // We come here after we have collected locales from existing user dictionary entries and
        // enabled subtypes. If we already have the locale-without-country version of the system
        // locale, we don't add the system locale to avoid confusion even though it's technically
        // correct to add it.
        if (!localeSet.contains(Locale.getDefault().getLanguage())) {
            localeSet.add(Locale.getDefault().toString());
        }

        return localeSet;
    }

    /**
     * Creates the entries that allow the user to go into the user dictionary for each locale.
     * @param userDictGroup The group to put the settings in.
     */
    protected void createUserDictSettings(final PreferenceGroup userDictGroup) {
        userDictGroup.removeAll();
        final TreeSet<String> localeSet = UserDictionaryList.getUserDictionaryLocalesSet(requireActivity());
        if (localeSet == null) {
            userDictGroup.addPreference(createUserDictionaryPreference(null));
            return;
        }

        if (localeSet.size() > 1) {
            // Have an "All languages" entry in the languages list if there are two or more active
            // languages
            localeSet.add("");
        }

        if (localeSet.isEmpty()) {
            userDictGroup.addPreference(createUserDictionaryPreference(null));
        } else {
            for (String locale : localeSet) {
                userDictGroup.addPreference(createUserDictionaryPreference(locale));
            }
        }
    }

    /**
     * Create a single User Dictionary Preference object, with its parameters set.
     * @param localeString The locale for which this user dictionary is for.
     * @return The corresponding preference.
     */
    protected Preference createUserDictionaryPreference(@Nullable final String localeString) {
        final Preference newPref = new Preference(requireContext());
        final Intent intent = new Intent(USER_DICTIONARY_SETTINGS_INTENT_ACTION);
        final ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.edit_personal_dictionary);
        }

        if (null == localeString) {
            newPref.setTitle(Locale.getDefault().getDisplayName());
        } else {
            if (localeString.isEmpty()) {
                newPref.setTitle(getString(R.string.user_dict_settings_all_languages));
            } else {
                newPref.setTitle(
                        LocaleUtils.constructLocaleFromString(localeString).getDisplayName());
            }
            intent.putExtra("locale", localeString);
            newPref.getExtras().putString("locale", localeString);
        }
        newPref.setIntent(intent);
        newPref.setFragment(UserDictionarySettings.class.getName());
        return newPref;
    }

    @Override
    public void onResume() {
        super.onResume();
        createUserDictSettings(getPreferenceScreen());
    }
}

