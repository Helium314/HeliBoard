// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import helium314.keyboard.latin.common.StringUtils
import org.junit.Test

// todo: actually this test could/should be significantly expanded...
class StringUtilsTest {
    @Test fun `not inside double quotes without quotes`() {
        assert(!StringUtils.isInsideDoubleQuoteOrAfterDigit("hello yes"))
    }

    @Test fun `inside double quotes after opening a quote`() {
        assert(StringUtils.isInsideDoubleQuoteOrAfterDigit("hello \"yes"))
    }

    @Test fun `inside double quotes with quote at start`() {
        assert(StringUtils.isInsideDoubleQuoteOrAfterDigit("\"hello yes"))
    }

    // maybe this is not that bad, should be correct after entering next text
    @Test fun `not inside double quotes directly after closing quote`() {
        assert(!StringUtils.isInsideDoubleQuoteOrAfterDigit("hello \"yes\""))
    }

    @Test fun `not inside double quotes after closing quote`() {
        assert(!StringUtils.isInsideDoubleQuoteOrAfterDigit("hello \"yes\" "))
    }

    @Test fun `not inside double quotes after closing quote followed by comma`() {
        assert(!StringUtils.isInsideDoubleQuoteOrAfterDigit("hello \"yes\", "))
    }

    @Test fun `inside double quotes after opening another quote`() {
        assert(StringUtils.isInsideDoubleQuoteOrAfterDigit("hello \"yes\" \"h"))
    }

    @Test fun `inside double quotes after opening another quote with closing quote followed by comma`() {
        assert(StringUtils.isInsideDoubleQuoteOrAfterDigit("hello \"yes\", \"h"))
    }

    // todo: add tests for emoji detection?
    //  could help towards fully fixing https://github.com/Helium314/openboard/issues/22
    //  though this might be tricky, as some emojis will show as one on new Android versions, and
    //  as two on older versions
}
