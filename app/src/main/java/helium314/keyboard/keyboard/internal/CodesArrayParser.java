/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal;

import android.text.TextUtils;

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode;
import helium314.keyboard.latin.common.Constants;
import helium314.keyboard.latin.common.StringUtils;

/**
 * The string parser of codesArray specification for <GridRows />. The attribute codesArray is an
 * array of string.
 * Each element of the array defines a key label by specifying a code point as a hexadecimal string.
 * A key label may consist of multiple code points separated by comma.
 * Each element of the array optionally can have an output text definition after vertical bar
 * marker. An output text may consist of multiple code points separated by comma.
 * The format of the codesArray element should be:
 * <pre>
 *   label1[,label2]*(|outputText1[,outputText2]*(|minSupportSdkVersion)?)?
 * </pre>
 */
// TODO: Write unit tests for this class.
public final class CodesArrayParser {
    // Constants for parsing.
    private static final char COMMA = Constants.CODE_COMMA;
    private static final String COMMA_REGEX = StringUtils.newSingleCodePointString(COMMA);
    private static final String VERTICAL_BAR_REGEX = // "\\|"
            new String(new char[] { Constants.CODE_BACKSLASH, Constants.CODE_VERTICAL_BAR });
    private static final int BASE_HEX = 16;

    private CodesArrayParser() {
     // This utility class is not publicly instantiable.
    }

    private static String getLabelSpec(final String codesArraySpec) {
        final String[] strs = codesArraySpec.split(VERTICAL_BAR_REGEX, -1);
        if (strs.length <= 1) {
            return codesArraySpec;
        }
        return strs[0];
    }

    public static String parseLabel(final String codesArraySpec) {
        final String labelSpec = getLabelSpec(codesArraySpec);
        final StringBuilder sb = new StringBuilder();
        for (final String codeInHex : labelSpec.split(COMMA_REGEX)) {
            final int codePoint = Integer.parseInt(codeInHex, BASE_HEX);
            sb.appendCodePoint(codePoint);
        }
        return sb.toString();
    }

    private static String getCodeSpec(final String codesArraySpec) {
        final String[] strs = codesArraySpec.split(VERTICAL_BAR_REGEX, -1);
        if (strs.length <= 1) {
            return codesArraySpec;
        }
        return TextUtils.isEmpty(strs[1]) ? strs[0] : strs[1];
    }

    public static int getMinSupportSdkVersion(final String codesArraySpec) {
        final String[] strs = codesArraySpec.split(VERTICAL_BAR_REGEX, -1);
        if (strs.length <= 2) {
            return 0;
        }
        try {
            return Integer.parseInt(strs[2]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static int parseCode(final String codesArraySpec) {
        final String codeSpec = getCodeSpec(codesArraySpec);
        if (codeSpec.indexOf(COMMA) < 0) {
            return Integer.parseInt(codeSpec, BASE_HEX);
        }
        return KeyCode.MULTIPLE_CODE_POINTS;
    }

    public static String parseOutputText(final String codesArraySpec) {
        final String codeSpec = getCodeSpec(codesArraySpec);
        if (codeSpec.indexOf(COMMA) < 0) {
            return null;
        }
        final StringBuilder sb = new StringBuilder();
        for (final String codeInHex : codeSpec.split(COMMA_REGEX)) {
            final int codePoint = Integer.parseInt(codeInHex, BASE_HEX);
            sb.appendCodePoint(codePoint);
        }
        return sb.toString();
    }
}
