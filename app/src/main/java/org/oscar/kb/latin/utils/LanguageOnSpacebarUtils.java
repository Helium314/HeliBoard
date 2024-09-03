/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.oscar.kb.latin.utils;

import android.view.inputmethod.InputMethodSubtype;

import androidx.annotation.NonNull;

import org.oscar.kb.latin.settings.Settings;
import org.oscar.kb.latin.RichInputMethodSubtype;
import org.oscar.kb.latin.settings.Settings;

import org.oscar.kb.latin.utils.SubtypeUtilsKt;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * This class determines that the language name on the spacebar should be displayed in what format.
 */
public final class LanguageOnSpacebarUtils {
    public static final int FORMAT_TYPE_NONE = 0;
    public static final int FORMAT_TYPE_LANGUAGE_ONLY = 1;
    public static final int FORMAT_TYPE_FULL_LOCALE = 2;

    private static List<InputMethodSubtype> sEnabledSubtypes = Collections.emptyList();
    private static boolean sIsSystemLanguageSameAsInputLanguage;

    private LanguageOnSpacebarUtils() {
        // This utility class is not publicly instantiable.
    }

    public static int getLanguageOnSpacebarFormatType(
            @NonNull final RichInputMethodSubtype subtype) {
        if (subtype.isNoLanguage()) {
            return FORMAT_TYPE_FULL_LOCALE;
        }
        // Only this subtype is enabled and equals to the system locale.
        if (sEnabledSubtypes.size() < 2 && sIsSystemLanguageSameAsInputLanguage && Settings.getInstance().getCurrent().mSecondaryLocales.isEmpty()) {
            return FORMAT_TYPE_NONE;
        }
        final Locale locale = subtype.getLocale();
        if (locale == null) {
            return FORMAT_TYPE_NONE;
        }
        final String keyboardLanguage = locale.getLanguage();
        final String keyboardLayout = subtype.getKeyboardLayoutSetName();
        int sameLanguageAndLayoutCount = 0;
        for (final InputMethodSubtype ims : sEnabledSubtypes) {
            final String language = SubtypeUtilsKt.locale(ims).getLanguage();
            if (keyboardLanguage.equals(language) && keyboardLayout.equals(
                    SubtypeLocaleUtils.getKeyboardLayoutSetName(ims))) {
                sameLanguageAndLayoutCount++;
            }
        }
        // Display full locale name only when there are multiple subtypes that have the same
        // locale and keyboard layout. Otherwise displaying language name is enough.
        return sameLanguageAndLayoutCount > 1 ? FORMAT_TYPE_FULL_LOCALE
                : FORMAT_TYPE_LANGUAGE_ONLY;
    }

    public static void setEnabledSubtypes(@NonNull final List<InputMethodSubtype> enabledSubtypes) {
        sEnabledSubtypes = enabledSubtypes;
    }

    public static void onSubtypeChanged(@NonNull final RichInputMethodSubtype subtype,
           final boolean implicitlyEnabledSubtype, @NonNull final Locale systemLocale) {
        final Locale newLocale = subtype.getLocale();
        if (systemLocale.equals(newLocale)) {
            sIsSystemLanguageSameAsInputLanguage = true;
            return;
        }
        if (!systemLocale.getLanguage().equals(newLocale.getLanguage())) {
            sIsSystemLanguageSameAsInputLanguage = false;
            return;
        }
        // If the subtype is enabled explicitly, the language name should be displayed even when
        // the keyboard language and the system language are equal.
        sIsSystemLanguageSameAsInputLanguage = implicitlyEnabledSubtype;
    }
}
