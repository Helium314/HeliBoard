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
import helium314.keyboard.keyboard.internal.keyboard_parser.POPUP_KEYS_NORMAL
import helium314.keyboard.keyboard.internal.keyboard_parser.SimpleKeyboardParser
import helium314.keyboard.keyboard.internal.keyboard_parser.addLocaleKeyTextsToParams
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.latin.utils.AdditionalSubtypeUtils.createEmojiCapableAdditionalSubtype
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
