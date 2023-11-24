/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.utils;

import org.dslul.openboard.inputmethod.latin.settings.AdvancedSettingsFragment;
import org.dslul.openboard.inputmethod.latin.settings.AppearanceSettingsFragment;
import org.dslul.openboard.inputmethod.latin.settings.CorrectionSettingsFragment;
import org.dslul.openboard.inputmethod.latin.settings.DebugSettingsFragment;
import org.dslul.openboard.inputmethod.latin.settings.GestureSettingsFragment;
import org.dslul.openboard.inputmethod.latin.settings.LanguageSettingsFragment;
import org.dslul.openboard.inputmethod.latin.settings.PreferencesSettingsFragment;
import org.dslul.openboard.inputmethod.latin.settings.SettingsFragment;
import org.dslul.openboard.inputmethod.latin.settings.AboutFragment;
import org.dslul.openboard.inputmethod.latin.spellcheck.SpellCheckerSettingsFragment;
import org.dslul.openboard.inputmethod.latin.userdictionary.UserDictionaryAddWordFragment;
import org.dslul.openboard.inputmethod.latin.userdictionary.UserDictionaryList;
import org.dslul.openboard.inputmethod.latin.userdictionary.UserDictionaryLocalePicker;
import org.dslul.openboard.inputmethod.latin.userdictionary.UserDictionarySettings;

import java.util.HashSet;

public class FragmentUtils {
    private static final HashSet<String> sLatinImeFragments = new HashSet<>();
    static {
        sLatinImeFragments.add(PreferencesSettingsFragment.class.getName());
        sLatinImeFragments.add(AppearanceSettingsFragment.class.getName());
        sLatinImeFragments.add(GestureSettingsFragment.class.getName());
        sLatinImeFragments.add(CorrectionSettingsFragment.class.getName());
        sLatinImeFragments.add(AdvancedSettingsFragment.class.getName());
        sLatinImeFragments.add(DebugSettingsFragment.class.getName());
        sLatinImeFragments.add(SettingsFragment.class.getName());
        sLatinImeFragments.add(AboutFragment.class.getName());
        sLatinImeFragments.add(SpellCheckerSettingsFragment.class.getName());
        sLatinImeFragments.add(UserDictionaryAddWordFragment.class.getName());
        sLatinImeFragments.add(UserDictionaryList.class.getName());
        sLatinImeFragments.add(UserDictionaryLocalePicker.class.getName());
        sLatinImeFragments.add(UserDictionarySettings.class.getName());
        sLatinImeFragments.add(LanguageSettingsFragment.class.getName());
    }

    public static boolean isValidFragment(String fragmentName) {
        return sLatinImeFragments.contains(fragmentName);
    }
}
