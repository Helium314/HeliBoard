// SPDX-License-Identifier: GPL-3.0-only
package org.oscar.kb.latin

import org.oscar.kb.latin.common.LocaleUtils.constructLocale
import org.oscar.kb.latin.utils.ScriptUtils.SCRIPT_CYRILLIC
import org.oscar.kb.latin.utils.ScriptUtils.SCRIPT_DEVANAGARI
import org.oscar.kb.latin.utils.ScriptUtils.SCRIPT_LATIN
import org.oscar.kb.latin.utils.ScriptUtils.script
import org.junit.Assert.assertEquals
import org.junit.Test

class ScriptUtilsTest {
    @Test fun defaultScript() {
        assertEquals(SCRIPT_LATIN, "en".constructLocale().script())
        assertEquals(SCRIPT_DEVANAGARI, "hi".constructLocale().script())
        assertEquals(SCRIPT_LATIN, "hi_zz".constructLocale().script())
        assertEquals(SCRIPT_LATIN, "sr-Latn".constructLocale().script())
        assertEquals(SCRIPT_CYRILLIC, "mk".constructLocale().script())
        assertEquals(SCRIPT_CYRILLIC, "fr-Cyrl".constructLocale().script())
    }
}
