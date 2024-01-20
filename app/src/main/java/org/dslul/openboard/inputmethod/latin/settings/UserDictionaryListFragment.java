/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.utils.DeviceProtectedUtils;
import org.dslul.openboard.inputmethod.latin.utils.SubtypeSettingsKt;

import java.util.Locale;
import java.util.TreeSet;

public class UserDictionaryListFragment extends SubScreenFragment {

    // TODO : Implement the import/export function in these menus
    /*private static final int OPTIONS_MENU_EXPORT = Menu.NONE;
    private static final int OPTIONS_MENU_IMPORT = Menu.NONE;*/

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(requireContext()));

        createUserDictSettings(getPreferenceScreen());
        // TODO : Uncomment to create the import/export function in the menu
        //setHasOptionsMenu(true);
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

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        LinearLayout view = (LinearLayout) super.onCreateView(inflater, container, savedInstanceState);
        View v = inflater.inflate(R.layout.user_dictionary_settings_list_fragment, null);
        Button addWordButton = v.findViewById(R.id.user_dictionary_add_word_button);
        addWordButton.setOnClickListener(v1 -> showAddWordFragment());
        view.addView(v);
        return view;
    }

    // TODO : Implement the import/export function in these menus
/*    @Override
    public void onCreateOptionsMenu(Menu menu, @NonNull MenuInflater inflater) {
        *//*menu.add(0, OPTIONS_MENU_EXPORT, 0, R.string.button_backup);
        menu.add(0, OPTIONS_MENU_IMPORT, 0, R.string.button_restore);*//*
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == OPTIONS_MENU_EXPORT) {
            return true;
        }
        if (item.getItemId() == OPTIONS_MENU_IMPORT) {
            return true;
        }

        return false;
    }*/

    /**
     * Creates the entries that allow the user to go into the user dictionary for each locale.
     * @param userDictGroup The group to put the settings in.
     */
    private void createUserDictSettings(final PreferenceGroup userDictGroup) {
        final TreeSet<String> sortedLanguages = getSortedDictionaryLocaleStrings(requireContext());

        // Add preference "for all locales"
        userDictGroup.addPreference(createUserDictionaryPreference(""));
        // Add preference for each dictionary locale
        for (String localeUserDictionary : sortedLanguages) {
            userDictGroup.addPreference(createUserDictionaryPreference(localeUserDictionary));
        }
    }

    static TreeSet<String> getSortedDictionaryLocaleStrings(final Context context) {
        final SharedPreferences prefs = DeviceProtectedUtils.getSharedPreferences(context);
        final boolean localeSystemOnly = prefs.getBoolean(Settings.PREF_USE_SYSTEM_LOCALES, true);
        final TreeSet<String> sortedLanguages = new TreeSet<>(String::compareToIgnoreCase);

        // Add the main language selected in the "Language and Layouts" setting except "No language"
        for (InputMethodSubtype mainSubtype : SubtypeSettingsKt.getEnabledSubtypes(prefs, true)) {
            if (!mainSubtype.getLocale().equals("zz")) {
                sortedLanguages.add(mainSubtype.getLocale());
            }
            // Secondary language is added only if main language is selected and if system language is not enabled
            if (!localeSystemOnly) {
                for (Locale secondaryLocale : Settings.getSecondaryLocales(prefs, mainSubtype.getLocale())) {
                    sortedLanguages.add(secondaryLocale.toString());
                }
            }
        }

        for (Locale systemSubtype : SubtypeSettingsKt.getSystemLocales()) {
            sortedLanguages.add(systemSubtype.toString());
        }
        return sortedLanguages;
    }

    /**
     * Create a single User Dictionary Preference object, with its parameters set.
     * @param localeString The locale for which this user dictionary is for.
     * @return The corresponding preference.
     */
    private Preference createUserDictionaryPreference(@NonNull final String localeString) {
        final Preference newPref = new Preference(requireContext());

        if (localeString.isEmpty()) {
            newPref.setTitle(getString(R.string.user_dict_settings_all_languages));
        } else {
            newPref.setTitle(UserDictionarySettings.getLocaleDisplayName(requireContext(), localeString));
        }
        newPref.getExtras().putString("locale", localeString);
        newPref.setIconSpaceReserved(false);
        newPref.setFragment(UserDictionarySettings.class.getName());

        return newPref;
    }

    private void showAddWordFragment() {
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