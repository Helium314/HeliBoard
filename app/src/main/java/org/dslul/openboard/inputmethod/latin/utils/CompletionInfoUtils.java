/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.utils;

import android.text.TextUtils;
import android.view.inputmethod.CompletionInfo;

import java.util.Arrays;

/**
 * Utilities to do various stuff with CompletionInfo.
 */
public class CompletionInfoUtils {
    private CompletionInfoUtils() {
        // This utility class is not publicly instantiable.
    }

    public static CompletionInfo[] removeNulls(final CompletionInfo[] src) {
        int j = 0;
        final CompletionInfo[] dst = new CompletionInfo[src.length];
        for (int i = 0; i < src.length; ++i) {
            if (null != src[i] && !TextUtils.isEmpty(src[i].getText())) {
                dst[j] = src[i];
                ++j;
            }
        }
        return Arrays.copyOfRange(dst, 0, j);
    }
}
