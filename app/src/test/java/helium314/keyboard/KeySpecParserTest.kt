package helium314.keyboard

import helium314.keyboard.keyboard.internal.KeySpecParser
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import kotlin.test.Test
import kotlin.test.assertEquals

class KeySpecParserTest {
    @Test fun label() {
        assertEquals("a", KeySpecParser.getLabel("a"))
        assertEquals("a", KeySpecParser.getLabel("a|b"))
        assertEquals("hey", KeySpecParser.getLabel("hey|there"))
        assertEquals("a|b", KeySpecParser.getLabel("a|b|c"))
        assertEquals("a|b", KeySpecParser.getLabel("a\\|b"))
        assertEquals("a|b", KeySpecParser.getLabel("a\\|b|c"))
        assertEquals("a|b|c", KeySpecParser.getLabel("a\\|b|c|d"))
    }

    @Test fun code() {
        assertEquals('a'.code, KeySpecParser.getCode("a"))
        assertEquals('b'.code, KeySpecParser.getCode("a|b"))
        assertEquals(KeyCode.MULTIPLE_CODE_POINTS, KeySpecParser.getCode("hey|there"))
        assertEquals('c'.code, KeySpecParser.getCode("a|b|c"))
        assertEquals(KeyCode.MULTIPLE_CODE_POINTS, KeySpecParser.getCode("a\\|b"))
        assertEquals('c'.code, KeySpecParser.getCode("a\\|b|c"))
        assertEquals('d'.code, KeySpecParser.getCode("a\\|b|c|d"))
    }
}
