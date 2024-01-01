/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.userdictionary;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.UserDictionary;
import android.text.TextUtils;
import android.text.method.DigitsKeyListener;
import android.view.View;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.EditText;

import androidx.annotation.Nullable;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.common.LocaleUtils;
import org.dslul.openboard.inputmethod.latin.settings.Settings;
import org.dslul.openboard.inputmethod.latin.settings.SubtypeSettingsKt;
import org.dslul.openboard.inputmethod.latin.settings.UserDictionarySettings;
import org.dslul.openboard.inputmethod.latin.utils.DeviceProtectedUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

// Caveat: This class is basically taken from
// packages/apps/Settings/src/com/android/settings/inputmethod/UserDictionaryAddWordContents.java
// in order to deal with some devices that have issues with the user dictionary handling

/**
 * A container class to factor common code to UserDictionaryAddWordFragment
 * and UserDictionaryAddWordActivity.
 */
public class UserDictionaryAddWordContents {
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_WORD = "word";
    public static final String EXTRA_WEIGHT = "weight";
    public static final String EXTRA_SHORTCUT = "shortcut";
    public static final String EXTRA_LOCALE = "locale";
    public static final String EXTRA_ORIGINAL_WORD = "originalWord";
    public static final String EXTRA_ORIGINAL_SHORTCUT = "originalShortcut";
    public static final String EXTRA_ORIGINAL_WEIGHT = "originalWeight";

    public static final int MODE_EDIT = 0;
    public static final int MODE_INSERT = 1;

    /* package */ static final int CODE_WORD_ADDED = 0;
    /* package */ static final int CODE_CANCEL = 1;
    /* package */ static final int CODE_ALREADY_PRESENT = 2;

    public static final int WEIGHT_FOR_USER_DICTIONARY_ADDS = 250;

    private final int mMode; // Either MODE_EDIT or MODE_INSERT
    private final EditText mWordEditText;
    private final EditText mShortcutEditText;
    private final EditText mWeightEditText;
    private String mLocale;
    private final String mOldWord;
    private final String mOldShortcut;
    private final String mOldWeight;
    private String mSavedWord;
    private String mSavedShortcut;
    private String mSavedWeight;

    /* package */ UserDictionaryAddWordContents(final View view, final Bundle args) {
        mWordEditText = view.findViewById(R.id.user_dictionary_add_word_text);
        mShortcutEditText = view.findViewById(R.id.user_dictionary_add_shortcut);
        mWeightEditText = view.findViewById(R.id.user_dictionary_add_weight);
        mWeightEditText.setKeyListener(DigitsKeyListener.getInstance("0123456789"));

        if (!UserDictionarySettings.IS_SHORTCUT_API_SUPPORTED) {
            mShortcutEditText.setVisibility(View.GONE);
            view.findViewById(R.id.user_dictionary_add_shortcut_label).setVisibility(View.GONE);
        }
        final String word = args.getString(EXTRA_WORD);
        if (null != word) {
            mWordEditText.setText(word);
            // Use getText in case the edit text modified the text we set. This happens when
            // it's too long to be edited.
            mWordEditText.setSelection(mWordEditText.getText().length());
        }
        if (UserDictionarySettings.IS_SHORTCUT_API_SUPPORTED) {
            final String shortcut = args.getString(EXTRA_SHORTCUT);
            if (null != shortcut && null != mShortcutEditText) {
                mShortcutEditText.setText(shortcut);
            }
            mOldShortcut = args.getString(EXTRA_SHORTCUT);
        } else {
            mOldShortcut = null;
        }

        final String weight = args.getString(EXTRA_WEIGHT);
        if (null != weight) {
            mWeightEditText.setText(weight);
        }
        mOldWeight = args.getString(EXTRA_WEIGHT);

        mMode = args.getInt(EXTRA_MODE); // default return value for #getInt() is 0 = MODE_EDIT
        mOldWord = args.getString(EXTRA_WORD);
        updateLocale(args.getString(EXTRA_LOCALE));
    }

    /* package */ UserDictionaryAddWordContents(final View view,
            final UserDictionaryAddWordContents oldInstanceToBeEdited) {
        mWordEditText = view.findViewById(R.id.user_dictionary_add_word_text);
        mShortcutEditText = view.findViewById(R.id.user_dictionary_add_shortcut);
        mWeightEditText = view.findViewById(R.id.user_dictionary_add_weight);
        mMode = MODE_EDIT;
        mOldWord = oldInstanceToBeEdited.mSavedWord;
        mOldShortcut = oldInstanceToBeEdited.mSavedShortcut;
        mOldWeight = oldInstanceToBeEdited.mSavedWeight;
        updateLocale(mLocale);
    }

    // locale may be null, this means default locale
    // It may also be the empty string, which means "all locales"
    /* package */ void updateLocale(final String locale) {
        mLocale = null == locale ? Locale.getDefault().toString() : locale;
    }

    /* package */ void saveStateIntoBundle(final Bundle outState) {
        outState.putString(EXTRA_WORD, mWordEditText.getText().toString());
        outState.putString(EXTRA_ORIGINAL_WORD, mOldWord);

        outState.putString(EXTRA_WEIGHT, mWeightEditText.getText().toString());
        outState.putString(EXTRA_ORIGINAL_WEIGHT, mOldWeight);

        if (null != mShortcutEditText) {
            outState.putString(EXTRA_SHORTCUT, mShortcutEditText.getText().toString());
        }
        if (null != mOldShortcut) {
            outState.putString(EXTRA_ORIGINAL_SHORTCUT, mOldShortcut);
        }
        outState.putString(EXTRA_LOCALE, mLocale);
    }

    /* package */ void delete(final Context context) {
        if (MODE_EDIT == mMode && !TextUtils.isEmpty(mOldWord)) {
            // Mode edit: remove the old entry.
            final ContentResolver resolver = context.getContentResolver();
            UserDictionarySettings.deleteWord(mOldWord, mOldShortcut, resolver);
        }
        // If we are in add mode, nothing was added, so we don't need to do anything.
    }

    /* package */
    int apply(final Context context) {
        final ContentResolver resolver = context.getContentResolver();
        if (MODE_EDIT == mMode && !TextUtils.isEmpty(mOldWord)) {
            // Mode edit: remove the old entry.
            UserDictionarySettings.deleteWord(mOldWord, mOldShortcut, resolver);
        }
        final String newWord = mWordEditText.getText().toString();
        final String newShortcut;
        final String newWeight;
        if (!UserDictionarySettings.IS_SHORTCUT_API_SUPPORTED) {
            newShortcut = null;
        } else if (null == mShortcutEditText) {
            newShortcut = null;
        } else {
            final String tmpShortcut = mShortcutEditText.getText().toString();
            if (TextUtils.isEmpty(tmpShortcut)) {
                newShortcut = null;
            } else {
                newShortcut = tmpShortcut;
            }
        }
        if (mWeightEditText == null) {
            newWeight = String.valueOf(WEIGHT_FOR_USER_DICTIONARY_ADDS);
        } else {
            final String tmpWeight = mWeightEditText.getText().toString();
            if (TextUtils.isEmpty(tmpWeight)) {
                newWeight = String.valueOf(WEIGHT_FOR_USER_DICTIONARY_ADDS);
            } else {
                newWeight = tmpWeight;
            }
        }
        if (TextUtils.isEmpty(newWord)) {
            // If the word is somehow empty, don't insert it.
            return CODE_CANCEL;
        }
        mSavedWord = newWord;
        mSavedShortcut = newShortcut;
        mSavedWeight = newWeight;
        // If there is no shortcut, and the word already exists in the database, then we
        // should not insert, because either A. the word exists with no shortcut, in which
        // case the exact same thing we want to insert is already there, or B. the word
        // exists with at least one shortcut, in which case it has priority on our word.
        if (TextUtils.isEmpty(newShortcut) && hasWord(newWord, context) || TextUtils.isEmpty(newWeight) && hasWord(newWord, context)) {
            return CODE_ALREADY_PRESENT;
        }

        // Disallow duplicates. If the same word with no shortcut is defined, remove it; if
        // the same word with the same shortcut is defined, remove it; but we don't mind if
        // there is the same word with a different, non-empty shortcut.
        UserDictionarySettings.deleteWord(newWord, null, resolver);
        if (!TextUtils.isEmpty(newShortcut)) {
            // If newShortcut is empty we just deleted this, no need to do it again
            UserDictionarySettings.deleteWord(newWord, newShortcut, resolver);
        }

        // In this class we use the empty string to represent 'all locales' and mLocale cannot
        // be null. However the addWord method takes null to mean 'all locales'.
        UserDictionary.Words.addWord(context, newWord,
                Integer.parseInt(newWeight), newShortcut, TextUtils.isEmpty(mLocale) ?
                        null : LocaleUtils.constructLocaleFromString(mLocale));

        return CODE_WORD_ADDED;
    }

    private static final String[] HAS_WORD_PROJECTION = { UserDictionary.Words.WORD };
    private static final String HAS_WORD_SELECTION_ONE_LOCALE = UserDictionary.Words.WORD
            + "=? AND " + UserDictionary.Words.LOCALE + "=?";
    private static final String HAS_WORD_SELECTION_ALL_LOCALES = UserDictionary.Words.WORD
            + "=? AND " + UserDictionary.Words.LOCALE + " is null";
    private boolean hasWord(final String word, final Context context) {
        final Cursor cursor;
        // mLocale == "" indicates this is an entry for all languages. Here, mLocale can't
        // be null at all (it's ensured by the updateLocale method).
        if ("".equals(mLocale)) {
            cursor = context.getContentResolver().query(UserDictionary.Words.CONTENT_URI,
                      HAS_WORD_PROJECTION, HAS_WORD_SELECTION_ALL_LOCALES,
                      new String[] { word }, null /* sort order */);
        } else {
            cursor = context.getContentResolver().query(UserDictionary.Words.CONTENT_URI,
                      HAS_WORD_PROJECTION, HAS_WORD_SELECTION_ONE_LOCALE,
                      new String[] { word, mLocale }, null /* sort order */);
        }
        try {
            if (null == cursor) return false;
            return cursor.getCount() > 0;
        } finally {
            if (null != cursor) cursor.close();
        }
    }

    public static class LocaleRenderer {
        private final String mLocaleString;
        private final String mDescription;

        public LocaleRenderer(final Context context, @Nullable final String localeString) {
            mLocaleString = localeString;
            // TODO: To be reactivated when UserDictionaryLocalePicker.UserDictionaryLocalePicker() is implemented
/*            if (null == localeString) {
                mDescription = context.getString(R.string.user_dict_settings_more_languages);
            } else if ("".equals(localeString)) {
                mDescription = context.getString(R.string.user_dict_settings_all_languages);
            } else {
                mDescription = LocaleUtils.constructLocaleFromString(localeString).getDisplayName();
            }*/
            // TODO: To be deleted when UserDictionaryLocalePicker.UserDictionaryLocalePicker() is implemented
            if (null == localeString || "".equals(localeString)) {
                mDescription = context.getString(R.string.user_dict_settings_all_languages);
            } else {
                mDescription = LocaleUtils.constructLocaleFromString(localeString).getDisplayName();
            }
        }
        @Override
        public String toString() {
            return mDescription;
        }
        public String getLocaleString() {
            return mLocaleString;
        }
        // "More languages..." is null ; "All languages" is the empty string.
        // TODO: To be reactivated when UserDictionaryLocalePicker.UserDictionaryLocalePicker() is implemented
/*        public boolean isMoreLanguages() {
            return null == mLocaleString;
        }*/
    }

    private static void addLocaleDisplayNameToList(final Context context,
            final ArrayList<LocaleRenderer> list, final String locale) {
        if (null != locale) {
            list.add(new LocaleRenderer(context, locale));
        }
    }

    // Helper method to get the list of locales and subtypes to display for this word
    public ArrayList<LocaleRenderer> getLocalesList(final Activity activity) {

        final SharedPreferences prefs = DeviceProtectedUtils.getSharedPreferences(activity.getApplicationContext());
        final boolean localeSystemOnly = prefs.getBoolean(Settings.PREF_USE_SYSTEM_LOCALES, true);
        final ArrayList<LocaleRenderer> localesList = new ArrayList<>();

        // List of main language
        final List<InputMethodSubtype> enabledMainSubtype = SubtypeSettingsKt.getEnabledSubtypes(prefs, true);
        // List of system language
        final List<Locale> enabledSystemLocale = SubtypeSettingsKt.getSystemLocales();
        // List of user dictionary
        final TreeSet<String> userDictionaryList = UserDictionaryList.getUserDictionaryLocalesSet(activity);

        // List of all enabled main languages
        Set<String> mainLocale = new HashSet<>();
        for (InputMethodSubtype subtype : enabledMainSubtype) {
            Locale locale = LocaleUtils.constructLocaleFromString(subtype.getLocale());
            String localeString = LocaleUtils.getLocaleDisplayNameInSystemLocale(locale, activity).toLowerCase();
            mainLocale.add(localeString);
            // To avoid duplicates between the main language and the user dictionary language
            if (localeString.equals(mLocale)) {
                mainLocale.remove(mLocale);
            }
            // To avoid duplicates between the main language and the language of the user dictionary list
            if (userDictionaryList != null) {
                for (String userDictionarySubtype : userDictionaryList) {
                    Locale userDictionaryLocale = LocaleUtils.constructLocaleFromString(String.valueOf(userDictionarySubtype));
                    String userDictionaryLocaleString = LocaleUtils.getLocaleDisplayNameInSystemLocale(userDictionaryLocale, activity).toLowerCase();
                    if (localeString.equals(userDictionaryLocaleString)) {
                        mainLocale.remove(userDictionaryLocaleString);
                    }
                }
            }
            // To avoid duplicates between the main language and the system language
            for (Locale systemSubtype : enabledSystemLocale) {
                Locale systemLocale = LocaleUtils.constructLocaleFromString(String.valueOf(systemSubtype));
                String systemLocaleString = LocaleUtils.getLocaleDisplayNameInSystemLocale(systemLocale, activity).toLowerCase();
                if (localeString.equals(systemLocaleString)) {
                    mainLocale.remove(systemLocaleString);
                }
            }
            // Secondary language is added only if main language is selected and if system language is not enabled,
            // so we write it here
            for (Locale secondSubtype : Settings.getSecondaryLocales(prefs, String.valueOf(locale))) {
                Locale secondLocale = LocaleUtils.constructLocaleFromString(String.valueOf(secondSubtype));
                String secondLocaleString = LocaleUtils.getLocaleDisplayNameInSystemLocale(secondLocale, activity).toLowerCase();
                if (!localeSystemOnly) {
                    mainLocale.add(secondLocaleString);
                }
                // To avoid duplicates between the secondary language and the user dictionary language
                if (secondLocaleString.equals(mLocale)) {
                    mainLocale.remove(mLocale);
                }
                // To avoid duplicates between the main language and the secondary language
                if (localeString.equals(secondLocaleString)) {
                    mainLocale.remove(secondLocaleString);
                }
                // To avoid duplicates between the secondary language and the language of the user dictionary list
                if (userDictionaryList != null) {
                    for (String userDictionarySubtype : userDictionaryList) {
                        Locale userDictionaryLocale = LocaleUtils.constructLocaleFromString(String.valueOf(userDictionarySubtype));
                        String userDictionaryLocaleString = LocaleUtils.getLocaleDisplayNameInSystemLocale(userDictionaryLocale, activity).toLowerCase();
                        if (secondLocaleString.equals(userDictionaryLocaleString)) {
                            mainLocale.remove(userDictionaryLocaleString);
                        }
                    }
                }
                // To avoid duplicates between the secondary language and the system language
                for (Locale systemSubtype : enabledSystemLocale) {
                    Locale systemLocale = LocaleUtils.constructLocaleFromString(String.valueOf(systemSubtype));
                    String systemLocaleString = LocaleUtils.getLocaleDisplayNameInSystemLocale(systemLocale, activity).toLowerCase();
                    if (secondLocaleString.equals(systemLocaleString)) {
                        mainLocale.remove(systemLocaleString);
                    }
                }
            }
        }

        // List of all enabled user dictionary languages
        Set<String> userDictionaryLocale = new HashSet<>();
        if (userDictionaryList != null) {
            for (String userDictionarySubtype : userDictionaryList) {
                Locale locale = LocaleUtils.constructLocaleFromString(String.valueOf(userDictionarySubtype));
                String localeString = LocaleUtils.getLocaleDisplayNameInSystemLocale(locale, activity).toLowerCase();
                // All user dictionary languages are added except "For all languages"
                if (!locale.toString().equals("")) {
                    userDictionaryLocale.add(localeString);
                }
                // To avoid duplicates between the language of the user dictionary list and the language of the user dictionary
                if (localeString.equals(mLocale)) {
                    userDictionaryLocale.remove(mLocale);
                }
                // To avoid duplicates between the language of the user dictionary list and the system language
                for (Locale systemSubtype : enabledSystemLocale) {
                    Locale systemLocale = LocaleUtils.constructLocaleFromString(String.valueOf(systemSubtype));
                    String systemLocaleString = LocaleUtils.getLocaleDisplayNameInSystemLocale(systemLocale, activity).toLowerCase();
                    if (localeString.equals(systemLocaleString)) {
                        userDictionaryLocale.remove(systemLocaleString);
                    }
                }
            }
        }

        // List of all enabled system languages
        Set<String> systemLocales = new HashSet<>();
        for (Locale subtype : enabledSystemLocale) {
            Locale locale = LocaleUtils.constructLocaleFromString(String.valueOf(subtype));
            String localeString = LocaleUtils.getLocaleDisplayNameInSystemLocale(locale, activity).toLowerCase();
            systemLocales.add(localeString);
            if (localeString.equals(mLocale)) {
                systemLocales.remove(mLocale);
            }
        }

        // First, add the language of the personal dictionary at the top of the list
        addLocaleDisplayNameToList(activity, localesList, mLocale);

        // Next, add the main language (and secondary language if defined)
        for (final String mainLanguage : mainLocale) {
            addLocaleDisplayNameToList(activity, localesList, mainLanguage);
        }

        // Next, add the language of the user dictionary list
        for (final String userDictionaryLanguage : userDictionaryLocale) {
            addLocaleDisplayNameToList(activity, localesList, userDictionaryLanguage);
        }

        // Next, add the system language
        for (final String systemLanguage : systemLocales) {
            addLocaleDisplayNameToList(activity, localesList, systemLanguage);
        }

        // Finally, add "All languages" at the end of the list
        if (!"".equals(mLocale)) {
            // If mLocale is "", then we already inserted the "all languages" item, so don't do it
            addLocaleDisplayNameToList(activity, localesList, ""); // meaning: all languages
        }

        // In edit mode from the user dictionary list, "For all languages" is at the top of the list
        // and the other languages are sorted alphabetically
        if ("".equals(mLocale)) {
            Collections.sort(localesList, (locale1, locale2)
                    -> locale1.getLocaleString().compareToIgnoreCase(locale2.getLocaleString()));
        }

        // TODO: To be reactivated when UserDictionaryLocalePicker.UserDictionaryLocalePicker() is implemented
        // localesList.add(new LocaleRenderer(activity, null)); // meaning: select another locale
        return localesList;
    }

    public String getDropDownMenuLanguage() {
        return mLocale;
    }

}

