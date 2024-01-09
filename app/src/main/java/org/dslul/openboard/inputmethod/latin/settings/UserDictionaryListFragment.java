/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.UserDictionary;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.common.LocaleUtils;
import org.dslul.openboard.inputmethod.latin.utils.DeviceProtectedUtils;
import org.dslul.openboard.inputmethod.latin.utils.SubtypeSettingsKt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

// Caveat: This class is basically taken from
// packages/apps/Settings/src/com/android/settings/inputmethod/UserDictionaryList.java
// in order to deal with some devices that have issues with the user dictionary handling

public class UserDictionaryListFragment extends SubScreenFragment {

    public static final String USER_DICTIONARY_SETTINGS_INTENT_ACTION =
            "android.settings.USER_DICTIONARY_SETTINGS";
    private static final int OPTIONS_MENU_ADD = Menu.FIRST;
    // Todo : Implement the import/export function in these menus
    /*private static final int OPTIONS_MENU_EXPORT = Menu.NONE;
    private static final int OPTIONS_MENU_IMPORT = Menu.NONE;*/

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(requireContext()));

        createUserDictSettings(getPreferenceScreen());

        setHasOptionsMenu(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        final ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.edit_personal_dictionary);
            actionBar.setSubtitle(null);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, @NonNull MenuInflater inflater) {
        // Todo : Implement the import/export function in these menus
        /*menu.add(0, OPTIONS_MENU_EXPORT, 0, R.string.button_backup);
        menu.add(0, OPTIONS_MENU_IMPORT, 0, R.string.button_restore);*/

        MenuItem actionItem = menu.add(0, OPTIONS_MENU_ADD, 0, R.string.user_dict_settings_add_menu_title)
                .setIcon(R.drawable.ic_plus);
        actionItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Todo : Implement the import/export function in these menus
        /*if (item.getItemId() == OPTIONS_MENU_EXPORT) {
        }
        if (item.getItemId() == OPTIONS_MENU_IMPORT) {
        }*/

        if (item.getItemId() == OPTIONS_MENU_ADD) {
            showAddWordDialog();
            return true;
        }
        return false;
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

        final InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        final List<InputMethodInfo> imis = imm.getEnabledInputMethodList();

        for (final InputMethodInfo imi : imis) {
            final List<InputMethodSubtype> subtypes = imm.getEnabledInputMethodSubtypeList(imi, true);
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
        final SharedPreferences prefs = DeviceProtectedUtils.getSharedPreferences(requireActivity().getApplicationContext());
        final boolean localeSystemOnly = prefs.getBoolean(Settings.PREF_USE_SYSTEM_LOCALES, true);
        // List of enabled user dictionary languages
        final TreeSet<String> enabledUserDictionary = getUserDictionaryLocalesSet(requireActivity());
        // List of main language
        final List<InputMethodSubtype> enabledMainSubtype = SubtypeSettingsKt.getEnabledSubtypes(prefs, true);
        // List of enabled system language
        final List<Locale> enabledSystemLocale = SubtypeSettingsKt.getSystemLocales();
        // To combine lists and handle duplicates automatically
        LinkedHashSet<String> userDictionaryLanguage = new LinkedHashSet<>();

        // Add "For all languages"
        userDictionaryLanguage.add("");

        // Add the language from the user dictionary if a word is present
        if (enabledUserDictionary != null) {
            userDictionaryLanguage.addAll(enabledUserDictionary);
        }

        // Add the main language selected in the "Language and Layouts" setting
        for (InputMethodSubtype mainSubtype : enabledMainSubtype) {
            Locale mainLocale = LocaleUtils.constructLocaleFromString(mainSubtype.getLocale());
            String mainLocaleString = mainLocale.toString();
            // Special treatment for the known languages with _ZZ and _zz types
            if (mainLocaleString.endsWith("_ZZ") || mainLocaleString.endsWith("_zz") || mainLocaleString.equals("zz")) {
                mainLocaleString = LocaleUtils.getLocaleDisplayNameInSystemLocale(mainLocale, requireActivity()).toLowerCase();
            }
            userDictionaryLanguage.add(mainLocaleString);

            // Add secondary language only if main language is selected and "Use system languages" setting is not enabled
            for (Locale secondSubtype : Settings.getSecondaryLocales(prefs, String.valueOf(mainLocale))) {
                Locale secondLocale = LocaleUtils.constructLocaleFromString(String.valueOf(secondSubtype));
                String secondLocaleString = secondLocale.toString();
                // Special treatment for the known languages with _ZZ and _zz types
                if (secondLocaleString.endsWith("_ZZ") || secondLocaleString.endsWith("_zz") || secondLocaleString.equals("zz")) {
                    secondLocaleString = LocaleUtils.getLocaleDisplayNameInSystemLocale(secondLocale, requireActivity()).toLowerCase();
                }
                if (!localeSystemOnly) {
                    userDictionaryLanguage.add(secondLocaleString);
                }
            }
        }

        // Finally, add the enabled system languages
        for (Locale subtype : enabledSystemLocale) {
            Locale systemLocale = LocaleUtils.constructLocaleFromString(String.valueOf(subtype));
            String systemLocaleString = systemLocale.toString();
            // Special treatment for the known languages with _ZZ and _zz types
            if (systemLocaleString.endsWith("_ZZ") || systemLocaleString.endsWith("_zz") || systemLocaleString.equals("zz")) {
                systemLocaleString = LocaleUtils.getLocaleDisplayNameInSystemLocale(systemLocale, requireActivity()).toLowerCase();
            }
            userDictionaryLanguage.add(systemLocaleString);
        }

        // Sort languages alphabetically
        ArrayList<String> sortedLanguages = new ArrayList<>(userDictionaryLanguage);
        Collections.sort(sortedLanguages, String::compareToIgnoreCase);

        // Add preferences
        if (userDictionaryLanguage.isEmpty()) {
            userDictGroup.addPreference(createUserDictionaryPreference(null));
        } else {
            for (String localeUserDictionary : sortedLanguages) {
                userDictGroup.addPreference(createUserDictionaryPreference(localeUserDictionary));
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
        newPref.setKey(localeString);
        newPref.setIntent(intent);
        newPref.setFragment(UserDictionarySettings.class.getName());

        return newPref;
    }

    private void showAddWordDialog() {
        final Bundle args = new Bundle();
        args.putInt(UserDictionaryAddWordContents.EXTRA_MODE, UserDictionaryAddWordContents.MODE_INSERT);
        args.putString(UserDictionaryAddWordContents.EXTRA_WORD, "");
        args.putString(UserDictionaryAddWordContents.EXTRA_SHORTCUT, "");
        args.putString(UserDictionaryAddWordContents.EXTRA_WEIGHT,
                String.valueOf(UserDictionaryAddWordContents.WEIGHT_FOR_USER_DICTIONARY_ADDS));
        args.putString(UserDictionaryAddWordContents.EXTRA_LOCALE, ""); // Empty means "For all languages"
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        activity.getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, UserDictionaryAddWordFragment.class, args)
                .addToBackStack(null)
                .commit();
    }

}

