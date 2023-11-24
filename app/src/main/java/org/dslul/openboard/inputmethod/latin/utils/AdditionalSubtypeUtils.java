/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.utils;

import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.InputMethodSubtype;

import org.dslul.openboard.inputmethod.annotations.UsedForTesting;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.common.LocaleUtils;
import org.dslul.openboard.inputmethod.latin.common.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;

import static org.dslul.openboard.inputmethod.latin.common.Constants.Subtype.ExtraValue.ASCII_CAPABLE;
import static org.dslul.openboard.inputmethod.latin.common.Constants.Subtype.ExtraValue.EMOJI_CAPABLE;
import static org.dslul.openboard.inputmethod.latin.common.Constants.Subtype.ExtraValue.IS_ADDITIONAL_SUBTYPE;
import static org.dslul.openboard.inputmethod.latin.common.Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET;
import static org.dslul.openboard.inputmethod.latin.common.Constants.Subtype.ExtraValue.UNTRANSLATABLE_STRING_IN_SUBTYPE_NAME;
import static org.dslul.openboard.inputmethod.latin.common.Constants.Subtype.KEYBOARD_MODE;

public final class AdditionalSubtypeUtils {
    private static final String TAG = AdditionalSubtypeUtils.class.getSimpleName();

    private static final InputMethodSubtype[] EMPTY_SUBTYPE_ARRAY = new InputMethodSubtype[0];

    private AdditionalSubtypeUtils() {
        // This utility class is not publicly instantiable.
    }

    @UsedForTesting
    public static boolean isAdditionalSubtype(final InputMethodSubtype subtype) {
        return subtype.containsExtraValueKey(IS_ADDITIONAL_SUBTYPE);
    }

    private static final String LOCALE_AND_LAYOUT_SEPARATOR = ":";
    private static final int INDEX_OF_LOCALE = 0;
    private static final int INDEX_OF_KEYBOARD_LAYOUT = 1;
    private static final int INDEX_OF_EXTRA_VALUE = 2;
    private static final int LENGTH_WITHOUT_EXTRA_VALUE = (INDEX_OF_KEYBOARD_LAYOUT + 1);
    private static final int LENGTH_WITH_EXTRA_VALUE = (INDEX_OF_EXTRA_VALUE + 1);
    private static final String PREF_SUBTYPE_SEPARATOR = ";";

    private static InputMethodSubtype createAdditionalSubtypeInternal(
            final String localeString, final String keyboardLayoutSetName,
            final boolean isAsciiCapable, final boolean isEmojiCapable) {
        final int nameId = SubtypeLocaleUtils.getSubtypeNameId(localeString, keyboardLayoutSetName);
        final String platformVersionDependentExtraValues = getPlatformVersionDependentExtraValue(
                localeString, keyboardLayoutSetName, isAsciiCapable, isEmojiCapable);
        final int platformVersionIndependentSubtypeId =
                getPlatformVersionIndependentSubtypeId(localeString, keyboardLayoutSetName);
        final InputMethodSubtype.InputMethodSubtypeBuilder builder = new InputMethodSubtype.InputMethodSubtypeBuilder()
                .setSubtypeNameResId(nameId)
                .setSubtypeIconResId(R.drawable.ic_ime_switcher_dark)
                .setSubtypeLocale(localeString)
                .setSubtypeMode(KEYBOARD_MODE)
                .setSubtypeExtraValue(platformVersionDependentExtraValues)
                .setIsAuxiliary(false)
                .setOverridesImplicitlyEnabledSubtype(false)
                .setSubtypeId(platformVersionIndependentSubtypeId)
                .setIsAsciiCapable(isAsciiCapable);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            builder.setLanguageTag(LocaleUtils.constructLocaleFromString(localeString).toLanguageTag());
        return builder.build();
    }

    public static InputMethodSubtype createDummyAdditionalSubtype(
            final String localeString, final String keyboardLayoutSetName) {
        return createAdditionalSubtypeInternal(localeString, keyboardLayoutSetName,
                false /* isAsciiCapable */, false /* isEmojiCapable */);
    }

    public static InputMethodSubtype createAsciiEmojiCapableAdditionalSubtype(
            final String localeString, final String keyboardLayoutSetName) {
        return createAdditionalSubtypeInternal(localeString, keyboardLayoutSetName,
                true /* isAsciiCapable */, true /* isEmojiCapable */);
    }

    public static String getPrefSubtype(final InputMethodSubtype subtype) {
        final String localeString = subtype.getLocale();
        final String keyboardLayoutSetName = SubtypeLocaleUtils.getKeyboardLayoutSetName(subtype);
        final String layoutExtraValue = KEYBOARD_LAYOUT_SET + "=" + keyboardLayoutSetName;
        final String extraValue = StringUtils.removeFromCommaSplittableTextIfExists(
                layoutExtraValue, StringUtils.removeFromCommaSplittableTextIfExists(
                        IS_ADDITIONAL_SUBTYPE, subtype.getExtraValue()));
        final String basePrefSubtype = localeString + LOCALE_AND_LAYOUT_SEPARATOR
                + keyboardLayoutSetName;
        return extraValue.isEmpty() ? basePrefSubtype
                : basePrefSubtype + LOCALE_AND_LAYOUT_SEPARATOR + extraValue;
    }

    public static InputMethodSubtype[] createAdditionalSubtypesArray(final String prefSubtypes) {
        if (TextUtils.isEmpty(prefSubtypes)) {
            return EMPTY_SUBTYPE_ARRAY;
        }
        final String[] prefSubtypeArray = prefSubtypes.split(PREF_SUBTYPE_SEPARATOR);
        final ArrayList<InputMethodSubtype> subtypesList = new ArrayList<>(prefSubtypeArray.length);
        for (final String prefSubtype : prefSubtypeArray) {
            final InputMethodSubtype subtype = createSubtypeFromString(prefSubtype);
            if (subtype != null)
                subtypesList.add(subtype);
        }
        return subtypesList.toArray(new InputMethodSubtype[0]);
    }

    // use string created with getPrefSubtype
    public static InputMethodSubtype createSubtypeFromString(final String prefSubtype) {
        final String[] elems = prefSubtype.split(LOCALE_AND_LAYOUT_SEPARATOR);
        if (elems.length != LENGTH_WITHOUT_EXTRA_VALUE
                && elems.length != LENGTH_WITH_EXTRA_VALUE) {
            Log.w(TAG, "Unknown additional subtype specified: " + prefSubtype);
            return null;
        }
        final String localeString = elems[INDEX_OF_LOCALE];
        final String keyboardLayoutSetName = elems[INDEX_OF_KEYBOARD_LAYOUT];
        // Here we assume that all the additional subtypes have AsciiCapable and EmojiCapable.
        // This is actually what the setting dialog for additional subtype is doing.
        final InputMethodSubtype subtype = createAsciiEmojiCapableAdditionalSubtype(
                localeString, keyboardLayoutSetName);
        if (subtype.getNameResId() == SubtypeLocaleUtils.UNKNOWN_KEYBOARD_LAYOUT) {
            // Skip unknown keyboard layout subtype. This may happen when predefined keyboard
            // layout has been removed.
            return null;
        }
        return subtype;
    }

    public static String createPrefSubtypes(final InputMethodSubtype[] subtypes) {
        if (subtypes == null || subtypes.length == 0) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        for (final InputMethodSubtype subtype : subtypes) {
            if (sb.length() > 0) {
                sb.append(PREF_SUBTYPE_SEPARATOR);
            }
            sb.append(getPrefSubtype(subtype));
        }
        return sb.toString();
    }

    public static String createPrefSubtypes(final String[] prefSubtypes) {
        if (prefSubtypes == null || prefSubtypes.length == 0) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        for (final String prefSubtype : prefSubtypes) {
            if (sb.length() > 0) {
                sb.append(PREF_SUBTYPE_SEPARATOR);
            }
            sb.append(prefSubtype);
        }
        return sb.toString();
    }

    /**
     * Returns the extra value that is optimized for the running OS.
     * <p>
     * Historically the extra value has been used as the last resort to annotate various kinds of
     * attributes. Some of these attributes are valid only on some platform versions. Thus we cannot
     * assume that the extra values stored in a persistent storage are always valid. We need to
     * regenerate the extra value on the fly instead.
     * </p>
     * @param localeString the locale string (e.g., "en_US").
     * @param keyboardLayoutSetName the keyboard layout set name (e.g., "dvorak").
     * @param isAsciiCapable true when ASCII characters are supported with this layout.
     * @param isEmojiCapable true when Unicode Emoji characters are supported with this layout.
     * @return extra value that is optimized for the running OS.
     * @see #getPlatformVersionIndependentSubtypeId(String, String)
     */
    private static String getPlatformVersionDependentExtraValue(final String localeString,
            final String keyboardLayoutSetName, final boolean isAsciiCapable,
            final boolean isEmojiCapable) {
        final ArrayList<String> extraValueItems = new ArrayList<>();
        extraValueItems.add(KEYBOARD_LAYOUT_SET + "=" + keyboardLayoutSetName);
        if (isAsciiCapable) {
            extraValueItems.add(ASCII_CAPABLE);
        }
        if (SubtypeLocaleUtils.isExceptionalLocale(localeString)) {
            extraValueItems.add(UNTRANSLATABLE_STRING_IN_SUBTYPE_NAME + "=" +
                    SubtypeLocaleUtils.getKeyboardLayoutSetDisplayName(keyboardLayoutSetName));
        }
        if (isEmojiCapable) {
            extraValueItems.add(EMOJI_CAPABLE);
        }
        extraValueItems.add(IS_ADDITIONAL_SUBTYPE);
        return TextUtils.join(",", extraValueItems);
    }

    /**
     * Returns the subtype ID that is supposed to be compatible between different version of OSes.
     * <p>
     * From the compatibility point of view, it is important to keep subtype id predictable and
     * stable between different OSes. For this purpose, the calculation code in this method is
     * carefully chosen and then fixed. Treat the following code as no more or less than a
     * hash function. Each component to be hashed can be different from the corresponding value
     * that is used to instantiate {@link InputMethodSubtype} actually.
     * For example, you don't need to update <code>compatibilityExtraValueItems</code> in this
     * method even when we need to add some new extra values for the actual instance of
     * {@link InputMethodSubtype}.
     * </p>
     * @param localeString the locale string (e.g., "en_US").
     * @param keyboardLayoutSetName the keyboard layout set name (e.g., "dvorak").
     * @return a platform-version independent subtype ID.
     * @see #getPlatformVersionDependentExtraValue(String, String, boolean, boolean)
     */
    private static int getPlatformVersionIndependentSubtypeId(final String localeString,
            final String keyboardLayoutSetName) {
        // For compatibility reasons, we concatenate the extra values in the following order.
        // - KeyboardLayoutSet
        // - AsciiCapable
        // - UntranslatableReplacementStringInSubtypeName
        // - EmojiCapable
        // - isAdditionalSubtype
        final ArrayList<String> compatibilityExtraValueItems = new ArrayList<>();
        compatibilityExtraValueItems.add(KEYBOARD_LAYOUT_SET + "=" + keyboardLayoutSetName);
        compatibilityExtraValueItems.add(ASCII_CAPABLE);
        if (SubtypeLocaleUtils.isExceptionalLocale(localeString)) {
            compatibilityExtraValueItems.add(UNTRANSLATABLE_STRING_IN_SUBTYPE_NAME + "=" +
                    SubtypeLocaleUtils.getKeyboardLayoutSetDisplayName(keyboardLayoutSetName));
        }
        compatibilityExtraValueItems.add(EMOJI_CAPABLE);
        compatibilityExtraValueItems.add(IS_ADDITIONAL_SUBTYPE);
        final String compatibilityExtraValues = TextUtils.join(",", compatibilityExtraValueItems);
        return Arrays.hashCode(new Object[] {
                localeString,
                KEYBOARD_MODE,
                compatibilityExtraValues,
                false /* isAuxiliary */,
                false /* overrideImplicitlyEnabledSubtype */ });
    }
}
