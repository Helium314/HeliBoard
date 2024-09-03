/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.oscar.kb.latin.common;

import androidx.annotation.NonNull;

/**
 * An immutable class that encapsulates a snapshot of word composition data.
 */
public class ComposedData {
    @NonNull
    public final InputPointers mInputPointers;
    public final boolean mIsBatchMode;
    @NonNull
    public final String mTypedWord;

    public ComposedData(@NonNull final InputPointers inputPointers, final boolean isBatchMode,
                        @NonNull final String typedWord) {
        mInputPointers = inputPointers;
        mIsBatchMode = isBatchMode;
        mTypedWord = typedWord;
    }

    /**
     * Copy the code points in the typed word to a destination array of ints.
     *
     * If the array is too small to hold the code points in the typed word, nothing is copied and
     * -1 is returned.
     *
     * @param destination the array of ints.
     * @return the number of copied code points.
     */
    public int copyCodePointsExceptTrailingSingleQuotesAndReturnCodePointCount(
            @NonNull final int[] destination) {
        // lastIndex is exclusive
        final int lastIndex = mTypedWord.length()
                - StringUtils.getTrailingSingleQuotesCount(mTypedWord);
        if (lastIndex <= 0) {
            // The string is empty or contains only single quotes.
            return 0;
        }

        // The following function counts the number of code points in the text range which begins
        // at index 0 and extends to the character at lastIndex.
        final int codePointSize = Character.codePointCount(mTypedWord, 0, lastIndex);
        if (codePointSize > destination.length) {
            return -1;
        }
        return StringUtils.copyCodePointsAndReturnCodePointCount(destination, mTypedWord, 0,
                lastIndex, true /* downCase */);
    }
}
