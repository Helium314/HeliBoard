/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal;

import android.text.TextUtils;
import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import helium314.keyboard.keyboard.Key;
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode;
import helium314.keyboard.latin.common.CollectionUtils;
import helium314.keyboard.latin.common.Constants;
import helium314.keyboard.latin.common.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

/**
 * The popup key specification object. The popup keys are an array of {@link PopupKeySpec}.
 * <p>
 * The popup keys specification is comma separated "key specification" each of which represents one
 * "popup key".
 * The key specification might have label or string resource reference in it. These references are
 * expanded before parsing comma.
 * Special character, comma ',' backslash '\' can be escaped by '\' character.
 * Note that the '\' is also parsed by XML parser and {@link PopupKeySpec#splitKeySpecs(String)}
 * as well.
 */
// TODO: Should extend the key specification object.
public final class PopupKeySpec {
    public final int mCode;
    @Nullable
    public final String mLabel;
    @Nullable
    public final String mOutputText;
    @Nullable
    public final String mIconName;

    public PopupKeySpec(@NonNull final String popupKeySpec, boolean needsToUpperCase,
                        @NonNull final Locale locale) {
        this(popupKeySpec, needsToUpperCase, locale, getCode(popupKeySpec, needsToUpperCase, locale), null);
    }

    public PopupKeySpec(@NonNull final String popupKeySpec, boolean needsToUpperCase,
                        @NonNull final Locale locale, int code, @Nullable String parentLabel) {
        if (popupKeySpec.isEmpty()) {
            throw new KeySpecParser.KeySpecParserError("Empty popup key spec");
        }
        final String label = KeySpecParser.getLabel(popupKeySpec);
        mLabel = needsToUpperCase ? StringUtils.toTitleCaseOfKeyLabel(label, locale) : label;
        if (code == KeyCode.NOT_SPECIFIED) {
            // Some letter, for example German Eszett (U+00DF: "ß"), has multiple characters
            // upper case representation ("SS").
            mCode = KeyCode.MULTIPLE_CODE_POINTS;
            mOutputText = mLabel;
        } else {
            mCode = code;
            final String outputText = KeySpecParser.getOutputText(parentLabel != null? parentLabel : popupKeySpec, code);
            mOutputText = needsToUpperCase
                    ? StringUtils.toTitleCaseOfKeyLabel(outputText, locale) : outputText;
        }
        mIconName = KeySpecParser.getIconName(popupKeySpec);
    }

    @NonNull
    public Key buildKey(final int x, final int y, final int labelFlags, final int background,
                        @NonNull final KeyboardParams params, PopupKeySpec[] popupKeys, String hintLabel,
                        String parentLabel) {
        return new Key(mLabel, mIconName, mCode, mOutputText, hintLabel, labelFlags, background, x, y,
                       params.mDefaultAbsoluteKeyWidth, params.mDefaultAbsoluteRowHeight, params.mHorizontalGap,
                       params.mVerticalGap, popupKeys);
    }

    @Override
    public int hashCode() {
        int hashCode = 31 + mCode;
        final String iconName = mIconName;
        hashCode = hashCode * 31 + (iconName == null ? 0 : iconName.hashCode());
        final String label = mLabel;
        hashCode = hashCode * 31 + (label == null ? 0 : label.hashCode());
        final String outputText = mOutputText;
        hashCode = hashCode * 31 + (outputText == null ? 0 : outputText.hashCode());
        return hashCode;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof PopupKeySpec) {
            final PopupKeySpec other = (PopupKeySpec)o;
            return mCode == other.mCode
                    && TextUtils.equals(mIconName, other.mIconName)
                    && TextUtils.equals(mLabel, other.mLabel)
                    && TextUtils.equals(mOutputText, other.mOutputText);
        }
        return false;
    }

    @Override
    public String toString() {
        final String label = (mIconName == null ? mLabel
                : KeyboardIconsSet.PREFIX_ICON + mIconName);
        final String output = (mCode == KeyCode.MULTIPLE_CODE_POINTS ? mOutputText
                : Constants.printableCode(mCode));
        if (StringUtils.codePointCount(label) == 1 && label.codePointAt(0) == mCode) {
            return output;
        }
        return label + "|" + output;
    }

    public static class LettersOnBaseLayout {
        private final SparseIntArray mCodes = new SparseIntArray();
        private final HashSet<String> mTexts = new HashSet<>();

        public void addLetter(@NonNull final Key.KeyParams key) {
            final int code = key.mCode;
            if (code > 32) {
                mCodes.put(code, 0);
            } else if (code == KeyCode.MULTIPLE_CODE_POINTS) {
                mTexts.add(key.getOutputText());
            }
        }

        public boolean contains(@NonNull final PopupKeySpec popupKey) {
            final int code = popupKey.mCode;
            if (mCodes.indexOfKey(code) >= 0) {
                return true;
            } else return code == KeyCode.MULTIPLE_CODE_POINTS && mTexts.contains(popupKey.mOutputText);
        }
    }

    @Nullable
    public static PopupKeySpec[] removeRedundantPopupKeys(@Nullable final PopupKeySpec[] popupKeys,
            @NonNull final LettersOnBaseLayout lettersOnBaseLayout) {
        if (popupKeys == null) {
            return null;
        }
        final ArrayList<PopupKeySpec> filteredPopupKeys = new ArrayList<>();
        for (final PopupKeySpec popupKey : popupKeys) {
            if (!lettersOnBaseLayout.contains(popupKey)) {
                filteredPopupKeys.add(popupKey);
            }
        }
        final int size = filteredPopupKeys.size();
        if (size == popupKeys.length) {
            return popupKeys;
        }
        if (size == 0) {
            return null;
        }
        return filteredPopupKeys.toArray(new PopupKeySpec[size]);
    }

    // Constants for parsing.
    private static final char COMMA = Constants.CODE_COMMA;
    private static final char BACKSLASH = Constants.CODE_BACKSLASH;
    private static final String ADDITIONAL_POPUP_KEY_MARKER =
            StringUtils.newSingleCodePointString(Constants.CODE_PERCENT);

    /**
     * Split the text containing multiple key specifications separated by commas into an array of
     * key specifications.
     * A key specification can contain a character escaped by the backslash character, including a
     * comma character.
     * Note that an empty key specification will be eliminated from the result array.
     *
     * @param text the text containing multiple key specifications.
     * @return an array of key specification text. Null if the specified <code>text</code> is empty
     * or has no key specifications.
     */
    @Nullable
    public static String[] splitKeySpecs(@Nullable final String text) {
        if (TextUtils.isEmpty(text)) {
            return null;
        }
        final int size = text.length();
        // Optimization for one-letter key specification.
        if (size == 1) {
            return text.charAt(0) == COMMA ? null : new String[] { text };
        }

        ArrayList<String> list = null;
        int start = 0;
        // The characters in question in this loop are COMMA and BACKSLASH. These characters never
        // match any high or low surrogate character. So it is OK to iterate through with char
        // index.
        for (int pos = 0; pos < size; pos++) {
            final char c = text.charAt(pos);
            if (c == COMMA) {
                // Skip empty entry.
                if (pos - start > 0) {
                    if (list == null) {
                        list = new ArrayList<>();
                    }
                    list.add(text.substring(start, pos));
                }
                // Skip comma
                start = pos + 1;
            } else if (c == BACKSLASH) {
                // Skip escape character and escaped character.
                pos++;
            }
        }
        final String remain = (size - start > 0) ? text.substring(start) : null;
        if (list == null) {
            return remain != null ? new String[] { remain } : null;
        }
        if (remain != null) {
            list.add(remain);
        }
        return list.toArray(new String[0]);
    }

    @NonNull
    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private static int getCode(@NonNull String popupKeySpec, boolean needsToUpperCase, @NonNull Locale locale) {
        final int codeInSpec = KeySpecParser.getCode(popupKeySpec);
        return needsToUpperCase? StringUtils.toTitleCaseOfKeyCode(codeInSpec, locale) : codeInSpec;
    }

    @NonNull
    public static String[] filterOutEmptyString(@Nullable final String[] array) {
        if (array == null) {
            return EMPTY_STRING_ARRAY;
        }
        ArrayList<String> out = null;
        for (int i = 0; i < array.length; i++) {
            final String entry = array[i];
            if (TextUtils.isEmpty(entry)) {
                if (out == null) {
                    out = CollectionUtils.arrayAsList(array, 0, i);
                }
            } else if (out != null) {
                out.add(entry);
            }
        }
        if (out == null) {
            return array;
        }
        return out.toArray(new String[0]);
    }

    public static String[] insertAdditionalPopupKeys(@Nullable final String[] popupKeySpecs,
            @Nullable final String[] additionalPopupKeySpecs) {
        final String[] popupKeys = filterOutEmptyString(popupKeySpecs);
        final String[] additionalPopupKeys = filterOutEmptyString(additionalPopupKeySpecs);
        final int popupKeysCount = popupKeys.length;
        final int additionalCount = additionalPopupKeys.length;
        ArrayList<String> out = null;
        int additionalIndex = 0;
        for (int popupKeyIndex = 0; popupKeyIndex < popupKeysCount; popupKeyIndex++) {
            final String popupKeySpec = popupKeys[popupKeyIndex];
            if (popupKeySpec.equals(ADDITIONAL_POPUP_KEY_MARKER)) {
                if (additionalIndex < additionalCount) {
                    // Replace '%' marker with additional popup key specification.
                    final String additionalPopupKey = additionalPopupKeys[additionalIndex];
                    if (out != null) {
                        out.add(additionalPopupKey);
                    } else {
                        popupKeys[popupKeyIndex] = additionalPopupKey;
                    }
                    additionalIndex++;
                } else {
                    // Filter out excessive '%' marker.
                    if (out == null) {
                        out = CollectionUtils.arrayAsList(popupKeys, 0, popupKeyIndex);
                    }
                }
            } else {
                if (out != null) {
                    out.add(popupKeySpec);
                }
            }
        }
        if (additionalCount > 0 && additionalIndex == 0) {
            // No '%' marker is found in popup keys.
            // Insert all additional popup keys to the head of popup keys.
            out = CollectionUtils.arrayAsList(additionalPopupKeys, additionalIndex, additionalCount);
            for (int i = 0; i < popupKeysCount; i++) {
                out.add(popupKeys[i]);
            }
        } else if (additionalIndex < additionalCount) {
            // The number of '%' markers are less than additional popup keys.
            // Append remained additional popup keys to the tail of popup keys.
            out = CollectionUtils.arrayAsList(popupKeys, 0, popupKeysCount);
            for (int i = additionalIndex; i < additionalCount; i++) {
                out.add(additionalPopupKeys[i]);
            }
        }
        if (out == null && popupKeysCount > 0) {
            return popupKeys;
        } else if (out != null && out.size() > 0) {
            return out.toArray(new String[0]);
        } else {
            return null;
        }
    }

    public static int getIntValue(@Nullable final String[] popupKeys, final String key,
            final int defaultValue) {
        if (popupKeys == null) {
            return defaultValue;
        }
        final int keyLen = key.length();
        boolean foundValue = false;
        int value = defaultValue;
        for (int i = 0; i < popupKeys.length; i++) {
            final String popupKeySpec = popupKeys[i];
            if (popupKeySpec == null || !popupKeySpec.startsWith(key)) {
                continue;
            }
            popupKeys[i] = null;
            try {
                if (!foundValue) {
                    value = Integer.parseInt(popupKeySpec.substring(keyLen));
                    foundValue = true;
                }
            } catch (NumberFormatException e) {
                throw new RuntimeException(
                        "integer should follow after " + key + ": " + popupKeySpec);
            }
        }
        return value;
    }

    public static boolean getBooleanValue(@Nullable final String[] popupKeys, final String key) {
        if (popupKeys == null) {
            return false;
        }
        boolean value = false;
        for (int i = 0; i < popupKeys.length; i++) {
            final String popupKeySpec = popupKeys[i];
            if (popupKeySpec == null || !popupKeySpec.equals(key)) {
                continue;
            }
            popupKeys[i] = null;
            value = true;
        }
        return value;
    }
}
