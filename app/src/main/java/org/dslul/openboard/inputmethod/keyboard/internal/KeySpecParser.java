/*
 * Copyright (C) 2010 The Android Open Source Project
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

package org.dslul.openboard.inputmethod.keyboard.internal;

import org.dslul.openboard.inputmethod.latin.common.Constants;
import org.dslul.openboard.inputmethod.latin.common.StringUtils;

import static org.dslul.openboard.inputmethod.latin.common.Constants.CODE_OUTPUT_TEXT;
import static org.dslul.openboard.inputmethod.latin.common.Constants.CODE_UNSPECIFIED;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The string parser of the key specification.
 *
 * Each key specification is one of the following:
 * - Label optionally followed by keyOutputText (keyLabel|keyOutputText).
 * - Label optionally followed by code point (keyLabel|!code/code_name).
 * - Icon followed by keyOutputText (!icon/icon_name|keyOutputText).
 * - Icon followed by code point (!icon/icon_name|!code/code_name).
 * Label and keyOutputText are one of the following:
 * - Literal string.
 * - Label reference represented by (!text/label_name), see {@link KeyboardTextsSet}.
 * - String resource reference represented by (!text/resource_name), see {@link KeyboardTextsSet}.
 * Icon is represented by (!icon/icon_name), see {@link KeyboardIconsSet}.
 * Code is one of the following:
 * - Code point presented by hexadecimal string prefixed with "0x"
 * - Code reference represented by (!code/code_name), see {@link KeyboardCodesSet}.
 * Special character, comma ',' backslash '\', and bar '|' can be escaped by '\' character.
 * Note that the '\' is also parsed by XML parser and {@link MoreKeySpec#splitKeySpecs(String)}
 * as well.
 */
// TODO: Rename to KeySpec and make this class to the key specification object.
public final class KeySpecParser {
    // Constants for parsing.
    private static final char BACKSLASH = Constants.CODE_BACKSLASH;
    private static final char VERTICAL_BAR = Constants.CODE_VERTICAL_BAR;
    private static final String PREFIX_HEX = "0x";

    private KeySpecParser() {
        // Intentional empty constructor for utility class.
    }

    private static boolean hasIcon(@NonNull final String keySpec) {
        return keySpec.startsWith(KeyboardIconsSet.PREFIX_ICON);
    }

    private static boolean hasCode(@NonNull final String keySpec, final int labelEnd) {
        if (labelEnd <= 0 || labelEnd + 1 >= keySpec.length()) {
            return false;
        }
        if (keySpec.startsWith(KeyboardCodesSet.PREFIX_CODE, labelEnd + 1)) {
            return true;
        }
        // This is a workaround to have a key that has a supplementary code point. We can't put a
        // string in resource as a XML entity of a supplementary code point or a surrogate pair.
        return keySpec.startsWith(PREFIX_HEX, labelEnd + 1);
    }

    @NonNull
    private static String parseEscape(@NonNull final String text) {
        if (text.indexOf(BACKSLASH) < 0) {
            return text;
        }
        final int length = text.length();
        final StringBuilder sb = new StringBuilder();
        for (int pos = 0; pos < length; pos++) {
            final char c = text.charAt(pos);
            if (c == BACKSLASH && pos + 1 < length) {
                // Skip escape char
                pos++;
                sb.append(text.charAt(pos));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static int indexOfLabelEnd(@NonNull final String keySpec) {
        final int length = keySpec.length();
        if (keySpec.indexOf(BACKSLASH) < 0) {
            final int labelEnd = keySpec.indexOf(VERTICAL_BAR);
            if (labelEnd == 0) {
                if (length == 1) {
                    // Treat a sole vertical bar as a special case of key label.
                    return -1;
                }
                throw new KeySpecParserError("Empty label");
            }
            return labelEnd;
        }
        for (int pos = 0; pos < length; pos++) {
            final char c = keySpec.charAt(pos);
            if (c == BACKSLASH && pos + 1 < length) {
                // Skip escape char
                pos++;
            } else if (c == VERTICAL_BAR) {
                return pos;
            }
        }
        return -1;
    }

    @NonNull
    private static String getBeforeLabelEnd(@NonNull final String keySpec, final int labelEnd) {
        return (labelEnd < 0) ? keySpec : keySpec.substring(0, labelEnd);
    }

    @NonNull
    private static String getAfterLabelEnd(@NonNull final String keySpec, final int labelEnd) {
        return keySpec.substring(labelEnd + /* VERTICAL_BAR */1);
    }

    private static void checkDoubleLabelEnd(@NonNull final String keySpec, final int labelEnd) {
        if (indexOfLabelEnd(getAfterLabelEnd(keySpec, labelEnd)) < 0) {
            return;
        }
        throw new KeySpecParserError("Multiple " + VERTICAL_BAR + ": " + keySpec);
    }

    @Nullable
    public static String getLabel(@Nullable final String keySpec) {
        if (keySpec == null) {
            // TODO: Throw {@link KeySpecParserError} once Key.keyLabel attribute becomes mandatory.
            return null;
        }
        if (hasIcon(keySpec)) {
            return null;
        }
        final int labelEnd = indexOfLabelEnd(keySpec);
        final String label = parseEscape(getBeforeLabelEnd(keySpec, labelEnd));
        if (label.isEmpty()) {
            throw new KeySpecParserError("Empty label: " + keySpec);
        }
        return label;
    }

    @Nullable
    private static String getOutputTextInternal(@NonNull final String keySpec, final int labelEnd) {
        if (labelEnd <= 0) {
            return null;
        }
        checkDoubleLabelEnd(keySpec, labelEnd);
        return parseEscape(getAfterLabelEnd(keySpec, labelEnd));
    }

    @Nullable
    public static String getOutputText(@Nullable final String keySpec) {
        if (keySpec == null) {
            // TODO: Throw {@link KeySpecParserError} once Key.keyLabel attribute becomes mandatory.
            return null;
        }
        final int labelEnd = indexOfLabelEnd(keySpec);
        if (hasCode(keySpec, labelEnd)) {
            return null;
        }
        final String outputText = getOutputTextInternal(keySpec, labelEnd);
        if (outputText != null) {
            if (StringUtils.codePointCount(outputText) == 1) {
                // If output text is one code point, it should be treated as a code.
                // See {@link #getCode(Resources, String)}.
                return null;
            }
            if (outputText.isEmpty()) {
                throw new KeySpecParserError("Empty outputText: " + keySpec);
            }
            return outputText;
        }
        final String label = getLabel(keySpec);
        if (label == null) {
            throw new KeySpecParserError("Empty label: " + keySpec);
        }
        // Code is automatically generated for one letter label. See {@link getCode()}.
        return (StringUtils.codePointCount(label) == 1) ? null : label;
    }

    public static int getCode(@Nullable final String keySpec) {
        if (keySpec == null) {
            // TODO: Throw {@link KeySpecParserError} once Key.keyLabel attribute becomes mandatory.
            return CODE_UNSPECIFIED;
        }
        final int labelEnd = indexOfLabelEnd(keySpec);
        if (hasCode(keySpec, labelEnd)) {
            checkDoubleLabelEnd(keySpec, labelEnd);
            return parseCode(getAfterLabelEnd(keySpec, labelEnd), CODE_UNSPECIFIED);
        }
        final String outputText = getOutputTextInternal(keySpec, labelEnd);
        if (outputText != null) {
            // If output text is one code point, it should be treated as a code.
            // See {@link #getOutputText(String)}.
            if (StringUtils.codePointCount(outputText) == 1) {
                return outputText.codePointAt(0);
            }
            return CODE_OUTPUT_TEXT;
        }
        final String label = getLabel(keySpec);
        if (label == null) {
            throw new KeySpecParserError("Empty label: " + keySpec);
        }
        // Code is automatically generated for one letter label.
        return (StringUtils.codePointCount(label) == 1) ? label.codePointAt(0) : CODE_OUTPUT_TEXT;
    }

    public static int parseCode(@Nullable final String text, final int defaultCode) {
        if (text == null) {
            return defaultCode;
        }
        if (text.startsWith(KeyboardCodesSet.PREFIX_CODE)) {
            return KeyboardCodesSet.getCode(text.substring(KeyboardCodesSet.PREFIX_CODE.length()));
        }
        // This is a workaround to have a key that has a supplementary code point. We can't put a
        // string in resource as a XML entity of a supplementary code point or a surrogate pair.
        if (text.startsWith(PREFIX_HEX)) {
            return Integer.parseInt(text.substring(PREFIX_HEX.length()), 16);
        }
        return defaultCode;
    }

    public static int getIconId(@Nullable final String keySpec) {
        if (keySpec == null) {
            // TODO: Throw {@link KeySpecParserError} once Key.keyLabel attribute becomes mandatory.
            return KeyboardIconsSet.ICON_UNDEFINED;
        }
        if (!hasIcon(keySpec)) {
            return KeyboardIconsSet.ICON_UNDEFINED;
        }
        final int labelEnd = indexOfLabelEnd(keySpec);
        final String iconName = getBeforeLabelEnd(keySpec, labelEnd)
                .substring(KeyboardIconsSet.PREFIX_ICON.length());
        return KeyboardIconsSet.getIconId(iconName);
    }

    @SuppressWarnings("serial")
    public static final class KeySpecParserError extends RuntimeException {
        public KeySpecParserError(final String message) {
            super(message);
        }
    }
}
