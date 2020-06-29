package org.dslul.openboard.inputmethod.event

import org.dslul.openboard.inputmethod.latin.common.Constants
import java.lang.StringBuilder
import java.util.ArrayList

class HangulCombiner : Combiner {

    val composingWord = StringBuilder()

    val history: MutableList<HangulSyllable> = mutableListOf()
    val syllable: HangulSyllable? get() = history.lastOrNull()

    override fun processEvent(previousEvents: ArrayList<Event>?, event: Event?): Event? {
        if(event == null) return event
        if(Character.isWhitespace(event.mCodePoint)) {
            val text = combiningStateFeedback
            reset()
            return createEventChainFromSequence(text, event)
        }
        if(event.isFunctionalKeyEvent) {
            if(event.mKeyCode == Constants.CODE_DELETE) {
                return when {
                    history.size == 1 && composingWord.isEmpty() ||
                            history.isEmpty() && composingWord.length == 1 -> {
                        reset()
                        Event.createHardwareKeypressEvent(0x20, Constants.CODE_SPACE, event, event.isKeyRepeat)
                    }
                    history.isNotEmpty() -> {
                        history.removeAt(history.lastIndex)
                        Event.createConsumedEvent(event)
                    }
                    composingWord.isNotEmpty() -> {
                        composingWord.deleteCharAt(composingWord.lastIndex)
                        Event.createConsumedEvent(event)
                    }
                    else -> event
                }
            }
            val text = combiningStateFeedback
            reset()
            return createEventChainFromSequence(text, event)
        } else {
            val currentSyllable = syllable ?: HangulSyllable()
            val jamo = HangulJamo.of(event.mCodePoint)
            if(jamo is HangulJamo.NonHangul) {
                val text = combiningStateFeedback
                reset()
                return createEventChainFromSequence(text, event)
            } else {
                when(jamo) {
                    is HangulJamo.Initial -> {
                        if(currentSyllable.initial != null) {
                            composingWord.append(currentSyllable.string)
                            history.clear()
                            history += HangulSyllable(initial = jamo)
                        }
                        else {
                            history += currentSyllable.copy(initial = jamo)
                        }
                    }
                    is HangulJamo.Medial -> {
                        if(currentSyllable.medial != null) {
                            composingWord.append(currentSyllable.string)
                            history.clear()
                            history += HangulSyllable(medial = jamo)
                        } else {
                            history += currentSyllable.copy(medial = jamo)
                        }
                    }
                    is HangulJamo.Final -> {
                        if(currentSyllable.final != null) {
                            composingWord.append(currentSyllable.string)
                            history.clear()
                            history += HangulSyllable(final = jamo)
                        } else {
                            history += currentSyllable.copy(final = jamo)
                        }
                    }
                }
            }
        }

        return Event.createConsumedEvent(event)
    }

    override val combiningStateFeedback: CharSequence
        get() = composingWord.toString() + (syllable?.string ?: "")

    override fun reset() {
        composingWord.setLength(0)
        history.clear()
    }

    sealed class HangulJamo {
        abstract val codePoint: Int
        abstract val modern: Boolean
        val string: String get() = codePoint.toChar().toString()
        data class NonHangul(override val codePoint: Int) : HangulJamo() {
            override val modern: Boolean get() = false
        }
        data class Initial(override val codePoint: Int) : HangulJamo() {
            override val modern: Boolean get() = codePoint in 0x1100 .. 0x1112
            val ordinal: Int get() = codePoint - 0x1100
            fun toConsonant(): Consonant? {
                val codePoint = COMPAT_CONSONANTS.getOrNull(CONVERT_INITIALS.indexOf(codePoint.toChar())) ?: return null
                if(codePoint.toInt() == 0) return null
                return Consonant(codePoint.toInt())
            }
        }
        data class Medial(override val codePoint: Int) : HangulJamo() {
            override val modern: Boolean get() = codePoint in 1161 .. 0x1175
            val ordinal: Int get() = codePoint - 0x1161
            fun toVowel(): Vowel? {
                val codePoint = COMPAT_VOWELS.getOrNull(CONVERT_MEDIALS.indexOf(codePoint.toChar())) ?: return null
                return Vowel(codePoint.toInt())
            }
        }
        data class Final(override val codePoint: Int) : HangulJamo() {
            override val modern: Boolean get() = codePoint in 0x11a8 .. 0x11c2
            val ordinal: Int get() = codePoint - 0x11a7
            fun toConsonant(): Consonant? {
                val codePoint = COMPAT_CONSONANTS.getOrNull(CONVERT_FINALS.indexOf(codePoint.toChar())) ?: return null
                if(codePoint.toInt() == 0) return null
                return Consonant(codePoint.toInt())
            }
        }
        data class Consonant(override val codePoint: Int) : HangulJamo() {
            override val modern: Boolean get() = codePoint in 0x3131 .. 0x314e
            val ordinal: Int get() = codePoint - 0x3131
            fun toInitial(): Initial? {
                val codePoint = CONVERT_INITIALS.getOrNull(COMPAT_CONSONANTS.indexOf(codePoint.toChar())) ?: return null
                if(codePoint.toInt() == 0) return null
                return Initial(codePoint.toInt())
            }
            fun toFinal(): Final? {
                val codePoint = CONVERT_FINALS.getOrNull(COMPAT_CONSONANTS.indexOf(codePoint.toChar())) ?: return null
                if(codePoint.toInt() == 0) return null
                return Final(codePoint.toInt())
            }
        }
        data class Vowel(override val codePoint: Int) : HangulJamo() {
            override val modern: Boolean get() = codePoint in 0x314f .. 0x3163
            val ordinal: Int get() = codePoint - 0x314f1
            fun toMedial(): Medial? {
                val codePoint = CONVERT_MEDIALS.getOrNull(COMPAT_VOWELS.indexOf(codePoint.toChar())) ?: return null
                if(codePoint.toInt() == 0) return null
                return Medial(codePoint.toInt())
            }
        }
        companion object {
            const val COMPAT_CONSONANTS = "ㄱㄲㄳㄴㄵㄶㄷㄸㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅃㅄㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
            const val COMPAT_VOWELS = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
            const val CONVERT_INITIALS = "ᄀᄁ\u0000ᄂ\u0000\u0000ᄃᄄᄅ\u0000\u0000\u0000\u0000\u0000\u0000\u0000ᄆᄇᄈ\u0000ᄉᄊᄋᄌᄍᄎᄏᄐᄑᄒ"
            const val CONVERT_MEDIALS = "ᅡᅢᅣᅤᅥᅦᅧᅨᅩᅪᅫᅬᅭᅮᅯᅰᅱᅲᅳᅴᅵ"
            const val CONVERT_FINALS = "ᆨᆩᆪᆫᆬᆭᆮ\u0000ᆯᆰᆱᆲᆳᆴᆵᆶᆷᆸ\u0000ᆹᆺᆻᆼᆽ\u0000ᆾᆿᇀᇁᇂ"
            fun of(codePoint: Int): HangulJamo {
                return when(codePoint) {
                    in 0x1100 .. 0x115f -> Initial(codePoint)
                    in 0x1160 .. 0x11a7 -> Medial(codePoint)
                    in 0x11a8 .. 0x11ff -> Final(codePoint)
                    else -> NonHangul(codePoint)
                }
            }
        }
    }

    data class HangulSyllable(
            val initial: HangulJamo.Initial? = null,
            val medial: HangulJamo.Medial? = null,
            val final: HangulJamo.Final? = null
    ) {
        val combinable: Boolean get() = (initial?.modern ?: false) && (medial?.modern ?: false) && (final?.modern ?: true)
        val combined: String get() = (0xac00 + (initial?.ordinal ?: 0) * 21 * 28
                + (medial?.ordinal ?: 0) * 28
                + (final?.ordinal ?: 0)).toChar().toString()
        val uncombined: String get() = (initial?.string ?: "") + (medial?.string ?: "") + (final?.string ?: "")
        val uncombinedCompat: String get() = (initial?.toConsonant()?.string ?: "") +
                (medial?.toVowel()?.string ?: "") + (final?.toConsonant()?.string ?: "")
        val string: String get() = if(this.combinable) this.combined else this.uncombinedCompat
    }

    companion object {
        private fun createEventChainFromSequence(text: CharSequence, originalEvent: Event?): Event? {
            var index = text.length
            if (index <= 0) {
                return originalEvent
            }
            var lastEvent: Event? = originalEvent
            do {
                val codePoint = Character.codePointBefore(text, index)
                lastEvent = Event.Companion.createHardwareKeypressEvent(codePoint,
                        originalEvent!!.mKeyCode, lastEvent, false /* isKeyRepeat */)
                index -= Character.charCount(codePoint)
            } while (index > 0)
            return lastEvent
        }
    }

}
