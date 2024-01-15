/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.settings;

import static android.util.TypedValue.COMPLEX_UNIT_DIP;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.UserDictionary;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.common.LocaleUtils;
import org.dslul.openboard.inputmethod.latin.utils.DeviceProtectedUtils;
import org.dslul.openboard.inputmethod.latin.utils.SubtypeSettingsKt;

import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

public class UserDictionaryListFragment extends SubScreenFragment {

    public static final String USER_DICTIONARY_SETTINGS_INTENT_ACTION =
            "android.settings.USER_DICTIONARY_SETTINGS";

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

        final Drawable drawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_user_dictionary_add_word, null);
        // Parameters of the LinearLayout containing the button
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(
                LinearLayoutCompat.LayoutParams.WRAP_CONTENT, LinearLayoutCompat.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.END;
        params.setMargins(toPixel(0), toPixel(0), toPixel(23), toPixel(10));

        Button addWordButton = new Button(new ContextThemeWrapper(requireContext(), R.style.User_Dictionary_Button), null, 0);
        addWordButton.setText(R.string.user_dict_add_word_button);
        addWordButton.setTextColor(getResources().getColor(android.R.color.white));
        addWordButton.setPadding(toPixel(30), toPixel(10), toPixel(10), toPixel(10));
        addWordButton.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null);
        addWordButton.setCompoundDrawablePadding(toPixel(20));
        addWordButton.setLayoutParams(params);
        addWordButton.setOnClickListener(v1 -> showAddWordDialog());

        view.addView(addWordButton);

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
        // To combine lists and display them in alphabetical order
        final TreeSet<String> sortedLanguages = new TreeSet<>(String::compareToIgnoreCase);

        // Add "For all languages"
        sortedLanguages.add("");

        // Add the language from the user dictionary if a word is present
        if (enabledUserDictionary != null) {
            sortedLanguages.addAll(enabledUserDictionary);
        }

        // Add the main language selected in the "Language and Layouts" setting except "No language"
        for (InputMethodSubtype mainSubtype : enabledMainSubtype) {
            // Add main subtypes if they are not included in the user's dictionary
            if (enabledUserDictionary != null && !enabledUserDictionary.contains(mainSubtype.getLocale())
                    && !mainSubtype.getLocale().equals("zz")) {
                sortedLanguages.add(mainSubtype.getLocale());
            }
            // Add secondary subtypes only if "Use system languages" setting is not enabled
            if (!localeSystemOnly) {
                for (Locale secondSubtype : Settings.getSecondaryLocales(prefs, mainSubtype.getLocale())) {
                    // Add secondary subtypes if they are not included in the user's dictionary
                    if (enabledUserDictionary != null && !enabledUserDictionary.contains(secondSubtype.toString())) {
                        sortedLanguages.add(secondSubtype.toString());
                    }
                }
            }
        }

        // Add the enabled system languages
        for (Locale systemSubtype : enabledSystemLocale) {
            sortedLanguages.add(systemSubtype.toString());
        }


        // Add preferences
        if (sortedLanguages.isEmpty()) {
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
                // Special treatment for the known languages with _zz and _ZZ types
                if (localeString.endsWith("_zz") || localeString.endsWith("_ZZ")) {
                    final int resId = requireContext().getResources().getIdentifier("subtype_"+localeString, "string", requireContext().getPackageName());
                    newPref.setTitle(requireContext().getString(resId));
                } else {
                    newPref.setTitle(LocaleUtils.constructLocaleFromString(localeString).getDisplayName());
                }
            }
            intent.putExtra("locale", localeString);
            newPref.getExtras().putString("locale", localeString);
        }
        newPref.setKey(localeString);
        newPref.setIconSpaceReserved(false);
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

    private int toPixel(int dp) {
        return (int) TypedValue.applyDimension(COMPLEX_UNIT_DIP, dp,
                requireContext().getResources().getDisplayMetrics());
    }

}