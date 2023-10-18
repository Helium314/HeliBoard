/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin;

import org.dslul.openboard.inputmethod.latin.common.FileUtils;

import java.io.File;

/**
 * Immutable class to hold the address of an asset.
 * As opposed to a normal file, an asset is usually represented as a contiguous byte array in
 * the package file. Open it correctly thus requires the name of the package it is in, but
 * also the offset in the file and the length of this data. This class encapsulates these three.
 */
public final class AssetFileAddress {
    public final String mFilename;
    public final long mOffset;
    public final long mLength;

    public AssetFileAddress(final String filename, final long offset, final long length) {
        mFilename = filename;
        mOffset = offset;
        mLength = length;
    }

    public static AssetFileAddress makeFromFile(final File file) {
        if (!file.isFile()) return null;
        return new AssetFileAddress(file.getAbsolutePath(), 0L, file.length());
    }

    public static AssetFileAddress makeFromFileName(final String filename) {
        if (null == filename) return null;
        return makeFromFile(new File(filename));
    }

    public static AssetFileAddress makeFromFileNameAndOffset(final String filename,
            final long offset, final long length) {
        if (null == filename) return null;
        final File f = new File(filename);
        if (!f.isFile()) return null;
        return new AssetFileAddress(filename, offset, length);
    }

    public boolean pointsToPhysicalFile() {
        return 0 == mOffset;
    }

    public void deleteUnderlyingFile() {
        FileUtils.deleteRecursively(new File(mFilename));
    }

    @Override
    public String toString() {
        return String.format("%s (offset=%d, length=%d)", mFilename, mOffset, mLength);
    }
}
