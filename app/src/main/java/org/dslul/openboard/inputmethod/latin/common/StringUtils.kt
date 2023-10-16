package org.dslul.openboard.inputmethod.latin.common

import org.dslul.openboard.inputmethod.latin.settings.SpacingAndPunctuations

fun loopOverCodePoints(s: CharSequence, run: (Int) -> Boolean) {
    val text = if (s is String) s else s.toString()
    var offset = 0
    while (offset < text.length) {
        val codepoint = text.codePointAt(offset)
        if (run(codepoint)) return
        offset += Character.charCount(codepoint)
    }
}

fun loopOverCodePointsBackwards(s: CharSequence, run: (Int) -> Boolean) {
    val text = if (s is String) s else s.toString()
    var offset = text.length
    while (offset > 0) {
        val codepoint = text.codePointBefore(offset)
        if (run(codepoint)) return
        offset -= Character.charCount(codepoint)
    }
}

fun nonWordCodePointAndNoSpaceBeforeCursor(s: CharSequence, spacingAndPunctuations: SpacingAndPunctuations): Boolean {
    var space = false
    var nonWordCodePoint = false
    loopOverCodePointsBackwards(s) {
        if (!space && Character.isWhitespace(it))
            space = true
        if (!nonWordCodePoint && !spacingAndPunctuations.isWordCodePoint(it))
            nonWordCodePoint = true
        space && nonWordCodePoint
    }
    return space && nonWordCodePoint
}

fun hasLetterBeforeLastSpaceBeforeCursor(s: CharSequence): Boolean {
    var letter = false
    loopOverCodePointsBackwards(s) {
        if (Character.isWhitespace(it)) true
        else if (Character.isLetter(it)) {
            letter = true
            true
        }
        else false
    }
    return letter
}