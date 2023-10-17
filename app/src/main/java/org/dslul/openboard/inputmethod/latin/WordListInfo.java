/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin;

/**
 * Information container for a word list.
 */
public final class WordListInfo {
    public final String mId;
    public final String mLocale;
    public final String mRawChecksum;
    public WordListInfo(final String id, final String locale, final String rawChecksum) {
        mId = id;
        mLocale = locale;
        mRawChecksum = rawChecksum;
    }
}
