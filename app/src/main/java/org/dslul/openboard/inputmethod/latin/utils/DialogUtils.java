/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
