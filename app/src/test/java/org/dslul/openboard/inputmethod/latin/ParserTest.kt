package org.dslul.openboard.inputmethod.latin

import org.dslul.openboard.inputmethod.keyboard.KeyboardId
import org.dslul.openboard.inputmethod.keyboard.KeyboardLayoutSet
import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardParams
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.MORE_KEYS_NORMAL
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.SimpleKeyboardParser
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.addLocaleKeyTextsToParams
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    ShadowInputMethodManager2::class,
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
        addLocaleKeyTextsToParams(latinIME, params, MORE_KEYS_NORMAL)
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
        )
        val wantedKeyLabels = listOf(listOf("a", "b", "c"), listOf("d", "e", "f"))
        layoutStrings.forEachIndexed { i, layout ->
            println(i)
            val keyLabels = SimpleKeyboardParser(params, latinIME).parseCoreLayout(layout).map { it.map { it.label } }
            assertEquals(wantedKeyLabels, keyLabels)
        }
    }
}
