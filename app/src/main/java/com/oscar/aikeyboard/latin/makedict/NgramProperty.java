/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.oscar.aikeyboard.latin.makedict;


import com.oscar.aikeyboard.latin.NgramContext;

public class NgramProperty {
    public final com.oscar.aikeyboard.latin.makedict.WeightedString mTargetWord;
    public final NgramContext mNgramContext;

    public NgramProperty(final com.oscar.aikeyboard.latin.makedict.WeightedString targetWord, final NgramContext ngramContext) {
        mTargetWord = targetWord;
        mNgramContext = ngramContext;
    }

    @Override
    public int hashCode() {
        return mTargetWord.hashCode() ^ mNgramContext.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof NgramProperty)) return false;
        final NgramProperty n = (NgramProperty)o;
        return mTargetWord.equals(n.mTargetWord) && mNgramContext.equals(n.mNgramContext);
    }
}
