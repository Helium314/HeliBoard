/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.userdictionary;

import android.content.Context;
import android.text.TextUtils;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.common.LocaleUtils;

import java.util.Locale;

/**
 * Utilities of the user dictionary settings
 * TODO: We really want to move these utilities to a static library.
 */
public class UserDictionarySettingsUtils {
    public static String getLocaleDisplayName(Context context, String localeStr) {
        if (TextUtils.isEmpty(localeStr)) {
            // CAVEAT: localeStr should not be null because a null locale stands for the system
            // locale in UserDictionary.Words.addWord.
            return context.getResources().getString(R.string.user_dict_settings_all_languages);
        }
        final Locale locale = LocaleUtils.constructLocaleFromString(localeStr);
        final Locale systemLocale = context.getResources().getConfiguration().locale;
        return locale.getDisplayName(systemLocale);
    }
}
