/*
 * Copyright (C) 2015 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.common;

/**
 * Emojis are supplementary characters expressed as a low+high pair. For instance,
 * the emoji U+1F625 is encoded as "\uD83D\uDE25" in UTF-16, where '\uD83D' is in
 * the range of [0xd800, 0xdbff] and '\uDE25' is in the range of [0xdc00, 0xdfff].
 * {@see http://docs.oracle.com/javase/6/docs/api/java/lang/Character.html#unicode}
 */
public final class UnicodeSurrogate {
    private static final char LOW_SURROGATE_MIN = '\uD800';
    private static final char LOW_SURROGATE_MAX = '\uDBFF';
    private static final char HIGH_SURROGATE_MIN = '\uDC00';
    private static final char HIGH_SURROGATE_MAX = '\uDFFF';

    public static boolean isLowSurrogate(final char c) {
        return c >= LOW_SURROGATE_MIN && c <= LOW_SURROGATE_MAX;
    }

    public static boolean isHighSurrogate(final char c) {
        return c >= HIGH_SURROGATE_MIN && c <= HIGH_SURROGATE_MAX;
    }
}
