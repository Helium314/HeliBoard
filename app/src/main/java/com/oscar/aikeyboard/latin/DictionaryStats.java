/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.oscar.aikeyboard.latin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.math.BigDecimal;
import java.util.Locale;

public class DictionaryStats {
    public static final int NOT_AN_ENTRY_COUNT = -1;

    public final Locale mLocale;
    public final String mDictType;
    public final String mDictFileName;
    public final long mDictFileSize;
    public final int mContentVersion;
    public final int mWordCount;

    public DictionaryStats(
            @NonNull final Locale locale,
            @NonNull final String dictType,
            @Nullable final String dictFileName,
            @Nullable final File dictFile,
            final int contentVersion) {
        mLocale = locale;
        mDictType = dictType;
        mDictFileSize = (dictFile == null || !dictFile.exists()) ? 0 : dictFile.length();
        mDictFileName = dictFileName;
        mContentVersion = contentVersion;
        mWordCount = -1;
    }

    public DictionaryStats(
            @NonNull final Locale locale,
            @NonNull final String dictType,
            final int wordCount) {
        mLocale = locale;
        mDictType = dictType;
        mDictFileSize = wordCount;
        mDictFileName = null;
        mContentVersion = 0;
        mWordCount = wordCount;
    }

    public String getFileSizeString() {
        BigDecimal bytes = new BigDecimal(mDictFileSize);
        BigDecimal kb = bytes.divide(new BigDecimal(1024), 2, BigDecimal.ROUND_HALF_UP);
        if (kb.longValue() == 0) {
            return bytes.toString() + " bytes";
        }
        BigDecimal mb = kb.divide(new BigDecimal(1024), 2, BigDecimal.ROUND_HALF_UP);
        if (mb.longValue() == 0) {
            return kb.toString() + " kb";
        }
        return mb.toString() + " Mb";
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder(mDictType);
        if (mDictType.equals(Dictionary.TYPE_MAIN)) {
            builder.append(" (");
            builder.append(mContentVersion);
            builder.append(")");
        }
        builder.append(": ");
        if (mWordCount > -1) {
            builder.append(mWordCount);
            builder.append(" words");
        } else {
            builder.append(mDictFileName);
            builder.append(" / ");
            builder.append(getFileSizeString());
        }
        return builder.toString();
    }

    public static String toString(final Iterable<DictionaryStats> stats) {
        final StringBuilder builder = new StringBuilder("LM Stats");
        for (DictionaryStats stat : stats) {
            builder.append("\n    ");
            builder.append(stat.toString());
        }
        return builder.toString();
    }
}
