// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin.utils

/**
 * Tokenizes strings by groupings of non-space characters, making them iterable. Note that letters,
 * punctuations, etc. are all treated the same by this construct.
 */
class SpacedTokens(phrase: String) : Iterable<String> {
    private val mPhrase = phrase
    private val mLength = phrase.length
    private val mStartPos = phrase.indexOfFirst { !Character.isWhitespace(it) }
    // the iterator should start at the first non-whitespace character

    override fun iterator() = object : Iterator<String> {
        private var startPos = mStartPos

        override fun hasNext(): Boolean {
            return startPos < mLength && startPos != -1
        }

        override fun next(): String {
            var endPos = startPos

            do if (++endPos >= mLength) break
            while (!Character.isWhitespace(mPhrase[endPos]))
            val word = mPhrase.substring(startPos, endPos)

            if (endPos < mLength) {
                do if (++endPos >= mLength) break
                while (Character.isWhitespace(mPhrase[endPos]))
            }
            startPos = endPos

            return word
        }
    }
}
