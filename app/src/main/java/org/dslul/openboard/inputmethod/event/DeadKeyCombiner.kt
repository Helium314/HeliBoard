/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.event

import android.text.TextUtils
import android.util.SparseIntArray
import org.dslul.openboard.inputmethod.latin.common.Constants
import java.text.Normalizer
import java.util.*

/**
 * A combiner that handles dead keys.
 */
class DeadKeyCombiner : Combiner {
    private object Data {
        // This class data taken from KeyCharacterMap.java.
/* Characters used to display placeholders for dead keys. */
        private const val ACCENT_ACUTE = '\u00B4'.code
        private const val ACCENT_BREVE = '\u02D8'.code
        private const val ACCENT_CARON = '\u02C7'.code
        private const val ACCENT_CEDILLA = '\u00B8'.code
        private const val ACCENT_CIRCUMFLEX = '\u02C6'.code
        private const val ACCENT_COMMA_ABOVE = '\u1FBD'.code
        private const val ACCENT_COMMA_ABOVE_RIGHT = '\u02BC'.code
        private const val ACCENT_DOT_ABOVE = '\u02D9'.code
        private const val ACCENT_DOT_BELOW = Constants.CODE_PERIOD // approximate
        private const val ACCENT_DOUBLE_ACUTE = '\u02DD'.code
        private const val ACCENT_GRAVE = '\u02CB'.code
        private const val ACCENT_HOOK_ABOVE = '\u02C0'.code
        private const val ACCENT_HORN = Constants.CODE_SINGLE_QUOTE // approximate
        private const val ACCENT_MACRON = '\u00AF'.code
        private const val ACCENT_MACRON_BELOW = '\u02CD'.code
        private const val ACCENT_OGONEK = '\u02DB'.code
        private const val ACCENT_REVERSED_COMMA_ABOVE = '\u02BD'.code
        private const val ACCENT_RING_ABOVE = '\u02DA'.code
        private const val ACCENT_STROKE = Constants.CODE_DASH // approximate
        private const val ACCENT_TILDE = '\u02DC'.code
        private const val ACCENT_TURNED_COMMA_ABOVE = '\u02BB'.code
        private const val ACCENT_UMLAUT = '\u00A8'.code
        private const val ACCENT_VERTICAL_LINE_ABOVE = '\u02C8'.code
        private const val ACCENT_VERTICAL_LINE_BELOW = '\u02CC'.code
        /* Legacy dead key display characters used in previous versions of the API (before L)
         * We still support these characters by mapping them to their non-legacy version. */
        private const val ACCENT_GRAVE_LEGACY = Constants.CODE_GRAVE_ACCENT
        private const val ACCENT_CIRCUMFLEX_LEGACY = Constants.CODE_CIRCUMFLEX_ACCENT
        private const val ACCENT_TILDE_LEGACY = Constants.CODE_TILDE
        /**
         * Maps Unicode combining diacritical to display-form dead key.
         */
        val sCombiningToAccent = SparseIntArray()
        val sAccentToCombining = SparseIntArray()
        private fun addCombining(combining: Int, accent: Int) {
            sCombiningToAccent.append(combining, accent)
            sAccentToCombining.append(accent, combining)
        }

        // Caution! This may only contain chars, not supplementary code points. It's unlikely
// it will ever need to, but if it does we'll have to change this
        private val sNonstandardDeadCombinations = SparseIntArray()

        private fun addNonStandardDeadCombination(deadCodePoint: Int,
                                                  spacingCodePoint: Int, result: Int) {
            val combination = deadCodePoint shl 16 or spacingCodePoint
            sNonstandardDeadCombinations.put(combination, result)
        }

        const val NOT_A_CHAR = 0
        const val BITS_TO_SHIFT_DEAD_CODE_POINT_FOR_NON_STANDARD_COMBINATION = 16
        // Get a non-standard combination
        fun getNonstandardCombination(deadCodePoint: Int,
                                      spacingCodePoint: Int): Char {
            val combination = spacingCodePoint or
                    (deadCodePoint shl BITS_TO_SHIFT_DEAD_CODE_POINT_FOR_NON_STANDARD_COMBINATION)
            return sNonstandardDeadCombinations[combination, NOT_A_CHAR].toChar()
        }

        init { // U+0300: COMBINING GRAVE ACCENT
            addCombining('\u0300'.code, ACCENT_GRAVE)
            // U+0301: COMBINING ACUTE ACCENT
            addCombining('\u0301'.code, ACCENT_ACUTE)
            // U+0302: COMBINING CIRCUMFLEX ACCENT
            addCombining('\u0302'.code, ACCENT_CIRCUMFLEX)
            // U+0303: COMBINING TILDE
            addCombining('\u0303'.code, ACCENT_TILDE)
            // U+0304: COMBINING MACRON
            addCombining('\u0304'.code, ACCENT_MACRON)
            // U+0306: COMBINING BREVE
            addCombining('\u0306'.code, ACCENT_BREVE)
            // U+0307: COMBINING DOT ABOVE
            addCombining('\u0307'.code, ACCENT_DOT_ABOVE)
            // U+0308: COMBINING DIAERESIS
            addCombining('\u0308'.code, ACCENT_UMLAUT)
            // U+0309: COMBINING HOOK ABOVE
            addCombining('\u0309'.code, ACCENT_HOOK_ABOVE)
            // U+030A: COMBINING RING ABOVE
            addCombining('\u030A'.code, ACCENT_RING_ABOVE)
            // U+030B: COMBINING DOUBLE ACUTE ACCENT
            addCombining('\u030B'.code, ACCENT_DOUBLE_ACUTE)
            // U+030C: COMBINING CARON
            addCombining('\u030C'.code, ACCENT_CARON)
            // U+030D: COMBINING VERTICAL LINE ABOVE
            addCombining('\u030D'.code, ACCENT_VERTICAL_LINE_ABOVE)
            // U+030E: COMBINING DOUBLE VERTICAL LINE ABOVE
//addCombining('\u030E', ACCENT_DOUBLE_VERTICAL_LINE_ABOVE);
// U+030F: COMBINING DOUBLE GRAVE ACCENT
//addCombining('\u030F', ACCENT_DOUBLE_GRAVE);
// U+0310: COMBINING CANDRABINDU
//addCombining('\u0310', ACCENT_CANDRABINDU);
// U+0311: COMBINING INVERTED BREVE
//addCombining('\u0311', ACCENT_INVERTED_BREVE);
// U+0312: COMBINING TURNED COMMA ABOVE
            addCombining('\u0312'.code, ACCENT_TURNED_COMMA_ABOVE)
            // U+0313: COMBINING COMMA ABOVE
            addCombining('\u0313'.code, ACCENT_COMMA_ABOVE)
            // U+0314: COMBINING REVERSED COMMA ABOVE
            addCombining('\u0314'.code, ACCENT_REVERSED_COMMA_ABOVE)
            // U+0315: COMBINING COMMA ABOVE RIGHT
            addCombining('\u0315'.code, ACCENT_COMMA_ABOVE_RIGHT)
            // U+031B: COMBINING HORN
            addCombining('\u031B'.code, ACCENT_HORN)
            // U+0323: COMBINING DOT BELOW
            addCombining('\u0323'.code, ACCENT_DOT_BELOW)
            // U+0326: COMBINING COMMA BELOW
//addCombining('\u0326', ACCENT_COMMA_BELOW);
// U+0327: COMBINING CEDILLA
            addCombining('\u0327'.code, ACCENT_CEDILLA)
            // U+0328: COMBINING OGONEK
            addCombining('\u0328'.code, ACCENT_OGONEK)
            // U+0329: COMBINING VERTICAL LINE BELOW
            addCombining('\u0329'.code, ACCENT_VERTICAL_LINE_BELOW)
            // U+0331: COMBINING MACRON BELOW
            addCombining('\u0331'.code, ACCENT_MACRON_BELOW)
            // U+0335: COMBINING SHORT STROKE OVERLAY
            addCombining('\u0335'.code, ACCENT_STROKE)
            // U+0342: COMBINING GREEK PERISPOMENI
//addCombining('\u0342', ACCENT_PERISPOMENI);
// U+0344: COMBINING GREEK DIALYTIKA TONOS
//addCombining('\u0344', ACCENT_DIALYTIKA_TONOS);
// U+0345: COMBINING GREEK YPOGEGRAMMENI
//addCombining('\u0345', ACCENT_YPOGEGRAMMENI);
// One-way mappings to equivalent preferred accents.
// U+0340: COMBINING GRAVE TONE MARK
            sCombiningToAccent.append('\u0340'.code, ACCENT_GRAVE)
            // U+0341: COMBINING ACUTE TONE MARK
            sCombiningToAccent.append('\u0341'.code, ACCENT_ACUTE)
            // U+0343: COMBINING GREEK KORONIS
            sCombiningToAccent.append('\u0343'.code, ACCENT_COMMA_ABOVE)
            // One-way legacy mappings to preserve compatibility with older applications.
// U+0300: COMBINING GRAVE ACCENT
            sAccentToCombining.append(ACCENT_GRAVE_LEGACY, '\u0300'.code)
            // U+0302: COMBINING CIRCUMFLEX ACCENT
            sAccentToCombining.append(ACCENT_CIRCUMFLEX_LEGACY, '\u0302'.code)
            // U+0303: COMBINING TILDE
            sAccentToCombining.append(ACCENT_TILDE_LEGACY, '\u0303'.code)
        }

        init { // Non-standard decompositions.
// Stroke modifier for Finnish multilingual keyboard and others.
// U+0110: LATIN CAPITAL LETTER D WITH STROKE
            addNonStandardDeadCombination(ACCENT_STROKE, 'D'.code, '\u0110'.code)
            // U+01E4: LATIN CAPITAL LETTER G WITH STROKE
            addNonStandardDeadCombination(ACCENT_STROKE, 'G'.code, '\u01e4'.code)
            // U+0126: LATIN CAPITAL LETTER H WITH STROKE
            addNonStandardDeadCombination(ACCENT_STROKE, 'H'.code, '\u0126'.code)
            // U+0197: LATIN CAPITAL LETTER I WITH STROKE
            addNonStandardDeadCombination(ACCENT_STROKE, 'I'.code, '\u0197'.code)
            // U+0141: LATIN CAPITAL LETTER L WITH STROKE
            addNonStandardDeadCombination(ACCENT_STROKE, 'L'.code, '\u0141'.code)
            // U+00D8: LATIN CAPITAL LETTER O WITH STROKE
            addNonStandardDeadCombination(ACCENT_STROKE, 'O'.code, '\u00d8'.code)
            // U+0166: LATIN CAPITAL LETTER T WITH STROKE
            addNonStandardDeadCombination(ACCENT_STROKE, 'T'.code, '\u0166'.code)
            // U+0111: LATIN SMALL LETTER D WITH STROKE
            addNonStandardDeadCombination(ACCENT_STROKE, 'd'.code, '\u0111'.code)
            // U+01E5: LATIN SMALL LETTER G WITH STROKE
            addNonStandardDeadCombination(ACCENT_STROKE, 'g'.code, '\u01e5'.code)
            // U+0127: LATIN SMALL LETTER H WITH STROKE
            addNonStandardDeadCombination(ACCENT_STROKE, 'h'.code, '\u0127'.code)
            // U+0268: LATIN SMALL LETTER I WITH STROKE
            addNonStandardDeadCombination(ACCENT_STROKE, 'i'.code, '\u0268'.code)
            // U+0142: LATIN SMALL LETTER L WITH STROKE
            addNonStandardDeadCombination(ACCENT_STROKE, 'l'.code, '\u0142'.code)
            // U+00F8: LATIN SMALL LETTER O WITH STROKE
            addNonStandardDeadCombination(ACCENT_STROKE, 'o'.code, '\u00f8'.code)
            // U+0167: LATIN SMALL LETTER T WITH STROKE
            addNonStandardDeadCombination(ACCENT_STROKE, 't'.code, '\u0167'.code)
        }
    }

    // TODO: make this a list of events instead
    val mDeadSequence = StringBuilder()

    override fun processEvent(previousEvents: ArrayList<Event>?, event: Event): Event {
        if (TextUtils.isEmpty(mDeadSequence)) { // No dead char is currently being tracked: this is the most common case.
            if (event.isDead) { // The event was a dead key. Start tracking it.
                mDeadSequence.appendCodePoint(event.mCodePoint)
                return Event.createConsumedEvent(event)
            }
            // Regular keystroke when not keeping track of a dead key. Simply said, there are
            // no dead keys at all in the current input, so this combiner has nothing to do and
            // simply returns the event as is. The majority of events will go through this path.
            return event
        }
        if (Character.isWhitespace(event.mCodePoint)
                || event.mCodePoint == mDeadSequence.codePointBefore(mDeadSequence.length)) { // When whitespace or twice the same dead key, we should output the dead sequence as is.
            val resultEvent = createEventChainFromSequence(mDeadSequence.toString(), event)
            mDeadSequence.setLength(0)
            return resultEvent
        }
        if (event.isFunctionalKeyEvent) {
            if (Constants.CODE_DELETE == event.mKeyCode) { // Remove the last code point
                val trimIndex = mDeadSequence.length - Character.charCount(
                        mDeadSequence.codePointBefore(mDeadSequence.length))
                mDeadSequence.setLength(trimIndex)
                return Event.createConsumedEvent(event)
            }
            return event
        }
        if (event.isDead) {
            mDeadSequence.appendCodePoint(event.mCodePoint)
            return Event.createConsumedEvent(event)
        }
        // Combine normally.
        val sb = StringBuilder()
        sb.appendCodePoint(event.mCodePoint)
        var codePointIndex = 0
        while (codePointIndex < mDeadSequence.length) {
            val deadCodePoint = mDeadSequence.codePointAt(codePointIndex)
            val replacementSpacingChar = Data.getNonstandardCombination(deadCodePoint, event.mCodePoint)
            if (Data.NOT_A_CHAR != replacementSpacingChar.code) {
                sb.setCharAt(0, replacementSpacingChar)
            } else {
                val combining = Data.sAccentToCombining[deadCodePoint]
                sb.appendCodePoint(if (0 == combining) deadCodePoint else combining)
            }
            codePointIndex += if (Character.isSupplementaryCodePoint(deadCodePoint)) 2 else 1
        }
        val normalizedString = Normalizer.normalize(sb, Normalizer.Form.NFC)
        val resultEvent = createEventChainFromSequence(normalizedString, event)
        mDeadSequence.setLength(0)
        return resultEvent
    }

    override fun reset() {
        mDeadSequence.setLength(0)
    }

    override val combiningStateFeedback: CharSequence
        get() = mDeadSequence

    companion object {
        private fun createEventChainFromSequence(text: CharSequence, originalEvent: Event): Event {
            var index = text.length
            if (index <= 0) {
                return originalEvent
            }
            lateinit var lastEvent: Event
            do {
                val codePoint = Character.codePointBefore(text, index)
                lastEvent = Event.createHardwareKeypressEvent(codePoint,
                        originalEvent.mKeyCode, lastEvent, false /* isKeyRepeat */)
                index -= Character.charCount(codePoint)
            } while (index > 0)
            return lastEvent
        }
    }
}