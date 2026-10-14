// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.common.codePointAt
import helium314.keyboard.latin.common.codePointBefore
import helium314.keyboard.latin.common.endsWithWordCodepoint
import helium314.keyboard.latin.common.getFullEmojiAtEnd
import helium314.keyboard.latin.common.isEmoji
import helium314.keyboard.latin.common.isSingleGrapheme
import helium314.keyboard.latin.common.nonWordCodePointAndNoSpaceBeforeCursor
import helium314.keyboard.latin.common.splitOnWhitespace
import helium314.keyboard.latin.common.stripTrailingSeparatorsAndConnectors
import helium314.keyboard.latin.settings.SpacingAndPunctuations
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

// todo: actually this test could/should be significantly expanded...
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    ShadowInputMethodManager2::class,
])
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

    @Test fun `non-word codepoints and no space`() {
        val sp = SpacingAndPunctuations(ApplicationProvider.getApplicationContext<App>().resources, false, Locale.ROOT)
        assert(!nonWordCodePointAndNoSpaceBeforeCursor("this is", sp))
        assert(!nonWordCodePointAndNoSpaceBeforeCursor("this ", sp))
        assert(!nonWordCodePointAndNoSpaceBeforeCursor("th.is ", sp))
        assert(nonWordCodePointAndNoSpaceBeforeCursor("th.is", sp))
    }

    @Test fun `is word-like at end`() {
        val sp = SpacingAndPunctuations(ApplicationProvider.getApplicationContext<App>().resources, false, Locale.ROOT)
        assert(!endsWithWordCodepoint("", sp))
        assert(endsWithWordCodepoint("don'", sp))
        assert(!endsWithWordCodepoint("hello!", sp))
        assert(!endsWithWordCodepoint("when ", sp))
        assert(!endsWithWordCodepoint("3-", sp))
        assert(!endsWithWordCodepoint("5'", sp))
        assert(!endsWithWordCodepoint("1", sp))
        assert(endsWithWordCodepoint("a-", sp))
        assert(!endsWithWordCodepoint("--", sp))
        assert(!endsWithWordCodepoint("\uD83D\uDE42", sp))
    }

    @Test fun singleGrapheme() {
        assert(!"".isSingleGrapheme)
        assert(",".isSingleGrapheme)
        assert(!"!!".isSingleGrapheme)
        assert("э́".isSingleGrapheme)
        assert(!"\uD83C\uDFF4\u200D☠️\uD83C\uDFF3️\u200D\uD83C\uDF08".isSingleGrapheme)
        assert("\uD83C\uDFF3️\u200D\uD83C\uDF08".isSingleGrapheme)
        assert("\uD83C\uDFF4\u200D☠️".isSingleGrapheme)
        assert(!" 🏼".isSingleGrapheme)
        assert(!"a🏼".isSingleGrapheme)
        assert(!"🏼🏼".isSingleGrapheme)
        assert("🏼".isSingleGrapheme)
        assert("🇦".isSingleGrapheme) // 🇦 standalone regional indicator is a single grapheme
        assert("🇩🇪".isSingleGrapheme) // 🇩🇪 flag (indicator pair) is still a single grapheme
        assert(!"🇦🇧🇨".isSingleGrapheme) // 🇦🇧🇨 three indicators are not one grapheme
    }

    @Test fun detectEmojisAtEnd() {
        assertEquals("", getFullEmojiAtEnd("\uD83C\uDF83 "))
        assertEquals("", getFullEmojiAtEnd("a"))
        assertEquals("\uD83C\uDF83", getFullEmojiAtEnd("\uD83C\uDF83"))
        assertEquals("ℹ️", getFullEmojiAtEnd("ℹ️"))
        assertEquals("ℹ️", getFullEmojiAtEnd("ℹ️ℹ️"))
        assertEquals("\uD83D\uDE22", getFullEmojiAtEnd("x\uD83D\uDE22"))
        assertEquals("", getFullEmojiAtEnd("x\uD83D\uDE22 "))
        assertEquals("\uD83C\uDFF4\u200D☠️", getFullEmojiAtEnd("ok \uD83C\uDFF4\u200D☠️"))
        assertEquals("\uD83C\uDFF3️\u200D\uD83C\uDF08", getFullEmojiAtEnd("\uD83C\uDFF3️\u200D\uD83C\uDF08"))
        assertEquals("\uD83C\uDFF3️\u200D\uD83C\uDF08", getFullEmojiAtEnd("\uD83C\uDFF4\u200D☠️\uD83C\uDFF3️\u200D\uD83C\uDF08"))
        assertEquals("\uD83C\uDFF3️\u200D⚧️", getFullEmojiAtEnd("hello there🏳️‍⚧️"))
        assertEquals("\uD83D\uDD75\uD83C\uDFFC", getFullEmojiAtEnd(" 🕵🏼"))
        assertEquals("\uD83D\uDD75\uD83C\uDFFC", getFullEmojiAtEnd("🕵🏼"))
        assertEquals("\uD83C\uDFFC", getFullEmojiAtEnd(" \uD83C\uDFFC"))
        assertEquals("1\uFE0F⃣", getFullEmojiAtEnd("1\uFE0F⃣")) // 1️⃣
        assertEquals("©\uFE0F", getFullEmojiAtEnd("©\uFE0F")) // ©️
        assertEquals("", getFullEmojiAtEnd("\u200D"))
        assertEquals("", getFullEmojiAtEnd("a\u200D"))
        assertEquals("\uD83D\uDE22", getFullEmojiAtEnd(" \u200D\uD83D\uDE22"))
    }

    @Test fun detectEmojisAtEndFails() {
        if (BuildConfig.BUILD_TYPE == "runTests") return
        // fails, but unlikely enough that we leave it unfixed (issue is that 🏼 is not a standalone emoji, but combining with 🎄 doesn't merge)
        assertEquals("\uD83C\uDFFC", getFullEmojiAtEnd("\uD83C\uDF84\uD83C\uDFFC")) // 🎄🏼
    }

    @Test fun isEmojiDetectsSingleEmojis() {
        assert(isEmoji("🎄"))
        assert(!isEmoji("🎄🎄"))
        assert(!isEmoji("🎄🏼"))
        assert(isEmoji("🏼")) // actually this is not a standalone emoji...
        assert(!isEmoji("a🎄"))
        assert(isEmoji("🖐️"))
        assert(isEmoji("🖐🏾"))
        assert(!isEmoji("🖐🏾🏼"))
    }

    @Test fun isEmojiDetectsAllAvailableEmojis() {
        val ctx = ApplicationProvider.getApplicationContext<App>()
        val allEmojis = ctx.assets.list("emoji")!!.flatMap {
            if (it == "minApi.txt" || it == "EMOTICONS.txt") return@flatMap emptyList()
            ctx.assets.open("emoji/$it").reader().readLines()
        }.flatMap { it.splitOnWhitespace() }

        val brokenDetectionAtStart = listOf("〰️", "〽️", "©️", "®️", "#️⃣", "*️⃣", "0️⃣", "1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣", "㊗️", "㊙️")
        allEmojis.forEach {
            assert(isEmoji(it))
            assert(StringUtils.mightBeEmoji(it.codePointBefore(it.length)))
            if (it !in brokenDetectionAtStart)
                assert(StringUtils.mightBeEmoji(it.codePointAt(0)))
        }
    }

    @Test fun `strip trailing separators and connectors`() {
        val ctx = ApplicationProvider.getApplicationContext<App>()
        val svfs = SpacingAndPunctuations(ctx.resources, false, Locale.ROOT)
        assertEquals("word", stripTrailingSeparatorsAndConnectors("word", svfs))
        assertEquals("word", stripTrailingSeparatorsAndConnectors("word)", svfs))
        assertEquals("word", stripTrailingSeparatorsAndConnectors("word\"", svfs))
        assertEquals("word", stripTrailingSeparatorsAndConnectors("word:", svfs))
        assertEquals("wor:d", stripTrailingSeparatorsAndConnectors("wor:d:", svfs))
        assertEquals("", stripTrailingSeparatorsAndConnectors(":", svfs))
        assertEquals("word", stripTrailingSeparatorsAndConnectors("word-", svfs))
        assertEquals("word", stripTrailingSeparatorsAndConnectors("word'", svfs))
        assertEquals("word", stripTrailingSeparatorsAndConnectors("word'-'", svfs))
    }
}
