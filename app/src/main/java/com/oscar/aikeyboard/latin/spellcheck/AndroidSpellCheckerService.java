/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.oscar.aikeyboard.latin.spellcheck;

import android.content.Intent;
import android.content.SharedPreferences;
import android.service.textservice.SpellCheckerService;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;
import android.view.textservice.SuggestionsInfo;

import androidx.annotation.NonNull;

import com.oscar.aikeyboard.keyboard.Keyboard;
import com.oscar.aikeyboard.keyboard.KeyboardId;
import com.oscar.aikeyboard.keyboard.KeyboardLayoutSet;
import com.oscar.aikeyboard.latin.DictionaryFacilitator;
import com.oscar.aikeyboard.latin.DictionaryFacilitatorLruCache;
import com.oscar.aikeyboard.latin.InputAttributes;
import com.oscar.aikeyboard.latin.NgramContext;
import org.samyarth.oskey.R;
import com.oscar.aikeyboard.latin.RichInputMethodSubtype;
import com.oscar.aikeyboard.latin.SuggestedWords;
import com.oscar.aikeyboard.latin.common.ComposedData;
import com.oscar.aikeyboard.latin.settings.Settings;
import com.oscar.aikeyboard.latin.settings.SettingsValuesForSuggestion;
import com.oscar.aikeyboard.latin.utils.AdditionalSubtypeUtils;
import com.oscar.aikeyboard.latin.utils.DeviceProtectedUtils;
import com.oscar.aikeyboard.latin.utils.SubtypeSettingsKt;
import com.oscar.aikeyboard.latin.utils.SuggestionResults;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

/**
 * Service for spell checking, using LatinIME's dictionaries and mechanisms.
 */
public final class AndroidSpellCheckerService extends SpellCheckerService
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final int SPELLCHECKER_DUMMY_KEYBOARD_WIDTH = 480;
    public static final int SPELLCHECKER_DUMMY_KEYBOARD_HEIGHT = 301;

    private static final String DICTIONARY_NAME_PREFIX = "spellcheck_";

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private final int MAX_NUM_OF_THREADS_READ_DICTIONARY = 2;
    private final Semaphore mSemaphore = new Semaphore(MAX_NUM_OF_THREADS_READ_DICTIONARY, true);
    // TODO: Make each spell checker session has its own session id.
    private final ConcurrentLinkedQueue<Integer> mSessionIdPool = new ConcurrentLinkedQueue<>();

    private final DictionaryFacilitatorLruCache mDictionaryFacilitatorCache =
            new DictionaryFacilitatorLruCache(this, DICTIONARY_NAME_PREFIX);
    private final ConcurrentHashMap<Locale, Keyboard> mKeyboardCache = new ConcurrentHashMap<>();

    // The threshold for a suggestion to be considered "recommended".
    private float mRecommendedThreshold;
    private SettingsValuesForSuggestion mSettingsValuesForSuggestion;

    public static final String SINGLE_QUOTE = "'";
    public static final String APOSTROPHE = "’";

    public AndroidSpellCheckerService() {
        super();
        for (int i = 0; i < MAX_NUM_OF_THREADS_READ_DICTIONARY; i++) {
            mSessionIdPool.add(i);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mRecommendedThreshold = Float.parseFloat(getString(R.string.spellchecker_recommended_threshold_value));
        final SharedPreferences prefs = DeviceProtectedUtils.getSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
        onSharedPreferenceChanged(prefs, Settings.PREF_USE_CONTACTS);
        final boolean blockOffensive = Settings.readBlockPotentiallyOffensive(prefs, getResources());
        mSettingsValuesForSuggestion = new SettingsValuesForSuggestion(blockOffensive, false);
        SubtypeSettingsKt.init(this);
    }

    public float getRecommendedThreshold() {
        return mRecommendedThreshold;
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        if (Settings.PREF_USE_CONTACTS.equals(key)) {
            final boolean useContactsDictionary = prefs.getBoolean(Settings.PREF_USE_CONTACTS, true);
            mDictionaryFacilitatorCache.setUseContactsDictionary(useContactsDictionary);
        } else if (Settings.PREF_BLOCK_POTENTIALLY_OFFENSIVE.equals(key)) {
            final boolean blockOffensive = Settings.readBlockPotentiallyOffensive(prefs, getResources());
            mSettingsValuesForSuggestion = new SettingsValuesForSuggestion(blockOffensive, false);
        }
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
        return new SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, EMPTY_STRING_ARRAY);
    }

    public boolean isValidWord(final Locale locale, final String word) {
        mSemaphore.acquireUninterruptibly();
        try {
            DictionaryFacilitator dictionaryFacilitatorForLocale = mDictionaryFacilitatorCache.get(locale);
            return dictionaryFacilitatorForLocale.isValidSpellingWord(word);
        } finally {
            mSemaphore.release();
        }
    }

    public SuggestionResults getSuggestionResults(final Locale locale,
            final ComposedData composedData, final NgramContext ngramContext,
            @NonNull final Keyboard keyboard) {
        Integer sessionId = null;
        mSemaphore.acquireUninterruptibly();
        try {
            sessionId = mSessionIdPool.poll();
            DictionaryFacilitator dictionaryFacilitatorForLocale = mDictionaryFacilitatorCache.get(locale);
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
            mKeyboardCache.put(locale, keyboard);
        }
        return keyboard;
    }

    private Keyboard createKeyboardForLocale(final Locale locale) {
        if (Settings.getInstance().getCurrent() == null) {
            // creating a keyboard reads SettingsValues from Settings instance
            // maybe it would be "more correct" to create an instance of SettingsValues and use that one instead
            // but creating a global one if not existing should be fine too
            Settings.init(this);
            final EditorInfo editorInfo = new EditorInfo();
            editorInfo.inputType = InputType.TYPE_CLASS_TEXT;
            Settings.getInstance().loadSettings(this, locale, new InputAttributes(editorInfo, false, getPackageName()));
        }
        final String keyboardLayoutName = SubtypeSettingsKt.getMatchingLayoutSetNameForLocale(locale);
        final InputMethodSubtype subtype = AdditionalSubtypeUtils.createDummyAdditionalSubtype(locale, keyboardLayoutName);
        final KeyboardLayoutSet keyboardLayoutSet = createKeyboardSetForSpellChecker(subtype);
        return keyboardLayoutSet.getKeyboard(KeyboardId.ELEMENT_ALPHABET);
    }

    private KeyboardLayoutSet createKeyboardSetForSpellChecker(final InputMethodSubtype subtype) {
        final EditorInfo editorInfo = new EditorInfo();
        editorInfo.inputType = InputType.TYPE_CLASS_TEXT;
        final KeyboardLayoutSet.Builder builder = new KeyboardLayoutSet.Builder(this, editorInfo);
        return builder
                .setKeyboardGeometry(SPELLCHECKER_DUMMY_KEYBOARD_WIDTH, SPELLCHECKER_DUMMY_KEYBOARD_HEIGHT)
                .setSubtype(RichInputMethodSubtype.getRichInputMethodSubtype(subtype))
                .setIsSpellChecker(true)
                .disableTouchPositionCorrectionData()
                .build();
    }
}
