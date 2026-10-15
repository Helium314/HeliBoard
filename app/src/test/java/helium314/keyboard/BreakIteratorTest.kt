package helium314.keyboard

import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.compat.CompatBreakIterator
import helium314.keyboard.latin.App
import helium314.keyboard.latin.common.WordBreakIterator
import helium314.keyboard.latin.common.getTouchedWordRange
import helium314.keyboard.latin.settings.SpacingAndPunctuations
import helium314.keyboard.latin.utils.TextRange
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class BreakIteratorTest {
    // for testing default behavior and comparing with SpacingAndPunctuationsWordIterator
    @Test fun breakIteratorBehavior() {
        val default = CompatBreakIterator.getWordInstance(Locale.ROOT)
        val text = "hello this is me"
        default.text = text
        assertEquals(0, default.current())
        assertEquals(0, default.first())
        assertEquals(5, default.next())
        assertEquals(5, default.current())
        assertEquals(6, default.next())
        assertEquals(13, default.following(11))
        assertEquals(13, default.current())
        assertEquals(16, default.next(2))
        assertEquals(0, default.first())
        assertEquals(16, default.last())
        assertEquals(16, default.current())
        assertEquals(14, default.previous())
        assertEquals(-1, default.next(2))
        assertEquals(-1, default.next())
        assertEquals(14, default.previous()) // why?
        assertEquals(0, default.first())
        assertEquals(0, default.current())
        assertEquals(-1, default.previous())
        assertEquals(-1, default.previous())
        assertEquals(-1, default.next()) // why? compare with the why above...
        assertEquals(true, default.isBoundary(14))
        assertEquals(14, default.current())
        assertEquals(false, default.isBoundary(8))
        assertEquals(10, default.current()) // this is weird...

        val wbi = WordBreakIterator.getForSpacingAndPunctuations(SpacingAndPunctuations(ApplicationProvider.getApplicationContext<App>().resources, false, Locale.ROOT))
        wbi.text = text
        assertEquals(0, wbi.current())
        assertEquals(0, wbi.first())
        assertEquals(5, wbi.next())
        assertEquals(5, wbi.current())
        assertEquals(6, wbi.next())
        assertEquals(13, wbi.following(11))
        assertEquals(13, wbi.current())
        assertEquals(16, wbi.next(2))
        assertEquals(0, wbi.first())
        assertEquals(16, wbi.last())
        assertEquals(16, wbi.current())
        assertEquals(14, wbi.previous())
        assertEquals(-1, wbi.next(2))
        assertEquals(-1, wbi.next())
//        assertEquals(14, wbi.previous()) // better
        assertEquals(0, wbi.first())
        assertEquals(0, wbi.current())
        assertEquals(-1, wbi.previous())
        assertEquals(-1, wbi.previous())
        assertEquals(-1, wbi.next())
        assertEquals(true, wbi.isBoundary(14))
//        assertEquals(14, wbi.current()) // does it matter?
        assertEquals(false, wbi.isBoundary(8))
//        assertEquals(10, wbi.current()) // does it matter?
    }

    @Test fun whatDoWeWant() {
        val default = CompatBreakIterator.getWordInstance(Locale.ROOT)
        val wbi = WordBreakIterator.getForSpacingAndPunctuations(SpacingAndPunctuations(ApplicationProvider.getApplicationContext<App>().resources, false, Locale.ROOT))
        default.text = "don't "
        wbi.text = default.text
        assertEquals(5, default.following(2))
        assertEquals(5, wbi.following(2))
        default.text = "don'"
        wbi.text = default.text
        assertEquals(3, default.following(2))
        assertEquals(4, wbi.following(2))
        default.text = "'don't"
        wbi.text = default.text
        assertEquals(6, default.following(2))
        assertEquals(6, wbi.following(2))
        assertEquals(1, default.preceding(2))
//        assertEquals(1, wbi.preceding(2)) // todo: needs improvement
        default.text = "'don'"
        wbi.text = default.text
        assertEquals(4, default.following(2))
//        assertEquals(4, wbi.following(2)) // todo: needs improvement
        default.text = "hell-bent"
        wbi.text = default.text
        assertEquals(4, default.following(2))
        assertEquals(9, wbi.following(2))
    }

    @Test fun bla() {
        val wbi = WordBreakIterator.getForSpacingAndPunctuations(SpacingAndPunctuations(ApplicationProvider.getApplicationContext<App>().resources, false, Locale.ROOT))
        wbi.text = "no boundary in don't!"
        assertEquals(20, wbi.following(15))
        assertEquals(15 to 20, wbi.getTouchedWordBounds(16))
        wbi.text = "2 boundaries in 'don'!"
        assertEquals(17, wbi.following(16))
        assertEquals(16 to 16, wbi.getTouchedWordBounds(16))
        assertEquals(20, wbi.following(17))
        assertEquals(17 to 20, wbi.getTouchedWordBounds(17))
        assertEquals(21, wbi.following(20))
        assertEquals(17, wbi.preceding(20))
        assertEquals(17 to 20, wbi.getTouchedWordBounds(20))
        assertEquals(21 to 21, wbi.getTouchedWordBounds(21))
        assertEquals(17 to 20, wbi.getTouchedWordBounds(19))
        wbi.text = "no boundary in don'!"
        assertEquals(19, wbi.following(15))
        assertEquals(15, wbi.preceding(19))
        assertEquals(15 to 19, wbi.getTouchedWordBounds(15))
        assertEquals(15 to 19, wbi.getTouchedWordBounds(17))
        assertEquals(15 to 19, wbi.getTouchedWordBounds(19))
    }

    @Test fun moveStepsToCharCount() {
        assertEquals(3, helium314.keyboard.latin.common.moveStepsToCharCount("abcd", 3))
        assertEquals(-3, helium314.keyboard.latin.common.moveStepsToCharCount("abcd", -3))
        assertEquals(4, helium314.keyboard.latin.common.moveStepsToCharCount("abcd", 10))
        assertEquals(-4, helium314.keyboard.latin.common.moveStepsToCharCount("abcd", -10))
        assertEquals(8, helium314.keyboard.latin.common.moveStepsToCharCount("\uD83C\uDFF3️\u200D\uD83C\uDF08bcd", 3))
        assertEquals(-3, helium314.keyboard.latin.common.moveStepsToCharCount("\uD83C\uDFF3️\u200D\uD83C\uDF08bcd", -3))
        assertEquals(4, helium314.keyboard.latin.common.moveStepsToCharCount("aэ́cd", 3))
        assertEquals(-2, helium314.keyboard.latin.common.moveStepsToCharCount("abcэ́", -1))
        assertEquals(19, helium314.keyboard.latin.common.moveStepsToCharCount("H̵̛͕̞̦̰̜͍̰̥̟͆̏͂̌͑́ͅэ́cd", 1))
        assertEquals(21, helium314.keyboard.latin.common.moveStepsToCharCount("H̵̛͕̞̦̰̜͍̰̥̟͆̏͂̌͑́ͅэ́cd", 2))
        assertEquals(22, helium314.keyboard.latin.common.moveStepsToCharCount("H̵̛͕̞̦̰̜͍̰̥̟͆̏͂̌͑́ͅэ́cd", 3))
        assertEquals(23, helium314.keyboard.latin.common.moveStepsToCharCount("H̵̛͕̞̦̰̜͍̰̥̟͆̏͂̌͑́ͅэ́cd", 4))
        assertEquals(-2, helium314.keyboard.latin.common.moveStepsToCharCount("aH̵̛͕̞̦̰̜͍̰̥̟͆̏͂̌͑́ͅcэ́", -1))
        assertEquals(-3, helium314.keyboard.latin.common.moveStepsToCharCount("aH̵̛͕̞̦̰̜͍̰̥̟͆̏͂̌͑́ͅcэ́", -2))
        assertEquals(-23, helium314.keyboard.latin.common.moveStepsToCharCount("aH̵̛͕̞̦̰̜͍̰̥̟͆̏͂̌͑́ͅcэ́", -4))
        assertEquals(3, helium314.keyboard.latin.common.moveStepsToCharCount("abcd", 3))
        assertEquals(5, helium314.keyboard.latin.common.moveStepsToCharCount("\uD83C\uDFF4\u200D☠️\uD83C\uDFF3️\u200D\uD83C\uDF08", 1))
        assertEquals(-6, helium314.keyboard.latin.common.moveStepsToCharCount("\uD83C\uDFF4\u200D☠️\uD83C\uDFF3️\u200D\uD83C\uDF08", -1))
    }

    @Test fun `get touched text range`() {
        val sp = SpacingAndPunctuations(ApplicationProvider.getApplicationContext<App>().resources, false, Locale.ROOT)
        val spUrl = SpacingAndPunctuations(ApplicationProvider.getApplicationContext<App>().resources, true, Locale.ROOT)
        checkTextRange("blabla this is v", "ery good", sp, 15, 19)
        checkTextRange(".hel", "lo...", sp, 1, 6)
        checkTextRange("(hi", ")", sp, 1, 3)
        checkTextRange("", "word", sp, 0, 4)

        checkTextRange("mail: blorb@", "florb.com or", sp, 12, 17)
        checkTextRange("mail: blorb@", "florb.com or", spUrl, 6, 21)
        checkTextRange("mail: blor", "b@florb.com or", sp, 6, 11)
        checkTextRange("mail: blor", "b@florb.com or", spUrl, 6, 21)
        checkTextRange("mail: blorb@f", "lorb.com or", sp, 12, 17)
        checkTextRange("mail: blorb@f", "lorb.com or", spUrl, 6, 21)

        checkTextRange("http://exam", "ple.com", sp, 7, 14)
        checkTextRange("http://exam", "ple.com", spUrl, 7, 18)
        checkTextRange("http://example.", "com", sp, 15, 18)
        checkTextRange("http://example.", "com", spUrl, 7, 18)
        checkTextRange("htt", "p://example.com", sp, 0, 4)
        checkTextRange("htt", "p://example.com", spUrl, 0, 18)
        checkTextRange("http:/", "/example.com", sp, 6, 6)
        checkTextRange("http:/", "/example.com", spUrl, 0, 18)

        checkTextRange("..", ".", spUrl, 2, 2)
        checkTextRange("...", "", spUrl, 3, 3)

        val spCyrl = SpacingAndPunctuations(ApplicationProvider.getApplicationContext<App>().resources,
            false, Locale.forLanguageTag("ru"))
        checkTextRange("", "э́є", spCyrl,0, 3)
        checkTextRange("э́", "є", spCyrl, 0, 3)
        checkTextRange("э", "́", spCyrl, 0, 2)

        // todo: these are bad cases of url detection
        //  also: sometimesWordConnectors are for URL and should be named accordingly
        checkTextRange("@@@", "@@@", spUrl, 0, 6)
        checkTextRange("a...", "", spUrl, 0, 4)
        checkTextRange("@@@", "", spUrl, 0, 3)
    }

    private fun checkTextRange(before: String, after: String, sp: SpacingAndPunctuations, wordStart: Int, wordEnd: Int) {
        val got = getTouchedWordRange(before, after, sp)
        val wanted = TextRange(before + after, wordStart, wordEnd, before.length, false)
        assertEquals(wanted, got)
    }
}
