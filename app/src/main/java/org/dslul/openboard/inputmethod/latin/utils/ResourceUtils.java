/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.utils;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Build;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;

import org.dslul.openboard.inputmethod.annotations.UsedForTesting;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.settings.SettingsValues;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.PatternSyntaxException;

public final class ResourceUtils {
    private static final String TAG = ResourceUtils.class.getSimpleName();

    public static final float UNDEFINED_RATIO = -1.0f;
    public static final int UNDEFINED_DIMENSION = -1;

    private ResourceUtils() {
        // This utility class is not publicly instantiable.
    }

    private static final HashMap<String, String> sDeviceOverrideValueMap = new HashMap<>();

    private static final String[] BUILD_KEYS_AND_VALUES = {
        "HARDWARE", Build.HARDWARE,
        "MODEL", Build.MODEL,
        "BRAND", Build.BRAND,
        "MANUFACTURER", Build.MANUFACTURER
    };
    private static final HashMap<String, String> sBuildKeyValues;
    private static final String sBuildKeyValuesDebugString;

    static {
        sBuildKeyValues = new HashMap<>();
        final ArrayList<String> keyValuePairs = new ArrayList<>();
        final int keyCount = BUILD_KEYS_AND_VALUES.length / 2;
        for (int i = 0; i < keyCount; i++) {
            final int index = i * 2;
            final String key = BUILD_KEYS_AND_VALUES[index];
            final String value = BUILD_KEYS_AND_VALUES[index + 1];
            sBuildKeyValues.put(key, value);
            keyValuePairs.add(key + '=' + value);
        }
        sBuildKeyValuesDebugString = "[" + TextUtils.join(" ", keyValuePairs) + "]";
    }

    public static String getDeviceOverrideValue(final Resources res, final int overrideResId,
            final String defaultValue) {
        final int orientation = res.getConfiguration().orientation;
        final String key = overrideResId + "-" + orientation;
        if (sDeviceOverrideValueMap.containsKey(key)) {
            return sDeviceOverrideValueMap.get(key);
        }

        final String[] overrideArray = res.getStringArray(overrideResId);
        final String overrideValue = findConstantForKeyValuePairs(sBuildKeyValues, overrideArray);
        // The overrideValue might be an empty string.
        if (overrideValue != null) {
            Log.i(TAG, "Find override value:"
                    + " resource="+ res.getResourceEntryName(overrideResId)
                    + " build=" + sBuildKeyValuesDebugString
                    + " override=" + overrideValue);
            sDeviceOverrideValueMap.put(key, overrideValue);
            return overrideValue;
        }

        sDeviceOverrideValueMap.put(key, defaultValue);
        return defaultValue;
    }

    @SuppressWarnings("serial")
    static class DeviceOverridePatternSyntaxError extends Exception {
        public DeviceOverridePatternSyntaxError(final String message, final String expression) {
            this(message, expression, null);
        }

        public DeviceOverridePatternSyntaxError(final String message, final String expression,
                final Throwable throwable) {
            super(message + ": " + expression, throwable);
        }
    }

    /**
     * Find the condition that fulfills specified key value pairs from an array of
     * "condition,constant", and return the corresponding string constant. A condition is
     * "pattern1[:pattern2...] (or an empty string for the default). A pattern is
     * "key=regexp_value" string. The condition matches only if all patterns of the condition
     * are true for the specified key value pairs.
     *
     * For example, "condition,constant" has the following format.
     *  - HARDWARE=mako,constantForNexus4
     *  - MODEL=Nexus 4:MANUFACTURER=LGE,constantForNexus4
     *  - ,defaultConstant
     *
     * @param keyValuePairs attributes to be used to look for a matched condition.
     * @param conditionConstantArray an array of "condition,constant" elements to be searched.
     * @return the constant part of the matched "condition,constant" element. Returns null if no
     * condition matches.
     * @see org.dslul.openboard.inputmethod.latin.utils.ResourceUtilsTests#testFindConstantForKeyValuePairsRegexp()
     */
    @UsedForTesting
    static String findConstantForKeyValuePairs(final HashMap<String, String> keyValuePairs,
            final String[] conditionConstantArray) {
        if (conditionConstantArray == null || keyValuePairs == null) {
            return null;
        }
        String foundValue = null;
        for (final String conditionConstant : conditionConstantArray) {
            final int posComma = conditionConstant.indexOf(',');
            if (posComma < 0) {
                Log.w(TAG, "Array element has no comma: " + conditionConstant);
                continue;
            }
            final String condition = conditionConstant.substring(0, posComma);
            if (condition.isEmpty()) {
                Log.w(TAG, "Array element has no condition: " + conditionConstant);
                continue;
            }
            try {
                if (fulfillsCondition(keyValuePairs, condition)) {
                    // Take first match
                    if (foundValue == null) {
                        foundValue = conditionConstant.substring(posComma + 1);
                    }
                    // And continue walking through all conditions.
                }
            } catch (final DeviceOverridePatternSyntaxError e) {
                Log.w(TAG, "Syntax error, ignored", e);
            }
        }
        return foundValue;
    }

    private static boolean fulfillsCondition(final HashMap<String,String> keyValuePairs,
            final String condition) throws DeviceOverridePatternSyntaxError {
        final String[] patterns = condition.split(":");
        // Check all patterns in a condition are true
        boolean matchedAll = true;
        for (final String pattern : patterns) {
            final int posEqual = pattern.indexOf('=');
            if (posEqual < 0) {
                throw new DeviceOverridePatternSyntaxError("Pattern has no '='", condition);
            }
            final String key = pattern.substring(0, posEqual);
            final String value = keyValuePairs.get(key);
            if (value == null) {
                throw new DeviceOverridePatternSyntaxError("Unknown key", condition);
            }
            final String patternRegexpValue = pattern.substring(posEqual + 1);
            try {
                if (!value.matches(patternRegexpValue)) {
                    matchedAll = false;
                    // And continue walking through all patterns.
                }
            } catch (final PatternSyntaxException e) {
                throw new DeviceOverridePatternSyntaxError("Syntax error", condition, e);
            }
        }
        return matchedAll;
    }

    public static int getKeyboardWidth(final Resources res, final SettingsValues settingsValues) {
        final int defaultKeyboardWidth = getDefaultKeyboardWidth(res);
        if (settingsValues.mOneHandedModeEnabled) {
            return (int) res.getFraction(R.fraction.config_one_handed_mode_width,
                    defaultKeyboardWidth, defaultKeyboardWidth);
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

    private static int getDefaultKeyboardHeight(final Resources res, final boolean showsNumberRow) {
        final DisplayMetrics dm = res.getDisplayMetrics();
        // increase height by 20% if number row is enabled
        final float keyboardHeight = res.getDimension(R.dimen.config_default_keyboard_height) * (showsNumberRow ? 1.2f : 1f);
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

    // {@link Resources#getDimensionPixelOffset(int)} may return zero pixel offset.
    public static boolean isValidDimensionPixelOffset(final int dimension) {
        return dimension >= 0;
    }

    public static float getFloatFromFraction(final Resources res, final int fractionResId) {
        return res.getFraction(fractionResId, 1, 1);
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

    public static int getEnumValue(final TypedArray a, final int index, final int defValue) {
        final TypedValue value = a.peekValue(index);
        if (value == null) {
            return defValue;
        }
        if (isIntegerValue(value)) {
            return a.getInt(index, defValue);
        }
        return defValue;
    }

    public static boolean isFractionValue(final TypedValue v) {
        return v.type == TypedValue.TYPE_FRACTION;
    }

    public static boolean isDimensionValue(final TypedValue v) {
        return v.type == TypedValue.TYPE_DIMENSION;
    }

    public static boolean isIntegerValue(final TypedValue v) {
        return v.type >= TypedValue.TYPE_FIRST_INT && v.type <= TypedValue.TYPE_LAST_INT;
    }

    public static boolean isStringValue(final TypedValue v) {
        return v.type == TypedValue.TYPE_STRING;
    }

    public static boolean isNight(final Resources res) {
        // todo: night mode can be unspecified -> maybe need to adjust for correct behavior on some devices?
        return (res.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }
}
