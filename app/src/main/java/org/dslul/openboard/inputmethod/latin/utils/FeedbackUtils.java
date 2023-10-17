/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.utils;

import android.content.Context;
import android.content.Intent;

@SuppressWarnings("unused")
public class FeedbackUtils {
    public static boolean isHelpAndFeedbackFormSupported() {
        return false;
    }

    public static void showHelpAndFeedbackForm(Context context) {
    }

    public static int getAboutKeyboardTitleResId() {
        return 0;
    }

    public static Intent getAboutKeyboardIntent(Context context) {
        return null;
    }
}
