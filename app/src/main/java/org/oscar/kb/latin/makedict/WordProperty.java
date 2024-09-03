/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.oscar.kb.latin.makedict;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.inputmethod.latin.BinaryDictionary;
import org.oscar.kb.latin.Dictionary;
import org.oscar.kb.latin.NgramContext;
import org.oscar.kb.latin.common.StringUtils;
import org.oscar.kb.latin.utils.CombinedFormatUtils;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Utility class for a word with a probability.
 * <p>
 * This is chiefly used to iterate a dictionary.
 */
public final class WordProperty implements Comparable<WordProperty> {
    public final String mWord;
    public final ProbabilityInfo mProbabilityInfo;
    public final ArrayList<WeightedString> mShortcutTargets;
    public final ArrayList<NgramProperty> mNgrams;
    // TODO: Support mIsBeginningOfSentence.
    public final boolean mIsBeginningOfSentence;
    public final boolean mIsNotAWord;
    public final boolean mIsPossiblyOffensive;
    public final boolean mHasShortcuts;
    public final boolean mHasNgrams;

    private int mHashCode = 0;

    // TODO: Support n-gram.
    public WordProperty(final String word, final ProbabilityInfo probabilityInfo,
                        final ArrayList<WeightedString> shortcutTargets,
                        @Nullable final ArrayList<WeightedString> bigrams,
                        final boolean isNotAWord, final boolean isPossiblyOffensive) {
        mWord = word;
        mProbabilityInfo = probabilityInfo;
        mShortcutTargets = shortcutTargets;
        if (null == bigrams) {
            mNgrams = null;
        } else {
            mNgrams = new ArrayList<>();
            final NgramContext ngramContext = new NgramContext(new NgramContext.WordInfo(mWord));
            for (final WeightedString bigramTarget : bigrams) {
                mNgrams.add(new NgramProperty(bigramTarget, ngramContext));
            }
        }
        mIsBeginningOfSentence = false;
        mIsNotAWord = isNotAWord;
        mIsPossiblyOffensive = isPossiblyOffensive;
        mHasNgrams = bigrams != null && !bigrams.isEmpty();
        mHasShortcuts = shortcutTargets != null && !shortcutTargets.isEmpty();
    }

    private static ProbabilityInfo createProbabilityInfoFromArray(final int[] probabilityInfo) {
        return new ProbabilityInfo(
                probabilityInfo[BinaryDictionary.FORMAT_WORD_PROPERTY_PROBABILITY_INDEX],
                probabilityInfo[BinaryDictionary.FORMAT_WORD_PROPERTY_TIMESTAMP_INDEX],
                probabilityInfo[BinaryDictionary.FORMAT_WORD_PROPERTY_LEVEL_INDEX],
                probabilityInfo[BinaryDictionary.FORMAT_WORD_PROPERTY_COUNT_INDEX]);
    }

    // Construct word property using information from native code.
    // This represents invalid word when the probability is BinaryDictionary.NOT_A_PROBABILITY.
    public WordProperty(final int[] codePoints, final boolean isNotAWord,
                        final boolean isPossiblyOffensive, final boolean hasBigram, final boolean hasShortcuts,
                        final boolean isBeginningOfSentence, final int[] probabilityInfo,
                        final ArrayList<int[][]> ngramPrevWordsArray,
                        final ArrayList<boolean[]> ngramPrevWordIsBeginningOfSentenceArray,
                        final ArrayList<int[]> ngramTargets, final ArrayList<int[]> ngramProbabilityInfo,
                        final ArrayList<int[]> shortcutTargets,
                        final ArrayList<Integer> shortcutProbabilities) {
        mWord = StringUtils.getStringFromNullTerminatedCodePointArray(codePoints);
        mProbabilityInfo = createProbabilityInfoFromArray(probabilityInfo);
        mShortcutTargets = new ArrayList<>();
        final ArrayList<NgramProperty> ngrams = new ArrayList<>();
        mIsBeginningOfSentence = isBeginningOfSentence;
        mIsNotAWord = isNotAWord;
        mIsPossiblyOffensive = isPossiblyOffensive;
        mHasShortcuts = hasShortcuts;
        mHasNgrams = hasBigram;

        final int relatedNgramCount = ngramTargets.size();
        for (int i = 0; i < relatedNgramCount; i++) {
            final String ngramTargetString =
                    StringUtils.getStringFromNullTerminatedCodePointArray(ngramTargets.get(i));
            final WeightedString ngramTarget = new WeightedString(ngramTargetString,
                    createProbabilityInfoFromArray(ngramProbabilityInfo.get(i)));
            final int[][] prevWords = ngramPrevWordsArray.get(i);
            final boolean[] isBeginningOfSentenceArray =
                    ngramPrevWordIsBeginningOfSentenceArray.get(i);
            final NgramContext.WordInfo[] wordInfoArray = new NgramContext.WordInfo[prevWords.length];
            for (int j = 0; j < prevWords.length; j++) {
                wordInfoArray[j] = isBeginningOfSentenceArray[j]
                        ? NgramContext.WordInfo.BEGINNING_OF_SENTENCE_WORD_INFO
                        : new NgramContext.WordInfo(StringUtils.getStringFromNullTerminatedCodePointArray(
                        prevWords[j]));
            }
            final NgramContext ngramContext = new NgramContext(wordInfoArray);
            ngrams.add(new NgramProperty(ngramTarget, ngramContext));
        }
        mNgrams = ngrams.isEmpty() ? null : ngrams;

        final int shortcutTargetCount = shortcutTargets.size();
        for (int i = 0; i < shortcutTargetCount; i++) {
            final String shortcutTargetString =
                    StringUtils.getStringFromNullTerminatedCodePointArray(shortcutTargets.get(i));
            mShortcutTargets.add(
                    new WeightedString(shortcutTargetString, shortcutProbabilities.get(i)));
        }
    }

    // TODO: Remove
    public ArrayList<WeightedString> getBigrams() {
        if (null == mNgrams) {
            return null;
        }
        final ArrayList<WeightedString> bigrams = new ArrayList<>();
        for (final NgramProperty ngram : mNgrams) {
            if (ngram.mNgramContext.getPrevWordCount() == 1) {
                bigrams.add(ngram.mTargetWord);
            }
        }
        return bigrams;
    }

    public int getProbability() {
        return mProbabilityInfo.mProbability;
    }

    private static int computeHashCode(WordProperty word) {
        return Arrays.hashCode(new Object[]{
                word.mWord,
                word.mProbabilityInfo,
                word.mShortcutTargets,
                word.mNgrams,
                word.mIsNotAWord,
                word.mIsPossiblyOffensive
        });
    }

    /**
     * Three-way comparison.
     * <p>
     * A Word x is greater than a word y if x has a higher frequency. If they have the same
     * frequency, they are sorted in lexicographic order.
     */
    @Override
    public int compareTo(final WordProperty w) {
        if (getProbability() < w.getProbability()) return 1;
        if (getProbability() > w.getProbability()) return -1;
        return mWord.compareTo(w.mWord);
    }

    /**
     * Equality test.
     * <p>
     * Words are equal if they have the same frequency, the same spellings, and the same
     * attributes.
     */
    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof WordProperty w)) return false;
        return mProbabilityInfo.equals(w.mProbabilityInfo) && mWord.equals(w.mWord)
                && mShortcutTargets.equals(w.mShortcutTargets) && equals(mNgrams, w.mNgrams)
                && mIsNotAWord == w.mIsNotAWord && mIsPossiblyOffensive == w.mIsPossiblyOffensive
                && mHasNgrams == w.mHasNgrams && mHasShortcuts && w.mHasNgrams;
    }

    // TDOO: Have a utility method like java.util.Objects.equals.
    private static <T> boolean equals(final ArrayList<T> a, final ArrayList<T> b) {
        if (null == a) {
            return null == b;
        }
        return a.equals(b);
    }

    @Override
    public int hashCode() {
        if (mHashCode == 0) {
            mHashCode = computeHashCode(this);
        }
        return mHashCode;
    }

    public boolean isValid() {
        return getProbability() != Dictionary.NOT_A_PROBABILITY;
    }

    @Override
    @NonNull
    public String toString() {
        return CombinedFormatUtils.formatWordProperty(this);
    }
}
