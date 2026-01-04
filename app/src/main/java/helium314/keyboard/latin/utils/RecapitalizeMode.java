package helium314.keyboard.latin.utils;

import java.util.Locale;

import helium314.keyboard.latin.common.StringUtils;

public enum RecapitalizeMode {
    ;
    public static final int NULL = -1;
    public static final int ORIGINAL_MIXED_CASE = 0;
    public static final int ALL_LOWER = 1;
    public static final int FIRST_WORD_UPPER = 2;
    public static final int ALL_UPPER = 3;
    // When adding a new mode, don't forget to update the ROTATION_SIZE constant.
    private static final int ROTATION_SIZE = 4;

    public static int of(final String string, final int[] sortedSeparators) {
        if (StringUtils.isIdenticalAfterUpcase(string)) {
            return ALL_UPPER;
        } else if (StringUtils.isIdenticalAfterDowncase(string)) {
            return ALL_LOWER;
        } else if (StringUtils.isIdenticalAfterCapitalizeEachWord(string, sortedSeparators)) {
            return FIRST_WORD_UPPER;
        } else {
            return ORIGINAL_MIXED_CASE;
        }
    }

    public static int count() {
        return ROTATION_SIZE;
    }

    public static int rotate(int recapitalizeMode, boolean skipOriginalMixedCaseMode) {
        ++recapitalizeMode;
        if (recapitalizeMode == ROTATION_SIZE) {
            recapitalizeMode = skipOriginalMixedCaseMode ? 1 : 0;
        }
        return recapitalizeMode;
    }

    public static String apply(int recapitalizeMode, String text, int[] sortedSeparators, Locale locale) {
        return switch (recapitalizeMode) {
            case RecapitalizeMode.ORIGINAL_MIXED_CASE -> text;
            case RecapitalizeMode.ALL_LOWER -> text.toLowerCase(locale);
            case RecapitalizeMode.FIRST_WORD_UPPER -> StringUtils.capitalizeEachWord(text, sortedSeparators, locale);
            case RecapitalizeMode.ALL_UPPER -> text.toUpperCase(locale);
            default -> throw new IllegalArgumentException();
        };
    }

    public static String toString(int recapitalizeMode) {
        return switch (recapitalizeMode) {
            case NULL -> "undefined";
            case ORIGINAL_MIXED_CASE -> "mixedCase";
            case ALL_LOWER -> "allLower";
            case FIRST_WORD_UPPER -> "firstWordUpper";
            case ALL_UPPER -> "allUpper";
            default -> throw new IllegalArgumentException();
        };
    }
}
