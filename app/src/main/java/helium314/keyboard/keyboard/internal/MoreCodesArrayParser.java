/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal;

import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import helium314.keyboard.latin.common.StringUtils;

/**
 * The string parser of moreCodesArray specification for <GridRows />. The attribute moreCodesArray is an
 * array of string.
 * The more codes array specification is semicolon separated "codes array specification" each of which represents one
 * "popup key".
 * Each element of the array defines a sequence of key labels specified as hexadecimal strings
 * representing code points separated by a vertical bar.
 *
 */
public final class MoreCodesArrayParser {
    // Constants for parsing.
    private static final char SEMICOLON = ';';
    private static final String SEMICOLON_REGEX = StringUtils.newSingleCodePointString(SEMICOLON);

    private MoreCodesArrayParser() {
     // This utility class is not publicly instantiable.
    }

    public static String parseKeySpecs(@Nullable String codeArraySpecs) {
        if (codeArraySpecs == null || TextUtils.isEmpty(codeArraySpecs)) {
            return null;
        }

        final StringBuilder sb = new StringBuilder();
        for (final String codeArraySpec : codeArraySpecs.split(SEMICOLON_REGEX)) {
            final int supportedMinSdkVersion = CodesArrayParser.getMinSupportSdkVersion(codeArraySpec);
            if (Build.VERSION.SDK_INT < supportedMinSdkVersion) {
                continue;
            }
            final String label = CodesArrayParser.parseLabel(codeArraySpec);
            final String outputText = CodesArrayParser.parseOutputText(codeArraySpec);

            sb.append(label).append("|").append(outputText);
            sb.append(",");
        }

        // Remove last comma
        if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);

        return sb.length() > 0 ? sb.toString() : null;
    }
}
