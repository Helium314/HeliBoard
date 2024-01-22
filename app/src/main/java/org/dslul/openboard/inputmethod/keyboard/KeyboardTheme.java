/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Build.VERSION_CODES;

import androidx.core.content.ContextCompat;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.common.Colors;
import org.dslul.openboard.inputmethod.latin.common.DynamicColors;
import org.dslul.openboard.inputmethod.latin.common.DefaultColors;
import org.dslul.openboard.inputmethod.latin.settings.Settings;
import org.dslul.openboard.inputmethod.latin.utils.DeviceProtectedUtils;

import java.util.Arrays;

public final class KeyboardTheme {

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
    public static final String THEME_DYNAMIC = "dynamic";
    public static final String THEME_USER = "user";
    public static final String THEME_USER_NIGHT = "user_night";
    public static final String THEME_BLUE_GRAY = "blue_gray";
    public static final String THEME_BROWN = "brown";
    public static final String THEME_CHOCOLATE = "chocolate";
    public static final String THEME_CLOUDY = "cloudy";
    public static final String THEME_FOREST = "forest";
    public static final String THEME_INDIGO = "indigo";
    public static final String THEME_OCEAN = "ocean";
    public static final String THEME_PINK = "pink";
    public static final String THEME_SAND = "sand";
    public static final String THEME_VIOLETTE = "violette";

    public static final String[] COLORS = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
            ? new String[] { THEME_LIGHT, THEME_HOLO_WHITE, THEME_DARK, THEME_DARKER, THEME_BLACK, THEME_USER,
            THEME_BLUE_GRAY, THEME_BROWN, THEME_CHOCOLATE, THEME_CLOUDY, THEME_FOREST, THEME_INDIGO,
            THEME_PINK, THEME_OCEAN, THEME_SAND, THEME_VIOLETTE }
            : new String[] { THEME_LIGHT, THEME_HOLO_WHITE, THEME_DARK, THEME_DARKER, THEME_BLACK, THEME_DYNAMIC,
            THEME_USER, THEME_BLUE_GRAY, THEME_BROWN, THEME_CHOCOLATE, THEME_CLOUDY, THEME_FOREST, THEME_INDIGO,
            THEME_PINK, THEME_OCEAN, THEME_SAND, THEME_VIOLETTE } ;
    public static final String[] COLORS_DARK = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
            ? new String[] { THEME_HOLO_WHITE, THEME_DARK, THEME_DARKER, THEME_BLACK, THEME_USER_NIGHT,
            THEME_CHOCOLATE, THEME_CLOUDY, THEME_FOREST, THEME_OCEAN, THEME_VIOLETTE }
            : new String[] { THEME_HOLO_WHITE, THEME_DARK, THEME_DARKER, THEME_BLACK, THEME_DYNAMIC,
            THEME_USER_NIGHT, THEME_CHOCOLATE, THEME_CLOUDY, THEME_FOREST, THEME_OCEAN, THEME_VIOLETTE } ;

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
            new KeyboardTheme(THEME_ID_HOLO_BASE, "HoloBase", R.style.KeyboardTheme_HoloBase),
            new KeyboardTheme(THEME_ID_LXX_BASE, "LXXBase", R.style.KeyboardTheme_LXX_Base),
            new KeyboardTheme(THEME_ID_LXX_BASE_BORDER, "LXXBaseBorder", R.style.KeyboardTheme_LXX_Base_Border),
            new KeyboardTheme(THEME_ID_ROUNDED_BASE, "RoundedBase", R.style.KeyboardTheme_Rounded_Base),
            new KeyboardTheme(THEME_ID_ROUNDED_BASE_BORDER, "RoundedBaseBorder", R.style.KeyboardTheme_Rounded_Base_Border)
    };

    public final int mThemeId;
    public final int mStyleId;
    public final String mThemeName;

    // Note: The themeId should be aligned with "themeId" attribute of Keyboard style
    // in values/themes-<style>.xml.
    private KeyboardTheme(final int themeId, final String themeName, final int styleId) {
        mThemeId = themeId;
        mThemeName = themeName;
        mStyleId = styleId;
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

    public static int getThemeActionAndEmojiKeyLabelFlags(final int themeId) {
        if (themeId == THEME_ID_LXX_BASE || themeId == THEME_ID_ROUNDED_BASE)
            return Key.LABEL_FLAGS_KEEP_BACKGROUND_ASPECT_RATIO;
        return 0;
    }

    public static Colors getThemeColors(final String themeColors, final String themeStyle, final Context context, final SharedPreferences prefs) {
        final boolean hasBorders = prefs.getBoolean(Settings.PREF_THEME_KEY_BORDERS, false);
        switch (themeColors) {
            case THEME_USER:
                return new DefaultColors(
                        themeStyle,
                        hasBorders,
                        Settings.readUserColor(prefs, context, Settings.PREF_COLOR_ACCENT_SUFFIX, false),
                        Settings.readUserColor(prefs, context, Settings.PREF_COLOR_GESTURE_SUFFIX, false),
                        Settings.readUserColor(prefs, context, Settings.PREF_COLOR_BACKGROUND_SUFFIX, false),
                        Settings.readUserColor(prefs, context, Settings.PREF_COLOR_KEYS_SUFFIX, false),
                        Settings.readUserColor(prefs, context, Settings.PREF_COLOR_FUNCTIONAL_KEYS_SUFFIX, false),
                        Settings.readUserColor(prefs, context, Settings.PREF_COLOR_SPACEBAR_SUFFIX, false),
                        Settings.readUserColor(prefs, context, Settings.PREF_COLOR_TEXT_SUFFIX, false),
                        Settings.readUserColor(prefs, context, Settings.PREF_COLOR_HINT_TEXT_SUFFIX, false),
                        Settings.readUserColor(prefs, context, Settings.PREF_COLOR_SUGGESTION_TEXT_SUFFIX, false),
                        Settings.readUserColor(prefs, context, Settings.PREF_COLOR_SPACEBAR_TEXT_SUFFIX, false)
                );
            case THEME_USER_NIGHT:
                return new DefaultColors(
                        themeStyle,
                        hasBorders,
                        Settings.readUserColor(prefs, context, Settings.PREF_COLOR_ACCENT_SUFFIX, true),
                        Settings.readUserColor(prefs, context, Settings.PREF_COLOR_GESTURE_SUFFIX, false),
                        Settings.readUserColor(prefs, context, Settings.PREF_COLOR_BACKGROUND_SUFFIX, true),
                        Settings.readUserColor(prefs, context, Settings.PREF_COLOR_KEYS_SUFFIX, true),
                        Settings.readUserColor(prefs, context, Settings.PREF_COLOR_FUNCTIONAL_KEYS_SUFFIX, true),
                        Settings.readUserColor(prefs, context, Settings.PREF_COLOR_SPACEBAR_SUFFIX, true),
                        Settings.readUserColor(prefs, context, Settings.PREF_COLOR_TEXT_SUFFIX, true),
                        Settings.readUserColor(prefs, context, Settings.PREF_COLOR_HINT_TEXT_SUFFIX, true),
                        Settings.readUserColor(prefs, context, Settings.PREF_COLOR_SUGGESTION_TEXT_SUFFIX, true),
                        Settings.readUserColor(prefs, context, Settings.PREF_COLOR_SPACEBAR_TEXT_SUFFIX, true)
                );
            case THEME_DARK:
                return new DefaultColors(
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
                        ContextCompat.getColor(context, R.color.key_text_color_lxx_dark),
                        ContextCompat.getColor(context, R.color.spacebar_letter_color_lxx_dark)
                );
            case THEME_HOLO_WHITE:
                return new DefaultColors(
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
                        Color.parseColor("#FFFFFF"),
                        Color.parseColor("#80FFFFFF")
                );
            case THEME_DARKER:
                return new DefaultColors(
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
                        ContextCompat.getColor(context, R.color.key_text_color_lxx_dark),
                        ContextCompat.getColor(context, R.color.spacebar_letter_color_lxx_dark)
                );
            case THEME_BLACK:
                return new DefaultColors(
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
                        ContextCompat.getColor(context, R.color.key_text_color_lxx_dark),
                        ContextCompat.getColor(context, R.color.spacebar_letter_color_lxx_dark)
                );
            case THEME_DYNAMIC:
                if (Build.VERSION.SDK_INT >= VERSION_CODES.S) {
                    return new DynamicColors(context, themeStyle, hasBorders);
                }
            case THEME_BLUE_GRAY:
                return new DefaultColors(
                        themeStyle,
                        hasBorders,
                        Color.rgb(120, 144, 156),
                        Color.rgb(120, 144, 156),
                        Color.rgb(236, 239, 241),
                        Color.rgb(255, 255, 255),
                        Color.rgb(207, 216, 220),
                        Color.rgb(255, 255, 255),
                        Color.rgb(0, 0, 0),
                        Color.rgb(0, 0, 0),
                        Color.rgb(0, 0, 0),
                        Color.rgb(0, 0, 0)
                );
            case THEME_BROWN:
                return new DefaultColors(
                        themeStyle,
                        hasBorders,
                        Color.rgb(141, 110, 99),
                        Color.rgb(141, 110, 99),
                        Color.rgb(239, 235, 233),
                        Color.rgb(255, 255, 255),
                        Color.rgb(215, 204, 200),
                        Color.rgb(255, 255, 255),
                        Color.rgb(0, 0, 0),
                        Color.rgb(0, 0, 0),
                        Color.rgb(0, 0, 0),
                        Color.rgb(0, 0, 0)
                );
            case THEME_CHOCOLATE:
                return new DefaultColors(
                        themeStyle,
                        hasBorders,
                        Color.rgb(80, 128, 255),
                        Color.rgb(80, 128, 255),
                        Color.rgb(140, 112, 94),
                        Color.rgb(193, 163, 146),
                        Color.rgb(168, 127, 103),
                        Color.rgb(193, 163, 146),
                        Color.rgb(255, 255, 255),
                        Color.rgb(255, 255, 255),
                        Color.rgb(255, 255, 255),
                        Color.rgb(255, 255, 255)
                );
            case THEME_CLOUDY:
                return new DefaultColors(
                        themeStyle,
                        hasBorders,
                        Color.rgb(255,113,129),
                        Color.rgb(255,113,129),
                        Color.rgb(81,97,113),
                        Color.rgb(117, 128, 142),
                        Color.rgb(99, 109, 121),
                        Color.rgb(117, 128, 142),
                        Color.rgb(255, 255, 255),
                        Color.rgb(255, 255, 255),
                        Color.rgb(255, 255, 255),
                        Color.rgb(255, 255, 255)
                );
            case THEME_FOREST:
                return new DefaultColors(
                        themeStyle,
                        hasBorders,
                        Color.rgb(75, 110, 75),
                        Color.rgb(75, 110, 75),
                        Color.rgb(181, 125, 88),
                        Color.rgb(228, 212, 191),
                        Color.rgb(212, 186, 153),
                        Color.rgb(228, 212, 191),
                        Color.rgb(0, 50, 0),
                        Color.rgb(0, 50, 0),
                        Color.rgb(0, 50, 0),
                        Color.rgb(0, 80, 0)
                );
            case THEME_INDIGO:
                return new DefaultColors(
                        themeStyle,
                        hasBorders,
                        Color.rgb(92, 107, 192),
                        Color.rgb(92, 107, 192),
                        Color.rgb(232, 234, 246),
                        Color.rgb(255, 255, 255),
                        Color.rgb(197, 202, 233),
                        Color.rgb(255, 255, 255),
                        Color.rgb(0, 0, 0),
                        Color.rgb(0, 0, 0),
                        Color.rgb(0, 0, 0),
                        Color.rgb(0, 0, 0)
                );
            case THEME_OCEAN:
                return new DefaultColors(
                        themeStyle,
                        hasBorders,
                        Color.rgb(255, 124, 0),
                        Color.rgb(255, 124, 0),
                        Color.rgb(89, 109, 155),
                        Color.rgb(132, 157, 212),
                        Color.rgb(81, 116, 194),
                        Color.rgb(132, 157, 212),
                        Color.rgb(255, 255, 255),
                        Color.rgb(255, 255, 255),
                        Color.rgb(255, 255, 255),
                        Color.rgb(255, 255, 255)
                );
            case THEME_PINK:
                return new DefaultColors(
                        themeStyle,
                        hasBorders,
                        Color.rgb(236, 64, 122),
                        Color.rgb(236, 64, 122),
                        Color.rgb(252, 228, 236),
                        Color.rgb(255, 255, 255),
                        Color.rgb(248, 187, 208),
                        Color.rgb(255, 255, 255),
                        Color.rgb(0, 0, 0),
                        Color.rgb(0, 0, 0),
                        Color.rgb(0, 0, 0),
                        Color.rgb(0, 0, 0)
                );
            case THEME_SAND:
                return new DefaultColors(
                        themeStyle,
                        hasBorders,
                        Color.rgb(110, 155, 255),
                        Color.rgb(110, 155, 255),
                        Color.rgb(242, 232, 218),
                        Color.rgb(255, 255, 255),
                        Color.rgb(234, 211, 185),
                        Color.rgb(255, 255, 255),
                        Color.rgb(0, 0, 0),
                        Color.rgb(0, 0, 0),
                        Color.rgb(0, 0, 0),
                        Color.rgb(0, 0, 0)
                );
            case THEME_VIOLETTE:
                return new DefaultColors(
                        themeStyle,
                        hasBorders,
                        Color.rgb(255, 96, 255),
                        Color.rgb(255, 96, 255),
                        Color.rgb(112, 112, 174),
                        Color.rgb(150, 150, 216),
                        Color.rgb(123, 123, 206),
                        Color.rgb(150, 150, 216),
                        Color.rgb(255, 255, 255),
                        Color.rgb(255, 255, 255),
                        Color.rgb(255, 255, 255),
                        Color.rgb(255, 255, 255)
                );
            case THEME_LIGHT:
            default:
                return new DefaultColors(
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
                        ContextCompat.getColor(context, R.color.key_text_color_lxx_light),
                        ContextCompat.getColor(context, R.color.spacebar_letter_color_lxx_light)
                );
        }
    }
}
