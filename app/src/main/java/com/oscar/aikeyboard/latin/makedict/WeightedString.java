/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.oscar.aikeyboard.latin.makedict;

import java.util.Arrays;

/**
 * A string with a probability.
 *
 * This represents an "attribute", that is either a bigram or a shortcut.
 */
public final class WeightedString {
    public final String mWord;
    public com.oscar.aikeyboard.latin.makedict.ProbabilityInfo mProbabilityInfo;

    public WeightedString(final String word, final int probability) {
        this(word, new com.oscar.aikeyboard.latin.makedict.ProbabilityInfo(probability));
    }

    public WeightedString(final String word, final com.oscar.aikeyboard.latin.makedict.ProbabilityInfo probabilityInfo) {
        mWord = word;
        mProbabilityInfo = probabilityInfo;
    }

    public int getProbability() {
        return mProbabilityInfo.mProbability;
    }

    public void setProbability(final int probability) {
        mProbabilityInfo = new com.oscar.aikeyboard.latin.makedict.ProbabilityInfo(probability);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[] { mWord, mProbabilityInfo});
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof WeightedString)) return false;
        final WeightedString w = (WeightedString)o;
        return mWord.equals(w.mWord) && mProbabilityInfo.equals(w.mProbabilityInfo);
    }
}