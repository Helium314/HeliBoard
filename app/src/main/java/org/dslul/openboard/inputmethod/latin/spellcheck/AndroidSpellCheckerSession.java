/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.spellcheck;

import android.content.res.Resources;
import android.os.Binder;
import android.text.TextUtils;
import android.util.Log;
import android.view.textservice.SentenceSuggestionsInfo;
import android.view.textservice.SuggestionsInfo;
import android.view.textservice.TextInfo;

import org.dslul.openboard.inputmethod.compat.TextInfoCompatUtils;
import org.dslul.openboard.inputmethod.latin.NgramContext;
import org.dslul.openboard.inputmethod.latin.utils.SpannableStringUtils;

import java.util.ArrayList;
import java.util.Locale;

public final class AndroidSpellCheckerSession extends AndroidWordLevelSpellCheckerSession {
    private static final String TAG = AndroidSpellCheckerSession.class.getSimpleName();
    private static final boolean DBG = false;
    private final Resources mResources;
    private SentenceLevelAdapter mSentenceLevelAdapter;

    public AndroidSpellCheckerSession(AndroidSpellCheckerService service) {
        super(service);
        mResources = service.getResources();
    }

    private SentenceSuggestionsInfo fixWronglyInvalidatedWordWithSingleQuote(TextInfo ti,
            SentenceSuggestionsInfo ssi) {
        final CharSequence typedText = TextInfoCompatUtils.getCharSequenceOrString(ti);
        if (!typedText.toString().contains(AndroidSpellCheckerService.SINGLE_QUOTE)) {
            return null;
        }
        final int N = ssi.getSuggestionsCount();
        final ArrayList<Integer> additionalOffsets = new ArrayList<>();
        final ArrayList<Integer> additionalLengths = new ArrayList<>();
        final ArrayList<SuggestionsInfo> additionalSuggestionsInfos = new ArrayList<>();
        for (int i = 0; i < N; ++i) {
            final SuggestionsInfo si = ssi.getSuggestionsInfoAt(i);
            final int flags = si.getSuggestionsAttributes();
            if ((flags & SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY) == 0) {
                continue;
            }
            final int offset = ssi.getOffsetAt(i);
            final int length = ssi.getLengthAt(i);
            final CharSequence subText = typedText.subSequence(offset, offset + length);
            if (!subText.toString().contains(AndroidSpellCheckerService.SINGLE_QUOTE)) {
                continue;
            }
            // Split preserving spans.
            final CharSequence[] splitTexts = SpannableStringUtils.split(subText,
                    AndroidSpellCheckerService.SINGLE_QUOTE,
                    true /* preserveTrailingEmptySegments */);
            if (splitTexts == null || splitTexts.length <= 1) {
                continue;
            }
            for (final CharSequence splitText : splitTexts) {
                if (TextUtils.isEmpty(splitText)) {
                    continue;
                }
                if (mSuggestionsCache.getSuggestionsFromCache(splitText.toString()) == null) {
                    continue;
                }
                final int newLength = splitText.length();
                // Neither RESULT_ATTR_IN_THE_DICTIONARY nor RESULT_ATTR_LOOKS_LIKE_TYPO
                final int newFlags = 0;
                final SuggestionsInfo newSi = new SuggestionsInfo(newFlags, EMPTY_STRING_ARRAY);
                newSi.setCookieAndSequence(si.getCookie(), si.getSequence());
                if (DBG) {
                    Log.d(TAG, "Override and remove old span over: " + splitText + ", "
                            + offset + "," + newLength);
                }
                additionalOffsets.add(offset);
                additionalLengths.add(newLength);
                additionalSuggestionsInfos.add(newSi);
            }
        }
        final int additionalSize = additionalOffsets.size();
        if (additionalSize == 0) {
            return null;
        }
        final int suggestionsSize = N + additionalSize;
        final int[] newOffsets = new int[suggestionsSize];
        final int[] newLengths = new int[suggestionsSize];
        final SuggestionsInfo[] newSuggestionsInfos = new SuggestionsInfo[suggestionsSize];
        int i;
        for (i = 0; i < N; ++i) {
            newOffsets[i] = ssi.getOffsetAt(i);
            newLengths[i] = ssi.getLengthAt(i);
            newSuggestionsInfos[i] = ssi.getSuggestionsInfoAt(i);
        }
        for (; i < suggestionsSize; ++i) {
            newOffsets[i] = additionalOffsets.get(i - N);
            newLengths[i] = additionalLengths.get(i - N);
            newSuggestionsInfos[i] = additionalSuggestionsInfos.get(i - N);
        }
        return new SentenceSuggestionsInfo(newSuggestionsInfos, newOffsets, newLengths);
    }

    @Override
    public SentenceSuggestionsInfo[] onGetSentenceSuggestionsMultiple(TextInfo[] textInfos,
            int suggestionsLimit) {
        final SentenceSuggestionsInfo[] retval = splitAndSuggest(textInfos, suggestionsLimit);
        if (retval == null || retval.length != textInfos.length) {
            return retval;
        }
        for (int i = 0; i < retval.length; ++i) {
            final SentenceSuggestionsInfo tempSsi =
                    fixWronglyInvalidatedWordWithSingleQuote(textInfos[i], retval[i]);
            if (tempSsi != null) {
                retval[i] = tempSsi;
            }
        }
        return retval;
    }

    /**
     * Get sentence suggestions for specified texts in an array of TextInfo. This is taken from
     * SpellCheckerService#onGetSentenceSuggestionsMultiple that we can't use because it's
     * using private variables.
     * The default implementation splits the input text to words and returns
     * {@link SentenceSuggestionsInfo} which contains suggestions for each word.
     * This function will run on the incoming IPC thread.
     * So, this is not called on the main thread,
     * but will be called in series on another thread.
     * @param textInfos an array of the text metadata
     * @param suggestionsLimit the maximum number of suggestions to be returned
     * @return an array of {@link SentenceSuggestionsInfo} returned by
     * {@link android.service.textservice.SpellCheckerService.Session#onGetSuggestions(TextInfo, int)}
     */
    private SentenceSuggestionsInfo[] splitAndSuggest(TextInfo[] textInfos, int suggestionsLimit) {
        if (textInfos == null || textInfos.length == 0) {
            return SentenceLevelAdapter.getEmptySentenceSuggestionsInfo();
        }
        SentenceLevelAdapter sentenceLevelAdapter;
        synchronized(this) {
            sentenceLevelAdapter = mSentenceLevelAdapter;
            if (sentenceLevelAdapter == null) {
                final String localeStr = getLocale();
                if (!TextUtils.isEmpty(localeStr)) {
                    sentenceLevelAdapter = new SentenceLevelAdapter(mResources,
                            new Locale(localeStr));
                    mSentenceLevelAdapter = sentenceLevelAdapter;
                }
            }
        }
        if (sentenceLevelAdapter == null) {
            return SentenceLevelAdapter.getEmptySentenceSuggestionsInfo();
        }
        final int infosSize = textInfos.length;
        final SentenceSuggestionsInfo[] retval = new SentenceSuggestionsInfo[infosSize];
        for (int i = 0; i < infosSize; ++i) {
            final SentenceLevelAdapter.SentenceTextInfoParams textInfoParams =
                    sentenceLevelAdapter.getSplitWords(textInfos[i]);
            final ArrayList<SentenceLevelAdapter.SentenceWordItem> mItems =
                    textInfoParams.mItems;
            final int itemsSize = mItems.size();
            final TextInfo[] splitTextInfos = new TextInfo[itemsSize];
            for (int j = 0; j < itemsSize; ++j) {
                splitTextInfos[j] = mItems.get(j).mTextInfo;
            }
            retval[i] = SentenceLevelAdapter.reconstructSuggestions(
                    textInfoParams, onGetSuggestionsMultiple(
                            splitTextInfos, suggestionsLimit, true));
        }
        return retval;
    }

    @Override
    public SuggestionsInfo[] onGetSuggestionsMultiple(TextInfo[] textInfos,
            int suggestionsLimit, boolean sequentialWords) {
        long ident = Binder.clearCallingIdentity();
        try {
            final int length = textInfos.length;
            final SuggestionsInfo[] retval = new SuggestionsInfo[length];
            for (int i = 0; i < length; ++i) {
                final CharSequence prevWord;
                if (sequentialWords && i > 0) {
                    final TextInfo prevTextInfo = textInfos[i - 1];
                    final CharSequence prevWordCandidate =
                            TextInfoCompatUtils.getCharSequenceOrString(prevTextInfo);
                    // Note that an empty string would be used to indicate the initial word
                    // in the future.
                    prevWord = TextUtils.isEmpty(prevWordCandidate) ? null : prevWordCandidate;
                } else {
                    prevWord = null;
                }
                final NgramContext ngramContext =
                        new NgramContext(new NgramContext.WordInfo(prevWord));
                final TextInfo textInfo = textInfos[i];
                retval[i] = onGetSuggestionsInternal(textInfo, ngramContext, suggestionsLimit);
                retval[i].setCookieAndSequence(textInfo.getCookie(), textInfo.getSequence());
            }
            return retval;
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }
}
