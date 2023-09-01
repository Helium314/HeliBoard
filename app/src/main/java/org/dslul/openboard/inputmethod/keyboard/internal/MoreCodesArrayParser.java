/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.dslul.openboard.inputmethod.latin.common.StringUtils;

/**
 * The string parser of moreCodesArray specification for <GridRows />. The attribute moreCodesArray is an
 * array of string.
 * The more codes array specification is semicolon separated "codes array specification" each of which represents one
 * "more key".
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
