/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.common;

import androidx.annotation.NonNull;

import org.dslul.openboard.inputmethod.annotations.UsedForTesting;

import java.util.Random;

// Utility methods related with code points used for tests.
// TODO: Figure out where this class should be.
@UsedForTesting
public class CodePointUtils {
    private CodePointUtils() {
        // This utility class is not publicly instantiable.
    }

    public static final int[] LATIN_ALPHABETS_LOWER = {
        'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
        'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
        0x00E0 /* LATIN SMALL LETTER A WITH GRAVE */,
        0x00E1 /* LATIN SMALL LETTER A WITH ACUTE */,
        0x00E2 /* LATIN SMALL LETTER A WITH CIRCUMFLEX */,
        0x00E3 /* LATIN SMALL LETTER A WITH TILDE */,
        0x00E4 /* LATIN SMALL LETTER A WITH DIAERESIS */,
        0x00E5 /* LATIN SMALL LETTER A WITH RING ABOVE */,
        0x00E6 /* LATIN SMALL LETTER AE */,
        0x00E7 /* LATIN SMALL LETTER C WITH CEDILLA */,
        0x00E8 /* LATIN SMALL LETTER E WITH GRAVE */,
        0x00E9 /* LATIN SMALL LETTER E WITH ACUTE */,
        0x00EA /* LATIN SMALL LETTER E WITH CIRCUMFLEX */,
        0x00EB /* LATIN SMALL LETTER E WITH DIAERESIS */,
        0x00EC /* LATIN SMALL LETTER I WITH GRAVE */,
        0x00ED /* LATIN SMALL LETTER I WITH ACUTE */,
        0x00EE /* LATIN SMALL LETTER I WITH CIRCUMFLEX */,
        0x00EF /* LATIN SMALL LETTER I WITH DIAERESIS */,
        0x00F0 /* LATIN SMALL LETTER ETH */,
        0x00F1 /* LATIN SMALL LETTER N WITH TILDE */,
        0x00F2 /* LATIN SMALL LETTER O WITH GRAVE */,
        0x00F3 /* LATIN SMALL LETTER O WITH ACUTE */,
        0x00F4 /* LATIN SMALL LETTER O WITH CIRCUMFLEX */,
        0x00F5 /* LATIN SMALL LETTER O WITH TILDE */,
        0x00F6 /* LATIN SMALL LETTER O WITH DIAERESIS */,
        0x00F7 /* LATIN SMALL LETTER O WITH STROKE */,
        0x00F9 /* LATIN SMALL LETTER U WITH GRAVE */,
        0x00FA /* LATIN SMALL LETTER U WITH ACUTE */,
        0x00FB /* LATIN SMALL LETTER U WITH CIRCUMFLEX */,
        0x00FC /* LATIN SMALL LETTER U WITH DIAERESIS */,
        0x00FD /* LATIN SMALL LETTER Y WITH ACUTE */,
        0x00FE /* LATIN SMALL LETTER THORN */,
        0x00FF /* LATIN SMALL LETTER Y WITH DIAERESIS */
    };

    @UsedForTesting
    @NonNull
    public static int[] generateCodePointSet(final int codePointSetSize,
            @NonNull final Random random) {
        final int[] codePointSet = new int[codePointSetSize];
        for (int i = codePointSet.length - 1; i >= 0; ) {
            final int r = Math.abs(random.nextInt());
            if (r < 0) {
                continue;
            }
            // Don't insert 0~0x20, but insert any other code point.
            // Code points are in the range 0~0x10FFFF.
            final int candidateCodePoint = 0x20 + r % (Character.MAX_CODE_POINT - 0x20);
            // Code points between MIN_ and MAX_SURROGATE are not valid on their own.
            if (candidateCodePoint >= Character.MIN_SURROGATE
                    && candidateCodePoint <= Character.MAX_SURROGATE) {
                continue;
            }
            codePointSet[i] = candidateCodePoint;
            --i;
        }
        return codePointSet;
    }

    /**
     * Generates a random word.
     */
    @UsedForTesting
    @NonNull
    public static String generateWord(@NonNull final Random random,
            @NonNull final int[] codePointSet) {
        final StringBuilder builder = new StringBuilder();
        // 8 * 4 = 32 chars max, but we do it the following way so as to bias the random toward
        // longer words. This should be closer to natural language, and more importantly, it will
        // exercise the algorithms in dicttool much more.
        final int count = 1 + (Math.abs(random.nextInt()) % 5)
                + (Math.abs(random.nextInt()) % 5)
                + (Math.abs(random.nextInt()) % 5)
                + (Math.abs(random.nextInt()) % 5)
                + (Math.abs(random.nextInt()) % 5)
                + (Math.abs(random.nextInt()) % 5)
                + (Math.abs(random.nextInt()) % 5)
                + (Math.abs(random.nextInt()) % 5);
        while (builder.length() < count) {
            builder.appendCodePoint(codePointSet[Math.abs(random.nextInt()) % codePointSet.length]);
        }
        return builder.toString();
    }
}
