/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.utils;

import helium314.keyboard.latin.makedict.DictionaryHeader;
import helium314.keyboard.latin.makedict.NgramProperty;
import helium314.keyboard.latin.makedict.ProbabilityInfo;
import helium314.keyboard.latin.makedict.WeightedString;
import helium314.keyboard.latin.makedict.WordProperty;

import java.util.HashMap;

public class CombinedFormatUtils {
    public static final String DICTIONARY_TAG = "dictionary";
    public static final String BIGRAM_TAG = "bigram";
    public static final String NGRAM_TAG = "ngram";
    public static final String NGRAM_PREV_WORD_TAG = "prev_word";
    public static final String SHORTCUT_TAG = "shortcut";
    public static final String PROBABILITY_TAG = "f";
    public static final String HISTORICAL_INFO_TAG = "historicalInfo";
    public static final String HISTORICAL_INFO_SEPARATOR = ":";
    public static final String WORD_TAG = "word";
    public static final String BEGINNING_OF_SENTENCE_TAG = "beginning_of_sentence";
    public static final String NOT_A_WORD_TAG = "not_a_word";
    public static final String POSSIBLY_OFFENSIVE_TAG = "possibly_offensive";
    public static final String TRUE_VALUE = "true";

    public static String formatAttributeMap(final HashMap<String, String> attributeMap) {
        final StringBuilder builder = new StringBuilder();
        builder.append(DICTIONARY_TAG + "=");
        if (attributeMap.containsKey(DictionaryHeader.DICTIONARY_ID_KEY)) {
            builder.append(attributeMap.get(DictionaryHeader.DICTIONARY_ID_KEY));
        }
        for (final String key : attributeMap.keySet()) {
            if (key.equals(DictionaryHeader.DICTIONARY_ID_KEY)) {
                continue;
            }
            final String value = attributeMap.get(key);
            builder.append("," + key + "=" + value);
        }
        builder.append("\n");
        return builder.toString();
    }

    public static String formatWordProperty(final WordProperty wordProperty) {
        final StringBuilder builder = new StringBuilder();
        builder.append(" " + WORD_TAG + "=" + wordProperty.mWord);
        builder.append(",");
        builder.append(formatProbabilityInfo(wordProperty.mProbabilityInfo));
        if (wordProperty.mIsBeginningOfSentence) {
            builder.append("," + BEGINNING_OF_SENTENCE_TAG + "=" + TRUE_VALUE);
        }
        if (wordProperty.mIsNotAWord) {
            builder.append("," + NOT_A_WORD_TAG + "=" + TRUE_VALUE);
        }
        if (wordProperty.mIsPossiblyOffensive) {
            builder.append("," + POSSIBLY_OFFENSIVE_TAG + "=" + TRUE_VALUE);
        }
        builder.append("\n");
        if (wordProperty.mHasShortcuts) {
            for (final WeightedString shortcutTarget : wordProperty.mShortcutTargets) {
                builder.append("  " + SHORTCUT_TAG + "=" + shortcutTarget.mWord);
                builder.append(",");
                builder.append(formatProbabilityInfo(shortcutTarget.mProbabilityInfo));
                builder.append("\n");
            }
        }
        if (wordProperty.mHasNgrams) {
            for (final NgramProperty ngramProperty : wordProperty.mNgrams) {
                builder.append(" " + NGRAM_TAG + "=" + ngramProperty.mTargetWord.mWord);
                builder.append(",");
                builder.append(formatProbabilityInfo(ngramProperty.mTargetWord.mProbabilityInfo));
                builder.append("\n");
                for (int i = 0; i < ngramProperty.mNgramContext.getPrevWordCount(); i++) {
                    builder.append("  " + NGRAM_PREV_WORD_TAG + "[" + i + "]="
                            + ngramProperty.mNgramContext.getNthPrevWord(i + 1));
                    if (ngramProperty.mNgramContext.isNthPrevWordBeginningOfSentence(i + 1)) {
                        builder.append("," + BEGINNING_OF_SENTENCE_TAG + "=true");
                    }
                    builder.append("\n");
                }
            }
        }
        return builder.toString();
    }

    public static String formatProbabilityInfo(final ProbabilityInfo probabilityInfo) {
        final StringBuilder builder = new StringBuilder();
        builder.append(PROBABILITY_TAG + "=" + probabilityInfo.mProbability);
        if (probabilityInfo.hasHistoricalInfo()) {
            builder.append(",");
            builder.append(HISTORICAL_INFO_TAG + "=");
            builder.append(probabilityInfo.mTimestamp);
            builder.append(HISTORICAL_INFO_SEPARATOR);
            builder.append(probabilityInfo.mLevel);
            builder.append(HISTORICAL_INFO_SEPARATOR);
            builder.append(probabilityInfo.mCount);
        }
        return builder.toString();
    }

    public static boolean isLiteralTrue(final String value) {
        return TRUE_VALUE.equalsIgnoreCase(value);
    }
}
