/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.utils;

import android.content.Context;
import android.view.ContextThemeWrapper;

import org.dslul.openboard.inputmethod.latin.R;

public final class DialogUtils {
    private DialogUtils() {
        // This utility class is not publicly instantiable.
    }

    // this is necessary for dialogs and popup menus created outside an activity
    public static Context getPlatformDialogThemeContext(final Context context) {
        // Because {@link AlertDialog.Builder.create()} doesn't honor the specified theme with
        // createThemeContextWrapper=false, the result dialog box has unneeded paddings around it.
        return new ContextThemeWrapper(context, R.style.platformActivityTheme);
    }
}
