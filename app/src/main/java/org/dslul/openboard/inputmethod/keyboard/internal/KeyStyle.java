/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.keyboard.internal;

import android.content.res.TypedArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class KeyStyle {
    private final KeyboardTextsSet mTextsSet;

    public abstract @Nullable String[] getStringArray(TypedArray a, int index);
    public abstract @Nullable String getString(TypedArray a, int index);
    public abstract int getInt(TypedArray a, int index, int defaultValue);
    public abstract int getFlags(TypedArray a, int index);

    protected KeyStyle(@NonNull final KeyboardTextsSet textsSet) {
        mTextsSet = textsSet;
    }

    @Nullable
    protected String parseString(final TypedArray a, final int index) {
        if (a.hasValue(index)) {
            return mTextsSet.resolveTextReference(a.getString(index));
        }
        return null;
    }

    @Nullable
    protected String[] parseStringArray(final TypedArray a, final int index) {
        if (a.hasValue(index)) {
            final String text = mTextsSet.resolveTextReference(a.getString(index));
            return MoreKeySpec.splitKeySpecs(text);
        }
        return null;
    }
}
