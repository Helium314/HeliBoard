// SPDX-License-Identifier: GPL-3.0-only
package org.oscar.kb.latin

import org.oscar.kb.latin.common.getFullEmojiAtEnd
import org.junit.Assert.assertEquals
import org.junit.Test
import org.oscar.kb.latin.common.StringUtils

// todo: actually this test could/should be significantly expanded...
class StringUtilsTest {
    @Test fun `not inside double quotes without quotes`() {
        assert(!_root_ide_package_.org.oscar.kb.latin.common.StringUtils.isInsideDoubleQuoteOrAfterDigit("hello yes"))
    }

    @Test fun `inside double quotes after opening a quote`() {
        assert(_root_ide_package_.org.oscar.kb.latin.common.StringUtils.isInsideDoubleQuoteOrAfterDigit("hello \"yes"))
    }

    @Test fun `inside double quotes with quote at start`() {
        assert(_root_ide_package_.org.oscar.kb.latin.common.StringUtils.isInsideDoubleQuoteOrAfterDigit("\"hello yes"))
    }

    // maybe this is not that bad, should be correct after entering next text
    @Test fun `not inside double quotes directly after closing quote`() {
        assert(!_root_ide_package_.org.oscar.kb.latin.common.StringUtils.isInsideDoubleQuoteOrAfterDigit("hello \"yes\""))
    }

    @Test fun `not inside double quotes after closing quote`() {
        assert(!_root_ide_package_.org.oscar.kb.latin.common.StringUtils.isInsideDoubleQuoteOrAfterDigit("hello \"yes\" "))
    }

    @Test fun `not inside double quotes after closing quote followed by comma`() {
        assert(!_root_ide_package_.org.oscar.kb.latin.common.StringUtils.isInsideDoubleQuoteOrAfterDigit("hello \"yes\", "))
    }

    @Test fun `inside double quotes after opening another quote`() {
        assert(_root_ide_package_.org.oscar.kb.latin.common.StringUtils.isInsideDoubleQuoteOrAfterDigit("hello \"yes\" \"h"))
    }

    @Test fun `inside double quotes after opening another quote with closing quote followed by comma`() {
        assert(_root_ide_package_.org.oscar.kb.latin.common.StringUtils.isInsideDoubleQuoteOrAfterDigit("hello \"yes\", \"h"))
    }

    @Test fun detectEmojisAtEnd() {
        assertEquals("\uD83C\uDF83", getFullEmojiAtEnd("\uD83C\uDF83"))
        assertEquals("ℹ️", getFullEmojiAtEnd("ℹ️"))
        assertEquals("ℹ️", getFullEmojiAtEnd("ℹ️ℹ️"))
        assertEquals("\uD83D\uDE22", getFullEmojiAtEnd("x\uD83D\uDE22"))
        assertEquals("", getFullEmojiAtEnd("x\uD83D\uDE22 "))
        assertEquals("\uD83C\uDFF4\u200D☠️", getFullEmojiAtEnd("ok \uD83C\uDFF4\u200D☠️"))
        assertEquals("\uD83C\uDFF3️\u200D\uD83C\uDF08", getFullEmojiAtEnd("\uD83C\uDFF3️\u200D\uD83C\uDF08"))
        assertEquals("\uD83C\uDFF3️\u200D\uD83C\uDF08", getFullEmojiAtEnd("\uD83C\uDFF4\u200D☠️\uD83C\uDFF3️\u200D\uD83C\uDF08"))
        assertEquals("\uD83C\uDFF3️\u200D⚧️", getFullEmojiAtEnd("hello there🏳️‍⚧️"))
    }

    // todo: add tests for emoji detection?
    //  could help towards fully fixing https://github.com/Helium314/HeliBoard/issues/22
    //  though this might be tricky, as some emojis will show as one on new Android versions, and
    //  as two on older versions
}
