/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.samyarth.oskey.latin.settings;

import android.content.res.Resources;

import org.samyarth.oskey.R;
import org.samyarth.oskey.compat.ConfigurationCompatKt;

import org.samyarth.oskey.keyboard.internal.PopupKeySpec;
import org.samyarth.oskey.latin.PunctuationSuggestions;
import org.samyarth.oskey.latin.common.Constants;
import org.samyarth.oskey.latin.common.StringUtils;

import java.util.Arrays;
import java.util.Locale;

public final class SpacingAndPunctuations {
    private final int[] mSortedSymbolsPrecededBySpace;
    private final int[] mSortedSymbolsFollowedBySpace;
    private final int[] mSortedSymbolsClusteringTogether;
    private final int[] mSortedWordConnectors;
    private final int[] mSortedSometimesWordConnectors; // maybe rename... they are some sort of glue for words containing separators
    public final int[] mSortedWordSeparators;
    public final PunctuationSuggestions mSuggestPuncList;
    private final int mSentenceSeparator;
    private final int mAbbreviationMarker;
    private final int[] mSortedSentenceTerminators;
    public final String mSentenceSeparatorAndSpace;
    public final boolean mCurrentLanguageHasSpaces;
    public final boolean mUsesAmericanTypography;
    public final boolean mUsesGermanRules;

    public SpacingAndPunctuations(final Resources res, final Boolean urlDetection) {
        // To be able to binary search the code point. See {@link #isUsuallyPrecededBySpace(int)}.
        mSortedSymbolsPrecededBySpace = StringUtils.toSortedCodePointArray(res.getString(R.string.symbols_preceded_by_space));
        // To be able to binary search the code point. See {@link #isUsuallyFollowedBySpace(int)}.
        mSortedSymbolsFollowedBySpace = StringUtils.toSortedCodePointArray(res.getString(R.string.symbols_followed_by_space));
        mSortedSymbolsClusteringTogether = StringUtils.toSortedCodePointArray(res.getString(R.string.symbols_clustering_together));
        // To be able to binary search the code point. See {@link #isWordConnector(int)}.
        mSortedWordConnectors = StringUtils.toSortedCodePointArray(res.getString(R.string.symbols_word_connectors));
        mSortedWordSeparators = StringUtils.toSortedCodePointArray(res.getString(R.string.symbols_word_separators));
        mSortedSentenceTerminators = StringUtils.toSortedCodePointArray(res.getString(R.string.symbols_sentence_terminators));
        mSentenceSeparator = res.getInteger(R.integer.sentence_separator);
        mAbbreviationMarker = res.getInteger(R.integer.abbreviation_marker);
        mSentenceSeparatorAndSpace = new String(new int[] {
                mSentenceSeparator, Constants.CODE_SPACE }, 0, 2);
        mCurrentLanguageHasSpaces = res.getBoolean(R.bool.current_language_has_spaces);
        // make it empty if language doesn't have spaces, to avoid weird glitches
        mSortedSometimesWordConnectors = (urlDetection && mCurrentLanguageHasSpaces) ? StringUtils.toSortedCodePointArray(res.getString(R.string.symbols_sometimes_word_connectors)) : new int[0];
        final Locale locale = ConfigurationCompatKt.locale(res.getConfiguration());
        // Heuristic: we use American Typography rules because it's the most common rules for all
        // English variants. German rules (not "German typography") also have small gotchas.
        mUsesAmericanTypography = Locale.ENGLISH.getLanguage().equals(locale.getLanguage());
        mUsesGermanRules = Locale.GERMAN.getLanguage().equals(locale.getLanguage());
        final String[] suggestPuncsSpec = PopupKeySpec.splitKeySpecs(
                res.getString(R.string.suggested_punctuations));
        mSuggestPuncList = PunctuationSuggestions.newPunctuationSuggestions(suggestPuncsSpec);
    }

    public boolean isWordSeparator(final int code) {
        return Arrays.binarySearch(mSortedWordSeparators, code) >= 0;
    }

    public boolean isWordConnector(final int code) {
        return Arrays.binarySearch(mSortedWordConnectors, code) >= 0;
    }

    public boolean isSometimesWordConnector(final int code) {
        return Arrays.binarySearch(mSortedSometimesWordConnectors, code) >= 0;
    }

    public boolean containsSometimesWordConnector(final CharSequence word) {
        final String s = (word instanceof String) ? (String) word : word.toString();
        final int length = s.length();
        int offset = 0;
        while (offset < length) {
            int cp = s.codePointAt(offset);
            if (isSometimesWordConnector(cp)) return true;
            offset += Character.charCount(cp);
        }
        return false;
    }

    public boolean isWordCodePoint(final int code) {
        return Character.isLetter(code) || isWordConnector(code);
    }

    public boolean isUsuallyPrecededBySpace(final int code) {
        return Arrays.binarySearch(mSortedSymbolsPrecededBySpace, code) >= 0;
    }

    public boolean isUsuallyFollowedBySpace(final int code) {
        return Arrays.binarySearch(mSortedSymbolsFollowedBySpace, code) >= 0;
    }

    public boolean isClusteringSymbol(final int code) {
        return Arrays.binarySearch(mSortedSymbolsClusteringTogether, code) >= 0;
    }

    public boolean isSentenceTerminator(final int code) {
        return Arrays.binarySearch(mSortedSentenceTerminators, code) >= 0;
    }

    public boolean isAbbreviationMarker(final int code) {
        return code == mAbbreviationMarker;
    }

    public boolean isSentenceSeparator(final int code) {
        return code == mSentenceSeparator;
    }

    public String dump() {
        return "mSortedSymbolsPrecededBySpace = " +
                "" + Arrays.toString(mSortedSymbolsPrecededBySpace) +
                "\n   mSortedSymbolsFollowedBySpace = " +
                "" + Arrays.toString(mSortedSymbolsFollowedBySpace) +
                "\n   mSortedWordConnectors = " +
                "" + Arrays.toString(mSortedWordConnectors) +
                "\n   mSortedWordSeparators = " +
                "" + Arrays.toString(mSortedWordSeparators) +
                "\n   mSuggestPuncList = " +
                "" + mSuggestPuncList +
                "\n   mSentenceSeparator = " +
                "" + mSentenceSeparator +
                "\n   mSentenceSeparatorAndSpace = " +
                "" + mSentenceSeparatorAndSpace +
                "\n   mCurrentLanguageHasSpaces = " +
                "" + mCurrentLanguageHasSpaces +
                "\n   mUsesAmericanTypography = " +
                "" + mUsesAmericanTypography +
                "\n   mUsesGermanRules = " +
                "" + mUsesGermanRules;
    }
}
