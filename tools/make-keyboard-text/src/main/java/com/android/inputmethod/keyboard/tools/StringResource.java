/*
 * Copyright (C) 2012 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.inputmethod.keyboard.tools;

public class StringResource {
    public final String mName;
    public final String mValue;
    public final String mComment;

    public StringResource(final String name, final String value, final String comment) {
        mName = name;
        mValue = value;
        mComment = comment;
    }
}
