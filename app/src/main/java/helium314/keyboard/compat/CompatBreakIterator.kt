// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.compat

import android.os.Build
import androidx.annotation.RequiresApi
import helium314.keyboard.latin.common.LocaleUtils
import helium314.keyboard.latin.common.WordBreakIterator
import java.util.Locale

/**
 *  BreakIterator that can use android.icu.text.BreakIterator, java.text.BreakIterator or custom implementations.
 *  Method names are related to boundaries, e.g. [first] gets the first boundary.
 */
// todo: some behavior is unexpected, maybe don't expose everything?
interface CompatBreakIterator {
    /**
     *  Somewhat weird thing, gets the "current" boundary, which is determined by previous method calls that
     *  might be called unexpectedly internally. So don't count on this returning the same after calling e.g. [isBoundary].
     *  Further, the initial value before setting a text can be anything (because we reuse instances).
     */
    fun current(): Int
    fun first(): Int
    fun following(offset: Int): Int
    fun last(): Int
    fun next(): Int
    fun next(n: Int): Int
    fun previous(): Int
    fun preceding(offset: Int): Int
    fun isBoundary(offset: Int): Boolean
    var text: CharSequence

    companion object {
        // from https://github.com/chattymin/Pebble/blob/main/pebble/src/main/java/com/chattymin/pebble/LocalBreakIterator.kt, Apache-2.0 license
        // there is more potentially useful code like String.graphemeLength (should be graphemeCount though)
        private val characterBreakIterator = ThreadLocal<CompatBreakIterator>() // maybe we want to use by locale later?

        /** gets an android.icu.text.BreakIterator if available, otherwise java.text.BreakIterator */
        // maybe also for locale if we have the need
        val CharacterInstance: CompatBreakIterator get() {
            characterBreakIterator.get()?.let { return it }
            val bi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) IcuBreakIterator(android.icu.text.BreakIterator.getCharacterInstance())
            else OldBreakIterator(java.text.BreakIterator.getCharacterInstance())
            characterBreakIterator.set(bi)
            return bi
        }

        private val wordBreakIterators = ThreadLocal<HashMap<Locale, WordBreakIterator>>()

        /** gets a WordInstance of android.icu.text.BreakIterator if available, otherwise java.text.BreakIterator */
        fun getWordInstance(locale: Locale): WordBreakIterator {
            wordBreakIterators.get()?.get(locale)?.let { return it }
            val bi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) IcuBreakIterator(android.icu.text.BreakIterator.getWordInstance(locale))
            else OldBreakIterator(java.text.BreakIterator.getWordInstance(locale))
            if (wordBreakIterators.get() == null)
                wordBreakIterators.set(hashMapOf(locale to bi))
            else wordBreakIterators.get()!![locale] = bi
            return bi
        }

        fun getAvailableLocales() =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) android.icu.text.BreakIterator.getAvailableLocales()
            else java.text.BreakIterator.getAvailableLocales()

        private val availableLocales = mutableListOf<Locale>()
        fun isLocaleAvailable(locale: Locale) = locale in availableLocales || getAvailableLocales().any {
            if (LocaleUtils.getMatchLevel(locale, it) >= LocaleUtils.LOCALE_GOOD_MATCH) {
                availableLocales.add(it)
                return@any true
            }
            false
        }
    }
}

private class OldBreakIterator(val bi: java.text.BreakIterator) : WordBreakIterator {
    override fun current() = bi.current()
    override fun first() = bi.first()
    override fun following(offset: Int) = bi.following(offset)
    override fun last() = bi.last()
    override fun next() = bi.next()
    override fun next(n: Int) = bi.next(n)
    override fun previous() = bi.previous()
    override fun preceding(offset: Int) = bi.preceding(offset)
    override fun isBoundary(offset: Int) = bi.isBoundary(offset)
    override var text: CharSequence = ""
        set(value) {
            field = value
            bi.setText(value.toString())
        }
}

@RequiresApi(Build.VERSION_CODES.N)
private class IcuBreakIterator(val bi: android.icu.text.BreakIterator) : WordBreakIterator {
    override fun current() = bi.current()
    override fun first() = bi.first()
    override fun following(offset: Int) = bi.following(offset)
    override fun last() = bi.last()
    override fun next() = bi.next()
    override fun next(n: Int) = bi.next(n)
    override fun previous() = bi.previous()
    override fun preceding(offset: Int) = bi.preceding(offset)
    override fun isBoundary(offset: Int) = bi.isBoundary(offset)
    override var text: CharSequence = ""
        set(value) {
            field = value
            bi.setText(value.toString())
        }
}
