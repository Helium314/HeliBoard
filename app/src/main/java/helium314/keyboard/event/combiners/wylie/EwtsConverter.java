/*******************************************************************************
 * Copyright (C) 2010 Roger Espel Llima
 * Copyright (c) 2011-2017 Buddhist Digital Resource Center (BDRC)
 *
 * If this file is a derivation of another work the license header will appear below;
 * otherwise, this work is licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package helium314.keyboard.event.combiners.wylie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

/**
 * Tibetan EWTS from/to Unicode converter object.
 *
 * @author Roger Espel Llima
 * @author Buddhist Digital Resource Center (BDRC)
 * @version 1.4.0
 */
public class EwtsConverter {

    // various options for Converter conversion
    private boolean check, check_strict, print_warnings, fix_spacing;

    // constant hashes and sets to help with the conversion
    private static HashMap<String, String> m_consonant, m_subjoined, m_vowel, m_final_uni, m_final_class, m_other,
            m_ambiguous_wylie, m_tib_vowel_long, m_tib_caret;
    private static HashMap<Character, String> m_tib_top, m_tib_subjoined, m_tib_vowel, m_tib_final_wylie,
            m_tib_final_class, m_tib_other;
    private static HashMap<String, Integer> m_ambiguous_key;
    private static HashMap<Character, Integer> m_tokens_start;
    private static HashSet<String> m_special, m_suffixes, m_tib_stacks, m_tokens, m_affixedsuff2;
    private static HashMap<String, HashSet<String>> m_superscripts, m_subscripts, m_prefixes, m_suff2;

    private static final String[] base = new String[45];
    private static final String[] repl = new String[45];

    private static final String[] baseL = new String[39];
    private static final String[] replL = new String[39];

    // initialize all the hashes with the correspondences between Converter and
    // Unicode.
    // this gets called from a 'static section' to initialize the hashes the moment
    // the
    // class gets loaded.
    private static final void initHashes() {
        HashSet<String> tmpSet;

        // mappings auto-generated from the Perl code

        // *** Converter to Unicode mappings ***

        // list of wylie consonant => unicode
        m_consonant = new HashMap<String, String>();
        m_consonant.put("k", "\u0f40");
        m_consonant.put("kh", "\u0f41");
        m_consonant.put("g", "\u0f42");
        m_consonant.put("gh", "\u0f42\u0fb7");
        m_consonant.put("g+h", "\u0f42\u0fb7");
        m_consonant.put("ng", "\u0f44");
        m_consonant.put("c", "\u0f45");
        m_consonant.put("ch", "\u0f46");
        m_consonant.put("j", "\u0f47");
        m_consonant.put("ny", "\u0f49");
        m_consonant.put("T", "\u0f4a");
        m_consonant.put("-t", "\u0f4a");
        m_consonant.put("Th", "\u0f4b");
        m_consonant.put("-th", "\u0f4b");
        m_consonant.put("D", "\u0f4c");
        m_consonant.put("-d", "\u0f4c");
        m_consonant.put("Dh", "\u0f4c\u0fb7");
        m_consonant.put("D+h", "\u0f4c\u0fb7");
        m_consonant.put("-dh", "\u0f4c\u0fb7");
        m_consonant.put("-d+h", "\u0f4c\u0fb7");
        m_consonant.put("N", "\u0f4e");
        m_consonant.put("-n", "\u0f4e");
        m_consonant.put("t", "\u0f4f");
        m_consonant.put("th", "\u0f50");
        m_consonant.put("d", "\u0f51");
        m_consonant.put("dh", "\u0f51\u0fb7");
        m_consonant.put("d+h", "\u0f51\u0fb7");
        m_consonant.put("n", "\u0f53");
        m_consonant.put("p", "\u0f54");
        m_consonant.put("ph", "\u0f55");
        m_consonant.put("b", "\u0f56");
        m_consonant.put("bh", "\u0f56\u0fb7");
        m_consonant.put("b+h", "\u0f56\u0fb7");
        m_consonant.put("m", "\u0f58");
        m_consonant.put("ts", "\u0f59");
        m_consonant.put("tsh", "\u0f5a");
        m_consonant.put("dz", "\u0f5b");
        m_consonant.put("dzh", "\u0f5b\u0fb7");
        m_consonant.put("dz+h", "\u0f5b\u0fb7");
        m_consonant.put("w", "\u0f5d");
        m_consonant.put("zh", "\u0f5e");
        m_consonant.put("z", "\u0f5f");
        m_consonant.put("'", "\u0f60");
        m_consonant.put("y", "\u0f61");
        m_consonant.put("r", "\u0f62");
        m_consonant.put("l", "\u0f63");
        m_consonant.put("sh", "\u0f64");
        m_consonant.put("Sh", "\u0f65");
        m_consonant.put("-sh", "\u0f65");
        m_consonant.put("s", "\u0f66");
        m_consonant.put("h", "\u0f67");
        m_consonant.put("W", "\u0f5d");
        m_consonant.put("Y", "\u0f61");
        m_consonant.put("R", "\u0f6a");
        m_consonant.put("f", "\u0f55\u0f39");
        m_consonant.put("v", "\u0f56\u0f39");

        // subjoined letters
        m_subjoined = new HashMap<String, String>();
        m_subjoined.put("k", "\u0f90");
        m_subjoined.put("kh", "\u0f91");
        m_subjoined.put("g", "\u0f92");
        m_subjoined.put("gh", "\u0f92\u0fb7");
        m_subjoined.put("g+h", "\u0f92\u0fb7");
        m_subjoined.put("ng", "\u0f94");
        m_subjoined.put("c", "\u0f95");
        m_subjoined.put("ch", "\u0f96");
        m_subjoined.put("j", "\u0f97");
        m_subjoined.put("ny", "\u0f99");
        m_subjoined.put("T", "\u0f9a");
        m_subjoined.put("-t", "\u0f9a");
        m_subjoined.put("Th", "\u0f9b");
        m_subjoined.put("-th", "\u0f9b");
        m_subjoined.put("D", "\u0f9c");
        m_subjoined.put("-d", "\u0f9c");
        m_subjoined.put("Dh", "\u0f9c\u0fb7");
        m_subjoined.put("D+h", "\u0f9c\u0fb7");
        m_subjoined.put("-dh", "\u0f9c\u0fb7");
        m_subjoined.put("-d+h", "\u0f9c\u0fb7");
        m_subjoined.put("N", "\u0f9e");
        m_subjoined.put("-n", "\u0f9e");
        m_subjoined.put("t", "\u0f9f");
        m_subjoined.put("th", "\u0fa0");
        m_subjoined.put("d", "\u0fa1");
        m_subjoined.put("dh", "\u0fa1\u0fb7");
        m_subjoined.put("d+h", "\u0fa1\u0fb7");
        m_subjoined.put("n", "\u0fa3");
        m_subjoined.put("p", "\u0fa4");
        m_subjoined.put("ph", "\u0fa5");
        m_subjoined.put("b", "\u0fa6");
        m_subjoined.put("bh", "\u0fa6\u0fb7");
        m_subjoined.put("b+h", "\u0fa6\u0fb7");
        m_subjoined.put("m", "\u0fa8");
        m_subjoined.put("ts", "\u0fa9");
        m_subjoined.put("tsh", "\u0faa");
        m_subjoined.put("dz", "\u0fab");
        m_subjoined.put("dzh", "\u0fab\u0fb7");
        m_subjoined.put("dz+h", "\u0fab\u0fb7");
        m_subjoined.put("w", "\u0fad");
        m_subjoined.put("zh", "\u0fae");
        m_subjoined.put("z", "\u0faf");
        m_subjoined.put("'", "\u0fb0");
        m_subjoined.put("y", "\u0fb1");
        m_subjoined.put("r", "\u0fb2");
        m_subjoined.put("l", "\u0fb3");
        m_subjoined.put("sh", "\u0fb4");
        m_subjoined.put("Sh", "\u0fb5");
        m_subjoined.put("-sh", "\u0fb5");
        m_subjoined.put("s", "\u0fb6");
        m_subjoined.put("h", "\u0fb7");
        m_subjoined.put("a", "\u0fb8");
        m_subjoined.put("W", "\u0fba");
        m_subjoined.put("Y", "\u0fbb");
        m_subjoined.put("R", "\u0fbc");

        // vowels
        m_vowel = new HashMap<String, String>();
        m_vowel.put("a", "\u0f68");
        m_vowel.put("A", "\u0f71");
        m_vowel.put("i", "\u0f72");
        m_vowel.put("I", "\u0f71\u0f72");
        m_vowel.put("u", "\u0f74");
        m_vowel.put("U", "\u0f71\u0f74");
        m_vowel.put("e", "\u0f7a");
        m_vowel.put("E", "\u0f71\u0f7a");
        m_vowel.put("ai", "\u0f7b");
        m_vowel.put("o", "\u0f7c");
        m_vowel.put("O", "\u0f71\u0f7c");
        m_vowel.put("au", "\u0f7d");
        m_vowel.put("-i", "\u0f80");
        m_vowel.put("-I", "\u0f71\u0f80");

        // final symbols to unicode
        m_final_uni = new HashMap<String, String>();
        m_final_uni.put("M", "\u0f7e");
        m_final_uni.put("~M`", "\u0f82");
        m_final_uni.put("~M", "\u0f83");
        m_final_uni.put("X", "\u0f37");
        m_final_uni.put("~X", "\u0f35");
        m_final_uni.put("H", "\u0f7f");
        m_final_uni.put("?", "\u0f84");
        m_final_uni.put("^", "\u0f39");
        m_final_uni.put("&", "\u0f85");

        // final symbols organized by class
        m_final_class = new HashMap<String, String>();
        m_final_class.put("M", "M");
        m_final_class.put("~M`", "M");
        m_final_class.put("~M", "M");
        m_final_class.put("X", "X");
        m_final_class.put("~X", "X");
        m_final_class.put("H", "H");
        m_final_class.put("?", "?");
        m_final_class.put("^", "^");
        m_final_class.put("&", "&");

        // other stand-alone symbols
        m_other = new HashMap<String, String>();
        m_other.put("0", "\u0f20");
        m_other.put("1", "\u0f21");
        m_other.put("2", "\u0f22");
        m_other.put("3", "\u0f23");
        m_other.put("4", "\u0f24");
        m_other.put("5", "\u0f25");
        m_other.put("6", "\u0f26");
        m_other.put("7", "\u0f27");
        m_other.put("8", "\u0f28");
        m_other.put("9", "\u0f29");
        m_other.put(" ", "\u0f0b");
        m_other.put("*", "\u0f0c");
        m_other.put("/", "\u0f0d");
        m_other.put("//", "\u0f0e");
        m_other.put(";", "\u0f0f");
        m_other.put("|", "\u0f11");
        m_other.put("!", "\u0f08");
        m_other.put(":", "\u0f14");
        m_other.put("_", " ");
        m_other.put("=", "\u0f34");
        m_other.put("<", "\u0f3a");
        m_other.put(">", "\u0f3b");
        m_other.put("(", "\u0f3c");
        m_other.put(")", "\u0f3d");
        m_other.put("@", "\u0f04");
        m_other.put("#", "\u0f05");
        m_other.put("$", "\u0f06");
        m_other.put("%", "\u0f07");

        // special characters: flag those if they occur out of context
        m_special = new HashSet<String>();
        m_special.add(".");
        m_special.add("+");
        m_special.add("-");
        m_special.add("~");
        m_special.add("^");
        m_special.add("?");
        m_special.add("`");
        m_special.add("]");

        // superscripts: hashmap of superscript => set of letters or stacks below
        m_superscripts = new HashMap<String, HashSet<String>>();
        tmpSet = new HashSet<String>();
        tmpSet.add("k");
        tmpSet.add("g");
        tmpSet.add("ng");
        tmpSet.add("j");
        tmpSet.add("ny");
        tmpSet.add("t");
        tmpSet.add("d");
        tmpSet.add("n");
        tmpSet.add("b");
        tmpSet.add("m");
        tmpSet.add("ts");
        tmpSet.add("dz");
        tmpSet.add("k+y");
        tmpSet.add("g+y");
        tmpSet.add("m+y");
        tmpSet.add("b+w");
        tmpSet.add("ts+w");
        tmpSet.add("g+w");
        m_superscripts.put("r", tmpSet);

        tmpSet = new HashSet<String>();
        tmpSet.add("k");
        tmpSet.add("g");
        tmpSet.add("ng");
        tmpSet.add("c");
        tmpSet.add("j");
        tmpSet.add("t");
        tmpSet.add("d");
        tmpSet.add("p");
        tmpSet.add("b");
        tmpSet.add("h");
        m_superscripts.put("l", tmpSet);

        tmpSet = new HashSet<String>();
        tmpSet.add("k");
        tmpSet.add("g");
        tmpSet.add("ng");
        tmpSet.add("ny");
        tmpSet.add("t");
        tmpSet.add("d");
        tmpSet.add("n");
        tmpSet.add("p");
        tmpSet.add("b");
        tmpSet.add("m");
        tmpSet.add("ts");
        tmpSet.add("k+y");
        tmpSet.add("g+y");
        tmpSet.add("p+y");
        tmpSet.add("b+y");
        tmpSet.add("m+y");
        tmpSet.add("k+r");
        tmpSet.add("g+r");
        tmpSet.add("p+r");
        tmpSet.add("b+r");
        tmpSet.add("m+r");
        tmpSet.add("n+r");
        m_superscripts.put("s", tmpSet);

        // subscripts => set of letters above
        m_subscripts = new HashMap<String, HashSet<String>>();
        tmpSet = new HashSet<String>();
        tmpSet.add("k");
        tmpSet.add("kh");
        tmpSet.add("g");
        tmpSet.add("p");
        tmpSet.add("ph");
        tmpSet.add("b");
        tmpSet.add("m");
        tmpSet.add("r+k");
        tmpSet.add("r+g");
        tmpSet.add("r+m");
        tmpSet.add("s+k");
        tmpSet.add("s+g");
        tmpSet.add("s+p");
        tmpSet.add("s+b");
        tmpSet.add("s+m");
        m_subscripts.put("y", tmpSet);

        tmpSet = new HashSet<String>();
        tmpSet.add("k");
        tmpSet.add("kh");
        tmpSet.add("g");
        tmpSet.add("t");
        tmpSet.add("th");
        tmpSet.add("d");
        tmpSet.add("n");
        tmpSet.add("p");
        tmpSet.add("ph");
        tmpSet.add("b");
        tmpSet.add("m");
        tmpSet.add("sh");
        tmpSet.add("s");
        tmpSet.add("h");
        tmpSet.add("dz");
        tmpSet.add("s+k");
        tmpSet.add("s+g");
        tmpSet.add("s+p");
        tmpSet.add("s+b");
        tmpSet.add("s+m");
        tmpSet.add("s+n");
        m_subscripts.put("r", tmpSet);

        tmpSet = new HashSet<String>();
        tmpSet.add("k");
        tmpSet.add("g");
        tmpSet.add("b");
        tmpSet.add("r");
        tmpSet.add("s");
        tmpSet.add("z");
        m_subscripts.put("l", tmpSet);

        tmpSet = new HashSet<String>();
        tmpSet.add("k");
        tmpSet.add("kh");
        tmpSet.add("g");
        tmpSet.add("c");
        tmpSet.add("ny");
        tmpSet.add("t");
        tmpSet.add("d");
        tmpSet.add("ts");
        tmpSet.add("tsh");
        tmpSet.add("zh");
        tmpSet.add("z");
        tmpSet.add("r");
        tmpSet.add("l");
        tmpSet.add("sh");
        tmpSet.add("s");
        tmpSet.add("h");
        tmpSet.add("g+r");
        tmpSet.add("d+r");
        tmpSet.add("ph+y");
        tmpSet.add("r+g");
        tmpSet.add("r+ts");
        m_subscripts.put("w", tmpSet);

        // prefixes => set of consonants or stacks after
        m_prefixes = new HashMap<String, HashSet<String>>();
        tmpSet = new HashSet<String>();
        tmpSet.add("c");
        tmpSet.add("ny");
        tmpSet.add("t");
        tmpSet.add("d");
        tmpSet.add("n");
        tmpSet.add("ts");
        tmpSet.add("zh");
        tmpSet.add("z");
        tmpSet.add("y");
        tmpSet.add("sh");
        tmpSet.add("s");
        m_prefixes.put("g", tmpSet);

        tmpSet = new HashSet<String>();
        tmpSet.add("k");
        tmpSet.add("g");
        tmpSet.add("ng");
        tmpSet.add("p");
        tmpSet.add("b");
        tmpSet.add("m");
        tmpSet.add("k+y");
        tmpSet.add("g+y");
        tmpSet.add("p+y");
        tmpSet.add("b+y");
        tmpSet.add("m+y");
        tmpSet.add("k+r");
        tmpSet.add("g+r");
        tmpSet.add("p+r");
        tmpSet.add("b+r");
        m_prefixes.put("d", tmpSet);

        tmpSet = new HashSet<String>();
        tmpSet.add("k");
        tmpSet.add("g");
        tmpSet.add("c");
        tmpSet.add("t");
        tmpSet.add("d");
        tmpSet.add("ts");
        tmpSet.add("zh");
        tmpSet.add("z");
        tmpSet.add("sh");
        tmpSet.add("s");
        tmpSet.add("r");
        tmpSet.add("l");
        tmpSet.add("k+y");
        tmpSet.add("g+y");
        tmpSet.add("k+r");
        tmpSet.add("g+r");
        tmpSet.add("r+l");
        tmpSet.add("s+l");
        tmpSet.add("r+k");
        tmpSet.add("r+g");
        tmpSet.add("r+ng");
        tmpSet.add("r+j");
        tmpSet.add("r+ny");
        tmpSet.add("r+t");
        tmpSet.add("r+d");
        tmpSet.add("r+n");
        tmpSet.add("r+ts");
        tmpSet.add("r+dz");
        tmpSet.add("s+k");
        tmpSet.add("s+g");
        tmpSet.add("s+ng");
        tmpSet.add("s+ny");
        tmpSet.add("s+t");
        tmpSet.add("s+d");
        tmpSet.add("s+n");
        tmpSet.add("s+ts");
        tmpSet.add("r+k+y");
        tmpSet.add("r+g+y");
        tmpSet.add("s+k+y");
        tmpSet.add("s+g+y");
        tmpSet.add("s+k+r");
        tmpSet.add("s+g+r");
        tmpSet.add("l+d");
        tmpSet.add("l+t");
        tmpSet.add("k+l");
        tmpSet.add("s+r");
        tmpSet.add("z+l");
        tmpSet.add("s+w");
        m_prefixes.put("b", tmpSet);

        tmpSet = new HashSet<String>();
        tmpSet.add("kh");
        tmpSet.add("g");
        tmpSet.add("ng");
        tmpSet.add("ch");
        tmpSet.add("j");
        tmpSet.add("ny");
        tmpSet.add("th");
        tmpSet.add("d");
        tmpSet.add("n");
        tmpSet.add("tsh");
        tmpSet.add("dz");
        tmpSet.add("kh+y");
        tmpSet.add("g+y");
        tmpSet.add("kh+r");
        tmpSet.add("g+r");
        m_prefixes.put("m", tmpSet);

        tmpSet = new HashSet<String>();
        tmpSet.add("kh");
        tmpSet.add("g");
        tmpSet.add("ch");
        tmpSet.add("j");
        tmpSet.add("th");
        tmpSet.add("d");
        tmpSet.add("ph");
        tmpSet.add("b");
        tmpSet.add("tsh");
        tmpSet.add("dz");
        tmpSet.add("kh+y");
        tmpSet.add("g+y");
        tmpSet.add("ph+y");
        tmpSet.add("b+y");
        tmpSet.add("kh+r");
        tmpSet.add("g+r");
        tmpSet.add("d+r");
        tmpSet.add("ph+r");
        tmpSet.add("b+r");
        m_prefixes.put("'", tmpSet);

        // set of suffix letters
        // also included are some Skt letters b/c they occur often in suffix position in
        // Skt words
        m_suffixes = new HashSet<String>();
        m_suffixes.add("'");
        m_suffixes.add("g");
        m_suffixes.add("ng");
        m_suffixes.add("d");
        m_suffixes.add("n");
        m_suffixes.add("b");
        m_suffixes.add("m");
        m_suffixes.add("r");
        m_suffixes.add("l");
        m_suffixes.add("s");
        m_suffixes.add("N");
        m_suffixes.add("T");
        m_suffixes.add("-n");
        m_suffixes.add("-t");

        // suffix2 => set of letters before
        m_suff2 = new HashMap<String, HashSet<String>>();
        tmpSet = new HashSet<String>();
        tmpSet.add("g");
        tmpSet.add("ng");
        tmpSet.add("b");
        tmpSet.add("m");
        m_suff2.put("s", tmpSet);

        tmpSet = new HashSet<String>();
        tmpSet.add("n");
        tmpSet.add("r");
        tmpSet.add("l");
        m_suff2.put("d", tmpSet);

        m_affixedsuff2 = new HashSet<String>();
        m_affixedsuff2.add("ng");
        m_affixedsuff2.add("m");

        // root letter index for very ambiguous three-stack syllables
        m_ambiguous_key = new HashMap<String, Integer>();
        m_ambiguous_key.put("dgs", 1);
        m_ambiguous_key.put("dms", 1);
        m_ambiguous_key.put("dngs", 0);
        m_ambiguous_key.put("'gs", 1);
        m_ambiguous_key.put("'bs", 1);
        m_ambiguous_key.put("mngs", 0);
        m_ambiguous_key.put("mgs", 0);
        m_ambiguous_key.put("bgs", 0);
        m_ambiguous_key.put("dbs", 1);

        m_ambiguous_wylie = new HashMap<String, String>();
        m_ambiguous_wylie.put("dgs", "dgas");
        m_ambiguous_wylie.put("dngs", "dangs");
        m_ambiguous_wylie.put("dms", "dmas");
        m_ambiguous_wylie.put("'gs", "'gas");
        m_ambiguous_wylie.put("'bs", "'bas");
        m_ambiguous_wylie.put("mngs", "mangs");
        m_ambiguous_wylie.put("mgs", "mags");
        m_ambiguous_wylie.put("bgs", "bags");
        m_ambiguous_wylie.put("dbs", "dbas");

        // *** Unicode to Converter mappings ***

        // top letters
        m_tib_top = new HashMap<Character, String>();
        m_tib_top.put('\u0f40', "k");
        m_tib_top.put('\u0f41', "kh");
        m_tib_top.put('\u0f42', "g");
        m_tib_top.put('\u0f43', "g+h");
        m_tib_top.put('\u0f44', "ng");
        m_tib_top.put('\u0f45', "c");
        m_tib_top.put('\u0f46', "ch");
        m_tib_top.put('\u0f47', "j");
        m_tib_top.put('\u0f49', "ny");
        m_tib_top.put('\u0f4a', "T");
        m_tib_top.put('\u0f4b', "Th");
        m_tib_top.put('\u0f4c', "D");
        m_tib_top.put('\u0f4d', "D+h");
        m_tib_top.put('\u0f4e', "N");
        m_tib_top.put('\u0f4f', "t");
        m_tib_top.put('\u0f50', "th");
        m_tib_top.put('\u0f51', "d");
        m_tib_top.put('\u0f52', "d+h");
        m_tib_top.put('\u0f53', "n");
        m_tib_top.put('\u0f54', "p");
        m_tib_top.put('\u0f55', "ph");
        m_tib_top.put('\u0f56', "b");
        m_tib_top.put('\u0f57', "b+h");
        m_tib_top.put('\u0f58', "m");
        m_tib_top.put('\u0f59', "ts");
        m_tib_top.put('\u0f5a', "tsh");
        m_tib_top.put('\u0f5b', "dz");
        m_tib_top.put('\u0f5c', "dz+h");
        m_tib_top.put('\u0f5d', "w");
        m_tib_top.put('\u0f5e', "zh");
        m_tib_top.put('\u0f5f', "z");
        m_tib_top.put('\u0f60', "'");
        m_tib_top.put('\u0f61', "y");
        m_tib_top.put('\u0f62', "r");
        m_tib_top.put('\u0f63', "l");
        m_tib_top.put('\u0f64', "sh");
        m_tib_top.put('\u0f65', "Sh");
        m_tib_top.put('\u0f66', "s");
        m_tib_top.put('\u0f67', "h");
        m_tib_top.put('\u0f68', "a");
        m_tib_top.put('\u0f69', "k+Sh");
        m_tib_top.put('\u0f6a', "R");

        // subjoined letters
        m_tib_subjoined = new HashMap<Character, String>();
        m_tib_subjoined.put('\u0f90', "k");
        m_tib_subjoined.put('\u0f91', "kh");
        m_tib_subjoined.put('\u0f92', "g");
        m_tib_subjoined.put('\u0f93', "g+h");
        m_tib_subjoined.put('\u0f94', "ng");
        m_tib_subjoined.put('\u0f95', "c");
        m_tib_subjoined.put('\u0f96', "ch");
        m_tib_subjoined.put('\u0f97', "j");
        m_tib_subjoined.put('\u0f99', "ny");
        m_tib_subjoined.put('\u0f9a', "T");
        m_tib_subjoined.put('\u0f9b', "Th");
        m_tib_subjoined.put('\u0f9c', "D");
        m_tib_subjoined.put('\u0f9d', "D+h");
        m_tib_subjoined.put('\u0f9e', "N");
        m_tib_subjoined.put('\u0f9f', "t");
        m_tib_subjoined.put('\u0fa0', "th");
        m_tib_subjoined.put('\u0fa1', "d");
        m_tib_subjoined.put('\u0fa2', "d+h");
        m_tib_subjoined.put('\u0fa3', "n");
        m_tib_subjoined.put('\u0fa4', "p");
        m_tib_subjoined.put('\u0fa5', "ph");
        m_tib_subjoined.put('\u0fa6', "b");
        m_tib_subjoined.put('\u0fa7', "b+h");
        m_tib_subjoined.put('\u0fa8', "m");
        m_tib_subjoined.put('\u0fa9', "ts");
        m_tib_subjoined.put('\u0faa', "tsh");
        m_tib_subjoined.put('\u0fab', "dz");
        m_tib_subjoined.put('\u0fac', "dz+h");
        m_tib_subjoined.put('\u0fad', "w");
        m_tib_subjoined.put('\u0fae', "zh");
        m_tib_subjoined.put('\u0faf', "z");
        m_tib_subjoined.put('\u0fb0', "'");
        m_tib_subjoined.put('\u0fb1', "y");
        m_tib_subjoined.put('\u0fb2', "r");
        m_tib_subjoined.put('\u0fb3', "l");
        m_tib_subjoined.put('\u0fb4', "sh");
        m_tib_subjoined.put('\u0fb5', "Sh");
        m_tib_subjoined.put('\u0fb6', "s");
        m_tib_subjoined.put('\u0fb7', "h");
        m_tib_subjoined.put('\u0fb8', "a");
        m_tib_subjoined.put('\u0fb9', "k+Sh");
        m_tib_subjoined.put('\u0fba', "W");
        m_tib_subjoined.put('\u0fbb', "Y");
        m_tib_subjoined.put('\u0fbc', "R");

        // vowel signs:
        // a-chen is not here because that's a top character, not a vowel sign.
        // pre-composed "I" and "U" are dealt here; other pre-composed Skt vowels are
        // more
        // easily handled by a global replace in toWylie(), b/c they turn into subjoined
        // "r"/"l".

        m_tib_vowel = new HashMap<Character, String>();
        m_tib_vowel.put('\u0f71', "A");
        m_tib_vowel.put('\u0f72', "i");
        m_tib_vowel.put('\u0f73', "I");
        m_tib_vowel.put('\u0f74', "u");
        m_tib_vowel.put('\u0f75', "U");
        m_tib_vowel.put('\u0f7a', "e");
        m_tib_vowel.put('\u0f7b', "ai");
        m_tib_vowel.put('\u0f7c', "o");
        m_tib_vowel.put('\u0f7d', "au");
        m_tib_vowel.put('\u0f80', "-i");

        // long (Skt) vowels
        m_tib_vowel_long = new HashMap<String, String>();
        m_tib_vowel_long.put("i", "I");
        m_tib_vowel_long.put("u", "U");
        m_tib_vowel_long.put("-i", "-I");
        // this is not in the original Wylie spec but this is
        // encountered in Chinese names
        m_tib_vowel_long.put("e", "E");
        m_tib_vowel_long.put("o", "O");

        // final symbols => wylie
        m_tib_final_wylie = new HashMap<Character, String>();
        m_tib_final_wylie.put('\u0f7e', "M");
        m_tib_final_wylie.put('\u0f82', "~M`");
        m_tib_final_wylie.put('\u0f83', "~M");
        m_tib_final_wylie.put('\u0f37', "X");
        m_tib_final_wylie.put('\u0f35', "~X");
        m_tib_final_wylie.put('\u0f39', "^");
        m_tib_final_wylie.put('\u0f7f', "H");
        m_tib_final_wylie.put('\u0f84', "?");
        m_tib_final_wylie.put('\u0f85', "&");

        // final symbols by class
        m_tib_final_class = new HashMap<Character, String>();
        m_tib_final_class.put('\u0f7e', "M");
        m_tib_final_class.put('\u0f82', "M");
        m_tib_final_class.put('\u0f83', "M");
        m_tib_final_class.put('\u0f37', "X");
        m_tib_final_class.put('\u0f35', "X");
        m_tib_final_class.put('\u0f39', "^");
        m_tib_final_class.put('\u0f7f', "H");
        m_tib_final_class.put('\u0f84', "?");
        m_tib_final_class.put('\u0f85', "&");

        // special characters introduced by ^
        m_tib_caret = new HashMap<String, String>();
        m_tib_caret.put("ph", "f");
        m_tib_caret.put("b", "v");

        // other stand-alone characters
        m_tib_other = new HashMap<Character, String>();
        m_tib_other.put(' ', "_");
        m_tib_other.put('\u0f04', "@");
        m_tib_other.put('\u0f05', "#");
        m_tib_other.put('\u0f06', "$");
        m_tib_other.put('\u0f07', "%");
        m_tib_other.put('\u0f08', "!");
        m_tib_other.put('\u0f0b', " ");
        m_tib_other.put('\u0f0c', "*");
        m_tib_other.put('\u0f0d', "/");
        m_tib_other.put('\u0f0e', "//");
        m_tib_other.put('\u0f0f', ";");
        m_tib_other.put('\u0f11', "|");
        m_tib_other.put('\u0f14', ":");
        m_tib_other.put('\u0f20', "0");
        m_tib_other.put('\u0f21', "1");
        m_tib_other.put('\u0f22', "2");
        m_tib_other.put('\u0f23', "3");
        m_tib_other.put('\u0f24', "4");
        m_tib_other.put('\u0f25', "5");
        m_tib_other.put('\u0f26', "6");
        m_tib_other.put('\u0f27', "7");
        m_tib_other.put('\u0f28', "8");
        m_tib_other.put('\u0f29', "9");
        m_tib_other.put('\u0f34', "=");
        m_tib_other.put('\u0f3a', "<");
        m_tib_other.put('\u0f3b', ">");
        m_tib_other.put('\u0f3c', "(");
        m_tib_other.put('\u0f3d', ")");

        // all these stacked consonant combinations don't need "+"s in them
        m_tib_stacks = new HashSet<String>();
        m_tib_stacks.add("b+l");
        m_tib_stacks.add("b+r");
        m_tib_stacks.add("b+y");
        m_tib_stacks.add("c+w");
        m_tib_stacks.add("d+r");
        m_tib_stacks.add("d+r+w");
        m_tib_stacks.add("d+w");
        m_tib_stacks.add("dz+r");
        m_tib_stacks.add("g+l");
        m_tib_stacks.add("g+r");
        m_tib_stacks.add("g+r+w");
        m_tib_stacks.add("g+w");
        m_tib_stacks.add("g+y");
        m_tib_stacks.add("h+r");
        m_tib_stacks.add("h+w");
        m_tib_stacks.add("k+l");
        m_tib_stacks.add("k+r");
        m_tib_stacks.add("k+w");
        m_tib_stacks.add("k+y");
        m_tib_stacks.add("kh+r");
        m_tib_stacks.add("kh+w");
        m_tib_stacks.add("kh+y");
        m_tib_stacks.add("l+b");
        m_tib_stacks.add("l+c");
        m_tib_stacks.add("l+d");
        m_tib_stacks.add("l+g");
        m_tib_stacks.add("l+h");
        m_tib_stacks.add("l+j");
        m_tib_stacks.add("l+k");
        m_tib_stacks.add("l+ng");
        m_tib_stacks.add("l+p");
        m_tib_stacks.add("l+t");
        m_tib_stacks.add("l+w");
        m_tib_stacks.add("m+r");
        m_tib_stacks.add("m+y");
        m_tib_stacks.add("n+r");
        m_tib_stacks.add("ny+w");
        m_tib_stacks.add("p+r");
        m_tib_stacks.add("p+y");
        m_tib_stacks.add("ph+r");
        m_tib_stacks.add("ph+y");
        m_tib_stacks.add("ph+y+w");
        m_tib_stacks.add("r+b");
        m_tib_stacks.add("r+d");
        m_tib_stacks.add("r+dz");
        m_tib_stacks.add("r+g");
        m_tib_stacks.add("r+g+w");
        m_tib_stacks.add("r+g+y");
        m_tib_stacks.add("r+j");
        m_tib_stacks.add("r+k");
        m_tib_stacks.add("r+k+y");
        m_tib_stacks.add("r+l");
        m_tib_stacks.add("r+m");
        m_tib_stacks.add("r+m+y");
        m_tib_stacks.add("r+n");
        m_tib_stacks.add("r+ng");
        m_tib_stacks.add("r+ny");
        m_tib_stacks.add("r+t");
        m_tib_stacks.add("r+ts");
        m_tib_stacks.add("r+ts+w");
        m_tib_stacks.add("r+w");
        m_tib_stacks.add("s+b");
        m_tib_stacks.add("s+b+r");
        m_tib_stacks.add("s+b+y");
        m_tib_stacks.add("s+d");
        m_tib_stacks.add("s+g");
        m_tib_stacks.add("s+g+r");
        m_tib_stacks.add("s+g+y");
        m_tib_stacks.add("s+k");
        m_tib_stacks.add("s+k+r");
        m_tib_stacks.add("s+k+y");
        m_tib_stacks.add("s+l");
        m_tib_stacks.add("s+m");
        m_tib_stacks.add("s+m+r");
        m_tib_stacks.add("s+m+y");
        m_tib_stacks.add("s+n");
        m_tib_stacks.add("s+n+r");
        m_tib_stacks.add("s+ng");
        m_tib_stacks.add("s+ny");
        m_tib_stacks.add("s+p");
        m_tib_stacks.add("s+p+r");
        m_tib_stacks.add("s+p+y");
        m_tib_stacks.add("s+r");
        m_tib_stacks.add("s+t");
        m_tib_stacks.add("s+ts");
        m_tib_stacks.add("s+w");
        m_tib_stacks.add("sh+r");
        m_tib_stacks.add("sh+w");
        m_tib_stacks.add("t+r");
        m_tib_stacks.add("t+w");
        m_tib_stacks.add("th+r");
        m_tib_stacks.add("ts+w");
        m_tib_stacks.add("tsh+w");
        m_tib_stacks.add("z+l");
        m_tib_stacks.add("z+w");
        m_tib_stacks.add("zh+w");

        // a map used to split the input string into tokens for toUnicode().
        // all letters which start tokens longer than one letter are mapped to the max
        // length of
        // tokens starting with that letter.
        m_tokens_start = new HashMap<Character, Integer>();
        m_tokens_start.put('S', 2);
        m_tokens_start.put('/', 2);
        m_tokens_start.put('d', 4);
        m_tokens_start.put('g', 3);
        m_tokens_start.put('b', 3);
        m_tokens_start.put('D', 3);
        m_tokens_start.put('z', 2);
        m_tokens_start.put('~', 3);
        m_tokens_start.put('-', 4);
        m_tokens_start.put('T', 2);
        m_tokens_start.put('a', 2);
        m_tokens_start.put('k', 2);
        m_tokens_start.put('t', 3);
        m_tokens_start.put('s', 2);
        m_tokens_start.put('c', 2);
        m_tokens_start.put('n', 2);
        m_tokens_start.put('p', 2);
        m_tokens_start.put('\r', 2);

        // also for tokenization - a set of tokens longer than one letter
        m_tokens = new HashSet<String>();
        m_tokens.add("-d+h");
        m_tokens.add("dz+h");
        m_tokens.add("-dh");
        m_tokens.add("-sh");
        m_tokens.add("-th");
        m_tokens.add("D+h");
        m_tokens.add("b+h");
        m_tokens.add("d+h");
        m_tokens.add("dzh");
        m_tokens.add("g+h");
        m_tokens.add("tsh");
        m_tokens.add("~M`");
        m_tokens.add("-I");
        m_tokens.add("-d");
        m_tokens.add("-i");
        m_tokens.add("-n");
        m_tokens.add("-t");
        m_tokens.add("//");
        m_tokens.add("Dh");
        m_tokens.add("Sh");
        m_tokens.add("Th");
        m_tokens.add("ai");
        m_tokens.add("au");
        m_tokens.add("bh");
        m_tokens.add("ch");
        m_tokens.add("dh");
        m_tokens.add("dz");
        m_tokens.add("gh");
        m_tokens.add("kh");
        m_tokens.add("ng");
        m_tokens.add("ny");
        m_tokens.add("ph");
        m_tokens.add("sh");
        m_tokens.add("th");
        m_tokens.add("ts");
        m_tokens.add("zh");
        m_tokens.add("~M");
        m_tokens.add("~X");
        m_tokens.add("\r\n");
    }

    private static void initSloppyRepl() {
        int i = 0;
        base[i] = "ʼ";
        repl[i] = "'";
        i++; // 0x02BC
        base[i] = "ʹ";
        repl[i] = "'";
        i++; // 0x02B9
        base[i] = "‘";
        repl[i] = "'";
        i++; // 0x2018
        base[i] = "’";
        repl[i] = "'";
        i++; // 0x2019
        base[i] = "ʾ";
        repl[i] = "'";
        i++; // 0x02BE
        base[i] = "x";
        repl[i] = "\\u0fbe";
        i++;
        base[i] = "X";
        repl[i] = "\\u0fbe";
        i++;
        base[i] = "...";
        repl[i] = "\\u0f0b\\u0f0b\\u0f0b";
        i++;
        base[i] = " (";
        repl[i] = "_(";
        i++;
        base[i] = ") ";
        repl[i] = ")_";
        i++;
        base[i] = "/ ";
        repl[i] = "/_";
        i++;
        base[i] = " 0";
        repl[i] = "_0";
        i++;
        base[i] = " 1";
        repl[i] = "_1";
        i++;
        base[i] = " 2";
        repl[i] = "_2";
        i++;
        base[i] = " 3";
        repl[i] = "_3";
        i++;
        base[i] = " 4";
        repl[i] = "_4";
        i++;
        base[i] = " 5";
        repl[i] = "_5";
        i++;
        base[i] = " 6";
        repl[i] = "_6";
        i++;
        base[i] = " 7";
        repl[i] = "_7";
        i++;
        base[i] = " 8";
        repl[i] = "_8";
        i++;
        base[i] = " 9";
        repl[i] = "_9";
        i++;
        base[i] = "_ ";
        repl[i] = "__";
        i++;
        base[i] = "G";
        repl[i] = "g";
        i++;
        base[i] = "K";
        repl[i] = "k";
        i++;
        base[i] = "C";
        repl[i] = "c";
        i++;
        base[i] = "B";
        repl[i] = "b";
        i++;
        base[i] = " b ";
        repl[i] = " ba ";
        i++;
        base[i] = "Ts";
        repl[i] = "ts";
        i++;
        base[i] = "Dz";
        repl[i] = "dz";
        i++;
        base[i] = "Ny";
        repl[i] = "ny";
        i++;
        base[i] = "Ng";
        repl[i] = "ng";
        i++;
        base[i] = " m ";
        repl[i] = " ma ";
        i++;
        base[i] = " m'i ";
        repl[i] = " ma'i ";
        i++;
        base[i] = " b'i ";
        repl[i] = " ba'i ";
        i++;
        base[i] = "P";
        repl[i] = "p";
        i++;
        base[i] = "L";
        repl[i] = "l";
        i++;
        base[i] = "Z";
        repl[i] = "z";
        i++;
        base[i] = "J";
        repl[i] = "j";
        i++;
        base[i] = "（";
        repl[i] = "(";
        i++;
        base[i] = "）";
        repl[i] = ")";
        i++;
        base[i] = "༼";
        repl[i] = "(";
        i++;
        base[i] = "༽";
        repl[i] = ")";
        i++;
        base[i] = "：";
        repl[i] = ":";
        i++;
        base[i] = "H ";
        repl[i] = "H";
        i++;
        base[i] = "adm";
        repl[i] = "ad+m";
        i++;
    }

    private static void initLenientRepl() {
        int i = 0;
        baseL[i] = "ʼ";
        replL[i] = "'";
        i++; // 0x02BC
        baseL[i] = "ʹ";
        replL[i] = "'";
        i++; // 0x02B9
        baseL[i] = "‘";
        replL[i] = "'";
        i++; // 0x2018
        baseL[i] = "’";
        replL[i] = "'";
        i++; // 0x2019
        baseL[i] = "ʾ";
        replL[i] = "'";
        i++; // 0x02BE
        baseL[i] = "x";
        replL[i] = "\\u0fbe";
        i++;
        baseL[i] = "X";
        replL[i] = "\\u0fbe";
        i++;
        baseL[i] = "...";
        replL[i] = "\\u0f0b\\u0f0b\\u0f0b";
        i++;
        baseL[i] = "-i";
        replL[i] = "i";
        i++;
        baseL[i] = "-";
        replL[i] = " ";
        i++;
        baseL[i] = "：";
        replL[i] = ":";
        i++;
        baseL[i] = "adm";
        replL[i] = "ad+m";
        i++;
        baseL[i] = "\u0304";
        replL[i] = "";
        i++;
        baseL[i] = "ḥ";
        replL[i] = "'";
        i++;
        baseL[i] = "h\u0323";
        replL[i] = "'";
        i++;
        baseL[i] = "ṣ";
        replL[i] = "sh";
        i++;
        baseL[i] = "ṣ";
        replL[i] = "sh";
        i++;
        baseL[i] = "m\0323";
        replL[i] = "M";
        i++;
        baseL[i] = "ṃ";
        replL[i] = "M";
        i++;
        baseL[i] = "s\u0323";
        replL[i] = "sh";
        i++;
        baseL[i] = "\u0323";
        replL[i] = "";
        i++;
        baseL[i] = "\u0310";
        replL[i] = "";
        i++;
        baseL[i] = "ś";
        replL[i] = "sh";
        i++;
        baseL[i] = "ź";
        replL[i] = "zh";
        i++;
        baseL[i] = "\u0301";
        replL[i] = "h"; // in ś and ź
        i++;
        baseL[i] = "ñ";
        replL[i] = "ny";
        i++;
        baseL[i] = "n\u0303";
        replL[i] = "ny";
        i++;
        baseL[i] = "ṅ";
        replL[i] = "ng";
        i++;
        baseL[i] = "n\u0307";
        replL[i] = "ng";
        i++;
        baseL[i] = "ā";
        replL[i] = "a";
        i++;
        baseL[i] = "ī";
        replL[i] = "i";
        i++;
        baseL[i] = "ū";
        replL[i] = "u";
        i++;
        baseL[i] = "ṁ";
        replL[i] = "M";
        i++;
        baseL[i] = "ṭ";
        replL[i] = "t";
        i++;
        baseL[i] = "ḍ";
        replL[i] = "d";
        i++;
        baseL[i] = "ṇ";
        replL[i] = "n";
        i++;
        // q and ! are H and M escaping lower casing
        baseL[i] = "q ";
        replL[i] = "H";
        i++;
        baseL[i] = "q";
        replL[i] = "H";
        i++;
        baseL[i] = "!";
        replL[i] = "M";
        i++;
    }

    static {
        initHashes();
        initLenientRepl();
        initSloppyRepl();
    }

    // setup a wylie object
    private void initWylie(boolean check, boolean check_strict, boolean print_warnings,
                           boolean fix_spacing) {
        // check_strict requires check
        if (check_strict && !check) {
            throw new RuntimeException("check_strict requires check.");
        }

        this.check = check;
        this.check_strict = check_strict;
        this.print_warnings = print_warnings;
        this.fix_spacing = fix_spacing;
    }

    /**
     * Default constructor, sets the following defaults:
     * <ul>
     * <li>check: true</li>
     * <li>check_strict: true</li>
     * <li>print_warning: false</li>
     * <li>fix_spacing: true</li>
     * </ul>
     */
    public EwtsConverter() {
        initWylie(true, true, false, true);
    }

    // helper functions to access the various hash tables
    private final String consonant(String s) {
        return m_consonant.get(s);
    }

    private final String subjoined(String s) {
        return m_subjoined.get(s);
    }

    private final String vowel(String s) {
        return m_vowel.get(s);
    }

    private final String final_uni(String s) {
        return m_final_uni.get(s);
    }

    private final String final_class(String s) {
        return m_final_class.get(s);
    }

    private final String other(String s) {
        return m_other.get(s);
    }

    private final boolean isSpecial(String s) {
        return m_special.contains(s);
    }

    private final boolean isSuperscript(String s) {
        return m_superscripts.containsKey(s);
    }

    private final boolean superscript(String sup, String below) {
        HashSet<?> tmpSet = m_superscripts.get(sup);
        if (tmpSet == null)
            return false;
        return tmpSet.contains(below);
    }

    private final boolean isSubscript(String s) {
        return m_subscripts.containsKey(s);
    }

    private final boolean subscript(String sub, String above) {
        HashSet<?> tmpSet = m_subscripts.get(sub);
        if (tmpSet == null)
            return false;
        return tmpSet.contains(above);
    }

    private final boolean isPrefix(String s) {
        return m_prefixes.containsKey(s);
    }

    private final boolean prefix(String pref, String after) {
        HashSet<?> tmpSet = m_prefixes.get(pref);
        if (tmpSet == null)
            return false;
        return tmpSet.contains(after);
    }

    private final boolean isSuffix(String s) {
        return m_suffixes.contains(s);
    }

    private final boolean isSuff2(String s) {
        return m_suff2.containsKey(s);
    }

    private final boolean suff2(String suff, String before) {
        HashSet<?> tmpSet = m_suff2.get(suff);
        if (tmpSet == null)
            return false;
        return tmpSet.contains(before);
    }

    private final Integer ambiguous_key(String syll) {
        return m_ambiguous_key.get(syll);
    }

    private final String ambiguous_wylie(String syll) {
        return m_ambiguous_wylie.get(syll);
    }

    private final String tib_top(Character c) {
        return m_tib_top.get(c);
    }

    private final String tib_subjoined(Character c) {
        return m_tib_subjoined.get(c);
    }

    private final String tib_vowel(Character c) {
        return m_tib_vowel.get(c);
    }

    private final String tib_vowel_long(String s) {
        return m_tib_vowel_long.get(s);
    }

    private final String tib_final_wylie(Character c) {
        return m_tib_final_wylie.get(c);
    }

    private final String tib_final_class(Character c) {
        return m_tib_final_class.get(c);
    }

    private final String tib_caret(String s) {
        return m_tib_caret.get(s);
    }

    private final String tib_other(Character c) {
        return m_tib_other.get(c);
    }

    private final boolean tib_stack(String s) {
        return m_tib_stacks.contains(s);
    }

    // split a string into Converter tokens;
    // make sure there is room for at least one null element at the end of the array
    private String[] splitIntoTokens(String str) {
        String[] tokens = new String[str.length() + 2];
        int o = 0, i = 0;
        int maxlen = str.length();

        TOKEN: while (i < maxlen) {
            char c = str.charAt(i);
            Integer mlo = m_tokens_start.get(c);

            // if there are multi-char tokens starting with this char, try them
            if (mlo != null) {
                for (int len = mlo.intValue(); len > 1; len--) {
                    if (i <= maxlen - len) {
                        String tr = str.substring(i, i + len);
                        if (m_tokens.contains(tr)) {
                            tokens[o++] = tr;
                            i += len;
                            continue TOKEN;
                        }
                    }
                }
            }

            // things starting with backslash are special
            if (c == '\\' && i <= maxlen - 2) {

                if (str.charAt(i + 1) == 'u' && i <= maxlen - 6) {
                    tokens[o++] = str.substring(i, i + 6); // \\uxxxx
                    i += 6;

                } else if (str.charAt(i + 1) == 'U' && i <= maxlen - 10) {
                    tokens[o++] = str.substring(i, i + 10); // \\Uxxxxxxxx
                    i += 10;

                } else {
                    tokens[o++] = str.substring(i, i + 2); // \\x
                    i += 2;
                }
                continue TOKEN;
            }

            // otherwise just take one char
            tokens[o++] = Character.toString(c);
            i += 1;
        }

        return tokens;
    }

    /**
     * Checks if a character is a Tibetan Unicode combining character.
     *
     * @param x
     *            the character to check
     * @return true if x is a Tibetan Unicode combining character
     */
    public static boolean isCombining(char x) {
        // inspired from
        // https://github.com/apache/jena/blob/master/jena-core/src/main/java/org/apache/jena/rdfxml/xmlinput/impl/CharacterModel.java
        return ((x > 0X0F71 && x < 0X0F84) || (x < 0X0F8D && x > 0X0FBC));
    }

    /**
     * Converts a string to Unicode, fixes common EWTS errors.
     *
     * @param str
     *            the string to convert
     * @return the converted string
     */
    public String toUnicode(String str) {
        return toUnicode(str, null);
    }

    public String toUnicode(String str, final List<String> warns) {
        if (str == null) {
            return null;
        }

        final StringBuilder out = new StringBuilder();
        int line = 1;
        int units = 0;

        // remove initial spaces if required
        if (this.fix_spacing) {
            str = str.replaceFirst("^\\s+", "");
        }

        // split into tokens
        final String[] tokens = splitIntoTokens(str);
        int i = 0;

        // iterate over the tokens
        ITER: while (tokens[i] != null) {
            String t = tokens[i];
            String o;

            // [non-tibetan text] : pass through, nesting brackets
            if (t.equals("[")) {

                int nesting = 1;
                i++;
                ESC: while (tokens[i] != null) {
                    t = tokens[i++];
                    if (t.equals("["))
                        nesting++;
                    if (t.equals("]"))
                        nesting--;
                    if (nesting == 0)
                        continue ITER;

                    // handle unicode escapes and \1-char escapes within [comments]...
                    if (t.startsWith("\\u") || t.startsWith("\\U")) {
                        o = unicodeEscape(warns, line, t);
                        if (o != null) {
                            out.append(o);
                            continue ESC;
                        }
                    }

                    if (t.startsWith("\\")) {
                        o = t.substring(1);
                    } else {
                        o = t;
                    }

                    out.append(o);
                }

                warnl(warns, line, "Unfinished [non-Converter stuff].");
                break ITER;
            }

            // punctuation, numbers, etc
            o = other(t);
            if (o != null) {
                out.append(o);
                i++;
                units++;

                // collapse multiple spaces?
                if (t.equals(" ") && this.fix_spacing) {
                    while (tokens[i] != null && tokens[i].equals(" "))
                        i++;
                }

                continue ITER;
            }

            // vowels & consonants: process tibetan script up to a tsek, punctuation or line
            // noise
            if (vowel(t) != null || consonant(t) != null) {
                WylieTsekbar tb = toUnicodeOneTsekbar(tokens, i);
                StringBuilder word = new StringBuilder();
                for (int j = 0; j < tb.tokens_used; j++) {
                    word.append(tokens[i + j]);
                }
                out.append(tb.uni_string);
                i += tb.tokens_used;
                units++;

                for (final String w : tb.warns) {
                    warnl(warns, line, "\"" + word.toString() + "\": " + w);
                }

                continue ITER;
            }

            // *** misc unicode and line handling stuff ***

            // ignore BOM and zero-width space
            if (t.equals("\ufeff") || t.equals("\u200b")) {
                i++;
                continue ITER;
            }

            // \\u, \\U unicode characters
            if (t.startsWith("\\u") || t.startsWith("\\U")) {
                o = unicodeEscape(warns, line, t);
                if (o != null) {
                    i++;
                    out.append(o);
                    continue ITER;
                }
            }

            // backslashed characters
            if (t.startsWith("\\")) {
                out.append(t.substring(1));
                i++;
                continue ITER;
            }

            // count lines
            if (t.equals("\r\n") || t.equals("\n") || t.equals("\r")) {
                line++;
                out.append(t);
                i++;

                // also eat spaces after newlines (optional)
                if (this.fix_spacing) {
                    while (tokens[i] != null && tokens[i].equals(" "))
                        i++;
                }

                continue ITER;
            }

            // stuff that shouldn't occur out of context: special chars and remaining
            // [a-zA-Z]
            final char c = t.charAt(0);
            if (isSpecial(t) || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                warnl(warns, line, "Unexpected character \"" + t + "\".");
            }

            // anything else: pass through
            out.append(t);
            i++;
        }

        if (units == 0)
            warn(warns, "No Tibetan characters found!");

        if (this.check_strict) {
            if (out.length() > 0 && isCombining(out.charAt(0))) {
                warn(warns, "String starts with combining character '" + out.charAt(0) + "'");
            }
        }

        return out.toString();
    }

    // does this string consist of only hexadecimal digits?
    private boolean validHex(String t) {
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (!((c >= 'a' && c <= 'f') || (c >= '0' && c <= '9')))
                return false;
        }
        return true;
    }

    // handle a Converter unicode escape, \\uxxxx or \\Uxxxxxxxx
    private String unicodeEscape(List<String> warns, int line, String t) {
        String hex = t.substring(2);
        if (hex.isEmpty())
            return null;

        if (!validHex(hex)) {
            warnl(warns, line, "\"" + t + "\": invalid hex code.");
            return "";
        }

        return Character.valueOf((char) Integer.parseInt(hex, 16)).toString();
    }

    // generate a warning if we are keeping them; prints it out if we were asked to
    private void warn(List<String> warns, String str) {
        if (warns != null)
            warns.add(str);
        if (this.print_warnings)
            System.out.println(str);
    }

    // warn with line number
    private void warnl(List<String> warns, int line, String str) {
        warn(warns, "line " + line + ": " + str);
    }

    // debug print
    @SuppressWarnings("unused")
    private void debug(String str) {
        System.out.println(str);
    }

    // debug variable value
    @SuppressWarnings("unused")
    private void debugvar(Object o, String name) {
        System.out.println(">>" + name + "<< : (" + (o == null ? "NULL" : o.toString()) + ")");
    }

    // join a list (ArrayList or LinkedList) of strings into a single string
    private String joinStrings(List<String> a, String sep) {
        StringBuilder out = new StringBuilder();
        int len = a.size();

        int i = 0;
        for (String v : a) {
            out.append(v);
            if (sep != null && i < len - 1)
                out.append(sep);
            i++;
        }

        return out.toString();
    }

    // Converts one stack's worth of Converter into unicode, starting at the given
    // index
    // within the array of tokens.
    // Assumes that the first available token is valid, and is either a vowel or a
    // consonant.
    // Returns a WylieStack object.
    @SuppressWarnings("unused")
    private WylieStack toUnicodeOneStack(String[] tokens, int i) {
        int orig_i = i;
        String t, t2, o;
        StringBuilder out = new StringBuilder();
        ArrayList<String> warns = new ArrayList<String>();

        int consonants = 0; // how many consonants found
        String vowel_found = null; // any vowels (including a-chen)
        String vowel_sign = null; // any vowel signs (that go under or above the main stack)
        String single_consonant = null; // did we find just a single consonant?
        boolean plus = false; // any explicit subjoining via '+'?
        int caret = 0; // find any '^'?
        HashMap<String, String> final_found = new HashMap<String, String>(); // keep track of finals (H, M, etc) by
        // class

        // do we have a superscript?
        t = tokens[i];
        t2 = tokens[i + 1];
        if (t2 != null && isSuperscript(t) && superscript(t, t2)) {

            if (this.check_strict) {
                String next = consonantString(tokens, i + 1);
                if (!superscript(t, next)) {
                    next = next.replace("+", "");
                    warns.add("Superscript \"" + t + "\" does not occur above combination \"" + next + "\".");
                }
            }

            out.append(consonant(t));
            consonants++;
            i++;
            while (tokens[i] != null && tokens[i].equals("^")) {
                caret++;
                i++;
            }
        }

        // main consonant + stuff underneath.
        // this is usually executed just once, but the "+" subjoining operator makes it
        // come back here
        MAIN: while (true) {

            // main consonant (or a "a" after a "+")
            t = tokens[i];
            if (consonant(t) != null || (out.length() > 0 && subjoined(t) != null)) {
                if (out.length() > 0) {
                    out.append(subjoined(t));
                } else {
                    out.append(consonant(t));
                }
                i++;

                if (t.equals("a")) {
                    vowel_found = "a";
                } else {
                    consonants++;
                    single_consonant = t;
                }

                while (tokens[i] != null && tokens[i].equals("^")) {
                    caret++;
                    i++;
                }

                // subjoined: rata, yata, lata, wazur. there can be up two subjoined letters in
                // a stack.
                for (int z = 0; z < 2; z++) {
                    t2 = tokens[i];

                    if (t2 != null && isSubscript(t2)) {

                        // lata does not occur below multiple consonants
                        // (otherwise we mess up "brla" = "b.r+la")
                        if (t2.equals("l") && consonants > 1)
                            break;

                        // full stack checking (disabled by "+")
                        if (this.check_strict && !plus) {
                            String prev = consonantStringBackwards(tokens, i - 1, orig_i);
                            if (!subscript(t2, prev)) {
                                prev = prev.replace("+", "");
                                warns.add("Subjoined \"" + t2 + "\" not expected after \"" + prev + "\".");
                            }

                            // simple check only
                        } else if (this.check) {
                            if (!subscript(t2, t) && !(z == 1 && t2.equals("w") && t.equals("y"))) {
                                warns.add("Subjoined \"" + t2 + "\"not expected after \"" + t + "\".");
                            }
                        }

                        out.append(subjoined(t2));
                        i++;
                        consonants++;

                        while (tokens[i] != null && tokens[i].equals("^")) {
                            caret++;
                            i++;
                        }

                        t = t2;

                    } else {
                        break;
                    }
                }
            }

            // caret (^) can come anywhere in Converter but in Unicode we generate it at the
            // end of
            // the stack but before vowels if it came there (seems to be what OpenOffice
            // expects),
            // or at the very end of the stack if that's how it was in the Converter.
            if (caret > 0) {
                if (caret > 1) {
                    warns.add("Cannot have more than one \"^\" applied to the same stack.");
                }
                final_found.put(final_class("^"), "^");
                out.append(final_uni("^"));
                caret = 0;
            }

            // vowel(s)
            t = tokens[i];
            if (t != null && vowel(t) != null) {
                if (out.length() == 0)
                    out.append(vowel("a"));
                if (!t.equals("a"))
                    out.append(vowel(t));
                i++;
                vowel_found = t;
                if (!t.equals("a"))
                    vowel_sign = t;
            }

            // plus sign: forces more subjoining
            t = tokens[i];
            if (t != null && t.equals("+")) {
                i++;
                plus = true;

                // sanity check: next token must be vowel or subjoinable consonant.
                t = tokens[i];
                if (t == null || (vowel(t) == null && subjoined(t) == null)) {
                    if (this.check)
                        warns.add("Expected vowel or consonant after \"+\".");
                    break MAIN;
                }

                // consonants after vowels doesn't make much sense but process it anyway
                if (this.check) {
                    if (vowel(t) == null && vowel_sign != null) {
                        warns.add(
                                "Cannot subjoin consonant (" + t + ") after vowel (" + vowel_sign + ") in same stack.");

                    } else if (t.equals("a") && vowel_sign != null) {
                        warns.add("Cannot subjoin a-chen (a) after vowel (" + vowel_sign + ") in same stack.");
                    }
                }

                continue MAIN;
            }

            break MAIN;
        }

        // final tokens
        t = tokens[i];
        while (t != null && final_class(t) != null) {
            String uni = final_uni(t);
            String klass = final_class(t);

            // check for duplicates
            if (final_found.containsKey(klass)) {
                if (final_found.get(klass).equals(t)) {
                    warns.add("Cannot have two \"" + t + "\" applied to the same stack.");
                } else {
                    warns.add("Cannot have \"" + t + "\" and \"" + final_found.get(klass)
                            + "\" applied to the same stack.");
                }
            } else {
                final_found.put(klass, t);
                out.append(uni);
            }

            i++;
            single_consonant = null;
            t = tokens[i];
        }

        // if next is a dot "." (stack separator), skip it.
        if (tokens[i] != null && tokens[i].equals("."))
            i++;

        // if we had more than a consonant and no vowel, and no explicit "+" joining,
        // backtrack and
        // return the 1st consonant alone
        if (consonants > 1 && vowel_found == null) {
            if (plus) {
                if (this.check)
                    warns.add("Stack with multiple consonants should end with vowel.");
            } else {
                i = orig_i + 1;
                consonants = 1;
                single_consonant = tokens[orig_i];
                out.setLength(0);
                out.append(consonant(single_consonant));
            }
        }

        // calculate "single consonant"
        if (consonants != 1 || plus) {
            single_consonant = null;
        }

        // return the stuff as a WylieStack struct
        WylieStack ret = new WylieStack();

        ret.uni_string = out.toString();
        ret.tokens_used = i - orig_i;

        if (vowel_found != null) {
            ret.single_consonant = null;
        } else {
            ret.single_consonant = single_consonant;
        }

        if (vowel_found != null && vowel_found.equals("a")) {
            ret.single_cons_a = single_consonant;
        } else {
            ret.single_cons_a = null;
        }

        ret.warns = warns;
        ret.visarga = final_found.containsKey("H");

        return ret;
    }

    // Converts successive stacks of Converter into unicode, starting at the given
    // index
    // within the array of tokens.
    //
    // Assumes that the first available token is valid, and is either a vowel or a
    // consonant.
    // Returns a WylieTsekbar object
    @SuppressWarnings("unused")
    private WylieTsekbar toUnicodeOneTsekbar(String[] tokens, int i) {
        int orig_i = i;
        String t = tokens[i];

        // variables for tracking the state within the syllable as we parse it
        WylieStack stack = null;

        String prev_cons = null;
        boolean visarga = false;

        // variables for checking the root letter, after parsing a whole tsekbar made of
        // only single
        // consonants and one consonant with "a" vowel
        boolean check_root = true;
        ArrayList<String> consonants = new ArrayList<String>();
        int root_idx = -1;

        StringBuilder out = new StringBuilder();
        ArrayList<String> warns = new ArrayList<String>();

        // the type of token that we are expecting next in the input stream
        // - PREFIX : expect a prefix consonant, or a main stack
        // - MAIN : expect only a main stack
        // - SUFF1 : expect a 1st suffix
        // - SUFF2 : expect a 2nd suffix
        // - NONE : expect nothing (after a 2nd suffix)
        //
        // the state machine is actually more lenient than this, in that a "main stack"
        // is allowed
        // to come at any moment, even after suffixes. this is because such syllables
        // are sometimes
        // found in abbreviations or other places. basically what we check is that
        // prefixes and
        // suffixes go with what they are attached to.
        //
        // valid tsek-bars end in one of these states: SUFF1, SUFF2, NONE
        State state = State.PREFIX;

        // iterate over the stacks of a tsek-bar
        STACK: while (t != null && (vowel(t) != null || consonant(t) != null) && !visarga) {

            // translate a stack
            if (stack != null)
                prev_cons = stack.single_consonant;
            stack = toUnicodeOneStack(tokens, i);
            i += stack.tokens_used;
            t = tokens[i];
            out.append(stack.uni_string);
            warns.addAll(stack.warns);
            visarga = stack.visarga;

            if (!this.check)
                continue;

            // check for syllable structure consistency by iterating a simple state machine
            // - prefix consonant
            if (state == State.PREFIX && stack.single_consonant != null) {
                consonants.add(stack.single_consonant);

                if (isPrefix(stack.single_consonant)) {
                    String next = t;
                    if (this.check_strict)
                        next = consonantString(tokens, i);

                    if (next != null && !prefix(stack.single_consonant, next)) {
                        next = next.replace("+", "");
                        warns.add("Prefix \"" + stack.single_consonant + "\" does not occur before \"" + next + "\".");
                    }

                } else {
                    warns.add("Invalid prefix consonant: \"" + stack.single_consonant + "\".");
                }
                state = State.MAIN;

                // - main stack with vowel or multiple consonants
            } else if (stack.single_consonant == null) {
                state = State.SUFF1;

                // keep track of the root consonant if it was a single cons with an "a" vowel
                if (root_idx >= 0) {
                    check_root = false;
                } else if (stack.single_cons_a != null) {
                    consonants.add(stack.single_cons_a);
                    root_idx = consonants.size() - 1;
                }

                // - unexpected single consonant after prefix
            } else if (state == State.MAIN) {
                warns.add("Expected vowel after \"" + stack.single_consonant + "\".");

                // - 1st suffix
            } else if (state == State.SUFF1) {
                consonants.add(stack.single_consonant);

                // check this one only in strict mode b/c it trips on lots of Skt stuff
                if (this.check_strict) {
                    if (!isSuffix(stack.single_consonant)) {
                        warns.add("Invalid suffix consonant: \"" + stack.single_consonant + "\".");
                    }
                }

                state = State.SUFF2;

                // - 2nd suffix
            } else if (state == State.SUFF2) {
                consonants.add(stack.single_consonant);
                if (isSuff2(stack.single_consonant)) {
                    if (!suff2(stack.single_consonant, prev_cons)) {
                        warns.add("Second suffix \"" + stack.single_consonant + "\" does not occur after \"" + prev_cons
                                + "\".");
                    }
                } else {
                    // handles pa'm, pa'ng
                    if (!m_affixedsuff2.contains(stack.single_consonant) || !prev_cons.equals("'")) {
                        warns.add("Invalid 2nd suffix consonant: \"" + stack.single_consonant + "\".");
                    }
                }
                state = State.NONE;

                // - more crap after a 2nd suffix
            } else if (state == State.NONE) {
                warns.add("Cannot have another consonant \"" + stack.single_consonant + "\" after 2nd suffix.");
            }
        }

        if (state == State.MAIN && stack.single_consonant != null && isPrefix(stack.single_consonant)) {
            warns.add("Vowel expected after \"" + stack.single_consonant + "\".");
        }

        // check root consonant placement only if there were no warnings so far, and the
        // syllable
        // looks ambiguous. not many checks are needed here because the previous state
        // machine
        // already takes care of most illegal combinations.
        if (this.check && warns.size() == 0 && check_root && root_idx >= 0) {

            // 2 letters where each could be prefix/suffix: root is 1st
            if (consonants.size() == 2 && root_idx != 0 && prefix(consonants.get(0), consonants.get(1))
                    && isSuffix(consonants.get(1))) {
                warns.add("Syllable should probably be \"" + consonants.get(0) + "a" + consonants.get(1) + "\".");

                // 3 letters where 1st can be prefix, 2nd can be postfix before "s" and last is
                // "s":
                // use a lookup table as this is completely ambiguous.
            } else if (consonants.size() == 3 && isPrefix(consonants.get(0)) && suff2("s", consonants.get(1))
                    && consonants.get(2).equals("s")) {
                String cc = joinStrings(consonants, "");
                cc = cc.replace('\u2018', '\'');
                cc = cc.replace('\u2019', '\''); // typographical quotes
                Integer expect_key = ambiguous_key(cc);
                if (expect_key != null && expect_key.intValue() != root_idx) {
                    warns.add("Syllable should probably be \"" + ambiguous_wylie(cc) + "\".");
                }
            }
        }

        // return the stuff as a WylieTsekbar struct
        WylieTsekbar ret = new WylieTsekbar();

        ret.uni_string = out.toString();
        ret.tokens_used = i - orig_i;
        ret.warns = warns;

        return ret;
    }

    // Looking from i onwards within tokens, returns as many consonants as it finds,
    // up to and not including the next vowel or punctuation. Skips the caret "^".
    // Returns: a string of consonants joined by "+" signs.
    private String consonantString(String[] tokens, int i) {
        ArrayList<String> out = new ArrayList<String>();
        String t;

        while (tokens[i] != null) {
            t = tokens[i++];
            if (t.equals("+") || t.equals("^"))
                continue;
            if (consonant(t) == null)
                break;
            out.add(t);
        }

        return joinStrings(out, "+");
    }

    // Looking from i backwards within tokens, at most up to orig_i, returns as
    // many consonants as it finds, up to and not including the next vowel or
    // punctuation. Skips the caret "^".
    // Returns: a string of consonants (in forward order) joined by "+" signs.
    private String consonantStringBackwards(String[] tokens, int i, int orig_i) {
        LinkedList<String> out = new LinkedList<String>();
        String t;

        while (i >= orig_i && tokens[i] != null) {
            t = tokens[i--];
            if (t.equals("+") || t.equals("^"))
                continue;
            if (consonant(t) == null)
                break;
            out.addFirst(t);
        }

        return joinStrings(out, "+");
    }

    // HELPER CLASSES AND STRUCTURES

    // An Enum for the list of states used to check the consistency of a Tibetan
    // tsekbar.

    private static enum State {
        PREFIX, MAIN, SUFF1, SUFF2, NONE
    }

    // A simple class to encapsulate the return value of toUnicodeOneStack.
    // Quick and dirty and not particularly OO.

    private static class WylieStack {
        // the converted unicode string
        public String uni_string;

        // how many tokens from the stream were used
        public int tokens_used;

        // did we find a single consonant without vowel? if so which one
        public String single_consonant;

        // did we find a single consonant with an "a"? if so which one
        public String single_cons_a;

        // list of warnings
        public ArrayList<String> warns;

        // found a visarga?
        public boolean visarga;
    }

    // A simple class to encapsulate the return value of toUnicodeOneTsekbar.
    // Quick and dirty and not particularly OO.

    private static class WylieTsekbar {
        // the converted unicode string
        public String uni_string;

        // how many tokens from the stream were used
        public int tokens_used;

        // list of warnings
        public ArrayList<String> warns;
    }
}