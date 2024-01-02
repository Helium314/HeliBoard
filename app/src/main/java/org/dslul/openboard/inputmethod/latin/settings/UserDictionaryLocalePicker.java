/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.settings;

import androidx.fragment.app.Fragment;

import java.util.Locale;

// Caveat: This class is basically taken from
// packages/apps/Settings/src/com/android/settings/inputmethod/UserDictionaryLocalePicker.java
// in order to deal with some devices that have issues with the user dictionary handling

public class UserDictionaryLocalePicker extends SubScreenFragment {
    public UserDictionaryLocalePicker() {
        super();
        // TODO: implement
    }

    public interface LocationChangedListener {
        void onLocaleSelected(Locale locale);
    }
}
