/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.oscar.aikeyboard.latin.settings;

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

import com.oscar.aikeyboard.R;
import com.oscar.aikeyboard.latin.utils.DeviceProtectedUtils;
import com.oscar.aikeyboard.latin.utils.SubtypeLocaleUtils;
import com.oscar.aikeyboard.latin.utils.SubtypeSettingsKt;
import com.oscar.aikeyboard.latin.utils.SubtypeUtilsKt;

import java.util.Comparator;
import java.util.Locale;
import java.util.TreeSet;

import com.oscar.aikeyboard.latin.settings.SubScreenFragment;
import com.oscar.aikeyboard.latin.settings.UserDictionarySettings;

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
        final TreeSet<Locale> sortedLocales = getSortedDictionaryLocales(requireContext());

        // Add preference "for all locales"
        userDictGroup.addPreference(createUserDictionaryPreference(UserDictionarySettings.emptyLocale));
        // Add preference for each dictionary locale
        for (final Locale locale : sortedLocales) {
            userDictGroup.addPreference(createUserDictionaryPreference(locale));
        }
    }

    static TreeSet<Locale> getSortedDictionaryLocales(final Context context) {
        final SharedPreferences prefs = DeviceProtectedUtils.getSharedPreferences(context);
        final boolean localeSystemOnly = prefs.getBoolean(Settings.PREF_USE_SYSTEM_LOCALES, true);
        final TreeSet<Locale> sortedLocales = new TreeSet<>(new LocaleComparator());

        // Add the main language selected in the "Language and Layouts" setting except "No language"
        for (InputMethodSubtype mainSubtype : SubtypeSettingsKt.getEnabledSubtypes(prefs, true)) {
            final Locale mainLocale = SubtypeUtilsKt.locale(mainSubtype);
            if (!mainLocale.toLanguageTag().equals(SubtypeLocaleUtils.NO_LANGUAGE)) {
                sortedLocales.add(mainLocale);
            }
            // Secondary language is added only if main language is selected and if system language is not enabled
            if (!localeSystemOnly) {
                sortedLocales.addAll(Settings.getSecondaryLocales(prefs, mainLocale));
            }
        }

        sortedLocales.addAll(SubtypeSettingsKt.getSystemLocales());
        return sortedLocales;
    }

    private static class LocaleComparator implements Comparator<Locale> {
        @Override
        public int compare(Locale locale1, Locale locale2) {
            return locale1.toLanguageTag().compareToIgnoreCase(locale2.toLanguageTag());
        }
    }

    /**
     * Create a single User Dictionary Preference object, with its parameters set.
     * @param locale The locale for which this user dictionary is for.
     * @return The corresponding preference.
     */
    private Preference createUserDictionaryPreference(@NonNull final Locale locale) {
        final Preference newPref = new Preference(requireContext());

        if (locale.toString().isEmpty()) {
            newPref.setTitle(getString(R.string.user_dict_settings_all_languages));
        } else {
            newPref.setTitle(UserDictionarySettings.getLocaleDisplayName(requireContext(), locale));
        }
        if (locale == UserDictionarySettings.emptyLocale) newPref.getExtras().putString("locale", "");
        else newPref.getExtras().putString("locale", locale.toLanguageTag());
        newPref.setIconSpaceReserved(false);
        newPref.setFragment(UserDictionarySettings.class.getName());

        return newPref;
    }

    private void showAddWordFragment() {
        final Bundle args = new Bundle();
        args.putInt(UserDictionaryAddWordContents.EXTRA_MODE, UserDictionaryAddWordContents.MODE_INSERT);
        args.putString(UserDictionaryAddWordContents.EXTRA_WORD, "");
        args.putString(UserDictionaryAddWordContents.EXTRA_SHORTCUT, "");
        args.putString(UserDictionaryAddWordContents.EXTRA_LOCALE, ""); // Empty means "For all languages"
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        activity.getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, UserDictionaryAddWordFragment.class, args)
                .addToBackStack(null)
                .commit();
    }

}