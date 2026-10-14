// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin.common

import helium314.keyboard.compat.CompatBreakIterator
import helium314.keyboard.latin.settings.SpacingAndPunctuations
import helium314.keyboard.latin.utils.ScriptUtils
import kotlin.math.max


interface WordBreakIterator : CompatBreakIterator {
    // very simple for now
    /**
     *  Returns the boundary indices of the word at [offset], or ([offset] to [offset]) if not touching a word.
     *  If [offset] is a boundary, it tries to determine on which side we have a word.
     *  What actually counts as word differs by implementation or underlying break iterator instance.
     */
    // we could check for digits as well, but maybe later
    fun getTouchedWordBounds(offset: Int): Pair<Int, Int> {
        val preceding = preceding(offset).coerceAtLeast(0)
        var following = following(offset)
        if (following == -1) following = text.length
        if (!isBoundary(offset))
            return preceding to following
        // first try to see if we're at the end, then at the start
        if (Character.isLetter(text.codePointBefore(offset)))
            return preceding to offset
        if (Character.isLetter(text.codePointAt(offset)))
            return offset to following
        return offset to offset
    }

    companion object {
        fun getForSpacingAndPunctuations(spacingAndPunctuations: SpacingAndPunctuations) =
            if (!spacingAndPunctuations.mCurrentLanguageHasSpaces && spacingAndPunctuations.mCurrentLanguageHasSpacesOrWordIterator)
                // we don't have one for this case, so use on that ignores spacingAndPunctuations
                CompatBreakIterator.getWordInstance(spacingAndPunctuations.mLocale)
            else SpacingAndPunctuationsWordIterator(spacingAndPunctuations)
    }
}

// ideally this should be consistent with the other WordBreakIterators, except for when we want it
private class SpacingAndPunctuationsWordIterator(val spacingAndPunctuations: SpacingAndPunctuations) : WordBreakIterator {
    override var text: CharSequence = ""
        set(value) {
            field = value
            index = 0
        }
    private var index = 0
    override fun current() = index // initially we have no boundary, but apparently this is fine

    // is it really that useless?
    override fun first(): Int {
        index = 0
        return index
    }

    override fun following(offset: Int): Int {
        index = offset
        if (index == -1) return index
        while (index < text.length) {
            val cp = text.codePointAt(index)
            index += Character.charCount(cp)
            if (isBoundary(index)) return index
        }
        index = -1
        return index
    }

    // is it really that useless?
    override fun last(): Int {
        index = text.length
        return index
    }

    override fun next() = following(index)

    override fun next(n: Int): Int {
        repeat(n) { next() }
        return index
    }

    override fun previous() = preceding(index)

    override fun preceding(offset: Int): Int {
        index = offset
        while (index > 0) {
            val cp = text.codePointBefore(index)
            index -= Character.charCount(cp)
            if (isBoundary(index)) return index
        }
        index = -1
        return index
    }

    // todo: this should be consistent with getTouchedWordBounds, ideally with some testing
    override fun isBoundary(offset: Int): Boolean {
        if (offset == 0 || offset == text.length) return true // end is always a boundary
        val previousCp = text.codePointBefore(offset)
        val nextCp = text.codePointAt(offset)
        val prevIsLetter = previousCp.isLetter
        val nextIsLetter = nextCp.isLetter
        if (prevIsLetter && nextIsLetter) return false

        val prevIsWhitespace = Character.isWhitespace(previousCp)
        val nextIsWhitespace = Character.isWhitespace(nextCp)
        if (prevIsWhitespace != nextIsWhitespace || !spacingAndPunctuations.mCurrentLanguageHasSpaces)
            return true // whitespace / non-whitespace is always a boundary
        if (prevIsWhitespace && nextIsWhitespace)
            return false // consecutive whitespaces are not a boundary

        // we defer more complicated cases to the probably slower getTouchedWordBounds, so we get consistent behavior
        val (start, end) = getTouchedWordBounds(offset)
        return start == offset || end == offset
    }

    // this is what was previously in StringUtils.getTouchedWordRange
    //  for now we keep it the same, but it should definitely be improved
    override fun getTouchedWordBounds(offset: Int): Pair<Int, Int> {
        val before = text.substring(0, offset)
        val after = text.substring(offset)
        // Going backward, find the first breaking point (separator)
        var startIndexInBefore = before.length
        var endIndexInAfter = -1 // todo: clarify why might we want to set it when checking before
        loopOverCodePointsBackwards(before) { codePoint, cpLength ->
            if (isPartOfCompositionForScript(codePoint, spacingAndPunctuations) || codePoint in combiningRange) {
                startIndexInBefore -= cpLength
                return@loopOverCodePointsBackwards false
            }
            if (Character.isWhitespace(codePoint) || !spacingAndPunctuations.mCurrentLanguageHasSpaces)
                return@loopOverCodePointsBackwards true
            // continue to the next whitespace and see whether this contains a sometimesWordConnector
            for (i in startIndexInBefore - 1 downTo 0) {
                val c = before[i]
                if (spacingAndPunctuations.isSometimesWordConnector(c.code)) {
                    // if yes -> whitespace is the index
                    startIndexInBefore = max(StringUtils.charIndexOfLastWhitespace(before), 0)
                    val firstSpaceAfter = StringUtils.charIndexOfFirstWhitespace(after)
                    endIndexInAfter = if (firstSpaceAfter == -1) after.length else firstSpaceAfter - 1
                    return@loopOverCodePointsBackwards true
                } else if (Character.isWhitespace(c)) {
                    // if no, just break normally
                    return@loopOverCodePointsBackwards true
                }
            }
            true
        }

        // Find last word separator after the cursor
        if (endIndexInAfter == -1) {
            endIndexInAfter = 0
            loopOverCodePoints(after) { codePoint, cpLength ->
                if (isPartOfCompositionForScript(codePoint, spacingAndPunctuations) || codePoint in combiningRange) {
                    endIndexInAfter += cpLength
                    return@loopOverCodePoints false
                }
                if (Character.isWhitespace(codePoint) || !spacingAndPunctuations.mCurrentLanguageHasSpaces)
                    return@loopOverCodePoints true
                // continue to the next whitespace and see whether this contains a sometimesWordConnector
                for (i in endIndexInAfter..<after.length) {
                    val c = after[i]
                    if (spacingAndPunctuations.isSometimesWordConnector(c.code)) {
                        // if yes -> whitespace is next to the index
                        startIndexInBefore = max(StringUtils.charIndexOfLastWhitespace(before), 0)
                        val firstSpaceAfter = StringUtils.charIndexOfFirstWhitespace(after)
                        endIndexInAfter = if (firstSpaceAfter == -1) after.length else firstSpaceAfter - 1
                        return@loopOverCodePoints true
                    } else if (Character.isWhitespace(c)) {
                        // if no, just break normally
                        return@loopOverCodePoints true
                    }
                }
                true
            }
        }

        // strip text before "//" (i.e. ignore http and other protocols)
        val beforeConsideringStart = before.substring(startIndexInBefore, before.length)
        val protocolEnd = beforeConsideringStart.lastIndexOf("//")
        if (protocolEnd != -1) startIndexInBefore += protocolEnd + 1

        // we don't want the end characters to be word separators
        while (endIndexInAfter > 0 && spacingAndPunctuations.isWordSeparator(after[endIndexInAfter - 1].code)) {
            --endIndexInAfter
        }
        while (startIndexInBefore < before.length && spacingAndPunctuations.isWordSeparator(before[startIndexInBefore].code)) {
            ++startIndexInBefore
        }
        return startIndexInBefore to (before.length + endIndexInAfter)
    }

    private val Int.isLetter get() =
        (!spacingAndPunctuations.isWordSeparator(this) && ScriptUtils.isLetterPartOfScript(this, spacingAndPunctuations.mScript))
            || this in combiningRange

    // need to be more careful here, because a wordConnector at the start should not be part of composition
    private fun isPartOfCompositionForScript(codePoint: Int, spacingAndPunctuations: SpacingAndPunctuations) =
        spacingAndPunctuations.isWordConnector(codePoint) // We always consider word connectors part of compositions.
            // Otherwise, it's part of composition if it's part of script and not a separator.
            || (!spacingAndPunctuations.isWordSeparator(codePoint) && ScriptUtils.isLetterPartOfScript(codePoint, spacingAndPunctuations.mScript))
}
