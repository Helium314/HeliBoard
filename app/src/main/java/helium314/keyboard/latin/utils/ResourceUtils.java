/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.utils;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import androidx.core.util.TypedValueCompat;

import helium314.keyboard.latin.R;
import helium314.keyboard.latin.settings.SettingsValues;

public final class ResourceUtils {

    public static final float UNDEFINED_RATIO = -1.0f;
    public static final int UNDEFINED_DIMENSION = -1;

    private ResourceUtils() {
        // This utility class is not publicly instantiable.
    }

    public static int getKeyboardWidth(final Resources res, final SettingsValues settingsValues) {
        final int defaultKeyboardWidth = getDefaultKeyboardWidth(res);
        if (settingsValues.mOneHandedModeEnabled) {
            return (int) (settingsValues.mOneHandedModeScale * defaultKeyboardWidth);
        }
        return defaultKeyboardWidth;
    }

    public static int getDefaultKeyboardWidth(final Resources res) {
        final DisplayMetrics dm = res.getDisplayMetrics();
        return dm.widthPixels;
    }

    public static int getKeyboardHeight(final Resources res, final SettingsValues settingsValues) {
        final int defaultKeyboardHeight = getDefaultKeyboardHeight(res, settingsValues.mShowsNumberRow);
        // mKeyboardHeightScale Ranges from [.5,1.5], from xml/prefs_screen_appearance.xml
        return (int)(defaultKeyboardHeight * settingsValues.mKeyboardHeightScale);
    }

    public static int getDefaultKeyboardHeight(final Resources res, final boolean showsNumberRow) {
        final DisplayMetrics dm = res.getDisplayMetrics();
        final float keyboardHeight = res.getDimension(R.dimen.config_default_keyboard_height) * (showsNumberRow ? 1.33f : 1f);
        final float maxKeyboardHeight = res.getFraction(
                R.fraction.config_max_keyboard_height, dm.heightPixels, dm.heightPixels);
        float minKeyboardHeight = res.getFraction(
                R.fraction.config_min_keyboard_height, dm.heightPixels, dm.heightPixels);
        if (minKeyboardHeight < 0.0f) {
            // Specified fraction was negative, so it should be calculated against display
            // width.
            minKeyboardHeight = -res.getFraction(
                    R.fraction.config_min_keyboard_height, dm.widthPixels, dm.widthPixels);
        }
        // Keyboard height will not exceed maxKeyboardHeight and will not be less than
        // minKeyboardHeight.
        return (int)Math.max(Math.min(keyboardHeight, maxKeyboardHeight), minKeyboardHeight);
    }

    public static boolean isValidFraction(final float fraction) {
        return fraction >= 0.0f;
    }

    // {@link Resources#getDimensionPixelSize(int)} returns at least one pixel size.
    public static boolean isValidDimensionPixelSize(final int dimension) {
        return dimension > 0;
    }

    public static float getFraction(final TypedArray a, final int index, final float defValue) {
        final TypedValue value = a.peekValue(index);
        if (value == null || !isFractionValue(value)) {
            return defValue;
        }
        return a.getFraction(index, 1, 1, defValue);
    }

    public static float getFraction(final TypedArray a, final int index) {
        return getFraction(a, index, UNDEFINED_RATIO);
    }

    public static int getDimensionPixelSize(final TypedArray a, final int index) {
        final TypedValue value = a.peekValue(index);
        if (value == null || !isDimensionValue(value)) {
            return ResourceUtils.UNDEFINED_DIMENSION;
        }
        return a.getDimensionPixelSize(index, ResourceUtils.UNDEFINED_DIMENSION);
    }

    public static float getDimensionOrFraction(final TypedArray a, final int index, final int base,
            final float defValue) {
        final TypedValue value = a.peekValue(index);
        if (value == null) {
            return defValue;
        }
        if (isFractionValue(value)) {
            return a.getFraction(index, base, base, defValue);
        } else if (isDimensionValue(value)) {
            return a.getDimension(index, defValue);
        }
        return defValue;
    }

    public static boolean isFractionValue(final TypedValue v) {
        return v.type == TypedValue.TYPE_FRACTION;
    }

    public static boolean isDimensionValue(final TypedValue v) {
        return v.type == TypedValue.TYPE_DIMENSION;
    }

    public static boolean isNight(final Resources res) {
        return (res.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    public static int toPx(final int dp, final Resources res) {
        return (int) TypedValueCompat.dpToPx(dp, res.getDisplayMetrics());
    }
}
