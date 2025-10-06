package helium314.keyboard.event.combiners

import android.icu.text.Normalizer2
import helium314.keyboard.event.Combiner
import helium314.keyboard.event.Event
import helium314.keyboard.latin.common.Constants
import java.util.ArrayList

class NFCNormalizingCombiner : Combiner {
    private val buffer = StringBuilder()

    companion object {
        @JvmStatic
        internal fun isHandledLetter(char: Char): Boolean =
            char.category in setOf(
                CharCategory.UPPERCASE_LETTER,   // Lu
                CharCategory.LOWERCASE_LETTER,   // Ll
                CharCategory.TITLECASE_LETTER,   // Lt
                CharCategory.MODIFIER_LETTER,    // Lm
                CharCategory.OTHER_LETTER,       // Lo
                CharCategory.NON_SPACING_MARK,   // Mn
                CharCategory.ENCLOSING_MARK,     // Me
                CharCategory.COMBINING_SPACING_MARK, // Mc
            )
    }

    override fun processEvent(
        previousEvents: ArrayList<Event?>?,
        event: Event?
    ): Event {
        if (event == null) return Event.createNotHandledEvent()
        val keypress = event.mCodePoint.toChar()
        if(!isHandledLetter(keypress)) {
            if(buffer.isNotEmpty()) {
                if (event.mKeyCode == Constants.CODE_DELETE) {
                    buffer.setLength(buffer.length - 1)
                    return Event.createConsumedEvent(event)
                }
            }
            return event
        }

        buffer.append(keypress)
        return Event.createConsumedEvent(event)
    }

    override fun getCombiningStateFeedback(): CharSequence {
        val result = Normalizer2.getNFCInstance().normalize(buffer)
        return result
    }

    override fun reset() {
        buffer.setLength(0)
    }
}