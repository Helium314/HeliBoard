// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import kotlin.test.Test
import helium314.keyboard.latin.makedict.DictIO
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class) // necessary because there is logging in writeBinaryDictionary
class DictionaryTest {
    // this is mostly for checking internals
    @Test fun readCombined() {
        val dict = DictIO.readCombinedDictionary("src/test/resources/somelist.combined")
        val pp = dict.first { it.mWord == "patty-pan" }
        assert(pp.mIsPossiblyOffensive)
        assertEquals(15, pp.probability)
        val i = dict.first { it.mWord == "i" }
        assert(!i.mIsPossiblyOffensive)
        assertEquals(15, i.mShortcutTargets!!.single().probability)
        assertEquals(16, i.probability)
        val ag = dict.first { it.mWord == "again" }
        assertEquals(168, ag.probability)
        assertEquals("when", ag.bigrams.first().mWord)
        assertEquals(1, ag.bigrams.first().mProbabilityInfo.mProbability)
        assertEquals("when", ag.mNgrams.first().mTargetWord.mWord)
    }

    @Test fun readAndWrite() {
        val dict = DictIO.readCombinedDictionary("src/test/resources/somelist.combined")
        val tempFile = "src/test/resources/somelist.temp"
        DictIO.writeCombinedDictionary(tempFile, dict)
        val dict2 = DictIO.readCombinedDictionary(tempFile)
        assertEquals(dict.count(), dict2.count())
        File(tempFile).delete()
        DictIO.writeBinaryDictionary(tempFile, dict)
        // can't read it without lib...
        File(tempFile).delete()
    }
}
