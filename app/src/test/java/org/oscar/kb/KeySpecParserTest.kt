package org.oscar.kb


import org.junit.Assert.assertEquals
import org.junit.Test
import org.oscar.kb.keyboard.internal.keyboard_parser.floris.KeyCode

class KeySpecParserTest {
    @Test fun label() {
        assertEquals("a", _root_ide_package_.org.oscar.kb.keyboard.internal.KeySpecParser.getLabel("a"))
        assertEquals("a", _root_ide_package_.org.oscar.kb.keyboard.internal.KeySpecParser.getLabel("a|b"))
        assertEquals("hey", _root_ide_package_.org.oscar.kb.keyboard.internal.KeySpecParser.getLabel("hey|there"))
        assertEquals("a|b", _root_ide_package_.org.oscar.kb.keyboard.internal.KeySpecParser.getLabel("a|b|c"))
        assertEquals("a|b", _root_ide_package_.org.oscar.kb.keyboard.internal.KeySpecParser.getLabel("a\\|b"))
        assertEquals("a|b", _root_ide_package_.org.oscar.kb.keyboard.internal.KeySpecParser.getLabel("a\\|b|c"))
        assertEquals("a|b|c", _root_ide_package_.org.oscar.kb.keyboard.internal.KeySpecParser.getLabel("a\\|b|c|d"))
    }

    @Test fun code() {
        assertEquals('a'.code, _root_ide_package_.org.oscar.kb.keyboard.internal.KeySpecParser.getCode("a"))
        assertEquals('b'.code, _root_ide_package_.org.oscar.kb.keyboard.internal.KeySpecParser.getCode("a|b"))
        assertEquals(KeyCode.MULTIPLE_CODE_POINTS, _root_ide_package_.org.oscar.kb.keyboard.internal.KeySpecParser.getCode("hey|there"))
        assertEquals('c'.code, _root_ide_package_.org.oscar.kb.keyboard.internal.KeySpecParser.getCode("a|b|c"))
        assertEquals(KeyCode.MULTIPLE_CODE_POINTS, _root_ide_package_.org.oscar.kb.keyboard.internal.KeySpecParser.getCode("a\\|b"))
        assertEquals('c'.code, _root_ide_package_.org.oscar.kb.keyboard.internal.KeySpecParser.getCode("a\\|b|c"))
        assertEquals('d'.code, _root_ide_package_.org.oscar.kb.keyboard.internal.KeySpecParser.getCode("a\\|b|c|d"))
    }
}
