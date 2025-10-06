package helium314.keyboard.event.combiners.wylie

import android.text.TextUtils
import helium314.keyboard.event.Combiner
import helium314.keyboard.event.Event
import helium314.keyboard.latin.common.Constants
import java.util.ArrayList

/**
 * Combiner that converts Extended Wylie transliteration to Tibetan script
 * Useful for Tibetan and Dzongkha languages
 * Uses the ewts-converter library: https://github.com/buda-base/ewts-converter
 */
class WylieCombiner: Combiner {
    private val buffer = StringBuilder() //Holds a single syllable (as divided by "tsheg"s)
    private val ewtsConverter = EwtsConverter()

    private fun isWylie(char: Char): Boolean {
        return char.code <= 0x7f &&
                (char.isLetter() ||
                        char in listOf('\'', '+', '-', '.', '~', '`', '&', '?') )
    } //ASCII uppercase and lowercase letters, and a few other characters used in Wylie.

    override fun processEvent(previousEvents: ArrayList<Event>?, event: Event?): Event {
        if (event == null) return Event.createNotHandledEvent()
        val keypress = event.mCodePoint.toChar()

        if (!isWylie(keypress)) {
            if (!TextUtils.isEmpty(buffer)) {
                if (event.mKeyCode == Constants.CODE_DELETE) {
                    return if (buffer.length == 1) {
                        reset()
                        Event.createHardwareKeypressEvent(0x20, Constants.CODE_SPACE,
                            event, event.isKeyRepeat)
                        // for some reason, this is needed, otherwise if there is only one letter
                        // in the buffer it won't be deleted
                    }
                    else {
                        buffer.setLength(buffer.length - 1)
                        Event.createConsumedEvent(event)
                    }
                }
            }
            return event
        }

        buffer.append(keypress)
        return Event.createConsumedEvent(event)
    }

    override fun getCombiningStateFeedback(): CharSequence {
        return ewtsConverter.toUnicode(buffer.toString())
    }

    override fun reset() {
        buffer.setLength(0)
    }
}