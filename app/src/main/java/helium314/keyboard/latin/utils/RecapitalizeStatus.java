/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.utils;

import helium314.keyboard.latin.common.StringUtils;

import java.util.Locale;

/**
 * The status of the current recapitalize process.
 */
public class RecapitalizeStatus {
    public static final int NOT_A_RECAPITALIZE_MODE = -1;
    public static final int CAPS_MODE_ORIGINAL_MIXED_CASE = 0;
    public static final int CAPS_MODE_ALL_LOWER = 1;
    public static final int CAPS_MODE_FIRST_WORD_UPPER = 2;
    public static final int CAPS_MODE_ALL_UPPER = 3;
    // When adding a new mode, don't forget to update the CAPS_MODE_LAST constant.
    public static final int CAPS_MODE_LAST = CAPS_MODE_ALL_UPPER;

    private static final int[] ROTATION_STYLE = {
        CAPS_MODE_ORIGINAL_MIXED_CASE,
        CAPS_MODE_ALL_LOWER,
        CAPS_MODE_FIRST_WORD_UPPER,
        CAPS_MODE_ALL_UPPER
    };

    private static final int getStringMode(final String string, final int[] sortedSeparators) {
        if (StringUtils.isIdenticalAfterUpcase(string)) {
            return CAPS_MODE_ALL_UPPER;
        } else if (StringUtils.isIdenticalAfterDowncase(string)) {
            return CAPS_MODE_ALL_LOWER;
        } else if (StringUtils.isIdenticalAfterCapitalizeEachWord(string, sortedSeparators)) {
            return CAPS_MODE_FIRST_WORD_UPPER;
        } else {
            return CAPS_MODE_ORIGINAL_MIXED_CASE;
        }
    }

    public static String modeToString(final int recapitalizeMode) {
        switch (recapitalizeMode) {
        case NOT_A_RECAPITALIZE_MODE: return "undefined";
        case CAPS_MODE_ORIGINAL_MIXED_CASE: return "mixedCase";
        case CAPS_MODE_ALL_LOWER: return "allLower";
        case CAPS_MODE_FIRST_WORD_UPPER: return "firstWordUpper";
        case CAPS_MODE_ALL_UPPER: return "allUpper";
        default: return "unknown<" + recapitalizeMode + ">";
        }
    }

    /**
     * We store the location of the cursor and the string that was there before the recapitalize
     * action was done, and the location of the cursor and the string that was there after.
     */
    private int mCursorStartBefore;
    private String mStringBefore;
    private int mCursorStartAfter;
    private int mCursorEndAfter;
    private int mRotationStyleCurrentIndex;
    private boolean mSkipOriginalMixedCaseMode;
    private Locale mLocale;
    private int[] mSortedSeparators;
    private String mStringAfter;
    private boolean mIsStarted;
    private boolean mIsEnabled = true;

    private static final int[] EMPTY_STORTED_SEPARATORS = {};

    public RecapitalizeStatus() {
        // By default, initialize with dummy values that won't match any real recapitalize.
        start(-1, -1, "", Locale.getDefault(), EMPTY_STORTED_SEPARATORS);
        stop();
    }

    public void start(final int cursorStart, final int cursorEnd, final String string,
            final Locale locale, final int[] sortedSeparators) {
        if (!mIsEnabled) {
            return;
        }
        mCursorStartBefore = cursorStart;
        mStringBefore = string;
        mCursorStartAfter = cursorStart;
        mCursorEndAfter = cursorEnd;
        mStringAfter = string;
        final int initialMode = getStringMode(mStringBefore, sortedSeparators);
        mLocale = locale;
        mSortedSeparators = sortedSeparators;
        if (CAPS_MODE_ORIGINAL_MIXED_CASE == initialMode) {
            mRotationStyleCurrentIndex = 0;
            mSkipOriginalMixedCaseMode = false;
        } else {
            // Find the current mode in the array.
            int currentMode;
            for (currentMode = ROTATION_STYLE.length - 1; currentMode > 0; --currentMode) {
                if (ROTATION_STYLE[currentMode] == initialMode) {
                    break;
                }
            }
            mRotationStyleCurrentIndex = currentMode;
            mSkipOriginalMixedCaseMode = true;
        }
        mIsStarted = true;
    }

    public void stop() {
        mIsStarted = false;
    }

    public boolean isStarted() {
        return mIsStarted;
    }

    public void enable() {
        mIsEnabled = true;
    }

    public void disable() {
        mIsEnabled = false;
    }

    public boolean mIsEnabled() {
        return mIsEnabled;
    }

    public boolean isSetAt(final int cursorStart, final int cursorEnd) {
        return cursorStart == mCursorStartAfter && cursorEnd == mCursorEndAfter;
    }

    /**
     * Rotate through the different possible capitalization modes.
     */
    public void rotate() {
        final String oldResult = mStringAfter;
        int count = 0; // Protection against infinite loop.
        do {
            mRotationStyleCurrentIndex = (mRotationStyleCurrentIndex + 1) % ROTATION_STYLE.length;
            if (CAPS_MODE_ORIGINAL_MIXED_CASE == ROTATION_STYLE[mRotationStyleCurrentIndex]
                    && mSkipOriginalMixedCaseMode) {
                mRotationStyleCurrentIndex =
                        (mRotationStyleCurrentIndex + 1) % ROTATION_STYLE.length;
            }
            ++count;
            switch (ROTATION_STYLE[mRotationStyleCurrentIndex]) {
            case CAPS_MODE_ORIGINAL_MIXED_CASE:
                mStringAfter = mStringBefore;
                break;
            case CAPS_MODE_ALL_LOWER:
                mStringAfter = mStringBefore.toLowerCase(mLocale);
                break;
            case CAPS_MODE_FIRST_WORD_UPPER:
                mStringAfter = StringUtils.capitalizeEachWord(mStringBefore, mSortedSeparators,
                        mLocale);
                break;
            case CAPS_MODE_ALL_UPPER:
                mStringAfter = mStringBefore.toUpperCase(mLocale);
                break;
            default:
                mStringAfter = mStringBefore;
            }
        } while (mStringAfter.equals(oldResult) && count < ROTATION_STYLE.length + 1);
        mCursorEndAfter = mCursorStartAfter + mStringAfter.length();
    }

    /**
     * Remove leading/trailing whitespace from the considered string.
     */
    public void trim() {
        final int len = mStringBefore.length();
        int nonWhitespaceStart = 0;
        for (; nonWhitespaceStart < len;
                nonWhitespaceStart = mStringBefore.offsetByCodePoints(nonWhitespaceStart, 1)) {
            final int codePoint = mStringBefore.codePointAt(nonWhitespaceStart);
            if (!Character.isWhitespace(codePoint)) break;
        }
        int nonWhitespaceEnd = len;
        for (; nonWhitespaceEnd > 0;
                nonWhitespaceEnd = mStringBefore.offsetByCodePoints(nonWhitespaceEnd, -1)) {
            final int codePoint = mStringBefore.codePointBefore(nonWhitespaceEnd);
            if (!Character.isWhitespace(codePoint)) break;
        }
        // If nonWhitespaceStart >= nonWhitespaceEnd, that means the selection contained only
        // whitespace, so we leave it as is.
        if ((0 != nonWhitespaceStart || len != nonWhitespaceEnd)
                && nonWhitespaceStart < nonWhitespaceEnd) {
            mCursorEndAfter = mCursorStartBefore + nonWhitespaceEnd;
            mCursorStartBefore = mCursorStartAfter = mCursorStartBefore + nonWhitespaceStart;
            mStringAfter = mStringBefore =
                    mStringBefore.substring(nonWhitespaceStart, nonWhitespaceEnd);
        }
    }

    public String getRecapitalizedString() {
        return mStringAfter;
    }

    public int getNewCursorStart() {
        return mCursorStartAfter;
    }

    public int getNewCursorEnd() {
        return mCursorEndAfter;
    }

    public int getCurrentMode() {
        return ROTATION_STYLE[mRotationStyleCurrentIndex];
    }
}
