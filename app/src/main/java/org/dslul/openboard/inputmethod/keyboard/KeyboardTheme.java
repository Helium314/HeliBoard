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
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.common.Colors;
import org.dslul.openboard.inputmethod.latin.settings.Settings;
import org.dslul.openboard.inputmethod.latin.utils.DeviceProtectedUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class KeyboardTheme implements Comparable<KeyboardTheme> {

    public static final String THEME_FAMILY_MATERIAL = "Material";
    public static final String THEME_FAMILY_HOLO = "Holo (Legacy)";
    public static final String THEME_VARIANT_LIGHT = "Light";
    public static final String THEME_VARIANT_DARK = "Dark";
    public static final String THEME_VARIANT_WHITE = "White";
    public static final String THEME_VARIANT_BLUE = "Blue";
    public static final String THEME_VARIANT_CUSTOM = "User-defined";
    public static final String THEME_VARIANT_HOLO_USER = "User-defined (Holo)";

    public static final String[] THEME_FAMILIES = {THEME_FAMILY_MATERIAL, THEME_FAMILY_HOLO};
    public static final Map<String, String[]> THEME_VARIANTS = new HashMap<>();

    static {
        THEME_VARIANTS.put(THEME_FAMILY_MATERIAL,
                new String[] {THEME_VARIANT_LIGHT, THEME_VARIANT_DARK, THEME_VARIANT_CUSTOM});
        THEME_VARIANTS.put(THEME_FAMILY_HOLO,
                new String[] {THEME_VARIANT_WHITE, THEME_VARIANT_BLUE, THEME_VARIANT_HOLO_USER});
        THEME_VARIANTS.put(THEME_FAMILY_HOLO,
                new String[] {THEME_VARIANT_WHITE, THEME_VARIANT_BLUE, THEME_VARIANT_HOLO_USER});
    }

    private static final String TAG = KeyboardTheme.class.getSimpleName();

    static final String KLP_KEYBOARD_THEME_KEY = "pref_keyboard_layout_20110916";
    static final String LXX_KEYBOARD_THEME_KEY = "pref_keyboard_theme_20140509";

    // These should be aligned with Keyboard.themeId and Keyboard.Case.keyboardTheme
    // attributes' values in attrs.xml.
    public static final int THEME_ID_ICS = 0;
    public static final int THEME_ID_KLP = 2;
    public static final int THEME_ID_KLP_CUSTOM = 13;
    public static final int THEME_ID_LXX_LIGHT = 3;
    public static final int THEME_ID_LXX_DARK_AMOLED = 4;
    public static final int THEME_ID_LXX_AUTO_AMOLED = 10;
    public static final int THEME_ID_LXX_LIGHT_BORDER = 5;
    public static final int THEME_ID_LXX_DARK_BORDER = 6;
    public static final int THEME_ID_LXX_DARK = 7;
    public static final int THEME_ID_LXX_AUTO = 9;
    public static final int THEME_ID_LXX_AUTO_BORDER = 8;
    public static final int THEME_ID_LXX_CUSTOM = 11;
    public static final int THEME_ID_LXX_CUSTOM_BORDER = 12;
    public static final int DEFAULT_THEME_ID = THEME_ID_LXX_DARK_BORDER;

    private static KeyboardTheme[] AVAILABLE_KEYBOARD_THEMES;

    /* package private for testing */
    static final KeyboardTheme[] KEYBOARD_THEMES = {
        new KeyboardTheme(THEME_ID_ICS, "ICS", R.style.KeyboardTheme_ICS,
                // This has never been selected because we support ICS or later.
                VERSION_CODES.BASE),
        new KeyboardTheme(THEME_ID_KLP, "KLP", R.style.KeyboardTheme_KLP,
                // Default theme for ICS, JB, and KLP.
                VERSION_CODES.ICE_CREAM_SANDWICH),
        new KeyboardTheme(THEME_ID_LXX_LIGHT, "LXXLight", R.style.KeyboardTheme_LXX_Light,
                // Default theme for LXX.
                VERSION_CODES.BASE),
        new KeyboardTheme(THEME_ID_LXX_DARK, "LXXDark", R.style.KeyboardTheme_LXX_Dark,
                // This has never been selected as default theme.
                VERSION_CODES.BASE),
        new KeyboardTheme(THEME_ID_LXX_DARK_AMOLED, "LXXDarkAmoled", R.style.KeyboardTheme_LXX_Dark_Amoled,
                // This has never been selected as default theme.
                VERSION_CODES.BASE),
        new KeyboardTheme(THEME_ID_LXX_LIGHT_BORDER, "LXXLightBorder", R.style.KeyboardTheme_LXX_Light_Border,
                // This has never been selected as default theme.
                Build.VERSION_CODES.BASE),
        new KeyboardTheme(THEME_ID_LXX_DARK_BORDER, "LXXDarkBorder", R.style.KeyboardTheme_LXX_Dark_Border,
                // This has never been selected as default theme.
                VERSION_CODES.LOLLIPOP),
        new KeyboardTheme(THEME_ID_LXX_AUTO_BORDER, "LXXAutoBorder", R.style.KeyboardTheme_LXX_Auto_Border,
                // This has never been selected as default theme.
                VERSION_CODES.LOLLIPOP),
        new KeyboardTheme(THEME_ID_LXX_AUTO, "LXXAuto", R.style.KeyboardTheme_LXX_Auto,
                // This has never been selected as default theme.
                VERSION_CODES.LOLLIPOP),
        new KeyboardTheme(THEME_ID_LXX_AUTO_AMOLED, "LXXAutoAmoled", R.style.KeyboardTheme_LXX_Auto_Amoled,
                // This has never been selected as default theme.
                VERSION_CODES.LOLLIPOP),
        new KeyboardTheme(THEME_ID_LXX_CUSTOM, "LXXCustom", R.style.KeyboardTheme_LXX_Base,
                // This has never been selected as default theme.
                VERSION_CODES.LOLLIPOP),
        new KeyboardTheme(THEME_ID_LXX_CUSTOM_BORDER, "LXXCustomBorder", R.style.KeyboardTheme_LXX_Base_Border,
                // This has never been selected as default theme.
                VERSION_CODES.LOLLIPOP),
        new KeyboardTheme(THEME_ID_KLP_CUSTOM, "KLPCustom", R.style.KeyboardTheme_KLP,
                // This has never been selected as default theme.
                VERSION_CODES.BASE),
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

    public static void saveKeyboardThemeId(final int themeId, final SharedPreferences prefs) {
        saveKeyboardThemeId(themeId, prefs, Build.VERSION.SDK_INT);
    }

    /* package private for testing */
    static String getPreferenceKey(final int sdkVersion) {
        if (sdkVersion <= VERSION_CODES.KITKAT) {
            return KLP_KEYBOARD_THEME_KEY;
        }
        return LXX_KEYBOARD_THEME_KEY;
    }

    /* package private for testing */
    static void saveKeyboardThemeId(final int themeId, final SharedPreferences prefs,
            final int sdkVersion) {
        final String prefKey = getPreferenceKey(sdkVersion);
        prefs.edit().putString(prefKey, Integer.toString(themeId)).apply();
    }

    public static KeyboardTheme getKeyboardTheme(final Context context) {
        final SharedPreferences prefs = DeviceProtectedUtils.getSharedPreferences(context);
        return getKeyboardTheme(prefs, Build.VERSION.SDK_INT, KEYBOARD_THEMES);
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
        if (themeId == THEME_ID_ICS || themeId == THEME_ID_KLP || themeId == THEME_ID_KLP_CUSTOM) return THEME_FAMILY_HOLO;
        return THEME_FAMILY_MATERIAL;
    }

    public static String getThemeVariant(int themeId) {
        switch (themeId) {
            case THEME_ID_LXX_DARK:
            case THEME_ID_LXX_DARK_AMOLED:
            case THEME_ID_LXX_DARK_BORDER:
                return THEME_VARIANT_DARK;
            case THEME_ID_LXX_LIGHT:
            case THEME_ID_LXX_LIGHT_BORDER:
                return THEME_VARIANT_LIGHT;
            case THEME_ID_KLP:
                return THEME_VARIANT_WHITE;
            case THEME_ID_ICS:
                return THEME_VARIANT_BLUE;
            case THEME_ID_LXX_CUSTOM:
            case THEME_ID_LXX_CUSTOM_BORDER:
                return THEME_VARIANT_CUSTOM;
            case THEME_ID_KLP_CUSTOM:
                return THEME_VARIANT_HOLO_USER;
            default:
                return null;
        }
    }

    public static boolean getHasKeyBorders(int themeId) {
        switch (themeId) {
            case THEME_ID_LXX_DARK_BORDER:
            case THEME_ID_LXX_LIGHT_BORDER:
            case THEME_ID_LXX_AUTO_BORDER:
            case THEME_ID_LXX_CUSTOM_BORDER:
            case THEME_ID_ICS:
            case THEME_ID_KLP:
                return true;
            default:
                return false;
        }
    }

    public static boolean getIsCustom(int themeId) {
        switch (themeId) {
            case THEME_ID_LXX_CUSTOM:
            case THEME_ID_LXX_CUSTOM_BORDER:
            case THEME_ID_KLP_CUSTOM:
                return true;
            default:
                return false;
        }
    }

    public static boolean getIsDayNight(int themeId) {
        switch (themeId) {
            case THEME_ID_LXX_AUTO:
            case THEME_ID_LXX_AUTO_AMOLED:
            case THEME_ID_LXX_AUTO_BORDER:
                return true;
            default:
                return false;
        }
    }

    public static boolean getIsAmoledMode(int themeId) {
        switch (themeId) {
            case THEME_ID_LXX_DARK_AMOLED:
            case THEME_ID_LXX_AUTO_AMOLED:
                return true;
            default:
                return false;
        }
    }

    public static int getThemeForParameters(String family, String variant,
            boolean keyBorders, boolean dayNight, boolean amoledMode) {
        if (THEME_FAMILY_HOLO.equals(family)) {
            if (THEME_VARIANT_BLUE.equals(variant)) return THEME_ID_ICS;
            if (THEME_VARIANT_HOLO_USER.equals(variant)) return THEME_ID_KLP_CUSTOM;
            return THEME_ID_KLP;
        }
        if (dayNight) {
            if (keyBorders) return THEME_ID_LXX_AUTO_BORDER;
            if (amoledMode) return THEME_ID_LXX_AUTO_AMOLED;
            return THEME_ID_LXX_AUTO;
        }
        if (THEME_VARIANT_DARK.equals(variant)) {
            if (keyBorders) return THEME_ID_LXX_DARK_BORDER;
            if (amoledMode) return THEME_ID_LXX_DARK_AMOLED;
            return THEME_ID_LXX_DARK;
        }
        if (THEME_VARIANT_CUSTOM.equals(variant)) {
            if (keyBorders) return THEME_ID_LXX_CUSTOM_BORDER;
            return THEME_ID_LXX_CUSTOM;
        }
        if (keyBorders) return THEME_ID_LXX_LIGHT_BORDER;
        return THEME_ID_LXX_LIGHT;
    }

    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";
    public static final String THEME_DARKER = "darker";
    public static final String THEME_BLACK = "black";
    public static final String THEME_USER = "user";
    public static final String[] CUSTOM_THEME_VARIANTS = new String[] { THEME_LIGHT, THEME_DARK, THEME_DARKER, THEME_BLACK, THEME_USER };

    // todo (later): material you, system accent, ...
    // todo: copies of original themes might need adjustments, though maybe it's only Colors that needs to be adjusted
    public static Colors getCustomTheme(String theme, Context context, SharedPreferences prefs) {
        switch (theme) {
            case THEME_USER:
                final int accent = prefs.getInt(Settings.PREF_THEME_USER_COLOR_ACCENT, Color.BLUE);
                final int keyBgColor = prefs.getInt(Settings.PREF_THEME_USER_COLOR_KEYS, Color.LTGRAY);
                final int keyTextColor = prefs.getInt(Settings.PREF_THEME_USER_COLOR_TEXT, Color.WHITE);
                final int hintTextColor = prefs.getInt(Settings.PREF_THEME_USER_COLOR_HINT_TEXT, Color.WHITE);
                final int background = prefs.getInt(Settings.PREF_THEME_USER_COLOR_BACKGROUND, Color.DKGRAY);
                return new Colors(accent, background, keyBgColor, Colors.brightenOrDarken(keyBgColor, true), keyBgColor, keyTextColor, hintTextColor);
            case THEME_DARK:
                return new Colors(
                        ContextCompat.getColor(context, R.color.gesture_trail_color_lxx_dark),
                        // colors taken from the drawable
                        Color.parseColor("#263238"),
                        Color.parseColor("#364248"),
                        Color.parseColor("#2d393f"),
                        Color.parseColor("#364248"),
                        ContextCompat.getColor(context, R.color.key_text_color_lxx_dark),
                        ContextCompat.getColor(context, R.color.key_hint_letter_color_lxx_dark)
                );
            case THEME_DARKER:
                return new Colors(
                        ContextCompat.getColor(context, R.color.gesture_trail_color_lxx_dark),
                        ContextCompat.getColor(context, R.color.keyboard_background_lxx_dark_border),
                        ContextCompat.getColor(context, R.color.key_background_normal_lxx_dark_border),
                        ContextCompat.getColor(context, R.color.key_background_functional_lxx_dark_border),
                        ContextCompat.getColor(context, R.color.key_background_normal_lxx_dark_border),
                        ContextCompat.getColor(context, R.color.key_text_color_lxx_dark),
                        ContextCompat.getColor(context, R.color.key_hint_letter_color_lxx_dark)
                );
            case THEME_BLACK:
                return new Colors(
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
                return new Colors(
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
