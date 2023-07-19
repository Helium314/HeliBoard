/*
 * Copyright (C) 2012 The Android Open Source Project
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

package org.dslul.openboard.inputmethod.latin.common;

import android.renderscript.Script;

import org.dslul.openboard.inputmethod.annotations.UsedForTesting;
import org.dslul.openboard.inputmethod.latin.utils.ScriptUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class StringUtils {

    public static final int CAPITALIZE_NONE = 0;  // No caps, or mixed case
    public static final int CAPITALIZE_FIRST = 1; // First only
    public static final int CAPITALIZE_ALL = 2;   // All caps

    @Nonnull
    private static final String EMPTY_STRING = "";

    private static final char CHAR_LINE_FEED = 0X000A;
    private static final char CHAR_VERTICAL_TAB = 0X000B;
    private static final char CHAR_FORM_FEED = 0X000C;
    private static final char CHAR_CARRIAGE_RETURN = 0X000D;
    private static final char CHAR_NEXT_LINE = 0X0085;
    private static final char CHAR_LINE_SEPARATOR = 0X2028;
    private static final char CHAR_PARAGRAPH_SEPARATOR = 0X2029;

    private StringUtils() {
        // This utility class is not publicly instantiable.
    }

    // Taken from android.text.TextUtils. We are extensively using this method in many places,
    // some of which don't have the android libraries available.

    /**
     * Returns true if the string is null or 0-length.
     *
     * @param str the string to be examined
     * @return true if str is null or zero length
     */
    public static boolean isEmpty(@Nullable final CharSequence str) {
        return (str == null || str.length() == 0);
    }

    // Taken from android.text.TextUtils to cut the dependency to the Android framework.

    /**
     * Returns a string containing the tokens joined by delimiters.
     *
     * @param delimiter the delimiter
     * @param tokens    an array objects to be joined. Strings will be formed from
     *                  the objects by calling object.toString().
     */
    @Nonnull
    public static String join(@Nonnull final CharSequence delimiter,
                              @Nonnull final Iterable<?> tokens) {
        final StringBuilder sb = new StringBuilder();
        boolean firstTime = true;
        for (final Object token : tokens) {
            if (firstTime) {
                firstTime = false;
            } else {
                sb.append(delimiter);
            }
            sb.append(token);
        }
        return sb.toString();
    }

    // Taken from android.text.TextUtils to cut the dependency to the Android framework.

    /**
     * Returns true if a and b are equal, including if they are both null.
     * <p><i>Note: In platform versions 1.1 and earlier, this method only worked well if
     * both the arguments were instances of String.</i></p>
     *
     * @param a first CharSequence to check
     * @param b second CharSequence to check
     * @return true if a and b are equal
     */
    public static boolean equals(@Nullable final CharSequence a, @Nullable final CharSequence b) {
        if (a == b) {
            return true;
        }
        final int length;
        if (a != null && b != null && (length = a.length()) == b.length()) {
            if (a instanceof String && b instanceof String) {
                return a.equals(b);
            }
            for (int i = 0; i < length; i++) {
                if (a.charAt(i) != b.charAt(i)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public static int codePointCount(@Nullable final CharSequence text) {
        if (isEmpty(text)) {
            return 0;
        }
        return Character.codePointCount(text, 0, text.length());
    }

    @Nonnull
    public static String newSingleCodePointString(final int codePoint) {
        if (Character.charCount(codePoint) == 1) {
            // Optimization: avoid creating a temporary array for characters that are
            // represented by a single char value
            return String.valueOf((char) codePoint);
        }
        // For surrogate pair
        return new String(Character.toChars(codePoint));
    }

    public static boolean containsInArray(@Nonnull final String text,
                                          @Nonnull final String[] array) {
        for (final String element : array) {
            if (text.equals(element)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Comma-Splittable Text is similar to Comma-Separated Values (CSV) but has much simpler syntax.
     * Unlike CSV, Comma-Splittable Text has no escaping mechanism, so that the text can't contain
     * a comma character in it.
     */
    @Nonnull
    private static final String SEPARATOR_FOR_COMMA_SPLITTABLE_TEXT = ",";

    public static boolean containsInCommaSplittableText(@Nonnull final String text,
                                                        @Nullable final String extraValues) {
        if (isEmpty(extraValues)) {
            return false;
        }
        return containsInArray(text, extraValues.split(SEPARATOR_FOR_COMMA_SPLITTABLE_TEXT));
    }

    @Nonnull
    public static String removeFromCommaSplittableTextIfExists(@Nonnull final String text,
                                                               @Nullable final String extraValues) {
        if (isEmpty(extraValues)) {
            return EMPTY_STRING;
        }
        final String[] elements = extraValues.split(SEPARATOR_FOR_COMMA_SPLITTABLE_TEXT);
        if (!containsInArray(text, elements)) {
            return extraValues;
        }
        final ArrayList<String> result = new ArrayList<>(elements.length - 1);
        for (final String element : elements) {
            if (!text.equals(element)) {
                result.add(element);
            }
        }
        return join(SEPARATOR_FOR_COMMA_SPLITTABLE_TEXT, result);
    }

    /**
     * Remove duplicates from an array of strings.
     * <p>
     * This method will always keep the first occurrence of all strings at their position
     * in the array, removing the subsequent ones.
     */
    public static void removeDupes(@Nonnull final ArrayList<String> suggestions) {
        if (suggestions.size() < 2) {
            return;
        }
        int i = 1;
        // Don't cache suggestions.size(), since we may be removing items
        while (i < suggestions.size()) {
            final String cur = suggestions.get(i);
            // Compare each suggestion with each previous suggestion
            for (int j = 0; j < i; j++) {
                final String previous = suggestions.get(j);
                if (equals(cur, previous)) {
                    suggestions.remove(i);
                    i--;
                    break;
                }
            }
            i++;
        }
    }

    @Nonnull
    public static String capitalizeFirstCodePoint(@Nonnull final String s,
                                                  @Nonnull final Locale locale) {
        if (s.length() <= 1) {
            return s.toUpperCase(getLocaleUsedForToTitleCase(locale));
        }
        // Please refer to the comment below in
        // {@link #capitalizeFirstAndDowncaseRest(String,Locale)} as this has the same shortcomings
        final int cutoff = s.offsetByCodePoints(0, 1);
        return s.substring(0, cutoff).toUpperCase(getLocaleUsedForToTitleCase(locale))
                + s.substring(cutoff);
    }

    @Nonnull
    public static String capitalizeFirstAndDowncaseRest(@Nonnull final String s,
                                                        @Nonnull final Locale locale) {
        if (s.length() <= 1) {
            return s.toUpperCase(getLocaleUsedForToTitleCase(locale));
        }
        // TODO: fix the bugs below
        // - It does not work for Serbian, because it fails to account for the "lj" character,
        // which should be "Lj" in title case and "LJ" in upper case.
        // - It does not work for Dutch, because it fails to account for the "ij" digraph when it's
        // written as two separate code points. They are two different characters but both should
        // be capitalized as "IJ" as if they were a single letter in most words (not all). If the
        // unicode char for the ligature is used however, it works.
        final int cutoff = s.offsetByCodePoints(0, 1);
        return s.substring(0, cutoff).toUpperCase(getLocaleUsedForToTitleCase(locale))
                + s.substring(cutoff).toLowerCase(locale);
    }

    @Nonnull
    public static int[] toCodePointArray(@Nonnull final CharSequence charSequence) {
        return toCodePointArray(charSequence, 0, charSequence.length());
    }

    @Nonnull
    private static final int[] EMPTY_CODEPOINTS = {};

    /**
     * Converts a range of a string to an array of code points.
     *
     * @param charSequence the source string.
     * @param startIndex   the start index inside the string in java chars, inclusive.
     * @param endIndex     the end index inside the string in java chars, exclusive.
     * @return a new array of code points. At most endIndex - startIndex, but possibly less.
     */
    @Nonnull
    public static int[] toCodePointArray(@Nonnull final CharSequence charSequence,
                                         final int startIndex, final int endIndex) {
        final int length = charSequence.length();
        if (length <= 0) {
            return EMPTY_CODEPOINTS;
        }
        final int[] codePoints =
                new int[Character.codePointCount(charSequence, startIndex, endIndex)];
        copyCodePointsAndReturnCodePointCount(codePoints, charSequence, startIndex, endIndex,
                false /* downCase */);
        return codePoints;
    }

    /**
     * Copies the codepoints in a CharSequence to an int array.
     * <p>
     * This method assumes there is enough space in the array to store the code points. The size
     * can be measured with Character#codePointCount(CharSequence, int, int) before passing to this
     * method. If the int array is too small, an ArrayIndexOutOfBoundsException will be thrown.
     * Also, this method makes no effort to be thread-safe. Do not modify the CharSequence while
     * this method is running, or the behavior is undefined.
     * This method can optionally downcase code points before copying them, but it pays no attention
     * to locale while doing so.
     *
     * @param destination  the int array.
     * @param charSequence the CharSequence.
     * @param startIndex   the start index inside the string in java chars, inclusive.
     * @param endIndex     the end index inside the string in java chars, exclusive.
     * @param downCase     if this is true, code points will be downcased before being copied.
     * @return the number of copied code points.
     */
    public static int copyCodePointsAndReturnCodePointCount(@Nonnull final int[] destination,
                                                            @Nonnull final CharSequence charSequence, final int startIndex, final int endIndex,
                                                            final boolean downCase) {
        int destIndex = 0;
        for (int index = startIndex; index < endIndex;
             index = Character.offsetByCodePoints(charSequence, index, 1)) {
            final int codePoint = Character.codePointAt(charSequence, index);
            // TODO: stop using this, as it's not aware of the locale and does not always do
            // the right thing.
            destination[destIndex] = downCase ? Character.toLowerCase(codePoint) : codePoint;
            destIndex++;
        }
        return destIndex;
    }

    @Nonnull
    public static int[] toSortedCodePointArray(@Nonnull final String string) {
        final int[] codePoints = toCodePointArray(string);
        Arrays.sort(codePoints);
        return codePoints;
    }

    /**
     * Construct a String from a code point array
     *
     * @param codePoints a code point array that is null terminated when its logical length is
     *                   shorter than the array length.
     * @return a string constructed from the code point array.
     */
    @Nonnull
    public static String getStringFromNullTerminatedCodePointArray(
            @Nonnull final int[] codePoints) {
        int stringLength = codePoints.length;
        for (int i = 0; i < codePoints.length; i++) {
            if (codePoints[i] == 0) {
                stringLength = i;
                break;
            }
        }
        return new String(codePoints, 0 /* offset */, stringLength);
    }

    // This method assumes the text is not null. For the empty string, it returns CAPITALIZE_NONE.
    public static int getCapitalizationType(@Nonnull final String text) {
        // If the first char is not uppercase, then the word is either all lower case or
        // camel case, and in either case we return CAPITALIZE_NONE.
        final int len = text.length();
        int index = 0;
        for (; index < len; index = text.offsetByCodePoints(index, 1)) {
            if (Character.isLetter(text.codePointAt(index))) {
                break;
            }
        }
        if (index == len) return CAPITALIZE_NONE;
        if (!Character.isUpperCase(text.codePointAt(index))) {
            return CAPITALIZE_NONE;
        }
        int capsCount = 1;
        int letterCount = 1;
        for (index = text.offsetByCodePoints(index, 1); index < len;
             index = text.offsetByCodePoints(index, 1)) {
            if (1 != capsCount && letterCount != capsCount) break;
            final int codePoint = text.codePointAt(index);
            if (Character.isUpperCase(codePoint)) {
                ++capsCount;
                ++letterCount;
            } else if (Character.isLetter(codePoint)) {
                // We need to discount non-letters since they may not be upper-case, but may
                // still be part of a word (e.g. single quote or dash, as in "IT'S" or "FULL-TIME")
                ++letterCount;
            }
        }
        // We know the first char is upper case. So we want to test if either every letter other
        // than the first is lower case, or if they are all upper case. If the string is exactly
        // one char long, then we will arrive here with letterCount 1, and this is correct, too.
        if (1 == capsCount) return CAPITALIZE_FIRST;
        return (letterCount == capsCount ? CAPITALIZE_ALL : CAPITALIZE_NONE);
    }

    public static boolean isIdenticalAfterUpcase(@Nonnull final String text) {
        final int length = text.length();
        int i = 0;
        while (i < length) {
            final int codePoint = text.codePointAt(i);
            if (Character.isLetter(codePoint) && !Character.isUpperCase(codePoint)) {
                return false;
            }
            i += Character.charCount(codePoint);
        }
        return true;
    }

    public static boolean isIdenticalAfterDowncase(@Nonnull final String text) {
        final int length = text.length();
        int i = 0;
        while (i < length) {
            final int codePoint = text.codePointAt(i);
            if (Character.isLetter(codePoint) && !Character.isLowerCase(codePoint)) {
                return false;
            }
            i += Character.charCount(codePoint);
        }
        return true;
    }

    public static boolean isIdenticalAfterCapitalizeEachWord(@Nonnull final String text,
                                                             @Nonnull final int[] sortedSeparators) {
        boolean needsCapsNext = true;
        final int len = text.length();
        for (int i = 0; i < len; i = text.offsetByCodePoints(i, 1)) {
            final int codePoint = text.codePointAt(i);
            if (Character.isLetter(codePoint)) {
                if ((needsCapsNext && !Character.isUpperCase(codePoint))
                        || (!needsCapsNext && !Character.isLowerCase(codePoint))) {
                    return false;
                }
            }
            // We need a capital letter next if this is a separator.
            needsCapsNext = (Arrays.binarySearch(sortedSeparators, codePoint) >= 0);
        }
        return true;
    }

    // TODO: like capitalizeFirst*, this does not work perfectly for Dutch because of the IJ digraph
    // which should be capitalized together in *some* cases.
    @Nonnull
    public static String capitalizeEachWord(@Nonnull final String text,
                                            @Nonnull final int[] sortedSeparators, @Nonnull final Locale locale) {
        final StringBuilder builder = new StringBuilder();
        boolean needsCapsNext = true;
        final int len = text.length();
        for (int i = 0; i < len; i = text.offsetByCodePoints(i, 1)) {
            final String nextChar = text.substring(i, text.offsetByCodePoints(i, 1));
            if (needsCapsNext) {
                builder.append(nextChar.toUpperCase(locale));
            } else {
                builder.append(nextChar.toLowerCase(locale));
            }
            // We need a capital letter next if this is a separator.
            needsCapsNext = (Arrays.binarySearch(sortedSeparators, nextChar.codePointAt(0)) >= 0);
        }
        return builder.toString();
    }

    /**
     * Approximates whether the text before the cursor looks like a URL.
     * <p>
     * This is not foolproof, but it should work well in the practice.
     * Essentially it walks backward from the cursor until it finds something that's not a letter,
     * digit, or common URL symbol like underscore. If it hasn't found a period yet, then it
     * does not look like a URL.
     * If the text:
     * - starts with www and contains a period
     * - starts with a slash preceded by either a slash, whitespace, or start-of-string
     * Then it looks like a URL and we return true. Otherwise, we return false.
     * <p>
     * Note: this method is called quite often, and should be fast.
     * <p>
     * TODO: This will return that "abc./def" and ".abc/def" look like URLs to keep down the
     * code complexity, but ideally it should not. It's acceptable for now.
     */
    public static boolean lastPartLooksLikeURL(@Nonnull final CharSequence text) {
        int i = text.length();
        if (0 == i) {
            return false;
        }
        int wCount = 0;
        int slashCount = 0;
        boolean hasSlash = false;
        boolean hasPeriod = false;
        int codePoint = 0;
        while (i > 0) {
            codePoint = Character.codePointBefore(text, i);
            if (codePoint < Constants.CODE_PERIOD || codePoint > 'z') {
                // Handwavy heuristic to see if that's a URL character. Anything between period
                // and z. This includes all lower- and upper-case ascii letters, period,
                // underscore, arrobase, question mark, equal sign. It excludes spaces, exclamation
                // marks, double quotes...
                // Anything that's not a URL-like character causes us to break from here and
                // evaluate normally.
                break;
            }
            if (Constants.CODE_PERIOD == codePoint) {
                hasPeriod = true;
            }
            if (Constants.CODE_SLASH == codePoint) {
                hasSlash = true;
                if (2 == ++slashCount) {
                    return true;
                }
            } else {
                slashCount = 0;
            }
            if ('w' == codePoint) {
                ++wCount;
            } else {
                wCount = 0;
            }
            i = Character.offsetByCodePoints(text, i, -1);
        }
        // End of the text run.
        // If it starts with www and includes a period, then it looks like a URL.
        if (wCount >= 3 && hasPeriod) {
            return true;
        }
        // If it starts with a slash, and the code point before is whitespace, it looks like an URL.
        if (1 == slashCount && (0 == i || Character.isWhitespace(codePoint))) {
            return true;
        }
        // If it has both a period and a slash, it looks like an URL.
        return hasPeriod && hasSlash;
        // Otherwise, it doesn't look like an URL.
    }

    /**
     * Examines the string and returns whether we're inside a double quote.
     * <p>
     * This is used to decide whether we should put an automatic space before or after a double
     * quote character. If we're inside a quotation, then we want to close it, so we want a space
     * after and not before. Otherwise, we want to open the quotation, so we want a space before
     * and not after. Exception: after a digit, we never want a space because the "inch" or
     * "minutes" use cases is dominant after digits.
     * In the practice, we determine whether we are in a quotation or not by finding the previous
     * double quote character, and looking at whether it's followed by whitespace. If so, that
     * was a closing quotation mark, so we're not inside a double quote. If it's not followed
     * by whitespace, then it was an opening quotation mark, and we're inside a quotation.
     *
     * @param text the text to examine.
     * @return whether we're inside a double quote.
     */
    public static boolean isInsideDoubleQuoteOrAfterDigit(@Nonnull final CharSequence text) {
        int i = text.length();
        if (0 == i) {
            return false;
        }
        int codePoint = Character.codePointBefore(text, i);
        if (Character.isDigit(codePoint)) {
            return true;
        }
        int prevCodePoint = 0;
        while (i > 0) {
            codePoint = Character.codePointBefore(text, i);
            if (Constants.CODE_DOUBLE_QUOTE == codePoint) {
                // If we see a double quote followed by whitespace, then that
                // was a closing quote.
                if (Character.isWhitespace(prevCodePoint)) {
                    return false;
                }
            }
            if (Character.isWhitespace(codePoint) && Constants.CODE_DOUBLE_QUOTE == prevCodePoint) {
                // If we see a double quote preceded by whitespace, then that
                // was an opening quote. No need to continue seeking.
                return true;
            }
            i -= Character.charCount(codePoint);
            prevCodePoint = codePoint;
        }
        // We reached the start of text. If the first char is a double quote, then we're inside
        // a double quote. Otherwise we're not.
        return Constants.CODE_DOUBLE_QUOTE == codePoint;
    }

    public static boolean isEmptyStringOrWhiteSpaces(@Nonnull final String s) {
        final int N = codePointCount(s);
        for (int i = 0; i < N; ++i) {
            if (!Character.isWhitespace(s.codePointAt(i))) {
                return false;
            }
        }
        return true;
    }

    @UsedForTesting
    @Nonnull
    public static String byteArrayToHexString(@Nullable final byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return EMPTY_STRING;
        }
        final StringBuilder sb = new StringBuilder();
        for (final byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    /**
     * Convert hex string to byte array. The string length must be an even number.
     */
    @UsedForTesting
    @Nullable
    public static byte[] hexStringToByteArray(@Nullable final String hexString) {
        if (isEmpty(hexString)) {
            return null;
        }
        final int N = hexString.length();
        if (N % 2 != 0) {
            throw new NumberFormatException("Input hex string length must be an even number."
                    + " Length = " + N);
        }
        final byte[] bytes = new byte[N / 2];
        for (int i = 0; i < N; i += 2) {
            bytes[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                    + Character.digit(hexString.charAt(i + 1), 16));
        }
        return bytes;
    }

    private static final String LANGUAGE_GREEK = "el";

    @Nonnull
    private static Locale getLocaleUsedForToTitleCase(@Nonnull final Locale locale) {
        // In Greek locale {@link String#toUpperCase(Locale)} eliminates accents from its result.
        // In order to get accented upper case letter, {@link Locale#ROOT} should be used.
        if (LANGUAGE_GREEK.equals(locale.getLanguage())) {
            return Locale.ROOT;
        }
        return locale;
    }

    @Nullable
    public static String toTitleCaseOfKeyLabel(@Nullable final String label,
                                               @Nonnull final Locale locale) {
        if (label == null || !ScriptUtils.scriptSupportsUppercase(locale.getLanguage())) {
            return label;
        }

        return label.toUpperCase(getLocaleUsedForToTitleCase(locale));
    }

    public static int toTitleCaseOfKeyCode(final int code, @Nonnull final Locale locale) {
        if (!Constants.isLetterCode(code)) {
            return code;
        }
        final String label = newSingleCodePointString(code);
        final String titleCaseLabel = toTitleCaseOfKeyLabel(label, locale);
        return codePointCount(titleCaseLabel) == 1
                ? titleCaseLabel.codePointAt(0) : Constants.CODE_UNSPECIFIED;
    }

    public static int getTrailingSingleQuotesCount(@Nonnull final CharSequence charSequence) {
        final int lastIndex = charSequence.length() - 1;
        int i = lastIndex;
        while (i >= 0 && charSequence.charAt(i) == Constants.CODE_SINGLE_QUOTE) {
            --i;
        }
        return lastIndex - i;
    }

    @UsedForTesting
    public static class Stringizer<E> {
        @Nonnull
        private static final String[] EMPTY_STRING_ARRAY = new String[0];

        @UsedForTesting
        @Nonnull
        public String stringize(@Nullable final E element) {
            if (element == null) {
                return "null";
            }
            return element.toString();
        }

        @UsedForTesting
        @Nonnull
        public final String join(@Nullable final E[] array) {
            return joinStringArray(toStringArray(array), null /* delimiter */);
        }

        @UsedForTesting
        public final String join(@Nullable final E[] array, @Nullable final String delimiter) {
            return joinStringArray(toStringArray(array), delimiter);
        }

        @Nonnull
        protected String[] toStringArray(@Nullable final E[] array) {
            if (array == null) {
                return EMPTY_STRING_ARRAY;
            }
            final String[] stringArray = new String[array.length];
            for (int index = 0; index < array.length; index++) {
                stringArray[index] = stringize(array[index]);
            }
            return stringArray;
        }

        @Nonnull
        protected String joinStringArray(@Nonnull final String[] stringArray,
                                         @Nullable final String delimiter) {
            if (delimiter == null) {
                return Arrays.toString(stringArray);
            }
            final StringBuilder sb = new StringBuilder();
            for (int index = 0; index < stringArray.length; index++) {
                sb.append(index == 0 ? "[" : delimiter);
                sb.append(stringArray[index]);
            }
            return sb + "]";
        }
    }

    /**
     * Returns whether the last composed word contains line-breaking character (e.g. CR or LF).
     *
     * @param text the text to be examined.
     * @return {@code true} if the last composed word contains line-breaking separator.
     */
    public static boolean hasLineBreakCharacter(@Nullable final String text) {
        if (isEmpty(text)) {
            return false;
        }
        for (int i = text.length() - 1; i >= 0; --i) {
            final char c = text.charAt(i);
            switch (c) {
                case CHAR_LINE_FEED:
                case CHAR_VERTICAL_TAB:
                case CHAR_FORM_FEED:
                case CHAR_CARRIAGE_RETURN:
                case CHAR_NEXT_LINE:
                case CHAR_LINE_SEPARATOR:
                case CHAR_PARAGRAPH_SEPARATOR:
                    return true;
            }
        }
        return false;
    }

    public static boolean probablyContainsEmoji(String s) {
        int offset = 0;
        int length = s.length();
        while (offset < length) {
            int c = Character.codePointAt(s, offset);
            if (probablyIsEmojiCodePoint(c))
                return true;
            offset += Character.charCount(c);
        }
        return false;
    }

    // seemingly arbitrary ranges taken from "somewhere on the internet"
    public static boolean probablyIsEmojiCodePoint(int c) {
        return (0x200D <= c && c <= 0x3299) // ??
                || (0x1F004 <= c && c <= 0x1F251) // ??
                || (0x1F300 <= c && c <= 0x1FFFF) // ??
                || c == 0xFE0F; // variation selector emoji with color
    }
}
