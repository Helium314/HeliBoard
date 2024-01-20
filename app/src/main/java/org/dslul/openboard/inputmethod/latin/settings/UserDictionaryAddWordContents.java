/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.settings;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.LocaleList;
import android.provider.UserDictionary;
import android.text.TextUtils;
import android.text.method.DigitsKeyListener;
import android.view.View;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.common.LocaleUtils;
import org.dslul.openboard.inputmethod.latin.utils.DeviceProtectedUtils;
import org.dslul.openboard.inputmethod.latin.utils.SubtypeSettingsKt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

public class UserDictionaryAddWordContents {
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_WORD = "word";
    public static final String EXTRA_WEIGHT = "weight";
    public static final String EXTRA_SHORTCUT = "shortcut";
    public static final String EXTRA_LOCALE = "locale";

    public static final int MODE_EDIT = 0; // To modify a word
    public static final int MODE_INSERT = 1; // To add a new or modified word

    static final int CODE_WORD_ADDED = 0;
    static final int CODE_CANCEL = 1;
    static final int CODE_UPDATED = 2;
    static final int CODE_ALREADY_PRESENT = 3;

    public static final int WEIGHT_FOR_USER_DICTIONARY_ADDS = 250;

    private int mMode; // Either MODE_EDIT or MODE_INSERT
    private final TextView mModeTitle;
    private final EditText mWordEditText;
    private final EditText mShortcutEditText;
    private final EditText mWeightEditText;
    private String mLocaleString;
    private final String mOldWord;
    private final String mOldShortcut;
    private final String mOldWeight;
    private String mSavedWord;
    private String mSavedShortcut;
    private String mSavedWeight;
    private Context mContext;

    UserDictionaryAddWordContents(final View view, final Bundle args) {
        mModeTitle = view.findViewById(R.id.user_dictionary_mode_title);
        mWordEditText = view.findViewById(R.id.user_dictionary_add_word_text);
        mWordEditText.requestFocus();
        mShortcutEditText = view.findViewById(R.id.user_dictionary_add_shortcut);
        mWeightEditText = view.findViewById(R.id.user_dictionary_add_weight);
        mWeightEditText.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
        final Button deleteWordButton = view.findViewById(R.id.user_dictionary_delete_button);

        final String word = args.getString(EXTRA_WORD);
        if (null != word) {
            mWordEditText.setText(word);
            // Use getText in case the edit text modified the text we set. This happens when
            // it's too long to be edited.
            mWordEditText.setSelection(mWordEditText.getText().length());
        }

        final String shortcut = args.getString(EXTRA_SHORTCUT);
        if (null != shortcut && null != mShortcutEditText) {
            mShortcutEditText.setText(shortcut);
        }
        mOldShortcut = args.getString(EXTRA_SHORTCUT);

        final String weight = args.getString(EXTRA_WEIGHT);
        if (null != weight) {
            mWeightEditText.setText(weight);
        }

        mMode = args.getInt(EXTRA_MODE);
        if (mMode == MODE_EDIT) {
            mModeTitle.setText(R.string.user_dict_mode_edit);
            deleteWordButton.setVisibility(View.VISIBLE);
        } else if (mMode == MODE_INSERT) {
            mModeTitle.setText(R.string.user_dict_mode_insert);
            deleteWordButton.setVisibility(View.INVISIBLE);
        }

        mOldWord = args.getString(EXTRA_WORD);
        mOldWeight = args.getString(EXTRA_WEIGHT);
        updateLocale(mContext, args.getString(EXTRA_LOCALE));
    }

    UserDictionaryAddWordContents(final View view, final UserDictionaryAddWordContents oldInstanceToBeEdited) {
        mModeTitle = view.findViewById(R.id.user_dictionary_mode_title);
        mWordEditText = view.findViewById(R.id.user_dictionary_add_word_text);
        mShortcutEditText = view.findViewById(R.id.user_dictionary_add_shortcut);
        mWeightEditText = view.findViewById(R.id.user_dictionary_add_weight);

        mOldWord = oldInstanceToBeEdited.mSavedWord;
        mOldShortcut = oldInstanceToBeEdited.mSavedShortcut;
        mOldWeight = oldInstanceToBeEdited.mSavedWeight;
        updateLocale(mContext, mLocaleString);
    }

    // locale may be null, this means system locale
    // It may also be the empty string, which means "For all languages"
    void updateLocale(final Context context, final String locale) {
        mContext = context;

        mLocaleString = null == locale
                ? mContext.getResources().getConfiguration().locale.toString()
                : locale;
        // The keyboard uses the language layout of the user dictionary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mWordEditText.setImeHintLocales(new LocaleList(LocaleUtils.constructLocaleFromString(mLocaleString)));
        }

    }

    String getLocale() {
        return mLocaleString;
    }

    void delete(final Context context) {
        final ContentResolver resolver = context.getContentResolver();
        final String localeInToast = new LocaleRenderer(context, mLocaleString).toString();
        final String messageDeleted = context.getString(R.string.user_dict_word_deleted, localeInToast);
        // Mode edit: remove the old entry.
        if (MODE_EDIT == mMode && !TextUtils.isEmpty(mOldWord)) {
            UserDictionarySettings.deleteWordInEditMode(mOldWord, mOldShortcut, mOldWeight, mLocaleString, resolver);
            // Toast appears to indicate that the word has been deleted
            Toast.makeText(context, messageDeleted, Toast.LENGTH_SHORT).show();
        }
    }

    public final int apply(@NonNull final Context context) {
        final ContentResolver resolver = context.getContentResolver();
        final String newWord = mWordEditText.getText().toString();
        final String newShortcut;
        final String newWeight;
        final String locale = mLocaleString;

        if (TextUtils.isEmpty(newWord)) {
            // If the word is empty, don't insert it.
            return CODE_CANCEL;
        }

        if (null == mShortcutEditText) {
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

        mSavedWord = newWord;
        mSavedShortcut = newShortcut;
        mSavedWeight = newWeight;

        // In edit mode, everything is modified without overwriting other existing words
        if (MODE_EDIT == mMode && hasWord(newWord, locale, context) && newWord.equals(mOldWord)) {
            UserDictionarySettings.deleteWordInEditMode(mOldWord, mOldShortcut, mOldWeight, locale, resolver);
            // Toast appears to indicate that the word has been modified
            Toast.makeText(context, context.getText(R.string.user_dict_word_modified), Toast.LENGTH_SHORT).show();
        } else {
            mMode = MODE_INSERT;
        }

        if (mMode == MODE_INSERT && hasWord(newWord, locale, context)) {
            return CODE_ALREADY_PRESENT;
        }

        if (mMode == MODE_INSERT) {
            // Delete duplicate when adding or updating new word
            UserDictionarySettings.deleteWordInEditMode(mOldWord, mOldShortcut, mOldWeight, locale, resolver);
            // Update the existing word by adding a new one
            UserDictionary.Words.addWord(context, newWord,
                    Integer.parseInt(newWeight), newShortcut, TextUtils.isEmpty(mLocaleString) ?
                            null : LocaleUtils.constructLocaleFromString(mLocaleString));

            // Toast appears either to indicate that the word has been modified or created
            if (!TextUtils.isEmpty(mOldWord)) {
                Toast.makeText(context, context.getText(R.string.user_dict_word_modified), Toast.LENGTH_SHORT).show();
            } else {
                final String localeInToast = new LocaleRenderer(context, mLocaleString).toString();
                final String messageWordAdded = context.getString(R.string.user_dict_word_added, localeInToast);
                Toast.makeText(context, messageWordAdded, Toast.LENGTH_SHORT).show();
            }
            return CODE_UPDATED;
        }

        // Delete duplicates
        UserDictionarySettings.deleteWord(newWord, locale, resolver);

        // In this class we use the empty string to represent 'all locales' and mLocale cannot
        // be null. However the addWord method takes null to mean 'all locales'.
        UserDictionary.Words.addWord(context, newWord,
                Integer.parseInt(newWeight), newShortcut, TextUtils.isEmpty(mLocaleString) ?
                        null : LocaleUtils.constructLocaleFromString(mLocaleString));

        return CODE_WORD_ADDED;
    }

    public boolean isExistingWord(final Context context) {
        final String newWord = mWordEditText.getText().toString();
        if (mMode == MODE_INSERT || apply(context) == CODE_ALREADY_PRESENT) {
            return hasWord(newWord, mLocaleString, context);
        } else {
            return false;
        }
    }

    private static final String[] HAS_WORD_PROJECTION = { UserDictionary.Words.WORD, UserDictionary.Words.LOCALE };
    private static final String HAS_WORD_AND_LOCALE_SELECTION = UserDictionary.Words.WORD + "=? AND "
            + UserDictionary.Words.LOCALE + "=?";
    private static final String HAS_WORD_AND_ALL_LOCALES_SELECTION = UserDictionary.Words.WORD + "=? AND "
            + UserDictionary.Words.LOCALE + " is null";

    private boolean hasWord(final String word, final String locale, final Context context) {
        final Cursor cursor;

        if ("".equals(locale)) {
            cursor = context.getContentResolver().query(UserDictionary.Words.CONTENT_URI,
                    HAS_WORD_PROJECTION, HAS_WORD_AND_ALL_LOCALES_SELECTION,
                    new String[] { word }, null);
        } else {
            cursor = context.getContentResolver().query(UserDictionary.Words.CONTENT_URI,
                    HAS_WORD_PROJECTION, HAS_WORD_AND_LOCALE_SELECTION,
                    new String[] { word, locale}, null);
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

            if (null == localeString || "".equals(localeString)) {
                mDescription = context.getString(R.string.user_dict_settings_all_languages);
            } else {
                mDescription = UserDictionarySettings.getLocaleDisplayName(context, localeString);
            }
        }
        @Override
        public String toString() {
            return mDescription;
        }
        public String getLocaleString() {
            return mLocaleString;
        }

    }

    private static void addLocaleDisplayNameToList(final Context context,
            final ArrayList<LocaleRenderer> list, final String locale) {
        if (null != locale) {
            list.add(new LocaleRenderer(context, locale));
        }
    }

    // Helper method to get the list of locales and subtypes to display for this word
    public ArrayList<LocaleRenderer> getLocalesList(final Context context) {

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

        // mLocale is removed from the language list as it will be added to the top of the list
        sortedLanguages.remove(mLocaleString);
        // "For all languages" is removed from the language list as it will be added at the end of the list
        sortedLanguages.remove("");

        // final list of locales to show
        final ArrayList<LocaleRenderer> localesList = new ArrayList<>();
        // First, add the language of the personal dictionary at the top of the list
        addLocaleDisplayNameToList(context, localesList, mLocaleString);

        // Next, add all other languages which will be sorted alphabetically in UserDictionaryAddWordFragment.updateSpinner()
        for (String language : sortedLanguages) {
            addLocaleDisplayNameToList(context, localesList, language);
        }

        // Finally, add "All languages" at the end of the list
        if (!"".equals(mLocaleString)) {
            addLocaleDisplayNameToList(context, localesList, "");
        }

        return localesList;
    }

}