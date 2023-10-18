/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin;

import android.util.Log;

import org.dslul.openboard.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import org.dslul.openboard.inputmethod.latin.common.ComposedData;
import org.dslul.openboard.inputmethod.latin.settings.SettingsValuesForSuggestion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Class for a collection of dictionaries that behave like one dictionary.
 */
public final class DictionaryCollection extends Dictionary {
    private final String TAG = DictionaryCollection.class.getSimpleName();
    protected final CopyOnWriteArrayList<Dictionary> mDictionaries;

    public DictionaryCollection(final String dictType, final Locale locale) {
        super(dictType, locale);
        mDictionaries = new CopyOnWriteArrayList<>();
    }

    public DictionaryCollection(final String dictType, final Locale locale,
            final Dictionary... dictionaries) {
        super(dictType, locale);
        if (null == dictionaries) {
            mDictionaries = new CopyOnWriteArrayList<>();
        } else {
            mDictionaries = new CopyOnWriteArrayList<>(dictionaries);
            mDictionaries.removeAll(Collections.singleton(null));
        }
    }

    public DictionaryCollection(final String dictType, final Locale locale,
            final Collection<Dictionary> dictionaries) {
        super(dictType, locale);
        mDictionaries = new CopyOnWriteArrayList<>(dictionaries);
        mDictionaries.removeAll(Collections.singleton(null));
    }

    @Override
    public ArrayList<SuggestedWordInfo> getSuggestions(final ComposedData composedData,
            final NgramContext ngramContext, final long proximityInfoHandle,
            final SettingsValuesForSuggestion settingsValuesForSuggestion,
            final int sessionId, final float weightForLocale,
            final float[] inOutWeightOfLangModelVsSpatialModel) {
        final CopyOnWriteArrayList<Dictionary> dictionaries = mDictionaries;
        if (dictionaries.isEmpty()) return null;
        // To avoid creating unnecessary objects, we get the list out of the first
        // dictionary and add the rest to it if not null, hence the get(0)
        ArrayList<SuggestedWordInfo> suggestions = dictionaries.get(0).getSuggestions(composedData,
                ngramContext, proximityInfoHandle, settingsValuesForSuggestion, sessionId,
                weightForLocale, inOutWeightOfLangModelVsSpatialModel);
        if (null == suggestions) suggestions = new ArrayList<>();
        final int length = dictionaries.size();
        for (int i = 1; i < length; ++ i) {
            final ArrayList<SuggestedWordInfo> sugg = dictionaries.get(i).getSuggestions(
                    composedData, ngramContext, proximityInfoHandle, settingsValuesForSuggestion,
                    sessionId, weightForLocale, inOutWeightOfLangModelVsSpatialModel);
            if (null != sugg) suggestions.addAll(sugg);
        }
        return suggestions;
    }

    @Override
    public boolean isInDictionary(final String word) {
        for (int i = mDictionaries.size() - 1; i >= 0; --i)
            if (mDictionaries.get(i).isInDictionary(word)) return true;
        return false;
    }

    @Override
    public int getFrequency(final String word) {
        int maxFreq = -1;
        for (int i = mDictionaries.size() - 1; i >= 0; --i) {
            final int tempFreq = mDictionaries.get(i).getFrequency(word);
            maxFreq = Math.max(tempFreq, maxFreq);
        }
        return maxFreq;
    }

    @Override
    public int getMaxFrequencyOfExactMatches(final String word) {
        int maxFreq = -1;
        for (int i = mDictionaries.size() - 1; i >= 0; --i) {
            final int tempFreq = mDictionaries.get(i).getMaxFrequencyOfExactMatches(word);
            maxFreq = Math.max(tempFreq, maxFreq);
        }
        return maxFreq;
    }

    @Override
    public boolean isInitialized() {
        return !mDictionaries.isEmpty();
    }

    @Override
    public void close() {
        for (final Dictionary dict : mDictionaries)
            dict.close();
    }

    // Warning: this is not thread-safe. Take necessary precaution when calling.
    public void addDictionary(final Dictionary newDict) {
        if (null == newDict) return;
        if (mDictionaries.contains(newDict)) {
            Log.w(TAG, "This collection already contains this dictionary: " + newDict);
        }
        mDictionaries.add(newDict);
    }

    // Warning: this is not thread-safe. Take necessary precaution when calling.
    public void removeDictionary(final Dictionary dict) {
        if (mDictionaries.contains(dict)) {
            mDictionaries.remove(dict);
        } else {
            Log.w(TAG, "This collection does not contain this dictionary: " + dict);
        }
    }
}
