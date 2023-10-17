/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.utils;

import android.content.Context;

import org.dslul.openboard.inputmethod.annotations.UsedForTesting;

public class ManagedProfileUtils {
    private static ManagedProfileUtils INSTANCE = new ManagedProfileUtils();
    private static ManagedProfileUtils sTestInstance;

    private ManagedProfileUtils() {
        // This utility class is not publicly instantiable.
    }

    @UsedForTesting
    public static void setTestInstance(final ManagedProfileUtils testInstance) {
        sTestInstance = testInstance;
    }

    public static ManagedProfileUtils getInstance() {
        return sTestInstance == null ? INSTANCE : sTestInstance;
    }

    public boolean hasWorkProfile(final Context context) {
        return false;
    }
}