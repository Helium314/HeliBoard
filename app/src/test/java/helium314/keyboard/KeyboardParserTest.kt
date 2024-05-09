// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodSubtype
import com.android.inputmethod.keyboard.ProximityInfo
import helium314.keyboard.keyboard.Key.KeyParams
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.internal.KeyboardBuilder
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.TouchPositionCorrection
import helium314.keyboard.keyboard.internal.UniqueKeysCache
import helium314.keyboard.keyboard.internal.keyboard_parser.JsonKeyboardParser
import helium314.keyboard.keyboard.internal.keyboard_parser.POPUP_KEYS_NORMAL
import helium314.keyboard.keyboard.internal.keyboard_parser.SimpleKeyboardParser
import helium314.keyboard.keyboard.internal.keyboard_parser.addLocaleKeyTextsToParams
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyType
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.MultiTextKeyData
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.latin.utils.AdditionalSubtypeUtils.createEmojiCapableAdditionalSubtype
import helium314.keyboard.latin.utils.POPUP_KEYS_LAYOUT
import org.junit.Assert.assertEquals
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
    private lateinit var latinIME: LatinIME

    @Before
    fun setUp() {
        latinIME = Robolectric.setupService(LatinIME::class.java)
        ShadowLog.setupLogging()
        ShadowLog.stream = System.out
    }

    // todo: add more tests
    //  (popup) keys with label and code
    //  (popup) keys with icon
    //  (popup) keys with that are essentially toolbar keys (yes, this should work at some point!)
    //  correct background type, depending on key type and maybe sth else

    @Test fun simpleParser() {
        val params = KeyboardParams()
        params.mId = KeyboardLayoutSet.getFakeKeyboardId(KeyboardId.ELEMENT_ALPHABET)
        addLocaleKeyTextsToParams(latinIME, params, POPUP_KEYS_NORMAL)
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
            val keyLabels = SimpleKeyboardParser(params, latinIME).parseCoreLayout(layout).map { it.map { it.label } }
            assertEquals(wantedKeyLabels, keyLabels)
        }
    }

    @Test fun jsonParser() {
        val params = KeyboardParams()
        params.mId = KeyboardLayoutSet.getFakeKeyboardId(KeyboardId.ELEMENT_ALPHABET)
        params.mPopupKeyTypes.add(POPUP_KEYS_LAYOUT)
        addLocaleKeyTextsToParams(latinIME, params, POPUP_KEYS_NORMAL)
        data class Expected(val label: String, val text: String?, val code: Int, val popups: List<String>?)
        val expected = listOf(
            Expected("a", null, 'a'.code, null),
            Expected("a", null, 'a'.code, null),
            Expected("$", null, '$'.code, listOf("£", "€", "¢", "¥", "₱")),
            Expected("$", null, '¥'.code, listOf("£", "€", "¢", "¥", "₱")),
            Expected("i", null, 105, null),
            Expected("্র", "্র", KeyCode.MULTIPLE_CODE_POINTS, null),
            Expected("x", "্র", KeyCode.MULTIPLE_CODE_POINTS, null),
            Expected(";", null, ';'.code, listOf(":")),
            Expected(".", null, '.'.code, listOf(">")),
            Expected("'", null, '\''.code, listOf("!", "\"")),
            Expected("9", null, '9'.code, null), // todo (later): also should have different background or whatever is related to type
            Expected("", null, -7, null), // todo: expect an icon
            Expected("?123", null, -207, null),
            Expected("", null, ' '.code, null),
            Expected("(", null, '('.code, listOf("<", "[", "{")),
            Expected("$", null, '$'.code, listOf("£", "₱", "€", "¢", "¥")),
            Expected("p", null, 'p'.code, null),
        )
        val layoutString = """
[
  [
    { "$": "auto_text_key" "label": "a" },
    { "$": "text_key" "label": "a" },
    { "label": "$$$" },
    { "label": "$$$", code: -805 },
    { "$": "case_selector",
      "lower": { "code":  105, "label": "i" },
      "upper": { "code":  304, "label": "İ" }
    },
    { "$": "multi_text_key", "codePoints": [2509, 2480], "label": "্র" },
    { "$": "multi_text_key", "codePoints": [2509, 2480], "label": "x" },
    { "$": "case_selector",
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
    },
    { "$": "shift_state_selector",
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
    },
    { "$": "shift_state_selector",
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
    },
    { "code":   57, "label": "9", "type": "numeric" },
    { "code":   -7, "label": "delete", "type": "enter_editing" },
    { "code": -207, "label": "view_phone2", "type": "system_gui" },
    { "code":   32, "label": "space" },
    { "$": "layout_direction_selector",
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
    },
    { "code": -801, "label": "currency_slot_1", "popup": {
      "main": { "code": -802, "label": "currency_slot_2" },
      "relevant": [
        { "code": -806, "label": "currency_slot_6" },
        { "code": -803, "label": "currency_slot_3" },
        { "code": -804, "label": "currency_slot_4" },
        { "code": -805, "label": "currency_slot_5" }
      ]
    } },
    { "label": "p" }
  ],
  [
    { "label": "q" },
    { "label": "s" },
    { "label": "d" },
    { "label": "f" },
    { "label": "g" },
    { "label": "h" },
    { "label": "j" },
    { "label": "k" },
    { "label": "l" },
    { "label": "m", "popup": { "main": { "label": "/" } } }
  ],
  [
    { "label": "w" },
    { "label": "x" },
    { "label": "c" },
    { "label": "v" },
    { "label": "b" },
    { "label": "n" }
  ]
]
        """.trimIndent()
        val keys = JsonKeyboardParser(params, latinIME).parseCoreLayout(layoutString)
        keys.first().forEachIndexed { index, keyData ->
            println("key ${keyData.label}: code ${keyData.code}, popups: ${keyData.popup.getPopupKeyLabels(params)}")
            if (keyData.type == KeyType.ENTER_EDITING || keyData.type == KeyType.SYSTEM_GUI) return@forEachIndexed // todo: currently not accepted, but should be (see below)
            val keyParams = keyData.toKeyParams(params)
            println("key ${keyParams.mLabel}: code ${keyParams.mCode}, popups: ${keyParams.mPopupKeys?.toList()}")
            if (keyParams.outputText == "space") return@forEachIndexed // todo: only works for numeric layouts... idea: parse space anywhere, and otherwise only if special type
            assertEquals(expected[index].label, keyParams.mLabel)
            assertEquals(expected[index].code, keyParams.mCode)
            assertEquals(expected[index].popups?.sorted(), keyParams.mPopupKeys?.mapNotNull { it.mLabel }?.sorted()) // todo (later): what's wrong with order?
            assertEquals(expected[index].text, keyParams.outputText)
        }
    }

    @Test fun canLoadKeyboard() {
        val editorInfo = EditorInfo()
        val subtype = createEmojiCapableAdditionalSubtype(Locale.ENGLISH, "qwerty", true)
        val (kb, keys) = buildKeyboard(editorInfo, subtype, KeyboardId.ELEMENT_ALPHABET)
        assertEquals(kb.sortedKeys.size, keys.sumOf { it.size })
    }

    @Test fun `dvorak has 4 rows`() {
        val editorInfo = EditorInfo()
        val subtype = createEmojiCapableAdditionalSubtype(Locale.ENGLISH, "dvorak", true)
        val (kb, keys) = buildKeyboard(editorInfo, subtype, KeyboardId.ELEMENT_ALPHABET)
        assertEquals(keys.size, 4)
    }

    @Test fun `de_DE has extra keys`() {
        val editorInfo = EditorInfo()
        val subtype = createEmojiCapableAdditionalSubtype(Locale.GERMANY, "qwertz+", true)
        val (kb, keys) = buildKeyboard(editorInfo, subtype, KeyboardId.ELEMENT_ALPHABET)
        assertEquals(keys[0].size, 11)
        assertEquals(keys[1].size, 11)
        assertEquals(keys[2].size, 10)
        val (kb2, keys2) = buildKeyboard(editorInfo, subtype, KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED)
        assertEquals(keys2[0].size, 11)
        assertEquals(keys2[1].size, 11)
        assertEquals(keys2[2].size, 10)
    }

    @Test fun `popup key count does not depend on shift for (for simple layout)`() {
        val editorInfo = EditorInfo()
        val subtype = createEmojiCapableAdditionalSubtype(Locale.ENGLISH, "qwerty", true)
        val (kb, keys) = buildKeyboard(editorInfo, subtype, KeyboardId.ELEMENT_ALPHABET)
        val (kb2, keys2) = buildKeyboard(editorInfo, subtype, KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED)
        assertEquals(kb.sortedKeys.size, kb2.sortedKeys.size)
        keys.forEachIndexed { i, kpList -> kpList.forEachIndexed { j, kp ->
            assertEquals(kp.mPopupKeys?.size, keys2[i][j].mPopupKeys?.size)
        } }
        kb.sortedKeys.forEachIndexed { index, key ->
            assertEquals(key.popupKeys?.size, kb2.sortedKeys[index].popupKeys?.size)
        }
    }

    private fun buildKeyboard(editorInfo: EditorInfo, subtype: InputMethodSubtype, elementId: Int): Pair<Keyboard, List<List<KeyParams>>> {
        val layoutParams = KeyboardLayoutSet.Params()
        val editorInfoField = KeyboardLayoutSet.Params::class.java.getDeclaredField("mEditorInfo").apply { isAccessible = true }
        editorInfoField.set(layoutParams, editorInfo)
        val subtypeField = KeyboardLayoutSet.Params::class.java.getDeclaredField("mSubtype").apply { isAccessible = true }
        subtypeField.set(layoutParams, RichInputMethodSubtype(subtype))
        val widthField = KeyboardLayoutSet.Params::class.java.getDeclaredField("mKeyboardWidth").apply { isAccessible = true }
        widthField.setInt(layoutParams, 500)
        val heightField = KeyboardLayoutSet.Params::class.java.getDeclaredField("mKeyboardHeight").apply { isAccessible = true }
        heightField.setInt(layoutParams, 300)

        val keysInRowsField = KeyboardBuilder::class.java.getDeclaredField("keysInRows").apply { isAccessible = true }

        val id = KeyboardId(elementId, layoutParams)
        val builder = KeyboardBuilder(latinIME, KeyboardParams(UniqueKeysCache.NO_CACHE))
        builder.load(id)
        return builder.build() to keysInRowsField.get(builder) as ArrayList<ArrayList<KeyParams>>
    }
}

@Implements(ProximityInfo::class)
class ShadowProximityInfo {
    @Implementation
    fun createNativeProximityInfo(tpc: TouchPositionCorrection): Long = 0
}
