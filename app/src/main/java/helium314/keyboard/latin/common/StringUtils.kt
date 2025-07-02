// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin.common

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.StringUtils.mightBeEmoji
import helium314.keyboard.latin.common.StringUtils.newSingleCodePointString
import helium314.keyboard.latin.settings.SpacingAndPunctuations
import helium314.keyboard.latin.utils.ScriptUtils
import helium314.keyboard.latin.utils.SpacedTokens
import helium314.keyboard.latin.utils.SpannableStringUtils
import helium314.keyboard.latin.utils.TextRange
import java.math.BigInteger
import java.util.Locale
import kotlin.math.max

fun CharSequence.codePointAt(offset: Int) = Character.codePointAt(this, offset)
fun CharSequence.codePointBefore(offset: Int) = Character.codePointBefore(this, offset)

/** Loops over the codepoints in [text]. Exits when [loop] returns true */
inline fun loopOverCodePoints(text: CharSequence, loop: (cp: Int, charCount: Int) -> Boolean) {
    var offset = 0
    while (offset < text.length) {
        val cp = text.codePointAt(offset)
        val charCount = Character.charCount(cp)
        if (loop(cp, charCount)) return
        offset += charCount
    }
}

/** Loops backwards over the codepoints in [text]. Exits when [loop] returns true */
inline fun loopOverCodePointsBackwards(text: CharSequence, loop: (cp: Int, charCount: Int) -> Boolean) {
    var offset = text.length
    while (offset > 0) {
        val cp = text.codePointBefore(offset)
        val charCount = Character.charCount(cp)
        if (loop(cp, charCount)) return
        offset -= charCount
    }
}

fun nonWordCodePointAndNoSpaceBeforeCursor(text: CharSequence, spacingAndPunctuations: SpacingAndPunctuations): Boolean {
    var space = false
    var nonWordCodePoint = false
    loopOverCodePointsBackwards(text) { cp, _ ->
        if (!space && Character.isWhitespace(cp)) space = true
        // treat double quote like a word codepoint for this function (not great, maybe clarify name or extend list of chars?)
        if (!nonWordCodePoint && !spacingAndPunctuations.isWordCodePoint(cp) && cp != '"'.code) {
            nonWordCodePoint = true
        }
        space && nonWordCodePoint // stop if both are found
    }
    return nonWordCodePoint && !space // return true if a non-word codepoint and no space was found
}

fun hasLetterBeforeLastSpaceBeforeCursor(text: CharSequence): Boolean {
    loopOverCodePointsBackwards(text) { cp, _ ->
        if (Character.isWhitespace(cp)) return false
        else if (Character.isLetter(cp)) return true
        false // continue
    }
    return false
}

/** get the complete emoji at end of [text], considering that emojis can be joined with ZWJ resulting in different emojis */
fun getFullEmojiAtEnd(text: CharSequence): String {
    val s = text.toString()
    var offset = s.length
    while (offset > 0) {
        val codepoint = s.codePointBefore(offset)
        // stop if codepoint can't be emoji
        if (!mightBeEmoji(codepoint)) return text.substring(offset)
        offset -= Character.charCount(codepoint)
        if (offset > 0 && s[offset - 1].code == KeyCode.ZWJ) {
            // todo: this appends ZWJ in weird cases like text, ZWJ, emoji
            //  and detects single ZWJ as emoji (at least irrelevant for current use of getFullEmojiAtEnd)
            offset -= 1
            continue
        }

        if (codepoint in 0x1F3FB..0x1F3FF) {
            // Skin tones are not added with ZWJ, but just appended. This is not nice as they can be emojis on their own,
            // but that's how it is done. Assume that an emoji before the skin tone will get merged (usually correct in practice)
            val codepointBefore = s.codePointBefore(offset)
            if (isEmoji(codepointBefore)) {
                offset -= Character.charCount(codepointBefore)
                continue
            }
        }
        // check the whole text after offset
        val textToCheck = s.substring(offset)
        if (isEmoji(textToCheck)) return textToCheck
    }
    return s.substring(offset)
}

/**
 *  Returns whether the [text] does not end with word separator, ignoring all word connectors.
 *  If the [text] is empty (after ignoring word connectors), the method returns false.
 */
// todo: this returns true on numbers, why isn't Character.isLetter(code) used?
fun endsWithWordCodepoint(text: String, spacingAndPunctuations: SpacingAndPunctuations): Boolean {
    if (text.isEmpty()) return false
    var codePoint = 0 // initial value irrelevant since length is always > 0
    loopOverCodePointsBackwards(text) { cp, _ ->
        codePoint = cp
        !spacingAndPunctuations.isWordConnector(cp)
    }
    // codePoint might still be a wordConnector (if text consists of wordConnectors)
    return !spacingAndPunctuations.isWordConnector(codePoint) && !spacingAndPunctuations.isWordSeparator(codePoint)
}

// todo: simplify... maybe compare with original code?
fun getTouchedWordRange(before: CharSequence, after: CharSequence, script: String, spacingAndPunctuations: SpacingAndPunctuations): TextRange {
    // Going backward, find the first breaking point (separator)
    var startIndexInBefore = before.length
    var endIndexInAfter = -1 // todo: clarify why might we want to set it when checking before
    loopOverCodePointsBackwards(before) { codePoint, cpLength ->
        if (!isPartOfCompositionForScript(codePoint, spacingAndPunctuations, script)) {
            if (Character.isWhitespace(codePoint) || !spacingAndPunctuations.mCurrentLanguageHasSpaces)
                return@loopOverCodePointsBackwards true
            // continue to the next whitespace and see whether this contains a sometimesWordConnector
            for (i in startIndexInBefore - 1 downTo 0) {
                val c = before[i]
                if (spacingAndPunctuations.isSometimesWordConnector(c.code)) {
                    // if yes -> whitespace is the index
                    startIndexInBefore = max(StringUtils.charIndexOfLastWhitespace(before).toDouble(), 0.0).toInt()
                    val firstSpaceAfter = StringUtils.charIndexOfFirstWhitespace(after)
                    endIndexInAfter = if (firstSpaceAfter == -1) after.length else firstSpaceAfter - 1
                    return@loopOverCodePointsBackwards true
                } else if (Character.isWhitespace(c)) {
                    // if no, just break normally
                    return@loopOverCodePointsBackwards true
                }
            }
            return@loopOverCodePointsBackwards true
        }
        startIndexInBefore -= cpLength
        false
    }

    // Find last word separator after the cursor
    if (endIndexInAfter == -1) {
        endIndexInAfter = 0
        loopOverCodePoints(after) { codePoint, cpLength ->
            if (!isPartOfCompositionForScript(codePoint, spacingAndPunctuations, script)) {
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
                return@loopOverCodePoints true
            }
            endIndexInAfter += cpLength
            false
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

    val hasUrlSpans = SpannableStringUtils.hasUrlSpans(before, startIndexInBefore, before.length)
        || SpannableStringUtils.hasUrlSpans(after, 0, endIndexInAfter)

    // We don't use TextUtils#concat because it copies all spans without respect to their
    // nature. If the text includes a PARAGRAPH span and it has been split, then
    // TextUtils#concat will crash when it tries to concat both sides of it.
    return TextRange(
        SpannableStringUtils.concatWithNonParagraphSuggestionSpansOnly(before, after),
        startIndexInBefore, before.length + endIndexInAfter, before.length,
        hasUrlSpans
    )
}

// actually this should not be in STRING Utils, but only used for getTouchedWordRange
private fun isPartOfCompositionForScript(codePoint: Int, spacingAndPunctuations: SpacingAndPunctuations, script: String) =
    spacingAndPunctuations.isWordConnector(codePoint) // We always consider word connectors part of compositions.
        // Otherwise, it's part of composition if it's part of script and not a separator.
        || (!spacingAndPunctuations.isWordSeparator(codePoint) && ScriptUtils.isLetterPartOfScript(codePoint, script))

/** split the string on the first of consecutive space only, further consecutive spaces are added to the next split */
fun String.splitOnFirstSpacesOnly(): List<String> {
    val out = mutableListOf<String>()
    val sb = StringBuilder()
    var previousSpace = false
    for (c in this) {
        if (c != ' ') {
            sb.append(c)
            previousSpace = false
            continue
        }
        if (!previousSpace) {
            out.add(sb.toString())
            sb.clear()
            previousSpace = true
        } else {
            sb.append(c)
        }
    }
    if (sb.isNotBlank()) out.add(sb.toString())
    return out
}

fun CharSequence.isValidNumber(): Boolean {
    return this.toString().toDoubleOrNull() != null
}

fun String.decapitalize(locale: Locale): String {
    if (isEmpty() || !this[0].isUpperCase()) return this
    return replaceFirstChar { it.lowercase(locale) }
}

fun encodeBase36(string: String): String = BigInteger(string.toByteArray()).toString(36)

fun decodeBase36(string: String) = BigInteger(string, 36).toByteArray().decodeToString()

fun containsValueWhenSplit(string: String?, value: String, split: String): Boolean {
    if (string == null) return false
    return string.split(split).contains(value)
}

fun isEmoji(c: Int): Boolean = mightBeEmoji(c) && isEmoji(newSingleCodePointString(c))

fun isEmoji(text: CharSequence): Boolean = mightBeEmoji(text) && text.matches(emoRegex)

fun String.splitOnWhitespace() = SpacedTokens(this).toList()

// from https://github.com/mathiasbynens/emoji-test-regex-pattern, MIT license
// matches single emojis only
private val emoRegex = "[#*0-9]\\uFE0F?\\u20E3|[\\xA9\\xAE\\u203C\\u2049\\u2122\\u2139\\u2194-\\u2199\\u21A9\\u21AA\\u231A\\u231B\\u2328\\u23CF\\u23ED-\\u23EF\\u23F1\\u23F2\\u23F8-\\u23FA\\u24C2\\u25AA\\u25AB\\u25B6\\u25C0\\u25FB\\u25FC\\u25FE\\u2600-\\u2604\\u260E\\u2611\\u2614\\u2615\\u2618\\u2620\\u2622\\u2623\\u2626\\u262A\\u262E\\u262F\\u2638-\\u263A\\u2640\\u2642\\u2648-\\u2653\\u265F\\u2660\\u2663\\u2665\\u2666\\u2668\\u267B\\u267E\\u267F\\u2692\\u2694-\\u2697\\u2699\\u269B\\u269C\\u26A0\\u26A7\\u26AA\\u26B0\\u26B1\\u26BD\\u26BE\\u26C4\\u26C8\\u26CF\\u26D1\\u26E9\\u26F0-\\u26F5\\u26F7\\u26F8\\u26FA\\u2702\\u2708\\u2709\\u270F\\u2712\\u2714\\u2716\\u271D\\u2721\\u2733\\u2734\\u2744\\u2747\\u2757\\u2763\\u27A1\\u2934\\u2935\\u2B05-\\u2B07\\u2B1B\\u2B1C\\u2B55\\u3030\\u303D\\u3297\\u3299\\x{1F004}\\x{1F170}\\x{1F171}\\x{1F17E}\\x{1F17F}\\x{1F202}\\x{1F237}\\x{1F321}\\x{1F324}-\\x{1F32C}\\x{1F336}\\x{1F37D}\\x{1F396}\\x{1F397}\\x{1F399}-\\x{1F39B}\\x{1F39E}\\x{1F39F}\\x{1F3CD}\\x{1F3CE}\\x{1F3D4}-\\x{1F3DF}\\x{1F3F5}\\x{1F3F7}\\x{1F43F}\\x{1F4FD}\\x{1F549}\\x{1F54A}\\x{1F56F}\\x{1F570}\\x{1F573}\\x{1F576}-\\x{1F579}\\x{1F587}\\x{1F58A}-\\x{1F58D}\\x{1F5A5}\\x{1F5A8}\\x{1F5B1}\\x{1F5B2}\\x{1F5BC}\\x{1F5C2}-\\x{1F5C4}\\x{1F5D1}-\\x{1F5D3}\\x{1F5DC}-\\x{1F5DE}\\x{1F5E1}\\x{1F5E3}\\x{1F5E8}\\x{1F5EF}\\x{1F5F3}\\x{1F5FA}\\x{1F6CB}\\x{1F6CD}-\\x{1F6CF}\\x{1F6E0}-\\x{1F6E5}\\x{1F6E9}\\x{1F6F0}\\x{1F6F3}]\\uFE0F?|[\\u261D\\u270C\\u270D\\x{1F574}\\x{1F590}][\\uFE0F\\x{1F3FB}-\\x{1F3FF}]?|[\\u26F9\\x{1F3CB}\\x{1F3CC}\\x{1F575}][\\uFE0F\\x{1F3FB}-\\x{1F3FF}]?(?:\\u200D[\\u2640\\u2642]\\uFE0F?)?|[\\u270A\\u270B\\x{1F385}\\x{1F3C2}\\x{1F3C7}\\x{1F442}\\x{1F443}\\x{1F446}-\\x{1F450}\\x{1F466}\\x{1F467}\\x{1F46B}-\\x{1F46D}\\x{1F472}\\x{1F474}-\\x{1F476}\\x{1F478}\\x{1F47C}\\x{1F483}\\x{1F485}\\x{1F48F}\\x{1F491}\\x{1F4AA}\\x{1F57A}\\x{1F595}\\x{1F596}\\x{1F64C}\\x{1F64F}\\x{1F6C0}\\x{1F6CC}\\x{1F90C}\\x{1F90F}\\x{1F918}-\\x{1F91F}\\x{1F930}-\\x{1F934}\\x{1F936}\\x{1F977}\\x{1F9B5}\\x{1F9B6}\\x{1F9BB}\\x{1F9D2}\\x{1F9D3}\\x{1F9D5}\\x{1FAC3}-\\x{1FAC5}\\x{1FAF0}\\x{1FAF2}-\\x{1FAF8}][\\x{1F3FB}-\\x{1F3FF}]?|[\\x{1F3C3}\\x{1F6B6}\\x{1F9CE}][\\x{1F3FB}-\\x{1F3FF}]?(?:\\u200D(?:[\\u2640\\u2642]\\uFE0F?(?:\\u200D\\u27A1\\uFE0F?)?|\\u27A1\\uFE0F?))?|[\\x{1F3C4}\\x{1F3CA}\\x{1F46E}\\x{1F470}\\x{1F471}\\x{1F473}\\x{1F477}\\x{1F481}\\x{1F482}\\x{1F486}\\x{1F487}\\x{1F645}-\\x{1F647}\\x{1F64B}\\x{1F64D}\\x{1F64E}\\x{1F6A3}\\x{1F6B4}\\x{1F6B5}\\x{1F926}\\x{1F935}\\x{1F937}-\\x{1F939}\\x{1F93D}\\x{1F93E}\\x{1F9B8}\\x{1F9B9}\\x{1F9CD}\\x{1F9CF}\\x{1F9D4}\\x{1F9D6}-\\x{1F9DD}][\\x{1F3FB}-\\x{1F3FF}]?(?:\\u200D[\\u2640\\u2642]\\uFE0F?)?|[\\x{1F46F}\\x{1F9DE}\\x{1F9DF}](?:\\u200D[\\u2640\\u2642]\\uFE0F?)?|[\\u23E9-\\u23EC\\u23F0\\u23F3\\u25FD\\u2693\\u26A1\\u26AB\\u26C5\\u26CE\\u26D4\\u26EA\\u26FD\\u2705\\u2728\\u274C\\u274E\\u2753-\\u2755\\u2795-\\u2797\\u27B0\\u27BF\\u2B50\\x{1F0CF}\\x{1F18E}\\x{1F191}-\\x{1F19A}\\x{1F201}\\x{1F21A}\\x{1F22F}\\x{1F232}-\\x{1F236}\\x{1F238}-\\x{1F23A}\\x{1F250}\\x{1F251}\\x{1F300}-\\x{1F320}\\x{1F32D}-\\x{1F335}\\x{1F337}-\\x{1F343}\\x{1F345}-\\x{1F34A}\\x{1F34C}-\\x{1F37C}\\x{1F37E}-\\x{1F384}\\x{1F386}-\\x{1F393}\\x{1F3A0}-\\x{1F3C1}\\x{1F3C5}\\x{1F3C6}\\x{1F3C8}\\x{1F3C9}\\x{1F3CF}-\\x{1F3D3}\\x{1F3E0}-\\x{1F3F0}\\x{1F3F8}-\\x{1F407}\\x{1F409}-\\x{1F414}\\x{1F416}-\\x{1F425}\\x{1F427}-\\x{1F43A}\\x{1F43C}-\\x{1F43E}\\x{1F440}\\x{1F444}\\x{1F445}\\x{1F451}-\\x{1F465}\\x{1F46A}\\x{1F479}-\\x{1F47B}\\x{1F47D}-\\x{1F480}\\x{1F484}\\x{1F488}-\\x{1F48E}\\x{1F490}\\x{1F492}-\\x{1F4A9}\\x{1F4AB}-\\x{1F4FC}\\x{1F4FF}-\\x{1F53D}\\x{1F54B}-\\x{1F54E}\\x{1F550}-\\x{1F567}\\x{1F5A4}\\x{1F5FB}-\\x{1F62D}\\x{1F62F}-\\x{1F634}\\x{1F637}-\\x{1F641}\\x{1F643}\\x{1F644}\\x{1F648}-\\x{1F64A}\\x{1F680}-\\x{1F6A2}\\x{1F6A4}-\\x{1F6B3}\\x{1F6B7}-\\x{1F6BF}\\x{1F6C1}-\\x{1F6C5}\\x{1F6D0}-\\x{1F6D2}\\x{1F6D5}-\\x{1F6D7}\\x{1F6DC}-\\x{1F6DF}\\x{1F6EB}\\x{1F6EC}\\x{1F6F4}-\\x{1F6FC}\\x{1F7E0}-\\x{1F7EB}\\x{1F7F0}\\x{1F90D}\\x{1F90E}\\x{1F910}-\\x{1F917}\\x{1F920}-\\x{1F925}\\x{1F927}-\\x{1F92F}\\x{1F93A}\\x{1F93F}-\\x{1F945}\\x{1F947}-\\x{1F976}\\x{1F978}-\\x{1F9B4}\\x{1F9B7}\\x{1F9BA}\\x{1F9BC}-\\x{1F9CC}\\x{1F9D0}\\x{1F9E0}-\\x{1F9FF}\\x{1FA70}-\\x{1FA7C}\\x{1FA80}-\\x{1FA89}\\x{1FA8F}-\\x{1FAC2}\\x{1FAC6}\\x{1FACE}-\\x{1FADC}\\x{1FADF}-\\x{1FAE9}]|\\u26D3\\uFE0F?(?:\\u200D\\x{1F4A5})?|\\u2764\\uFE0F?(?:\\u200D[\\x{1F525}\\x{1FA79}])?|\\x{1F1E6}[\\x{1F1E8}-\\x{1F1EC}\\x{1F1EE}\\x{1F1F1}\\x{1F1F2}\\x{1F1F4}\\x{1F1F6}-\\x{1F1FA}\\x{1F1FC}\\x{1F1FD}\\x{1F1FF}]|\\x{1F1E7}[\\x{1F1E6}\\x{1F1E7}\\x{1F1E9}-\\x{1F1EF}\\x{1F1F1}-\\x{1F1F4}\\x{1F1F6}-\\x{1F1F9}\\x{1F1FB}\\x{1F1FC}\\x{1F1FE}\\x{1F1FF}]|\\x{1F1E8}[\\x{1F1E6}\\x{1F1E8}\\x{1F1E9}\\x{1F1EB}-\\x{1F1EE}\\x{1F1F0}-\\x{1F1F7}\\x{1F1FA}-\\x{1F1FF}]|\\x{1F1E9}[\\x{1F1EA}\\x{1F1EC}\\x{1F1EF}\\x{1F1F0}\\x{1F1F2}\\x{1F1F4}\\x{1F1FF}]|\\x{1F1EA}[\\x{1F1E6}\\x{1F1E8}\\x{1F1EA}\\x{1F1EC}\\x{1F1ED}\\x{1F1F7}-\\x{1F1FA}]|\\x{1F1EB}[\\x{1F1EE}-\\x{1F1F0}\\x{1F1F2}\\x{1F1F4}\\x{1F1F7}]|\\x{1F1EC}[\\x{1F1E6}\\x{1F1E7}\\x{1F1E9}-\\x{1F1EE}\\x{1F1F1}-\\x{1F1F3}\\x{1F1F5}-\\x{1F1FA}\\x{1F1FC}\\x{1F1FE}]|\\x{1F1ED}[\\x{1F1F0}\\x{1F1F2}\\x{1F1F3}\\x{1F1F7}\\x{1F1F9}\\x{1F1FA}]|\\x{1F1EE}[\\x{1F1E8}-\\x{1F1EA}\\x{1F1F1}-\\x{1F1F4}\\x{1F1F6}-\\x{1F1F9}]|\\x{1F1EF}[\\x{1F1EA}\\x{1F1F2}\\x{1F1F4}\\x{1F1F5}]|\\x{1F1F0}[\\x{1F1EA}\\x{1F1EC}-\\x{1F1EE}\\x{1F1F2}\\x{1F1F3}\\x{1F1F5}\\x{1F1F7}\\x{1F1FC}\\x{1F1FE}\\x{1F1FF}]|\\x{1F1F1}[\\x{1F1E6}-\\x{1F1E8}\\x{1F1EE}\\x{1F1F0}\\x{1F1F7}-\\x{1F1FB}\\x{1F1FE}]|\\x{1F1F2}[\\x{1F1E6}\\x{1F1E8}-\\x{1F1ED}\\x{1F1F0}-\\x{1F1FF}]|\\x{1F1F3}[\\x{1F1E6}\\x{1F1E8}\\x{1F1EA}-\\x{1F1EC}\\x{1F1EE}\\x{1F1F1}\\x{1F1F4}\\x{1F1F5}\\x{1F1F7}\\x{1F1FA}\\x{1F1FF}]|\\x{1F1F4}\\x{1F1F2}|\\x{1F1F5}[\\x{1F1E6}\\x{1F1EA}-\\x{1F1ED}\\x{1F1F0}-\\x{1F1F3}\\x{1F1F7}-\\x{1F1F9}\\x{1F1FC}\\x{1F1FE}]|\\x{1F1F6}\\x{1F1E6}|\\x{1F1F7}[\\x{1F1EA}\\x{1F1F4}\\x{1F1F8}\\x{1F1FA}\\x{1F1FC}]|\\x{1F1F8}[\\x{1F1E6}-\\x{1F1EA}\\x{1F1EC}-\\x{1F1F4}\\x{1F1F7}-\\x{1F1F9}\\x{1F1FB}\\x{1F1FD}-\\x{1F1FF}]|\\x{1F1F9}[\\x{1F1E6}\\x{1F1E8}\\x{1F1E9}\\x{1F1EB}-\\x{1F1ED}\\x{1F1EF}-\\x{1F1F4}\\x{1F1F7}\\x{1F1F9}\\x{1F1FB}\\x{1F1FC}\\x{1F1FF}]|\\x{1F1FA}[\\x{1F1E6}\\x{1F1EC}\\x{1F1F2}\\x{1F1F3}\\x{1F1F8}\\x{1F1FE}\\x{1F1FF}]|\\x{1F1FB}[\\x{1F1E6}\\x{1F1E8}\\x{1F1EA}\\x{1F1EC}\\x{1F1EE}\\x{1F1F3}\\x{1F1FA}]|\\x{1F1FC}[\\x{1F1EB}\\x{1F1F8}]|\\x{1F1FD}\\x{1F1F0}|\\x{1F1FE}[\\x{1F1EA}\\x{1F1F9}]|\\x{1F1FF}[\\x{1F1E6}\\x{1F1F2}\\x{1F1FC}]|\\x{1F344}(?:\\u200D\\x{1F7EB})?|\\x{1F34B}(?:\\u200D\\x{1F7E9})?|\\x{1F3F3}\\uFE0F?(?:\\u200D(?:\\u26A7\\uFE0F?|\\x{1F308}))?|\\x{1F3F4}(?:\\u200D\\u2620\\uFE0F?|\\x{E0067}\\x{E0062}(?:\\x{E0065}\\x{E006E}\\x{E0067}|\\x{E0073}\\x{E0063}\\x{E0074}|\\x{E0077}\\x{E006C}\\x{E0073})\\x{E007F})?|\\x{1F408}(?:\\u200D\\u2B1B)?|\\x{1F415}(?:\\u200D\\x{1F9BA})?|\\x{1F426}(?:\\u200D[\\u2B1B\\x{1F525}])?|\\x{1F43B}(?:\\u200D\\u2744\\uFE0F?)?|\\x{1F441}\\uFE0F?(?:\\u200D\\x{1F5E8}\\uFE0F?)?|\\x{1F468}(?:\\u200D(?:[\\u2695\\u2696\\u2708]\\uFE0F?|[\\x{1F468}\\x{1F469}]\\u200D(?:\\x{1F466}(?:\\u200D\\x{1F466})?|\\x{1F467}(?:\\u200D[\\x{1F466}\\x{1F467}])?)|[\\x{1F9AF}\\x{1F9BC}\\x{1F9BD}](?:\\u200D\\u27A1\\uFE0F?)?|[\\x{1F33E}\\x{1F373}\\x{1F37C}\\x{1F393}\\x{1F3A4}\\x{1F3A8}\\x{1F3EB}\\x{1F3ED}\\x{1F4BB}\\x{1F4BC}\\x{1F527}\\x{1F52C}\\x{1F680}\\x{1F692}\\x{1F9B0}-\\x{1F9B3}]|\\u2764\\uFE0F?\\u200D(?:\\x{1F48B}\\u200D)?\\x{1F468}|\\x{1F466}(?:\\u200D\\x{1F466})?|\\x{1F467}(?:\\u200D[\\x{1F466}\\x{1F467}])?)|\\x{1F3FB}(?:\\u200D(?:[\\u2695\\u2696\\u2708]\\uFE0F?|[\\x{1F9AF}\\x{1F9BC}\\x{1F9BD}](?:\\u200D\\u27A1\\uFE0F?)?|[\\x{1F33E}\\x{1F373}\\x{1F37C}\\x{1F393}\\x{1F3A4}\\x{1F3A8}\\x{1F3EB}\\x{1F3ED}\\x{1F4BB}\\x{1F4BC}\\x{1F527}\\x{1F52C}\\x{1F680}\\x{1F692}\\x{1F9B0}-\\x{1F9B3}]|\\u2764\\uFE0F?\\u200D(?:\\x{1F48B}\\u200D)?\\x{1F468}[\\x{1F3FB}-\\x{1F3FF}]|\\x{1F91D}\\u200D\\x{1F468}[\\x{1F3FC}-\\x{1F3FF}]))?|\\x{1F3FC}(?:\\u200D(?:[\\u2695\\u2696\\u2708]\\uFE0F?|[\\x{1F9AF}\\x{1F9BC}\\x{1F9BD}](?:\\u200D\\u27A1\\uFE0F?)?|[\\x{1F33E}\\x{1F373}\\x{1F37C}\\x{1F393}\\x{1F3A4}\\x{1F3A8}\\x{1F3EB}\\x{1F3ED}\\x{1F4BB}\\x{1F4BC}\\x{1F527}\\x{1F52C}\\x{1F680}\\x{1F692}\\x{1F9B0}-\\x{1F9B3}]|\\u2764\\uFE0F?\\u200D(?:\\x{1F48B}\\u200D)?\\x{1F468}[\\x{1F3FB}-\\x{1F3FF}]|\\x{1F91D}\\u200D\\x{1F468}[\\x{1F3FB}\\x{1F3FD}-\\x{1F3FF}]))?|\\x{1F3FD}(?:\\u200D(?:[\\u2695\\u2696\\u2708]\\uFE0F?|[\\x{1F9AF}\\x{1F9BC}\\x{1F9BD}](?:\\u200D\\u27A1\\uFE0F?)?|[\\x{1F33E}\\x{1F373}\\x{1F37C}\\x{1F393}\\x{1F3A4}\\x{1F3A8}\\x{1F3EB}\\x{1F3ED}\\x{1F4BB}\\x{1F4BC}\\x{1F527}\\x{1F52C}\\x{1F680}\\x{1F692}\\x{1F9B0}-\\x{1F9B3}]|\\u2764\\uFE0F?\\u200D(?:\\x{1F48B}\\u200D)?\\x{1F468}[\\x{1F3FB}-\\x{1F3FF}]|\\x{1F91D}\\u200D\\x{1F468}[\\x{1F3FB}\\x{1F3FC}\\x{1F3FE}\\x{1F3FF}]))?|\\x{1F3FE}(?:\\u200D(?:[\\u2695\\u2696\\u2708]\\uFE0F?|[\\x{1F9AF}\\x{1F9BC}\\x{1F9BD}](?:\\u200D\\u27A1\\uFE0F?)?|[\\x{1F33E}\\x{1F373}\\x{1F37C}\\x{1F393}\\x{1F3A4}\\x{1F3A8}\\x{1F3EB}\\x{1F3ED}\\x{1F4BB}\\x{1F4BC}\\x{1F527}\\x{1F52C}\\x{1F680}\\x{1F692}\\x{1F9B0}-\\x{1F9B3}]|\\u2764\\uFE0F?\\u200D(?:\\x{1F48B}\\u200D)?\\x{1F468}[\\x{1F3FB}-\\x{1F3FF}]|\\x{1F91D}\\u200D\\x{1F468}[\\x{1F3FB}-\\x{1F3FD}\\x{1F3FF}]))?|\\x{1F3FF}(?:\\u200D(?:[\\u2695\\u2696\\u2708]\\uFE0F?|[\\x{1F9AF}\\x{1F9BC}\\x{1F9BD}](?:\\u200D\\u27A1\\uFE0F?)?|[\\x{1F33E}\\x{1F373}\\x{1F37C}\\x{1F393}\\x{1F3A4}\\x{1F3A8}\\x{1F3EB}\\x{1F3ED}\\x{1F4BB}\\x{1F4BC}\\x{1F527}\\x{1F52C}\\x{1F680}\\x{1F692}\\x{1F9B0}-\\x{1F9B3}]|\\u2764\\uFE0F?\\u200D(?:\\x{1F48B}\\u200D)?\\x{1F468}[\\x{1F3FB}-\\x{1F3FF}]|\\x{1F91D}\\u200D\\x{1F468}[\\x{1F3FB}-\\x{1F3FE}]))?)?|\\x{1F469}(?:\\u200D(?:[\\u2695\\u2696\\u2708]\\uFE0F?|[\\x{1F9AF}\\x{1F9BC}\\x{1F9BD}](?:\\u200D\\u27A1\\uFE0F?)?|[\\x{1F33E}\\x{1F373}\\x{1F37C}\\x{1F393}\\x{1F3A4}\\x{1F3A8}\\x{1F3EB}\\x{1F3ED}\\x{1F4BB}\\x{1F4BC}\\x{1F527}\\x{1F52C}\\x{1F680}\\x{1F692}\\x{1F9B0}-\\x{1F9B3}]|\\u2764\\uFE0F?\\u200D(?:\\x{1F48B}\\u200D)?[\\x{1F468}\\x{1F469}]|\\x{1F466}(?:\\u200D\\x{1F466})?|\\x{1F467}(?:\\u200D[\\x{1F466}\\x{1F467}])?|\\x{1F469}\\u200D(?:\\x{1F466}(?:\\u200D\\x{1F466})?|\\x{1F467}(?:\\u200D[\\x{1F466}\\x{1F467}])?))|\\x{1F3FB}(?:\\u200D(?:[\\u2695\\u2696\\u2708]\\uFE0F?|[\\x{1F9AF}\\x{1F9BC}\\x{1F9BD}](?:\\u200D\\u27A1\\uFE0F?)?|[\\x{1F33E}\\x{1F373}\\x{1F37C}\\x{1F393}\\x{1F3A4}\\x{1F3A8}\\x{1F3EB}\\x{1F3ED}\\x{1F4BB}\\x{1F4BC}\\x{1F527}\\x{1F52C}\\x{1F680}\\x{1F692}\\x{1F9B0}-\\x{1F9B3}]|\\u2764\\uFE0F?\\u200D(?:[\\x{1F468}\\x{1F469}]|\\x{1F48B}\\u200D[\\x{1F468}\\x{1F469}])[\\x{1F3FB}-\\x{1F3FF}]|\\x{1F91D}\\u200D[\\x{1F468}\\x{1F469}][\\x{1F3FC}-\\x{1F3FF}]))?|\\x{1F3FC}(?:\\u200D(?:[\\u2695\\u2696\\u2708]\\uFE0F?|[\\x{1F9AF}\\x{1F9BC}\\x{1F9BD}](?:\\u200D\\u27A1\\uFE0F?)?|[\\x{1F33E}\\x{1F373}\\x{1F37C}\\x{1F393}\\x{1F3A4}\\x{1F3A8}\\x{1F3EB}\\x{1F3ED}\\x{1F4BB}\\x{1F4BC}\\x{1F527}\\x{1F52C}\\x{1F680}\\x{1F692}\\x{1F9B0}-\\x{1F9B3}]|\\u2764\\uFE0F?\\u200D(?:[\\x{1F468}\\x{1F469}]|\\x{1F48B}\\u200D[\\x{1F468}\\x{1F469}])[\\x{1F3FB}-\\x{1F3FF}]|\\x{1F91D}\\u200D[\\x{1F468}\\x{1F469}][\\x{1F3FB}\\x{1F3FD}-\\x{1F3FF}]))?|\\x{1F3FD}(?:\\u200D(?:[\\u2695\\u2696\\u2708]\\uFE0F?|[\\x{1F9AF}\\x{1F9BC}\\x{1F9BD}](?:\\u200D\\u27A1\\uFE0F?)?|[\\x{1F33E}\\x{1F373}\\x{1F37C}\\x{1F393}\\x{1F3A4}\\x{1F3A8}\\x{1F3EB}\\x{1F3ED}\\x{1F4BB}\\x{1F4BC}\\x{1F527}\\x{1F52C}\\x{1F680}\\x{1F692}\\x{1F9B0}-\\x{1F9B3}]|\\u2764\\uFE0F?\\u200D(?:[\\x{1F468}\\x{1F469}]|\\x{1F48B}\\u200D[\\x{1F468}\\x{1F469}])[\\x{1F3FB}-\\x{1F3FF}]|\\x{1F91D}\\u200D[\\x{1F468}\\x{1F469}][\\x{1F3FB}\\x{1F3FC}\\x{1F3FE}\\x{1F3FF}]))?|\\x{1F3FE}(?:\\u200D(?:[\\u2695\\u2696\\u2708]\\uFE0F?|[\\x{1F9AF}\\x{1F9BC}\\x{1F9BD}](?:\\u200D\\u27A1\\uFE0F?)?|[\\x{1F33E}\\x{1F373}\\x{1F37C}\\x{1F393}\\x{1F3A4}\\x{1F3A8}\\x{1F3EB}\\x{1F3ED}\\x{1F4BB}\\x{1F4BC}\\x{1F527}\\x{1F52C}\\x{1F680}\\x{1F692}\\x{1F9B0}-\\x{1F9B3}]|\\u2764\\uFE0F?\\u200D(?:[\\x{1F468}\\x{1F469}]|\\x{1F48B}\\u200D[\\x{1F468}\\x{1F469}])[\\x{1F3FB}-\\x{1F3FF}]|\\x{1F91D}\\u200D[\\x{1F468}\\x{1F469}][\\x{1F3FB}-\\x{1F3FD}\\x{1F3FF}]))?|\\x{1F3FF}(?:\\u200D(?:[\\u2695\\u2696\\u2708]\\uFE0F?|[\\x{1F9AF}\\x{1F9BC}\\x{1F9BD}](?:\\u200D\\u27A1\\uFE0F?)?|[\\x{1F33E}\\x{1F373}\\x{1F37C}\\x{1F393}\\x{1F3A4}\\x{1F3A8}\\x{1F3EB}\\x{1F3ED}\\x{1F4BB}\\x{1F4BC}\\x{1F527}\\x{1F52C}\\x{1F680}\\x{1F692}\\x{1F9B0}-\\x{1F9B3}]|\\u2764\\uFE0F?\\u200D(?:[\\x{1F468}\\x{1F469}]|\\x{1F48B}\\u200D[\\x{1F468}\\x{1F469}])[\\x{1F3FB}-\\x{1F3FF}]|\\x{1F91D}\\u200D[\\x{1F468}\\x{1F469}][\\x{1F3FB}-\\x{1F3FE}]))?)?|\\x{1F62E}(?:\\u200D\\x{1F4A8})?|\\x{1F635}(?:\\u200D\\x{1F4AB})?|\\x{1F636}(?:\\u200D\\x{1F32B}\\uFE0F?)?|\\x{1F642}(?:\\u200D[\\u2194\\u2195]\\uFE0F?)?|\\x{1F93C}(?:[\\x{1F3FB}-\\x{1F3FF}]|\\u200D[\\u2640\\u2642]\\uFE0F?)?|\\x{1F9D1}(?:\\u200D(?:[\\u2695\\u2696\\u2708]\\uFE0F?|[\\x{1F9AF}\\x{1F9BC}\\x{1F9BD}](?:\\u200D\\u27A1\\uFE0F?)?|[\\x{1F33E}\\x{1F373}\\x{1F37C}\\x{1F384}\\x{1F393}\\x{1F3A4}\\x{1F3A8}\\x{1F3EB}\\x{1F3ED}\\x{1F4BB}\\x{1F4BC}\\x{1F527}\\x{1F52C}\\x{1F680}\\x{1F692}\\x{1F9B0}-\\x{1F9B3}]|\\x{1F91D}\\u200D\\x{1F9D1}|\\x{1F9D1}\\u200D\\x{1F9D2}(?:\\u200D\\x{1F9D2})?|\\x{1F9D2}(?:\\u200D\\x{1F9D2})?)|\\x{1F3FB}(?:\\u200D(?:[\\u2695\\u2696\\u2708]\\uFE0F?|[\\x{1F9AF}\\x{1F9BC}\\x{1F9BD}](?:\\u200D\\u27A1\\uFE0F?)?|[\\x{1F33E}\\x{1F373}\\x{1F37C}\\x{1F384}\\x{1F393}\\x{1F3A4}\\x{1F3A8}\\x{1F3EB}\\x{1F3ED}\\x{1F4BB}\\x{1F4BC}\\x{1F527}\\x{1F52C}\\x{1F680}\\x{1F692}\\x{1F9B0}-\\x{1F9B3}]|\\u2764\\uFE0F?\\u200D(?:\\x{1F48B}\\u200D)?\\x{1F9D1}[\\x{1F3FC}-\\x{1F3FF}]|\\x{1F91D}\\u200D\\x{1F9D1}[\\x{1F3FB}-\\x{1F3FF}]))?|\\x{1F3FC}(?:\\u200D(?:[\\u2695\\u2696\\u2708]\\uFE0F?|[\\x{1F9AF}\\x{1F9BC}\\x{1F9BD}](?:\\u200D\\u27A1\\uFE0F?)?|[\\x{1F33E}\\x{1F373}\\x{1F37C}\\x{1F384}\\x{1F393}\\x{1F3A4}\\x{1F3A8}\\x{1F3EB}\\x{1F3ED}\\x{1F4BB}\\x{1F4BC}\\x{1F527}\\x{1F52C}\\x{1F680}\\x{1F692}\\x{1F9B0}-\\x{1F9B3}]|\\u2764\\uFE0F?\\u200D(?:\\x{1F48B}\\u200D)?\\x{1F9D1}[\\x{1F3FB}\\x{1F3FD}-\\x{1F3FF}]|\\x{1F91D}\\u200D\\x{1F9D1}[\\x{1F3FB}-\\x{1F3FF}]))?|\\x{1F3FD}(?:\\u200D(?:[\\u2695\\u2696\\u2708]\\uFE0F?|[\\x{1F9AF}\\x{1F9BC}\\x{1F9BD}](?:\\u200D\\u27A1\\uFE0F?)?|[\\x{1F33E}\\x{1F373}\\x{1F37C}\\x{1F384}\\x{1F393}\\x{1F3A4}\\x{1F3A8}\\x{1F3EB}\\x{1F3ED}\\x{1F4BB}\\x{1F4BC}\\x{1F527}\\x{1F52C}\\x{1F680}\\x{1F692}\\x{1F9B0}-\\x{1F9B3}]|\\u2764\\uFE0F?\\u200D(?:\\x{1F48B}\\u200D)?\\x{1F9D1}[\\x{1F3FB}\\x{1F3FC}\\x{1F3FE}\\x{1F3FF}]|\\x{1F91D}\\u200D\\x{1F9D1}[\\x{1F3FB}-\\x{1F3FF}]))?|\\x{1F3FE}(?:\\u200D(?:[\\u2695\\u2696\\u2708]\\uFE0F?|[\\x{1F9AF}\\x{1F9BC}\\x{1F9BD}](?:\\u200D\\u27A1\\uFE0F?)?|[\\x{1F33E}\\x{1F373}\\x{1F37C}\\x{1F384}\\x{1F393}\\x{1F3A4}\\x{1F3A8}\\x{1F3EB}\\x{1F3ED}\\x{1F4BB}\\x{1F4BC}\\x{1F527}\\x{1F52C}\\x{1F680}\\x{1F692}\\x{1F9B0}-\\x{1F9B3}]|\\u2764\\uFE0F?\\u200D(?:\\x{1F48B}\\u200D)?\\x{1F9D1}[\\x{1F3FB}-\\x{1F3FD}\\x{1F3FF}]|\\x{1F91D}\\u200D\\x{1F9D1}[\\x{1F3FB}-\\x{1F3FF}]))?|\\x{1F3FF}(?:\\u200D(?:[\\u2695\\u2696\\u2708]\\uFE0F?|[\\x{1F9AF}\\x{1F9BC}\\x{1F9BD}](?:\\u200D\\u27A1\\uFE0F?)?|[\\x{1F33E}\\x{1F373}\\x{1F37C}\\x{1F384}\\x{1F393}\\x{1F3A4}\\x{1F3A8}\\x{1F3EB}\\x{1F3ED}\\x{1F4BB}\\x{1F4BC}\\x{1F527}\\x{1F52C}\\x{1F680}\\x{1F692}\\x{1F9B0}-\\x{1F9B3}]|\\u2764\\uFE0F?\\u200D(?:\\x{1F48B}\\u200D)?\\x{1F9D1}[\\x{1F3FB}-\\x{1F3FE}]|\\x{1F91D}\\u200D\\x{1F9D1}[\\x{1F3FB}-\\x{1F3FF}]))?)?|\\x{1FAF1}(?:\\x{1F3FB}(?:\\u200D\\x{1FAF2}[\\x{1F3FC}-\\x{1F3FF}])?|\\x{1F3FC}(?:\\u200D\\x{1FAF2}[\\x{1F3FB}\\x{1F3FD}-\\x{1F3FF}])?|\\x{1F3FD}(?:\\u200D\\x{1FAF2}[\\x{1F3FB}\\x{1F3FC}\\x{1F3FE}\\x{1F3FF}])?|\\x{1F3FE}(?:\\u200D\\x{1FAF2}[\\x{1F3FB}-\\x{1F3FD}\\x{1F3FF}])?|\\x{1F3FF}(?:\\u200D\\x{1FAF2}[\\x{1F3FB}-\\x{1F3FE}])?)?".toRegex()
