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

package org.dslul.openboard.inputmethod.latin.utils;


import androidx.collection.ArraySet;

import java.util.Locale;
import java.util.TreeMap;

/**
 * A class to help with handling different writing scripts.
 */
public class ScriptUtils {

    // Used for hardware keyboards
    public static final int SCRIPT_UNKNOWN = -1;

    public static final int SCRIPT_ARABIC = 0;
    public static final int SCRIPT_ARMENIAN = 1;
    public static final int SCRIPT_BENGALI = 2;
    public static final int SCRIPT_CYRILLIC = 3;
    public static final int SCRIPT_DEVANAGARI = 4;
    public static final int SCRIPT_GEORGIAN = 5;
    public static final int SCRIPT_GREEK = 6;
    public static final int SCRIPT_HEBREW = 7;
    public static final int SCRIPT_KANNADA = 8;
    public static final int SCRIPT_KHMER = 9;
    public static final int SCRIPT_LAO = 10;
    public static final int SCRIPT_LATIN = 11;
    public static final int SCRIPT_MALAYALAM = 12;
    public static final int SCRIPT_MYANMAR = 13;
    public static final int SCRIPT_SINHALA = 14;
    public static final int SCRIPT_TAMIL = 15;
    public static final int SCRIPT_TELUGU = 16;
    public static final int SCRIPT_THAI = 17;
    public static final int SCRIPT_BULGARIAN = 18;

    public static final String LANGUAGE_GEORGIAN = "ka";
    public static final String LANGUAGE_BENGALI = "bn";
    public static final String LANGUAGE_HINDI = "hi";
    public static final String LANGUAGE_THAI = "th";
    public static final String LANGUAGE_KHMER = "km";
    public static final String LANGUAGE_LAO = "lo";
    public static final String LANGUAGE_SINHALA = "si";
    public static final String LANGUAGE_NEPALI = "ne";



    private static final TreeMap<String, Integer> mLanguageCodeToScriptCode;
    private final static ArraySet<String> NON_UPPERCASE_SCRIPTS = new ArraySet<>();


    static {
        mLanguageCodeToScriptCode = new TreeMap<>();
        mLanguageCodeToScriptCode.put("", SCRIPT_LATIN); // default
        mLanguageCodeToScriptCode.put("ar", SCRIPT_ARABIC);
        mLanguageCodeToScriptCode.put("hy", SCRIPT_ARMENIAN);
        mLanguageCodeToScriptCode.put("bg", SCRIPT_BULGARIAN);
        mLanguageCodeToScriptCode.put("bn", SCRIPT_BENGALI);
        mLanguageCodeToScriptCode.put("sr", SCRIPT_CYRILLIC);
        mLanguageCodeToScriptCode.put("ru", SCRIPT_CYRILLIC);
        mLanguageCodeToScriptCode.put("ka", SCRIPT_GEORGIAN);
        mLanguageCodeToScriptCode.put("el", SCRIPT_GREEK);
        mLanguageCodeToScriptCode.put("iw", SCRIPT_HEBREW);
        mLanguageCodeToScriptCode.put("km", SCRIPT_KHMER);
        mLanguageCodeToScriptCode.put("lo", SCRIPT_LAO);
        mLanguageCodeToScriptCode.put("ml", SCRIPT_MALAYALAM);
        mLanguageCodeToScriptCode.put("my", SCRIPT_MYANMAR);
        mLanguageCodeToScriptCode.put("si", SCRIPT_SINHALA);
        mLanguageCodeToScriptCode.put("ta", SCRIPT_TAMIL);
        mLanguageCodeToScriptCode.put("te", SCRIPT_TELUGU);
        mLanguageCodeToScriptCode.put("th", SCRIPT_THAI);
        mLanguageCodeToScriptCode.put("uk", SCRIPT_CYRILLIC);

        NON_UPPERCASE_SCRIPTS.add(LANGUAGE_GEORGIAN);
        NON_UPPERCASE_SCRIPTS.add(LANGUAGE_BENGALI);
        NON_UPPERCASE_SCRIPTS.add(LANGUAGE_HINDI);
        NON_UPPERCASE_SCRIPTS.add(LANGUAGE_THAI);
        NON_UPPERCASE_SCRIPTS.add(LANGUAGE_KHMER);
        NON_UPPERCASE_SCRIPTS.add(LANGUAGE_LAO);
        NON_UPPERCASE_SCRIPTS.add(LANGUAGE_SINHALA);
        NON_UPPERCASE_SCRIPTS.add(LANGUAGE_NEPALI);
    }


    public static boolean scriptSupportsUppercase(String language) {
        return !NON_UPPERCASE_SCRIPTS.contains(language);
    }

    /*
     * Returns whether the code point is a letter that makes sense for the specified
     * locale for this spell checker.
     * The dictionaries supported by Latin IME are described in res/xml/spellchecker.xml
     * and is limited to EFIGS languages and Russian.
     * Hence at the moment this explicitly tests for Cyrillic characters or Latin characters
     * as appropriate, and explicitly excludes CJK, Arabic and Hebrew characters.
     */
    public static boolean isLetterPartOfScript(final int codePoint, final int scriptId) {
        switch (scriptId) {
            case SCRIPT_ARABIC:
                // Arabic letters can be in any of the following blocks:
                // Arabic U+0600..U+06FF
                // Arabic Supplement, Thaana U+0750..U+077F, U+0780..U+07BF
                // Arabic Extended-A U+08A0..U+08FF
                // Arabic Presentation Forms-A U+FB50..U+FDFF
                // Arabic Presentation Forms-B U+FE70..U+FEFF
                return (codePoint >= 0x600 && codePoint <= 0x6FF)
                        || (codePoint >= 0x750 && codePoint <= 0x7BF)
                        || (codePoint >= 0x8A0 && codePoint <= 0x8FF)
                        || (codePoint >= 0xFB50 && codePoint <= 0xFDFF)
                        || (codePoint >= 0xFE70 && codePoint <= 0xFEFF);
            case SCRIPT_ARMENIAN:
                // Armenian letters are in the Armenian unicode block, U+0530..U+058F and
                // Alphabetic Presentation Forms block, U+FB00..U+FB4F, but only in the Armenian part
                // of that block, which is U+FB13..U+FB17.
                return (codePoint >= 0x530 && codePoint <= 0x58F
                        || codePoint >= 0xFB13 && codePoint <= 0xFB17);
            case SCRIPT_BENGALI:
                // Bengali unicode block is U+0980..U+09FF
                return (codePoint >= 0x980 && codePoint <= 0x9FF);
            case SCRIPT_BULGARIAN:
            case SCRIPT_CYRILLIC:
                // All Cyrillic characters are in the 400~52F block. There are some in the upper
                // Unicode range, but they are archaic characters that are not used in modern
                // Russian and are not used by our dictionary.
                return codePoint >= 0x400 && codePoint <= 0x52F && Character.isLetter(codePoint);
            case SCRIPT_DEVANAGARI:
                // Devanagari unicode block is +0900..U+097F
                return (codePoint >= 0x900 && codePoint <= 0x97F);
            case SCRIPT_GEORGIAN:
                // Georgian letters are in the Georgian unicode block, U+10A0..U+10FF,
                // or Georgian supplement block, U+2D00..U+2D2F
                return (codePoint >= 0x10A0 && codePoint <= 0x10FF
                        || codePoint >= 0x2D00 && codePoint <= 0x2D2F);
            case SCRIPT_GREEK:
                // Greek letters are either in the 370~3FF range (Greek & Coptic), or in the
                // 1F00~1FFF range (Greek extended). Our dictionary contains both sort of characters.
                // Our dictionary also contains a few words with 0xF2; it would be best to check
                // if that's correct, but a web search does return results for these words so
                // they are probably okay.
                return (codePoint >= 0x370 && codePoint <= 0x3FF)
                        || (codePoint >= 0x1F00 && codePoint <= 0x1FFF)
                        || codePoint == 0xF2;
            case SCRIPT_HEBREW:
                // Hebrew letters are in the Hebrew unicode block, which spans from U+0590 to U+05FF,
                // or in the Alphabetic Presentation Forms block, U+FB00..U+FB4F, but only in the
                // Hebrew part of that block, which is U+FB1D..U+FB4F.
                return (codePoint >= 0x590 && codePoint <= 0x5FF
                        || codePoint >= 0xFB1D && codePoint <= 0xFB4F);
            case SCRIPT_KANNADA:
                // Kannada unicode block is U+0C80..U+0CFF
                return (codePoint >= 0xC80 && codePoint <= 0xCFF);
            case SCRIPT_KHMER:
                // Khmer letters are in unicode block U+1780..U+17FF, and the Khmer symbols block
                // is U+19E0..U+19FF
                return (codePoint >= 0x1780 && codePoint <= 0x17FF
                        || codePoint >= 0x19E0 && codePoint <= 0x19FF);
            case SCRIPT_LAO:
                // The Lao block is U+0E80..U+0EFF
                return (codePoint >= 0xE80 && codePoint <= 0xEFF);
            case SCRIPT_LATIN:
                // Our supported latin script dictionaries (EFIGS) at the moment only include
                // characters in the C0, C1, Latin Extended A and B, IPA extensions unicode
                // blocks. As it happens, those are back-to-back in the code range 0x40 to 0x2AF,
                // so the below is a very efficient way to test for it. As for the 0-0x3F, it's
                // excluded from isLetter anyway.
                return codePoint <= 0x2AF && Character.isLetter(codePoint);
            case SCRIPT_MALAYALAM:
                // Malayalam unicode block is U+0D00..U+0D7F
                return (codePoint >= 0xD00 && codePoint <= 0xD7F);
            case SCRIPT_MYANMAR:
                // Myanmar has three unicode blocks :
                // Myanmar U+1000..U+109F
                // Myanmar extended-A U+AA60..U+AA7F
                // Myanmar extended-B U+A9E0..U+A9FF
                return (codePoint >= 0x1000 && codePoint <= 0x109F
                        || codePoint >= 0xAA60 && codePoint <= 0xAA7F
                        || codePoint >= 0xA9E0 && codePoint <= 0xA9FF);
            case SCRIPT_SINHALA:
                // Sinhala unicode block is U+0D80..U+0DFF
                return (codePoint >= 0xD80 && codePoint <= 0xDFF);
            case SCRIPT_TAMIL:
                // Tamil unicode block is U+0B80..U+0BFF
                return (codePoint >= 0xB80 && codePoint <= 0xBFF);
            case SCRIPT_TELUGU:
                // Telugu unicode block is U+0C00..U+0C7F
                return (codePoint >= 0xC00 && codePoint <= 0xC7F);
            case SCRIPT_THAI:
                // Thai unicode block is U+0E00..U+0E7F
                return (codePoint >= 0xE00 && codePoint <= 0xE7F);
            case SCRIPT_UNKNOWN:
                return true;
            default:
                // Should never come here
                throw new RuntimeException("Impossible value of script: " + scriptId);
        }
    }

    /**
     * @param locale spell checker locale
     * @return internal Latin IME script code that maps to a language code
     * {@see http://en.wikipedia.org/wiki/List_of_ISO_639-1_codes}
     */
    public static int getScriptFromSpellCheckerLocale(final Locale locale) {
        // need special treatment of serbian latin, which would get detected as cyrillic
        if (locale.toString().toLowerCase(Locale.ENGLISH).equals("sr_zz"))
            return ScriptUtils.SCRIPT_LATIN;
        String language = locale.getLanguage();
        Integer script = mLanguageCodeToScriptCode.get(language);
        if (script == null) {
            // Default to Latin.
            script = mLanguageCodeToScriptCode.get("");
        }
        return script;
    }
}
