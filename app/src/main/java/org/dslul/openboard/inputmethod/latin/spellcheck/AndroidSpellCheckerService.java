/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dslul.openboard.inputmethod.latin.spellcheck;

import android.content.Intent;
import android.content.SharedPreferences;
import android.service.textservice.SpellCheckerService;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;
import android.view.textservice.SuggestionsInfo;

import org.dslul.openboard.inputmethod.keyboard.Keyboard;
import org.dslul.openboard.inputmethod.keyboard.KeyboardId;
import org.dslul.openboard.inputmethod.keyboard.KeyboardLayoutSet;
import org.dslul.openboard.inputmethod.latin.DictionaryFacilitator;
import org.dslul.openboard.inputmethod.latin.DictionaryFacilitatorLruCache;
import org.dslul.openboard.inputmethod.latin.NgramContext;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.RichInputMethodSubtype;
import org.dslul.openboard.inputmethod.latin.SuggestedWords;
import org.dslul.openboard.inputmethod.latin.common.ComposedData;
import org.dslul.openboard.inputmethod.latin.settings.SettingsValuesForSuggestion;
import org.dslul.openboard.inputmethod.latin.utils.AdditionalSubtypeUtils;
import org.dslul.openboard.inputmethod.latin.utils.DeviceProtectedUtils;
import org.dslul.openboard.inputmethod.latin.utils.ScriptUtils;
import org.dslul.openboard.inputmethod.latin.utils.SuggestionResults;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import javax.annotation.Nonnull;

/**
 * Service for spell checking, using LatinIME's dictionaries and mechanisms.
 */
public final class AndroidSpellCheckerService extends SpellCheckerService
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = AndroidSpellCheckerService.class.getSimpleName();
    private static final boolean DEBUG = false;

    public static final String PREF_USE_CONTACTS_KEY = "pref_spellcheck_use_contacts";

    private static final int SPELLCHECKER_DUMMY_KEYBOARD_WIDTH = 480;
    private static final int SPELLCHECKER_DUMMY_KEYBOARD_HEIGHT = 301;

    private static final String DICTIONARY_NAME_PREFIX = "spellcheck_";

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private final int MAX_NUM_OF_THREADS_READ_DICTIONARY = 2;
    private final Semaphore mSemaphore = new Semaphore(MAX_NUM_OF_THREADS_READ_DICTIONARY,
            true /* fair */);
    // TODO: Make each spell checker session has its own session id.
    private final ConcurrentLinkedQueue<Integer> mSessionIdPool = new ConcurrentLinkedQueue<>();

    private final DictionaryFacilitatorLruCache mDictionaryFacilitatorCache =
            new DictionaryFacilitatorLruCache(this /* context */, DICTIONARY_NAME_PREFIX);
    private final ConcurrentHashMap<Locale, Keyboard> mKeyboardCache = new ConcurrentHashMap<>();

    // The threshold for a suggestion to be considered "recommended".
    private float mRecommendedThreshold;
    // TODO: make a spell checker option to block offensive words or not
    private final SettingsValuesForSuggestion mSettingsValuesForSuggestion =
            new SettingsValuesForSuggestion(true /* blockPotentiallyOffensive */);

    public static final String SINGLE_QUOTE = "\u0027";
    public static final String APOSTROPHE = "\u2019";

    public AndroidSpellCheckerService() {
        super();
        for (int i = 0; i < MAX_NUM_OF_THREADS_READ_DICTIONARY; i++) {
            mSessionIdPool.add(i);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mRecommendedThreshold = Float.parseFloat(
                getString(R.string.spellchecker_recommended_threshold_value));
        final SharedPreferences prefs = DeviceProtectedUtils.getSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
        onSharedPreferenceChanged(prefs, PREF_USE_CONTACTS_KEY);
    }

    public float getRecommendedThreshold() {
        return mRecommendedThreshold;
    }

    private static String getKeyboardLayoutNameForLocale(final Locale locale) {
        // See b/19963288.
        if (locale.getLanguage().equals("sr")) {
            return "south_slavic";
        }
        final int script = ScriptUtils.getScriptFromSpellCheckerLocale(locale);
        switch (script) {
        case ScriptUtils.SCRIPT_LATIN:
            return "qwerty";
        case ScriptUtils.SCRIPT_ARMENIAN:
            return "armenian_phonetic";
        case ScriptUtils.SCRIPT_CYRILLIC:
            return "east_slavic";
        case ScriptUtils.SCRIPT_GREEK:
            return "greek";
        case ScriptUtils.SCRIPT_HEBREW:
            return "hebrew";
        case ScriptUtils.SCRIPT_BULGARIAN:
            return "bulgarian";
        case ScriptUtils.SCRIPT_GEORGIAN:
            return "georgian";
        case ScriptUtils.SCRIPT_BENGALI:
            return "bengali_unijoy";
        default:
            throw new RuntimeException("Wrong script supplied: " + script);
        }
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        if (!PREF_USE_CONTACTS_KEY.equals(key)) return;
        final boolean useContactsDictionary = prefs.getBoolean(PREF_USE_CONTACTS_KEY, true);
        mDictionaryFacilitatorCache.setUseContactsDictionary(useContactsDictionary);
    }

    @Override
    public Session createSession() {
        // Should not refer to AndroidSpellCheckerSession directly considering
        // that AndroidSpellCheckerSession may be overlaid.
        return AndroidSpellCheckerSessionFactory.newInstance(this);
    }

    /**
     * Returns an empty SuggestionsInfo with flags signaling the word is not in the dictionary.
     * @param reportAsTypo whether this should include the flag LOOKS_LIKE_TYPO, for red underline.
     * @return the empty SuggestionsInfo with the appropriate flags set.
     */
    public static SuggestionsInfo getNotInDictEmptySuggestions(final boolean reportAsTypo) {
        return new SuggestionsInfo(reportAsTypo ? SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO : 0,
                EMPTY_STRING_ARRAY);
    }

    /**
     * Returns an empty suggestionInfo with flags signaling the word is in the dictionary.
     * @return the empty SuggestionsInfo with the appropriate flags set.
     */
    public static SuggestionsInfo getInDictEmptySuggestions() {
        return new SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY,
                EMPTY_STRING_ARRAY);
    }

    public boolean isValidWord(final Locale locale, final String word) {
        mSemaphore.acquireUninterruptibly();
        try {
            DictionaryFacilitator dictionaryFacilitatorForLocale =
                    mDictionaryFacilitatorCache.get(locale);
            return dictionaryFacilitatorForLocale.isValidSpellingWord(word);
        } finally {
            mSemaphore.release();
        }
    }

    public SuggestionResults getSuggestionResults(final Locale locale,
            final ComposedData composedData, final NgramContext ngramContext,
            @Nonnull final Keyboard keyboard) {
        Integer sessionId = null;
        mSemaphore.acquireUninterruptibly();
        try {
            sessionId = mSessionIdPool.poll();
            DictionaryFacilitator dictionaryFacilitatorForLocale =
                    mDictionaryFacilitatorCache.get(locale);
            return dictionaryFacilitatorForLocale.getSuggestionResults(composedData, ngramContext,
                    keyboard, mSettingsValuesForSuggestion,
                    sessionId, SuggestedWords.INPUT_STYLE_TYPING);
        } finally {
            if (sessionId != null) {
                mSessionIdPool.add(sessionId);
            }
            mSemaphore.release();
        }
    }

    public boolean hasMainDictionaryForLocale(final Locale locale) {
        mSemaphore.acquireUninterruptibly();
        try {
            final DictionaryFacilitator dictionaryFacilitator =
                    mDictionaryFacilitatorCache.get(locale);
            return dictionaryFacilitator.hasAtLeastOneInitializedMainDictionary();
        } finally {
            mSemaphore.release();
        }
    }

    @Override
    public boolean onUnbind(final Intent intent) {
        mSemaphore.acquireUninterruptibly(MAX_NUM_OF_THREADS_READ_DICTIONARY);
        try {
            mDictionaryFacilitatorCache.closeDictionaries();
        } finally {
            mSemaphore.release(MAX_NUM_OF_THREADS_READ_DICTIONARY);
        }
        mKeyboardCache.clear();
        return false;
    }

    public Keyboard getKeyboardForLocale(final Locale locale) {
        Keyboard keyboard = mKeyboardCache.get(locale);
        if (keyboard == null) {
            keyboard = createKeyboardForLocale(locale);
            if (keyboard != null) {
                mKeyboardCache.put(locale, keyboard);
            }
        }
        return keyboard;
    }

    private Keyboard createKeyboardForLocale(final Locale locale) {
        final String keyboardLayoutName = getKeyboardLayoutNameForLocale(locale);
        final InputMethodSubtype subtype = AdditionalSubtypeUtils.createDummyAdditionalSubtype(
                locale.toString(), keyboardLayoutName);
        final KeyboardLayoutSet keyboardLayoutSet = createKeyboardSetForSpellChecker(subtype);
        return keyboardLayoutSet.getKeyboard(KeyboardId.ELEMENT_ALPHABET);
    }

    private KeyboardLayoutSet createKeyboardSetForSpellChecker(final InputMethodSubtype subtype) {
        final EditorInfo editorInfo = new EditorInfo();
        editorInfo.inputType = InputType.TYPE_CLASS_TEXT;
        final KeyboardLayoutSet.Builder builder = new KeyboardLayoutSet.Builder(this, editorInfo);
        builder.setKeyboardGeometry(
                SPELLCHECKER_DUMMY_KEYBOARD_WIDTH, SPELLCHECKER_DUMMY_KEYBOARD_HEIGHT);
        builder.setSubtype(RichInputMethodSubtype.getRichInputMethodSubtype(subtype));
        builder.setIsSpellChecker(true /* isSpellChecker */);
        builder.disableTouchPositionCorrectionData();
        return builder.build();
    }
}
