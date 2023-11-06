/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build.VERSION_CODES;

import androidx.core.content.ContextCompat;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.common.Colors;
import org.dslul.openboard.inputmethod.latin.settings.Settings;
import org.dslul.openboard.inputmethod.latin.utils.ColorUtilKt;
import org.dslul.openboard.inputmethod.latin.utils.DeviceProtectedUtils;

import java.util.Arrays;

public final class KeyboardTheme implements Comparable<KeyboardTheme> {

    // old themes
    public static final String STYLE_MATERIAL = "Material";
    public static final String STYLE_HOLO = "Holo";
    public static final String STYLE_ROUNDED = "Rounded";

    // new themes using the custom colors
    public static final String THEME_LIGHT = "light";
    public static final String THEME_HOLO_WHITE = "holo_white";
    public static final String THEME_DARK = "dark";
    public static final String THEME_DARKER = "darker";
    public static final String THEME_BLACK = "black";
    public static final String THEME_USER = "user";
    public static final String THEME_USER_NIGHT = "user_night";
    public static final String[] COLORS = new String[] { THEME_LIGHT, THEME_HOLO_WHITE, THEME_DARK, THEME_DARKER, THEME_BLACK, THEME_USER };
    public static final String[] COLORS_DARK = new String[] { THEME_HOLO_WHITE, THEME_DARK, THEME_DARKER, THEME_BLACK, THEME_USER_NIGHT };

    public static final String[] STYLES = { STYLE_MATERIAL, STYLE_HOLO, STYLE_ROUNDED };

    // These should be aligned with Keyboard.themeId and Keyboard.Case.keyboardTheme
    // attributes' values in attrs.xml.
    public static final int THEME_ID_HOLO_BASE = 0;
    public static final int THEME_ID_LXX_BASE = 1;
    public static final int THEME_ID_LXX_BASE_BORDER = 2;
    public static final int THEME_ID_ROUNDED_BASE = 3;
    public static final int THEME_ID_ROUNDED_BASE_BORDER = 4;
    public static final int DEFAULT_THEME_ID = THEME_ID_LXX_BASE;

    /* package private for testing */
    static final KeyboardTheme[] KEYBOARD_THEMES = {
            new KeyboardTheme(THEME_ID_HOLO_BASE, "HoloBase", R.style.KeyboardTheme_HoloBase,
                    VERSION_CODES.BASE),
            new KeyboardTheme(THEME_ID_LXX_BASE, "LXXBase", R.style.KeyboardTheme_LXX_Base,
                    VERSION_CODES.LOLLIPOP),
            new KeyboardTheme(THEME_ID_LXX_BASE_BORDER, "LXXBaseBorder", R.style.KeyboardTheme_LXX_Base_Border,
                    VERSION_CODES.LOLLIPOP),
            new KeyboardTheme(THEME_ID_ROUNDED_BASE, "RoundedBase", R.style.KeyboardTheme_Rounded_Base,
                    VERSION_CODES.LOLLIPOP),
            new KeyboardTheme(THEME_ID_ROUNDED_BASE_BORDER, "RoundedBaseBorder", R.style.KeyboardTheme_Rounded_Base_Border,
                    VERSION_CODES.LOLLIPOP)
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

    public static String getKeyboardThemeName(final int themeId) {
        final KeyboardTheme theme = searchKeyboardThemeById(themeId, KEYBOARD_THEMES);
        return theme.mThemeName;
    }

    public static KeyboardTheme getKeyboardTheme(final Context context) {
        final SharedPreferences prefs = DeviceProtectedUtils.getSharedPreferences(context);
        final String style = prefs.getString(Settings.PREF_THEME_STYLE, STYLE_MATERIAL);
        final boolean borders = prefs.getBoolean(Settings.PREF_THEME_KEY_BORDERS, false);
        final int matchingId;
        if (style.equals(STYLE_HOLO))
            matchingId = THEME_ID_HOLO_BASE;
        else if (style.equals(STYLE_ROUNDED))
            matchingId = borders ? THEME_ID_ROUNDED_BASE_BORDER : THEME_ID_ROUNDED_BASE;
        else
            matchingId = borders ? THEME_ID_LXX_BASE_BORDER : THEME_ID_LXX_BASE;
        for (KeyboardTheme keyboardTheme : KEYBOARD_THEMES) {
            if (keyboardTheme.mThemeId == matchingId)
                return keyboardTheme;
        }
        return KEYBOARD_THEMES[DEFAULT_THEME_ID];
    }

    public static Colors getThemeColors(final String themeColors, final String themeStyle, final Context context, final SharedPreferences prefs) {
        final boolean hasBorders = prefs.getBoolean(Settings.PREF_THEME_KEY_BORDERS, false);
        switch (themeColors) {
            case THEME_USER:
                final int accent = Settings.readUserColor(prefs, context, Settings.PREF_COLOR_ACCENT_SUFFIX, false);
                final int gesture = Settings.readUserColor(prefs, context, Settings.PREF_COLOR_GESTURE_SUFFIX, false);
                final int keyBgColor = Settings.readUserColor(prefs, context, Settings.PREF_COLOR_KEYS_SUFFIX, false);
                final int functionalKeyBgColor = Settings.readUserColor(prefs, context, Settings.PREF_COLOR_FUNCTIONAL_KEYS_SUFFIX, false);
                final int spaceBarBgColor = Settings.readUserColor(prefs, context, Settings.PREF_COLOR_SPACEBAR_SUFFIX, false);
                final int keyTextColor = Settings.readUserColor(prefs, context, Settings.PREF_COLOR_TEXT_SUFFIX, false);
                final int hintTextColor = Settings.readUserColor(prefs, context, Settings.PREF_COLOR_HINT_TEXT_SUFFIX, false);
                final int spaceBarTextColor = Settings.readUserColor(prefs, context, Settings.PREF_COLOR_SPACEBAR_TEXT_SUFFIX, false);
                final int background = Settings.readUserColor(prefs, context, Settings.PREF_COLOR_BACKGROUND_SUFFIX, false);
                return new Colors(themeStyle, hasBorders, accent, gesture, background, keyBgColor, functionalKeyBgColor, spaceBarBgColor, keyTextColor, hintTextColor, spaceBarTextColor);
            case THEME_USER_NIGHT:
                final int accent2 = Settings.readUserColor(prefs, context, Settings.PREF_COLOR_ACCENT_SUFFIX, true);
                final int gesture2 = Settings.readUserColor(prefs, context, Settings.PREF_COLOR_GESTURE_SUFFIX, false);
                final int keyBgColor2 = Settings.readUserColor(prefs, context, Settings.PREF_COLOR_KEYS_SUFFIX, true);
                final int functionalKeyBgColor2 = Settings.readUserColor(prefs, context, Settings.PREF_COLOR_FUNCTIONAL_KEYS_SUFFIX, true);
                final int spaceBarBgColor2 = Settings.readUserColor(prefs, context, Settings.PREF_COLOR_SPACEBAR_SUFFIX, true);
                final int keyTextColor2 = Settings.readUserColor(prefs, context, Settings.PREF_COLOR_TEXT_SUFFIX, true);
                final int hintTextColor2 = Settings.readUserColor(prefs, context, Settings.PREF_COLOR_HINT_TEXT_SUFFIX, true);
                final int spaceBarTextColor2 = Settings.readUserColor(prefs, context, Settings.PREF_COLOR_SPACEBAR_TEXT_SUFFIX, true);
                final int background2 = Settings.readUserColor(prefs, context, Settings.PREF_COLOR_BACKGROUND_SUFFIX, true);
                return new Colors(themeStyle, hasBorders, accent2, gesture2, background2, keyBgColor2, functionalKeyBgColor2, spaceBarBgColor2, keyTextColor2, hintTextColor2, spaceBarTextColor2);
            case THEME_DARK:
                return new Colors(
                        themeStyle,
                        hasBorders,
                        ContextCompat.getColor(context, R.color.gesture_trail_color_lxx_dark),
                        ContextCompat.getColor(context, R.color.gesture_trail_color_lxx_dark),
                        // colors taken from the drawable
                        Color.parseColor("#263238"),
                        Color.parseColor("#364248"),
                        Color.parseColor("#2d393f"),
                        Color.parseColor("#364248"),
                        ContextCompat.getColor(context, R.color.key_text_color_lxx_dark),
                        ContextCompat.getColor(context, R.color.key_hint_letter_color_lxx_dark),
                        ContextCompat.getColor(context, R.color.spacebar_letter_color_lxx_dark)
                );
            case THEME_HOLO_WHITE:
                return new Colors(
                        themeStyle,
                        hasBorders,
                        Color.parseColor("#FFFFFF"),
                        Color.parseColor("#FFFFFF"),
                        // colors taken from the drawable
                        Color.parseColor("#282828"),
                        Color.parseColor("#FFFFFF"), // transparency!
                        Color.parseColor("#444444"), // should be 222222, but the key drawable is already grey
                        Color.parseColor("#FFFFFF"),
                        Color.parseColor("#FFFFFF"),
                        Color.parseColor("#282828"),
                        Color.parseColor("#80FFFFFF")
                );
            case THEME_DARKER:
                return new Colors(
                        themeStyle,
                        hasBorders,
                        ContextCompat.getColor(context, R.color.gesture_trail_color_lxx_dark),
                        ContextCompat.getColor(context, R.color.gesture_trail_color_lxx_dark),
                        ContextCompat.getColor(context, R.color.keyboard_background_lxx_dark_border),
                        ContextCompat.getColor(context, R.color.key_background_normal_lxx_dark_border),
                        ContextCompat.getColor(context, R.color.key_background_functional_lxx_dark_border),
                        ContextCompat.getColor(context, R.color.key_background_normal_lxx_dark_border),
                        ContextCompat.getColor(context, R.color.key_text_color_lxx_dark),
                        ContextCompat.getColor(context, R.color.key_hint_letter_color_lxx_dark),
                        ContextCompat.getColor(context, R.color.spacebar_letter_color_lxx_dark)
                );
            case THEME_BLACK:
                return new Colors(
                        themeStyle,
                        hasBorders,
                        ContextCompat.getColor(context, R.color.gesture_trail_color_lxx_dark),
                        ContextCompat.getColor(context, R.color.gesture_trail_color_lxx_dark),
                        ContextCompat.getColor(context, R.color.background_amoled_black),
                        ContextCompat.getColor(context, R.color.background_amoled_dark),
                        ContextCompat.getColor(context, R.color.background_amoled_dark),
                        ContextCompat.getColor(context, R.color.background_amoled_dark),
                        ContextCompat.getColor(context, R.color.key_text_color_lxx_dark),
                        ContextCompat.getColor(context, R.color.key_hint_letter_color_lxx_dark),
                        ContextCompat.getColor(context, R.color.spacebar_letter_color_lxx_dark)
                );
            case THEME_LIGHT:
            default:
                return new Colors(
                        themeStyle,
                        hasBorders,
                        ContextCompat.getColor(context, R.color.gesture_trail_color_lxx_light),
                        ContextCompat.getColor(context, R.color.gesture_trail_color_lxx_light),
                        ContextCompat.getColor(context, R.color.keyboard_background_lxx_light_border),
                        ContextCompat.getColor(context, R.color.key_background_normal_lxx_light_border),
                        ContextCompat.getColor(context, R.color.key_background_functional_lxx_light_border),
                        ContextCompat.getColor(context, R.color.key_background_normal_lxx_light_border),
                        ContextCompat.getColor(context, R.color.key_text_color_lxx_light),
                        ContextCompat.getColor(context, R.color.key_hint_letter_color_lxx_light),
                        ContextCompat.getColor(context, R.color.spacebar_letter_color_lxx_light)
                );
        }
    }
}
