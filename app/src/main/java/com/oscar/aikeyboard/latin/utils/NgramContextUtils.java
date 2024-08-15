/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.oscar.aikeyboard.latin.utils;

import androidx.annotation.NonNull;

import com.oscar.aikeyboard.latin.NgramContext;
import com.oscar.aikeyboard.latin.NgramContext.WordInfo;
import com.oscar.aikeyboard.latin.define.DecoderSpecificConstants;
import com.oscar.aikeyboard.latin.settings.SpacingAndPunctuations;

import java.util.Arrays;
import java.util.regex.Pattern;

public final class NgramContextUtils {
    private NgramContextUtils() {
        // Intentional empty constructor for utility class.
    }

    private static final Pattern NEWLINE_REGEX = Pattern.compile("[\\r\\n]+");
    private static final Pattern SPACE_REGEX = Pattern.compile("\\s+");
    // Get context information from nth word before the cursor. n = 1 retrieves the words
    // immediately before the cursor, n = 2 retrieves the words before that, and so on. This splits
    // on whitespace only.
    // Also, it won't return words that end in a separator (if the nth word before the cursor
    // ends in a separator, it returns information representing beginning-of-sentence).
    // Example (when Constants.MAX_PREV_WORD_COUNT_FOR_N_GRAM is 2):
    // (n = 1) "abc def|" -> abc, def
    // (n = 1) "abc def |" -> abc, def
    // (n = 1) "abc 'def|" -> empty, 'def
    // (n = 1) "abc def. |" -> beginning-of-sentence
    // (n = 1) "abc def . |" -> beginning-of-sentence
    // (n = 2) "abc def|" -> beginning-of-sentence, abc
    // (n = 2) "abc def |" -> beginning-of-sentence, abc
    // (n = 2) "abc 'def|" -> empty. The context is different from "abc def", but we cannot
    // represent this situation using NgramContext. See TODO in the method.
    // TODO: The next example's result should be "abc, def". This have to be fixed before we
    // retrieve the prior context of Beginning-of-Sentence.
    // (n = 2) "abc def. |" -> beginning-of-sentence, abc
    // (n = 2) "abc def . |" -> abc, def
    // (n = 2) "abc|" -> beginning-of-sentence
    // (n = 2) "abc |" -> beginning-of-sentence
    // (n = 2) "abc. def|" -> beginning-of-sentence
    @NonNull
    public static NgramContext getNgramContextFromNthPreviousWord(final CharSequence prev,
            final SpacingAndPunctuations spacingAndPunctuations, final int n) {
        if (prev == null) return NgramContext.EMPTY_PREV_WORDS_INFO;
        final String[] lines = NEWLINE_REGEX.split(prev);
        if (lines.length == 0) {
            return new NgramContext(WordInfo.BEGINNING_OF_SENTENCE_WORD_INFO);
        }
        final String[] w = SPACE_REGEX.split(lines[lines.length - 1]);
        final WordInfo[] prevWordsInfo =
                new WordInfo[DecoderSpecificConstants.MAX_PREV_WORD_COUNT_FOR_N_GRAM];
        Arrays.fill(prevWordsInfo, WordInfo.EMPTY_WORD_INFO);
        for (int i = 0; i < prevWordsInfo.length; i++) {
            final int focusedWordIndex = w.length - n - i;
            // Referring to the word after the focused word.
            if ((focusedWordIndex + 1) >= 0 && (focusedWordIndex + 1) < w.length) {
                final String wordFollowingTheNthPrevWord = w[focusedWordIndex + 1];
                if (!wordFollowingTheNthPrevWord.isEmpty()) {
                    final char firstChar = wordFollowingTheNthPrevWord.charAt(0);
                    if (spacingAndPunctuations.isWordConnector(firstChar)) {
                        // The word following the focused word is starting with a word connector.
                        // TODO: Return meaningful context for this case.
                        break;
                    }
                }
            }
            // If we can't find (n + i) words, the context is beginning-of-sentence.
            if (focusedWordIndex < 0) {
                prevWordsInfo[i] = WordInfo.BEGINNING_OF_SENTENCE_WORD_INFO;
                break;
            }

            final String focusedWord = w[focusedWordIndex];
            // If the word is empty, the context is beginning-of-sentence.
            final int length = focusedWord.length();
            if (length <= 0) {
                prevWordsInfo[i] = NgramContext.WordInfo.BEGINNING_OF_SENTENCE_WORD_INFO;
                break;
            }
            // If the word ends in a sentence terminator, the context is beginning-of-sentence.
            final char lastChar = focusedWord.charAt(length - 1);
            if (spacingAndPunctuations.isSentenceTerminator(lastChar)) {
                prevWordsInfo[i] = NgramContext.WordInfo.BEGINNING_OF_SENTENCE_WORD_INFO;
                break;
            }
            // If ends in a word separator or connector, the context is unclear.
            // TODO: Return meaningful context for this case.
            if (spacingAndPunctuations.isWordSeparator(lastChar)
                    || spacingAndPunctuations.isWordConnector(lastChar)) {
                break;
            }
            prevWordsInfo[i] = new NgramContext.WordInfo(focusedWord);
        }
        return new NgramContext(prevWordsInfo);
    }
}
