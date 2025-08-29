// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.event

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for Bengali combiner.
 * Tests the core functionality ported from Python khipro implementation.
 */
class BnKhiproCombinerTest {

    @Test
    fun testBasicConsonants() {
        assertEquals("ক", BnKhiproCombiner.convert("k"))
        assertEquals("খ", BnKhiproCombiner.convert("kh"))
        assertEquals("গ", BnKhiproCombiner.convert("g"))
        assertEquals("ঘ", BnKhiproCombiner.convert("gh"))
        assertEquals("ত", BnKhiproCombiner.convert("t"))
        assertEquals("থ", BnKhiproCombiner.convert("th"))
        assertEquals("দ", BnKhiproCombiner.convert("d"))
        assertEquals("ধ", BnKhiproCombiner.convert("dh"))
        assertEquals("ন", BnKhiproCombiner.convert("n"))
        assertEquals("প", BnKhiproCombiner.convert("p"))
        assertEquals("ফ", BnKhiproCombiner.convert("ph"))
        assertEquals("ব", BnKhiproCombiner.convert("b"))
        assertEquals("ভ", BnKhiproCombiner.convert("v"))
        assertEquals("ম", BnKhiproCombiner.convert("m"))
        assertEquals("য", BnKhiproCombiner.convert("z"))
        assertEquals("র", BnKhiproCombiner.convert("r"))
        assertEquals("ল", BnKhiproCombiner.convert("l"))
        assertEquals("শ", BnKhiproCombiner.convert("sh"))
        assertEquals("ষ", BnKhiproCombiner.convert("sf"))
        assertEquals("স", BnKhiproCombiner.convert("s"))
        assertEquals("হ", BnKhiproCombiner.convert("h"))
    }

    @Test
    fun testBasicVowels() {
        assertEquals("আ", BnKhiproCombiner.convert("a"))
        assertEquals("ই", BnKhiproCombiner.convert("i"))
        assertEquals("ঈ", BnKhiproCombiner.convert("ii"))
        assertEquals("উ", BnKhiproCombiner.convert("u"))
        assertEquals("ঊ", BnKhiproCombiner.convert("uu"))
        assertEquals("ঋ", BnKhiproCombiner.convert("q"))
        assertEquals("এ", BnKhiproCombiner.convert("e"))
        assertEquals("ঐ", BnKhiproCombiner.convert("oi"))
        assertEquals("ও", BnKhiproCombiner.convert("w"))
        assertEquals("ঔ", BnKhiproCombiner.convert("ou"))
    }

    @Test
    fun testConsonantVowelCombinations() {
        assertEquals("কা", BnKhiproCombiner.convert("ka"))
        assertEquals("কি", BnKhiproCombiner.convert("ki"))
        assertEquals("কী", BnKhiproCombiner.convert("kii"))
        assertEquals("কু", BnKhiproCombiner.convert("ku"))
        assertEquals("কূ", BnKhiproCombiner.convert("kuu"))
        assertEquals("কে", BnKhiproCombiner.convert("ke"))
        assertEquals("কো", BnKhiproCombiner.convert("kw"))
        assertEquals("কৌ", BnKhiproCombiner.convert("kou"))
    }

    @Test
    fun testJuktoborno() {
        // Basic conjuncts
        assertEquals("ক্ক", BnKhiproCombiner.convert("kk"))
        assertEquals("ক্ত", BnKhiproCombiner.convert("kt"))
        assertEquals("ক্ত্র", BnKhiproCombiner.convert("ktr"))
        assertEquals("ক্ষ", BnKhiproCombiner.convert("kf"))
        assertEquals("ক্ষ", BnKhiproCombiner.convert("ksf"))
        assertEquals("ক্ষ", BnKhiproCombiner.convert("kkh"))
        assertEquals("ক্ষ্ণ", BnKhiproCombiner.convert("kfn"))
        assertEquals("ক্র", BnKhiproCombiner.convert("kr"))
        assertEquals("ক্ল", BnKhiproCombiner.convert("kl"))
        
        // More complex conjuncts
        assertEquals("ত্ত", BnKhiproCombiner.convert("tt"))
        assertEquals("ত্র", BnKhiproCombiner.convert("tr"))
        assertEquals("ন্ত", BnKhiproCombiner.convert("nt"))
        assertEquals("ন্দ", BnKhiproCombiner.convert("nd"))
        assertEquals("ন্ধ", BnKhiproCombiner.convert("ndh"))
        assertEquals("স্ত", BnKhiproCombiner.convert("st"))
        assertEquals("স্থ", BnKhiproCombiner.convert("sth"))
    }

    @Test
    fun testJuktobornoWithVowels() {
        assertEquals("ক্তি", BnKhiproCombiner.convert("kti"))
        assertEquals("ক্ত্রি", BnKhiproCombiner.convert("ktri"))
        assertEquals("ন্দি", BnKhiproCombiner.convert("ndi"))
        assertEquals("ন্ধি", BnKhiproCombiner.convert("ndhi"))
        assertEquals("স্থি", BnKhiproCombiner.convert("sthi"))
    }

    @Test
    fun testReph() {
        assertEquals("র্", BnKhiproCombiner.convert("rr"))
        assertEquals("র", BnKhiproCombiner.convert("r"))
    }

    @Test
    fun testPhola() {
        // Test phola insertion with virama
        // This tests the special rule: PHOLA in BYANJON_STATE inserts virama before mapped char
        assertEquals("ক্র", BnKhiproCombiner.convert("kr"))
        assertEquals("গ্র", BnKhiproCombiner.convert("gr"))
        assertEquals("ত্র", BnKhiproCombiner.convert("tr"))
        assertEquals("প্র", BnKhiproCombiner.convert("pr"))
        assertEquals("ব্র", BnKhiproCombiner.convert("br"))
    }

    @Test
    fun testNumbers() {
        assertEquals("১", BnKhiproCombiner.convert("1"))
        assertEquals("২", BnKhiproCombiner.convert("2"))
        assertEquals("৩", BnKhiproCombiner.convert("3"))
        assertEquals("৪", BnKhiproCombiner.convert("4"))
        assertEquals("৫", BnKhiproCombiner.convert("5"))
        assertEquals("৬", BnKhiproCombiner.convert("6"))
        assertEquals("৭", BnKhiproCombiner.convert("7"))
        assertEquals("৮", BnKhiproCombiner.convert("8"))
        assertEquals("৯", BnKhiproCombiner.convert("9"))
        assertEquals("০", BnKhiproCombiner.convert("0"))
    }

    @Test
    fun testDiacritics() {
        assertEquals("্", BnKhiproCombiner.convert("qq"))
        assertEquals("ঃ", BnKhiproCombiner.convert("x"))
        assertEquals("ং", BnKhiproCombiner.convert("ng"))
        assertEquals("ঁ", BnKhiproCombiner.convert("/"))
        assertEquals("ৎ", BnKhiproCombiner.convert("t/"))
    }

    @Test
    fun testBiram() {
        assertEquals("।", BnKhiproCombiner.convert("."))
        assertEquals("৳", BnKhiproCombiner.convert("$"))
        assertEquals("₹", BnKhiproCombiner.convert("\$f"))
    }

    @Test
    fun testGreedyMatching() {
        // Test that longer matches are preferred over shorter ones
        assertEquals("খ", BnKhiproCombiner.convert("kh")) // not "ক" + "h"
        assertEquals("ছ", BnKhiproCombiner.convert("ch")) // not "চ" + "h"
        assertEquals("থ", BnKhiproCombiner.convert("th")) // not "ত" + "h"
        assertEquals("ক্ষ", BnKhiproCombiner.convert("kkh")) // not "ক্ক" + "h"
        assertEquals("ক্ষ্ণ", BnKhiproCombiner.convert("kkhn")) // not "ক্ষ" + "ন"
    }

    @Test
    fun testComplexWords() {
        // Test some complete words
        assertEquals("বাংলা", BnKhiproCombiner.convert("bangla"))
        assertEquals("ভাশা", BnKhiproCombiner.convert("vasha"))
        assertEquals("লিখন", BnKhiproCombiner.convert("likhon"))
        assertEquals("কম্পিউতার", BnKhiproCombiner.convert("kompiutar"))
    }

    @Test
    fun testUnmappedCharacters() {
        // Test that unmapped characters pass through unchanged
        assertEquals("আবচ", BnKhiproCombiner.convert("abc"))
        assertEquals("ক@ল", BnKhiproCombiner.convert("k@l"))
        assertEquals("তেস্ত১২৩", BnKhiproCombiner.convert("test123"))
    }

    @Test
    fun testMixedContent() {
        // Test mixing Bengali and English
        assertEquals("হেল্ল ক", BnKhiproCombiner.convert("hello k"))
        assertEquals("ক ওঅরলদ", BnKhiproCombiner.convert("k world"))
        assertEquals("তেস্ত ক্ষ তেস্ত", BnKhiproCombiner.convert("test kf test"))
    }

    @Test
    fun testEdgeCases() {
        // Test empty string
        assertEquals("", BnKhiproCombiner.convert(""))
        
        // Test single characters
        assertEquals("আ", BnKhiproCombiner.convert("a"))
        assertEquals("ক", BnKhiproCombiner.convert("k"))
        
        // Test special sequences
        assertEquals("", BnKhiproCombiner.convert(";"))
        assertEquals(";", BnKhiproCombiner.convert(";;"))
    }

    @Test
    fun testStateTransitions() {
        // Test that state transitions work correctly
        // These test the state machine behavior more explicitly
        assertEquals("আক", BnKhiproCombiner.convert("ak")) // INIT -> SHOR_STATE -> BYANJON_STATE
        assertEquals("কা", BnKhiproCombiner.convert("ka")) // INIT -> BYANJON_STATE -> SHOR_STATE
        assertEquals("র্ক", BnKhiproCombiner.convert("rrk")) // INIT -> REPH_STATE -> BYANJON_STATE
    }
}
