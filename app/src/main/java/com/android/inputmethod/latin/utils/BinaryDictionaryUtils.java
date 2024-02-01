/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.android.inputmethod.latin.utils;

import com.android.inputmethod.latin.BinaryDictionary;
import helium314.keyboard.latin.common.StringUtils;
import helium314.keyboard.latin.makedict.DictionaryHeader;
import helium314.keyboard.latin.makedict.UnsupportedFormatException;
import helium314.keyboard.latin.utils.JniUtils;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BinaryDictionaryUtils {
    private static final String TAG = BinaryDictionaryUtils.class.getSimpleName();

    private BinaryDictionaryUtils() {
        // This utility class is not publicly instantiable.
    }

    static {
        JniUtils.loadNativeLibrary();
    }

    private static native boolean createEmptyDictFileNative(String filePath, long dictVersion,
            String locale, String[] attributeKeyStringArray, String[] attributeValueStringArray);
    private static native float calcNormalizedScoreNative(int[] before, int[] after, int score);
    private static native int setCurrentTimeForTestNative(int currentTime);

    public static DictionaryHeader getHeader(final File dictFile)
            throws IOException, UnsupportedFormatException {
        return getHeaderWithOffsetAndLength(dictFile, 0 /* offset */, dictFile.length());
    }

    public static DictionaryHeader getHeaderWithOffsetAndLength(final File dictFile,
            final long offset, final long length) throws IOException, UnsupportedFormatException {
        // dictType is never used for reading the header. Passing an empty string.
        final BinaryDictionary binaryDictionary = new BinaryDictionary(
                dictFile.getAbsolutePath(), offset, length,
                true /* useFullEditDistance */, null /* locale */, "" /* dictType */,
                false /* isUpdatable */);
        final DictionaryHeader header = binaryDictionary.getHeader();
        binaryDictionary.close();
        if (header == null) {
            throw new IOException();
        }
        return header;
    }

    public static boolean renameDict(final File dictFile, final File newDictFile) {
        if (dictFile.isFile()) {
            return dictFile.renameTo(newDictFile);
        } else if (dictFile.isDirectory()) {
            final String dictName = dictFile.getName();
            final String newDictName = newDictFile.getName();
            if (newDictFile.exists()) {
                return false;
            }
            for (final File file : dictFile.listFiles()) {
                if (!file.isFile()) {
                    continue;
                }
                final String fileName = file.getName();
                final String newFileName = fileName.replaceFirst(
                        Pattern.quote(dictName), Matcher.quoteReplacement(newDictName));
                if (!file.renameTo(new File(dictFile, newFileName))) {
                    return false;
                }
            }
            return dictFile.renameTo(newDictFile);
        }
        return false;
    }

    public static boolean createEmptyDictFile(final String filePath, final long dictVersion,
            final Locale locale, final Map<String, String> attributeMap) {
        final String[] keyArray = new String[attributeMap.size()];
        final String[] valueArray = new String[attributeMap.size()];
        int index = 0;
        for (final String key : attributeMap.keySet()) {
            keyArray[index] = key;
            valueArray[index] = attributeMap.get(key);
            index++;
        }
        return createEmptyDictFileNative(filePath, dictVersion, locale.toString(), keyArray,
                valueArray);
    }

    public static float calcNormalizedScore(final String before, final String after,
            final int score) {
        return calcNormalizedScoreNative(StringUtils.toCodePointArray(before),
                StringUtils.toCodePointArray(after), score);
    }

    /**
     * Control the current time to be used in the native code. If currentTime >= 0, this method sets
     * the current time and gets into test mode.
     * In test mode, set timestamp is used as the current time in the native code.
     * If currentTime < 0, quit the test mode and returns to using time() to get the current time.
     *
     * @param currentTime seconds since the unix epoch
     * @return current time got in the native code.
     */
    public static int setCurrentTimeForTest(final int currentTime) {
        return setCurrentTimeForTestNative(currentTime);
    }
}
