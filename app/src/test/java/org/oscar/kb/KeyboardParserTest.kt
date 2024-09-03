// SPDX-License-Identifier: GPL-3.0-only
package org.oscar.kb

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodSubtype
import org.inputmethod.keyboard.ProximityInfo
import org.oscar.kb.keyboard.Key.KeyParams
import org.oscar.kb.keyboard.internal.KeyboardBuilder
import org.oscar.kb.keyboard.internal.keyboard_parser.POPUP_KEYS_NORMAL
import org.oscar.kb.keyboard.internal.keyboard_parser.RawKeyboardParser
import org.oscar.kb.keyboard.internal.keyboard_parser.addLocaleKeyTextsToParams
import org.oscar.kb.keyboard.internal.keyboard_parser.floris.KeyCode
import org.oscar.kb.latin.utils.AdditionalSubtypeUtils.createEmojiCapableAdditionalSubtype
import org.oscar.kb.latin.utils.POPUP_KEYS_LAYOUT
import org.oscar.kb.latin.utils.checkKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLog
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    ShadowInputMethodManager2::class,
    ShadowProximityInfo::class,
])
class ParserTest {
    private lateinit var latinIME: _root_ide_package_.org.oscar.kb.latin.LatinIME
    private lateinit var params: _root_ide_package_.org.oscar.kb.keyboard.internal.KeyboardParams

    @Before
    fun setUp() {
        latinIME = Robolectric.setupService(_root_ide_package_.org.oscar.kb.latin.LatinIME::class.java)
        ShadowLog.setupLogging()
        ShadowLog.stream = System.out
        params = _root_ide_package_.org.oscar.kb.keyboard.internal.KeyboardParams()
        params.mId = _root_ide_package_.org.oscar.kb.keyboard.KeyboardLayoutSet.getFakeKeyboardId(
            _root_ide_package_.org.oscar.kb.keyboard.KeyboardId.ELEMENT_ALPHABET)
        params.mPopupKeyTypes.add(POPUP_KEYS_LAYOUT)
        addLocaleKeyTextsToParams(latinIME, params, POPUP_KEYS_NORMAL)
    }

    // todo: add tests for background type, also consider e.g. emoji key has functional bg by default

    @Test fun simpleParser() {
        val layoutStrings = listOf(
"""
a
b
c

d
e
f
""", // normal
"""
a
b
c
    
d
e
f
""", // spaces in the empty line
"""
a
b
c

d
e
f
""".replace("\n", "\r\n"), // windows file endings
"""
a
b
c


d
e
f

""", // too many newlines
"""
a 
b x  
c v

d
e
f
""", // spaces in the end
"""
a
b
c

d
e
f""", // no newline at the end
        )
        val wantedKeyLabels = listOf(listOf("a", "b", "c"), listOf("d", "e", "f"))
        layoutStrings.forEachIndexed { i, layout ->
            println(i)
            val keyLabels = RawKeyboardParser.parseSimpleString(layout).map { it.map { it.toKeyParams(params).mLabel } }
            assertEquals(wantedKeyLabels, keyLabels)
        }
    }

    @Test fun simpleKey() {
        assertIsExpected("""[[{ "$": "auto_text_key" "label": "a" }]]""", Expected('a'.code, "a"))
        assertIsExpected("""[[{ "$": "text_key" "label": "a" }]]""", Expected('a'.code, "a"))
        assertIsExpected("""[[{ "label": "a" }]]""", Expected('a'.code, "a"))
    }

    @Test fun labelAndExplicitCode() {
        assertIsExpected("""[[{ "$": "text_key" "label": "a", "code": 98 }]]""", Expected('b'.code, "a"))
    }

    @Test fun labelAndImplicitCode() {
        assertIsExpected("""[[{ "$": "text_key" "label": "a|b" }]]""", Expected('b'.code, "a"))
    }

    @Test fun labelAndImplicitText() {
        assertIsExpected("""[[{ "$": "text_key" "label": "a|bb" }]]""", Expected(KeyCode.MULTIPLE_CODE_POINTS, "a", text = "bb"))
        // todo: should this actually work?
        assertIsExpected("""[[{ "$": "text_key" "label": "a|" }]]""", Expected(KeyCode.MULTIPLE_CODE_POINTS, "a", text = ""))
    }

    @Test fun labelAndImplicitAndExplicitCode() { // explicit code overrides implicit code
        assertIsExpected("""[[{ "code": 32, "label": "a|b" }]]""", Expected(' '.code, "a"))
        assertIsExpected("""[[{ "code": 32, "label": "a|!code/key_delete" }]]""", Expected(' '.code, "a"))
        // todo: should text be null? it's not used at all (it could be, but it really should not)
        assertIsExpected("""[[{ "code": 32, "label": "a|bb" }]]""", Expected(' '.code, "a", text = "bb"))
    }

    @Test fun keyWithIconAndExplicitCode() {
        assertIsExpected("""[[{ "label": "!icon/clipboard", "code": 55 }]]""", Expected(55, icon = "clipboard"))
    }

    @Test fun keyWithIconAndImplicitCode() {
        assertIsExpected("""[[{ "label": "!icon/clipboard_action_key|!code/key_clipboard" }]]""", Expected(KeyCode.CLIPBOARD, icon = "clipboard_action_key"))
    }

    @Test fun popupKeyWithIconAndExplicitCode() {
        assertIsExpected("""[[{ "label": "a", "popup": { "relevant": [
       { "label": "!icon/go_key", "code": 32 }
      ]
    } }]]""", Expected('a'.code, "a", popups = listOf(null to ' '.code))
        )
    }

    @Test fun popupKeyWithIconAndExplicitAndImplicitCode() {
        assertIsExpected("""[[{ "label": "a", "popup": { "relevant": [
       { "label": "!icon/go_key|", "code": 32 }
      ]
    } }]]""", Expected('a'.code, "a", popups = listOf(null to ' '.code))
        )
        assertIsExpected("""[[{ "label": "a", "popup": { "relevant": [
       { "label": "!icon/go_key|abc", "code": 32 }
      ]
    } }]]""", Expected('a'.code, "a", popups = listOf(null to ' '.code))
        )
    }

    @Test fun labelAndImplicitCodeForPopup() {
        assertIsExpected("""[[{ "$": "text_key" "label": "a|b", "popup": { "main": { "label": "b|a" } } }]]""", Expected('b'.code, "a", popups = listOf("b" to 'a'.code)))
        assertIsExpected("""[[{ "label": "a", "popup": { "relevant": [
       { "label": "!icon/go_key|" }
      ]
    } }]]""", Expected('a'.code, "a",
            popups = listOf(null to KeyCode.MULTIPLE_CODE_POINTS))
        )
    }

    @Test fun `| works`() {
        assertIsExpected("""[[{ "label": "|", "popup": { "main": { "label": "|" } } }]]""", Expected('|'.code, "|", popups = listOf("|" to '|'.code)))
    }

    @Test fun currencyKey() {
        assertIsExpected("""[[{ "label": "$$$" }]]""", Expected('$'.code, "$", popups = listOf("£", "€", "¢", "¥", "₱").map { it to it.first().code }))
    }

    @Test fun currencyKeyWithOtherCurrencyCode() {
        assertIsExpected("""[[{ "label": "$$$", code: -805 }]]""", Expected('¥'.code, "$", popups = listOf("£", "€", "¢", "¥", "₱").map { it to it.first().code }))
    }

    @Test fun currencyPopup() {
        assertIsExpected("""[[{ "label": "p", "popup": { "main": { "label": "$$$" } } }]]""", Expected('p'.code, "p", null, null, listOf("$" to '$'.code)))
        assertIsExpected("""[[{ "label": "p", "popup": { "main": { "label": "a", "code": -804 } } }]]""", Expected('p'.code, "p", null, null, listOf("a" to '€'.code)))
        assertIsExpected("""[[{ "label": "p", "popup": { "main": { "label": "!icon/clipboard_action_key", "code": -804 } } }]]""", Expected('p'.code, "p", null, null, listOf(null to '€'.code)))
    }

    @Test fun weirdCurrencyKey() {
        assertIsExpected("""[[{ "code": -801, "label": "currency_slot_1", "popup": {
      "main": { "code": -802, "label": "currency_slot_2" },
      "relevant": [
        { "code": -806, "label": "currency_slot_6" },
        { "code": -803, "label": "currency_slot_3" },
        { "code": -804, "label": "currency_slot_4" },
        { "code": -805, "label": "currency_slot_5" },
        { "code": -804, "label": "$$$4" }
      ]
    } }]]""", Expected('$'.code, "$", popups = listOf("£" to '£'.code, "₱" to '₱'.code, "€" to '€'.code, "¢" to '¢'.code, "¥" to '¥'.code, "¥" to '€'.code))
        )
    }

    @Test fun caseSelector() {
        assertIsExpected("""[[{ "$": "case_selector",
      "lower": { "code":  105, "label": "i" },
      "upper": { "code":  304, "label": "İ" }
    }]]""", Expected(105, "i")
        )
    }

    @Test fun caseSelectorWithPopup() {
        assertIsExpected("""[[{ "$": "case_selector",
      "lower": { "code":   59, "label": ";", "popup": {
        "relevant": [
          { "code":   58, "label": ":" }
        ]
      } },
      "upper": { "code":   58, "label": ":", "popup": {
        "relevant": [
          { "code":   59, "label": ";" }
        ]
      } }
    }]]""", Expected(';'.code, ";", popups = listOf(":").map { it to it.first().code })
        )
    }

    @Test fun shiftSelector() {
        assertIsExpected("""[[{ "$": "shift_state_selector",
      "shiftedManual": { "code":   62, "label": ">", "popup": {
        "relevant": [
          { "code":   46, "label": "." }
        ]
      } },
      "default": { "code":   46, "label": ".", "popup": {
        "relevant": [
          { "code":   62, "label": ">" }
        ]
      } }
    }]]""", Expected('.'.code, ".", popups = listOf(">").map { it to it.first().code })
        )
    }

    @Test fun nestedSelectors() {
        assertIsExpected("""[[{ "$": "shift_state_selector",
      "shiftedManual": { "code":   34, "label": "\"", "popup": {
        "relevant": [
          { "code":   33, "label": "!" },
          { "code":   39, "label": "'"}
        ]
      } },
      "default": { "$": "variation_selector",
        "email":   { "code":   64, "label": "@" },
        "uri":     { "code":   47, "label": "/" },
        "default": { "code":   39, "label": "'", "popup": {
          "relevant": [
            { "code":   33, "label": "!" },
            { "code":   34, "label": "\"" }
          ]
        } }
      }
    }]]""", Expected('\''.code, "'", popups = listOf("!", "\"").map { it to it.first().code })
        )
    }

    @Test fun layoutDirectionSelector() {
        assertIsExpected("""[[{ "$": "layout_direction_selector",
      "ltr": { "code":   40, "label": "(", "popup": {
        "main": { "code":   60, "label": "<" },
        "relevant": [
          { "code":   91, "label": "[" },
          { "code":  123, "label": "{" }
        ]
      } },
      "rtl": { "code":   41, "label": "(", "popup": {
        "main": { "code":   62, "label": "<" },
        "relevant": [
          { "code":   93, "label": "[" },
          { "code":  125, "label": "{" }
        ]
      } }
    }]]""", Expected('('.code, "(", popups = listOf("<", "[", "{").map { it to it.first().code })
        )
    }
    
    @Test fun autoMultiTextKey() {
        assertIsExpected("""[[{ "label": "্র" }]]""", Expected(KeyCode.MULTIPLE_CODE_POINTS, "্র", text = "্র"))
    }

    @Test fun multiTextKey() { // pointless without codepoints!
        assertIsExpected("""[[{ "$": "multi_text_key", "codePoints": [2509, 2480], "label": "্র" }]]""", Expected(KeyCode.MULTIPLE_CODE_POINTS, "্র", text = "্র"))
        assertIsExpected("""[[{ "$": "multi_text_key", "codePoints": [2509, 2480], "label": "x" }]]""", Expected(KeyCode.MULTIPLE_CODE_POINTS, "x", text = "্র"))
    }

    @Test fun negativeCode() {
        assertIsExpected("""[[{ "code":   -7, "label": "delete" }]]""", Expected(-7, icon = "delete_key"))
    }

    @Test fun keyWithType() {
        assertIsExpected("""[[{ "code":   57, "label": "9", "type": "numeric" }]]""", Expected(57, "9"))
        assertIsExpected("""[[{ "code":   -7, "label": "delete", "type": "enter_editing" }]]""", Expected(-7, icon = "delete_key"))
        // -207 gets translated to -202 in Int.toKeyEventCode
        assertIsExpected("""[[{ "code": -207, "label": "view_phone2", "type": "system_gui" }]]""", Expected(-202, "?123", text = "?123"))
    }

    @Test fun spaceKey() {
        assertIsExpected("""[[{ "code":   32, "label": "space" }]]""", Expected(32, icon = "space_key"))
    }

    @Test fun invalidKeys() {
        assertThrows(_root_ide_package_.org.oscar.kb.keyboard.internal.KeySpecParser.KeySpecParserError::class.java) {
            RawKeyboardParser.parseJsonString("""[[{ "label": "!icon/clipboard_action_key" }]]""").map { it.mapNotNull { it.compute(params)?.toKeyParams(params) } }
        }
    }

    @Test fun popupWithCodeAndLabel() {
        val key = RawKeyboardParser.parseJsonString("""[[{ "label": "w", "popup": {
          "main": { "code":   55, "label": "!" }
    } }]]""").map { it.mapNotNull { it.compute(params) } }.flatten().single()
        assertEquals("!", key.toKeyParams(params).mPopupKeys?.first()?.mLabel)
        assertEquals('7'.code, key.toKeyParams(params).mPopupKeys?.first()?.mCode)
    }

    @Test fun popupWithCodeAndIcon() {
        val key = RawKeyboardParser.parseJsonString("""[[{ "label": "w", "popup": {
          "main": { "code":   55, "label": "!icon/clipboard_action_key" }
    } }]]""").map { it.mapNotNull { it.compute(params) } }.flatten().single()
        assertEquals(null, key.toKeyParams(params).mPopupKeys?.first()?.mLabel)
        assertEquals("clipboard_action_key", key.toKeyParams(params).mPopupKeys?.first()?.mIconName)
        assertEquals('7'.code, key.toKeyParams(params).mPopupKeys?.first()?.mCode)
    }

    @Test fun popupToolbarKey() {
        val key = RawKeyboardParser.parseJsonString("""[[{ "label": "x", "popup": {
          "main": { "label": "undo" }
    } }]]""").map { it.mapNotNull { it.compute(params) } }.flatten().single()
        assertEquals(null, key.toKeyParams(params).mPopupKeys?.first()?.mLabel)
        assertEquals("undo", key.toKeyParams(params).mPopupKeys?.first()?.mIconName)
        assertEquals(KeyCode.UNDO, key.toKeyParams(params).mPopupKeys?.first()?.mCode)
    }

    @Test fun popupKeyWithIconAndImplicitText() {
        val key = RawKeyboardParser.parseJsonString("""[[{ "label": "a", "popup": { "relevant": [
       { "label": "!icon/go_key|aa" }
      ]
    } }]]""").map { it.mapNotNull { it.compute(params) } }.flatten().single()
        assertEquals(null, key.toKeyParams(params).mPopupKeys?.first()?.mLabel)
        assertEquals("go_key", key.toKeyParams(params).mPopupKeys?.first()?.mIconName)
        assertEquals(KeyCode.MULTIPLE_CODE_POINTS, key.toKeyParams(params).mPopupKeys?.first()?.mCode)
        assertEquals("aa", key.toKeyParams(params).mPopupKeys?.first()?.mOutputText)

        val key2 = RawKeyboardParser.parseJsonString("""[[{ "label": "a", "popup": { "relevant": [
       { "label": "!icon/go_key|" }
      ]
    } }]]""").map { it.mapNotNull { it.compute(params) } }.flatten().single()
        assertEquals(null, key2.toKeyParams(params).mPopupKeys?.first()?.mLabel)
        assertEquals("go_key", key2.toKeyParams(params).mPopupKeys?.first()?.mIconName)
        assertEquals(KeyCode.MULTIPLE_CODE_POINTS, key2.toKeyParams(params).mPopupKeys?.first()?.mCode)
        assertEquals("", key2.toKeyParams(params).mPopupKeys?.first()?.mOutputText)
    }

    // output text is null here, maybe should be changed?
    @Test fun popupKeyWithIconAndCodeAndImplicitText() {
        val key = RawKeyboardParser.parseJsonString("""[[{ "label": "a", "popup": { "relevant": [
       { "label": "!icon/go_key|", "code": 55 }
      ]
    } }]]""").map { it.mapNotNull { it.compute(params) } }.flatten().single()
        assertEquals(null, key.toKeyParams(params).mPopupKeys?.first()?.mLabel)
        assertEquals("go_key", key.toKeyParams(params).mPopupKeys?.first()?.mIconName)
        assertEquals(55, key.toKeyParams(params).mPopupKeys?.first()?.mCode)
        assertEquals(null, key.toKeyParams(params).mPopupKeys?.first()?.mOutputText)

        val key2 = RawKeyboardParser.parseJsonString("""[[{ "label": "a", "popup": { "relevant": [
       { "label": "!icon/go_key|a", "code": 55 }
      ]
    } }]]""").map { it.mapNotNull { it.compute(params) } }.flatten().single()
        assertEquals(null, key2.toKeyParams(params).mPopupKeys?.first()?.mLabel)
        assertEquals("go_key", key2.toKeyParams(params).mPopupKeys?.first()?.mIconName)
        assertEquals(55, key2.toKeyParams(params).mPopupKeys?.first()?.mCode)
        assertEquals(null, key2.toKeyParams(params).mPopupKeys?.first()?.mOutputText)

        val key3 = RawKeyboardParser.parseJsonString("""[[{ "label": "a", "popup": { "relevant": [
       { "label": "!icon/go_key|aa", "code": 55 }
      ]
    } }]]""").map { it.mapNotNull { it.compute(params) } }.flatten().single()
        assertEquals(null, key3.toKeyParams(params).mPopupKeys?.first()?.mLabel)
        assertEquals("go_key", key3.toKeyParams(params).mPopupKeys?.first()?.mIconName)
        assertEquals(55, key3.toKeyParams(params).mPopupKeys?.first()?.mCode)
        assertEquals(null, key3.toKeyParams(params).mPopupKeys?.first()?.mOutputText)
    }

    @Test fun invalidPopupKeys() {
        assertThrows(_root_ide_package_.org.oscar.kb.keyboard.internal.KeySpecParser.KeySpecParserError::class.java) {
            RawKeyboardParser.parseJsonString("""[[{ "label": "a", "popup": {
          "main": { "label": "!icon/clipboard_action_key" }
    } }]]""").map { it.mapNotNull { it.compute(params)?.toKeyParams(params) } }
        }
    }

    @Test fun popupSymbolAlpha() {
        val key = RawKeyboardParser.parseJsonString("""[[{ "label": "c", "popup": {
          "main": { "code":   -10001, "label": "x" }
    } }]]""").map { it.mapNotNull { it.compute(params) } }.flatten().single()
        assertEquals("x", key.toKeyParams(params).mPopupKeys?.first()?.mLabel)
        assertEquals(-10001, key.toKeyParams(params).mPopupKeys?.first()?.mCode)
    }

    @Test fun canLoadKeyboard() {
        val editorInfo = EditorInfo()
        val subtype = createEmojiCapableAdditionalSubtype(Locale.ENGLISH, "qwerty", true)
        val (kb, keys) = buildKeyboard(editorInfo, subtype, _root_ide_package_.org.oscar.kb.keyboard.KeyboardId.ELEMENT_ALPHABET)
        assertEquals(kb.sortedKeys.size, keys.sumOf { it.size })
    }

    @Test fun `dvorak has 4 rows`() {
        val editorInfo = EditorInfo()
        val subtype = createEmojiCapableAdditionalSubtype(Locale.ENGLISH, "dvorak", true)
        val (kb, keys) = buildKeyboard(editorInfo, subtype, _root_ide_package_.org.oscar.kb.keyboard.KeyboardId.ELEMENT_ALPHABET)
        assertEquals(keys.size, 4)
    }

    @Test fun `de_DE has extra keys`() {
        val editorInfo = EditorInfo()
        val subtype = createEmojiCapableAdditionalSubtype(Locale.GERMANY, "qwertz+", true)
        val (kb, keys) = buildKeyboard(editorInfo, subtype, _root_ide_package_.org.oscar.kb.keyboard.KeyboardId.ELEMENT_ALPHABET)
        assertEquals(11, keys[0].size)
        assertEquals(11, keys[1].size)
        assertEquals(10, keys[2].size)
        val (kb2, keys2) = buildKeyboard(editorInfo, subtype, _root_ide_package_.org.oscar.kb.keyboard.KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED)
        assertEquals(11, keys2[0].size)
        assertEquals(11, keys2[1].size)
        assertEquals(10, keys2[2].size)
    }

    @Test fun `popup key count does not depend on shift for (for simple layout)`() {
        val editorInfo = EditorInfo()
        val subtype = createEmojiCapableAdditionalSubtype(Locale.ENGLISH, "qwerty", true)
        val (kb, keys) = buildKeyboard(editorInfo, subtype, _root_ide_package_.org.oscar.kb.keyboard.KeyboardId.ELEMENT_ALPHABET)
        val (kb2, keys2) = buildKeyboard(editorInfo, subtype, _root_ide_package_.org.oscar.kb.keyboard.KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED)
        assertEquals(kb.sortedKeys.size, kb2.sortedKeys.size)
        keys.forEachIndexed { i, kpList -> kpList.forEachIndexed { j, kp ->
            assertEquals(kp.mPopupKeys?.size, keys2[i][j].mPopupKeys?.size)
        } }
        kb.sortedKeys.forEachIndexed { index, key ->
            assertEquals(key.popupKeys?.size, kb2.sortedKeys[index].popupKeys?.size)
        }
    }

    private data class Expected(val code: Int, val label: String? = null, val icon: String? = null, val text: String? = null, val popups: List<Pair<String?, Int>>? = null)

    private fun assertIsExpected(json: String, expected: Expected) {
        assertAreExpected(json, listOf(expected))
    }

    private fun assertAreExpected(json: String, expected: List<Expected>) {
        val keys = RawKeyboardParser.parseJsonString(json).map { it.mapNotNull { it.compute(params) } }.flatten()
        keys.forEachIndexed { index, keyData ->
            println("data: key ${keyData.label}: code ${keyData.code}, popups: ${keyData.popup.getPopupKeyLabels(params)}")
            val keyParams = keyData.toKeyParams(params)
            println("params: key ${keyParams.mLabel}: code ${keyParams.mCode}, popups: ${keyParams.mPopupKeys?.toList()}")
            assertEquals(expected[index].label, keyParams.mLabel)
            assertEquals(expected[index].icon, keyParams.mIconName)
            assertEquals(expected[index].code, keyParams.mCode)
            // todo (later): what's wrong with popup order?
            assertEquals(expected[index].popups?.sortedBy { it.first }, keyParams.mPopupKeys?.mapNotNull { it.mLabel to it.mCode }?.sortedBy { it.first })
            assertEquals(expected[index].text, keyParams.outputText)
            assertTrue(checkKeys(listOf(listOf(keyParams))))
        }
    }

    private fun buildKeyboard(editorInfo: EditorInfo, subtype: InputMethodSubtype, elementId: Int): Pair<_root_ide_package_.org.oscar.kb.keyboard.Keyboard, List<List<KeyParams>>> {
        val layoutParams = _root_ide_package_.org.oscar.kb.keyboard.KeyboardLayoutSet.Params()
        val editorInfoField = _root_ide_package_.org.oscar.kb.keyboard.KeyboardLayoutSet.Params::class.java.getDeclaredField("mEditorInfo").apply { isAccessible = true }
        editorInfoField.set(layoutParams, editorInfo)
        val subtypeField = _root_ide_package_.org.oscar.kb.keyboard.KeyboardLayoutSet.Params::class.java.getDeclaredField("mSubtype").apply { isAccessible = true }
        subtypeField.set(layoutParams,
            _root_ide_package_.org.oscar.kb.latin.RichInputMethodSubtype(subtype)
        )
        val widthField = _root_ide_package_.org.oscar.kb.keyboard.KeyboardLayoutSet.Params::class.java.getDeclaredField("mKeyboardWidth").apply { isAccessible = true }
        widthField.setInt(layoutParams, 500)
        val heightField = _root_ide_package_.org.oscar.kb.keyboard.KeyboardLayoutSet.Params::class.java.getDeclaredField("mKeyboardHeight").apply { isAccessible = true }
        heightField.setInt(layoutParams, 300)

        val keysInRowsField = KeyboardBuilder::class.java.getDeclaredField("keysInRows").apply { isAccessible = true }

        val id = _root_ide_package_.org.oscar.kb.keyboard.KeyboardId(elementId, layoutParams)
        val builder = KeyboardBuilder(latinIME,
            _root_ide_package_.org.oscar.kb.keyboard.internal.KeyboardParams(_root_ide_package_.org.oscar.kb.keyboard.internal.UniqueKeysCache.NO_CACHE)
        )
        builder.load(id)
        return builder.build() to keysInRowsField.get(builder) as ArrayList<ArrayList<KeyParams>>
    }
}

@Implements(ProximityInfo::class)
class ShadowProximityInfo {
    @Implementation
    fun createNativeProximityInfo(tpc: _root_ide_package_.org.oscar.kb.keyboard.internal.TouchPositionCorrection): Long = 0
}
