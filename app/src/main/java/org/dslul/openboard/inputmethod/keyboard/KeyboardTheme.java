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

package org.dslul.openboard.inputmethod.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build.VERSION_CODES;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.common.Colors;
import org.dslul.openboard.inputmethod.latin.settings.Settings;
import org.dslul.openboard.inputmethod.latin.utils.ColorUtilKt;
import org.dslul.openboard.inputmethod.latin.utils.DeviceProtectedUtils;

import java.util.Arrays;

public final class KeyboardTheme implements Comparable<KeyboardTheme> {

    // old themes
    public static final String THEME_STYLE_MATERIAL = "Material";
    public static final String THEME_STYLE_HOLO = "Holo";

    // new themes using the custom colors
    public static final String THEME_LIGHT = "light";
    public static final String THEME_HOLO_WHITE = "holo_white"; // todo: rename (but useful to have for testing)
    public static final String THEME_DARK = "dark";
    public static final String THEME_DARKER = "darker";
    public static final String THEME_BLACK = "black";
    public static final String THEME_USER = "user";
    public static final String THEME_USER_DARK = "user_dark";
    public static final String[] THEME_VARIANTS = new String[] { THEME_LIGHT, THEME_HOLO_WHITE, THEME_DARK, THEME_DARKER, THEME_BLACK, THEME_USER };
    public static final String[] THEME_VARIANTS_DARK = new String[] { THEME_DARK, THEME_DARKER, THEME_BLACK, THEME_USER_DARK };

    public static final String[] THEME_STYLES = { THEME_STYLE_MATERIAL, THEME_STYLE_HOLO };

    private static final String TAG = KeyboardTheme.class.getSimpleName();

    static final String KLP_KEYBOARD_THEME_KEY = "pref_keyboard_layout_20110916";
    static final String LXX_KEYBOARD_THEME_KEY = "pref_keyboard_theme_20140509";

    // These should be aligned with Keyboard.themeId and Keyboard.Case.keyboardTheme
    // attributes' values in attrs.xml.
    public static final int THEME_ID_HOLO_BASE = 0;
    public static final int THEME_ID_HOLO_BASE_NO_BORDER = 1;
    public static final int THEME_ID_LXX_BASE = 2;
    public static final int THEME_ID_LXX_BASE_BORDER = 3;
    public static final int DEFAULT_THEME_ID = THEME_ID_LXX_BASE;

    /* package private for testing */
    static final KeyboardTheme[] KEYBOARD_THEMES = {
            new KeyboardTheme(THEME_ID_HOLO_BASE, "HoloBase", R.style.KeyboardTheme_HoloBase,
                    VERSION_CODES.BASE),
            new KeyboardTheme(THEME_ID_HOLO_BASE_NO_BORDER, "HoloBaseNoBorder", R.style.KeyboardTheme_HoloBaseNoBorder,
                    VERSION_CODES.BASE),
        new KeyboardTheme(THEME_ID_LXX_BASE, "LXXBase", R.style.KeyboardTheme_LXX_Base,
                VERSION_CODES.LOLLIPOP),
        new KeyboardTheme(THEME_ID_LXX_BASE_BORDER, "LXXBaseBorder", R.style.KeyboardTheme_LXX_Base_Border,
                VERSION_CODES.LOLLIPOP),
    };

    static {
        // Sort {@link #KEYBOARD_THEME} by descending order of {@link #mMinApiVersion}.
        Arrays.sort(KEYBOARD_THEMES);
    }

    public final int mThemeId;
    public final int mStyleId;
    public final String mThemeName;
    public final int mMinApiVersion;

    // Note: The themeId should be aligned with "themeId" attribute of Keyboard style
    // in values/themes-<style>.xml.
    private KeyboardTheme(final int themeId, final String themeName, final int styleId,
            final int minApiVersion) {
        mThemeId = themeId;
        mThemeName = themeName;
        mStyleId = styleId;
        mMinApiVersion = minApiVersion;
    }

    @Override
    public int compareTo(final KeyboardTheme rhs) {
        if (mMinApiVersion > rhs.mMinApiVersion) return -1;
        if (mMinApiVersion < rhs.mMinApiVersion) return 1;
        return 0;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) return true;
        return (o instanceof KeyboardTheme) && ((KeyboardTheme)o).mThemeId == mThemeId;
    }

    @Override
    public int hashCode() {
        return mThemeId;
    }

    /* package private for testing */
    static KeyboardTheme searchKeyboardThemeById(final int themeId,
            final KeyboardTheme[] availableThemeIds) {
        // TODO: This search algorithm isn't optimal if there are many themes.
        for (final KeyboardTheme theme : availableThemeIds) {
            if (theme.mThemeId == themeId) {
                return theme;
            }
        }
        return null;
    }

    /* package private for testing */
    static KeyboardTheme getDefaultKeyboardTheme(final SharedPreferences prefs,
            final int sdkVersion, final KeyboardTheme[] availableThemeArray) {
        final String klpThemeIdString = prefs.getString(KLP_KEYBOARD_THEME_KEY, null);
        if (klpThemeIdString != null) {
            if (sdkVersion <= VERSION_CODES.KITKAT) {
                try {
                    final int themeId = Integer.parseInt(klpThemeIdString);
                    final KeyboardTheme theme = searchKeyboardThemeById(themeId,
                            availableThemeArray);
                    if (theme != null) {
                        return theme;
                    }
                    Log.w(TAG, "Unknown keyboard theme in KLP preference: " + klpThemeIdString);
                } catch (final NumberFormatException e) {
                    Log.w(TAG, "Illegal keyboard theme in KLP preference: " + klpThemeIdString, e);
                }
            }
            // Remove old preference.
            Log.i(TAG, "Remove KLP keyboard theme preference: " + klpThemeIdString);
            prefs.edit().remove(KLP_KEYBOARD_THEME_KEY).apply();
        }
        // TODO: This search algorithm isn't optimal if there are many themes.
        for (final KeyboardTheme theme : availableThemeArray) {
            if (sdkVersion >= theme.mMinApiVersion) {
                return theme;
            }
        }
        return searchKeyboardThemeById(DEFAULT_THEME_ID, availableThemeArray);
    }

    public static String getKeyboardThemeName(final int themeId) {
        final KeyboardTheme theme = searchKeyboardThemeById(themeId, KEYBOARD_THEMES);
        return theme.mThemeName;
    }

    // todo: this actually should be called style now, as the colors are independent
    //  and selection should be simplified, because really...
    public static KeyboardTheme getKeyboardTheme(final Context context) {
        final SharedPreferences prefs = DeviceProtectedUtils.getSharedPreferences(context);
        final String style = prefs.getString(Settings.PREF_THEME_STYLE, THEME_STYLE_MATERIAL);
        final boolean borders = prefs.getBoolean(Settings.PREF_THEME_KEY_BORDERS, false);
        final int matchingId;
        if (style.equals(THEME_STYLE_HOLO))
            matchingId = borders ? THEME_ID_HOLO_BASE : THEME_ID_HOLO_BASE_NO_BORDER;
        else
            matchingId = borders ? THEME_ID_LXX_BASE_BORDER : THEME_ID_LXX_BASE;
        for (KeyboardTheme keyboardTheme : KEYBOARD_THEMES) {
            if (keyboardTheme.mThemeId == matchingId)
                return keyboardTheme;
        }
        return KEYBOARD_THEMES[3]; // base no border as default
    }

    /* package private for testing */
    static KeyboardTheme getKeyboardTheme(final SharedPreferences prefs, final int sdkVersion,
            final KeyboardTheme[] availableThemeArray) {
        final String lxxThemeIdString = prefs.getString(LXX_KEYBOARD_THEME_KEY, null);
        if (lxxThemeIdString == null) {
            return getDefaultKeyboardTheme(prefs, sdkVersion, availableThemeArray);
        }
        try {
            final int themeId = Integer.parseInt(lxxThemeIdString);
            final KeyboardTheme theme = searchKeyboardThemeById(themeId, availableThemeArray);
            if (theme != null) {
                return theme;
            }
            Log.w(TAG, "Unknown keyboard theme in LXX preference: " + lxxThemeIdString);
        } catch (final NumberFormatException e) {
            Log.w(TAG, "Illegal keyboard theme in LXX preference: " + lxxThemeIdString, e);
        }
        // Remove preference that contains unknown or illegal theme id.
        prefs.edit().remove(LXX_KEYBOARD_THEME_KEY).apply();
        return getDefaultKeyboardTheme(prefs, sdkVersion, availableThemeArray);
    }

    public static String getThemeFamily(int themeId) {
        if (themeId == THEME_ID_HOLO_BASE) return THEME_STYLE_HOLO;
        return THEME_STYLE_MATERIAL;
    }

    public static boolean getHasKeyBorders(int themeId) {
        return themeId != THEME_ID_LXX_BASE; // THEME_ID_LXX_BASE is the only without borders
    }


    // todo (later): material you, system accent, ...
    public static Colors getThemeColors(final String themeColors, final String themeStyle, final Context context, final SharedPreferences prefs) {
        switch (themeColors) {
            case THEME_USER:
                final int accent = prefs.getInt(Settings.PREF_THEME_USER_COLOR_ACCENT, Color.BLUE);
                final int keyBgColor = prefs.getInt(Settings.PREF_THEME_USER_COLOR_KEYS, Color.LTGRAY);
                final int keyTextColor = prefs.getInt(Settings.PREF_THEME_USER_COLOR_TEXT, Color.WHITE);
                final int hintTextColor = prefs.getInt(Settings.PREF_THEME_USER_COLOR_HINT_TEXT, Color.WHITE);
                final int background = prefs.getInt(Settings.PREF_THEME_USER_COLOR_BACKGROUND, Color.DKGRAY);
                return Colors.newColors(themeStyle, accent, background, keyBgColor, ColorUtilKt.brightenOrDarken(keyBgColor, true), keyBgColor, keyTextColor, hintTextColor);
            case THEME_USER_DARK:
                final int accent2 = prefs.getInt(Settings.PREF_THEME_USER_DARK_COLOR_ACCENT, Color.BLUE);
                final int keyBgColor2 = prefs.getInt(Settings.PREF_THEME_USER_DARK_COLOR_KEYS, Color.LTGRAY);
                final int keyTextColor2 = prefs.getInt(Settings.PREF_THEME_USER_DARK_COLOR_TEXT, Color.WHITE);
                final int hintTextColor2 = prefs.getInt(Settings.PREF_THEME_USER_DARK_COLOR_HINT_TEXT, Color.WHITE);
                final int background2 = prefs.getInt(Settings.PREF_THEME_USER_DARK_COLOR_BACKGROUND, Color.DKGRAY);
                return Colors.newColors(themeStyle, accent2, background2, keyBgColor2, ColorUtilKt.brightenOrDarken(keyBgColor2, true), keyBgColor2, keyTextColor2, hintTextColor2);
            case THEME_DARK:
                return Colors.newColors(
                        themeStyle,
                        ContextCompat.getColor(context, R.color.gesture_trail_color_lxx_dark),
                        // colors taken from the drawable
                        Color.parseColor("#263238"),
                        Color.parseColor("#364248"),
                        Color.parseColor("#2d393f"),
                        Color.parseColor("#364248"),
                        ContextCompat.getColor(context, R.color.key_text_color_lxx_dark),
                        ContextCompat.getColor(context, R.color.key_hint_letter_color_lxx_dark)
                );
            case THEME_HOLO_WHITE:
                return Colors.newColors(
                        themeStyle,
                        Color.parseColor("#FFFFFF"),
                        // colors taken from the drawable
                        Color.parseColor("#282828"),
                        Color.parseColor("#FFFFFF"), // transparency!
                        Color.parseColor("#444444"), // should be 222222, but the key drawable is already grey
                        Color.parseColor("#FFFFFF"),
                        Color.parseColor("#FFFFFF"),
                        Color.parseColor("#282828")
                );
            case THEME_DARKER:
                return Colors.newColors(
                        themeStyle,
                        ContextCompat.getColor(context, R.color.gesture_trail_color_lxx_dark),
                        ContextCompat.getColor(context, R.color.keyboard_background_lxx_dark_border),
                        ContextCompat.getColor(context, R.color.key_background_normal_lxx_dark_border),
                        ContextCompat.getColor(context, R.color.key_background_functional_lxx_dark_border),
                        ContextCompat.getColor(context, R.color.key_background_normal_lxx_dark_border),
                        ContextCompat.getColor(context, R.color.key_text_color_lxx_dark),
                        ContextCompat.getColor(context, R.color.key_hint_letter_color_lxx_dark)
                );
            case THEME_BLACK:
                return Colors.newColors(
                        themeStyle,
                        ContextCompat.getColor(context, R.color.gesture_trail_color_lxx_dark),
                        ContextCompat.getColor(context, R.color.background_amoled_black),
                        ContextCompat.getColor(context, R.color.background_amoled_dark),
                        ContextCompat.getColor(context, R.color.background_amoled_dark),
                        ContextCompat.getColor(context, R.color.background_amoled_dark),
                        ContextCompat.getColor(context, R.color.key_text_color_lxx_dark),
                        ContextCompat.getColor(context, R.color.key_hint_letter_color_lxx_dark)
                );
            case THEME_LIGHT:
            default:
                return Colors.newColors(
                        themeStyle,
                        ContextCompat.getColor(context, R.color.gesture_trail_color_lxx_light),
                        ContextCompat.getColor(context, R.color.keyboard_background_lxx_light_border),
                        ContextCompat.getColor(context, R.color.key_background_normal_lxx_light_border),
                        ContextCompat.getColor(context, R.color.key_background_functional_lxx_light_border),
                        ContextCompat.getColor(context, R.color.key_background_normal_lxx_light_border),
                        ContextCompat.getColor(context, R.color.key_text_color_lxx_light),
                        ContextCompat.getColor(context, R.color.key_hint_letter_color_lxx_light)
                );
        }
    }
}
