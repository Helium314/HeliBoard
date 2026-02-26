package helium314.keyboard.event.combiners

import helium314.keyboard.event.Combiner
import helium314.keyboard.event.Event

class DeadKeyPreCombiner : Combiner {
    override fun processEvent(previousEvents: ArrayList<Event>?, event: Event?): Event {
        if (event == null) return Event.createNotHandledEvent()
        // If we get a keyboard event with a combining accent character, we send it as a DeadKeyEvent
        if (event.mCodePoint in 0x300..0x35b && event.eventType == Event.EVENT_TYPE_INPUT_KEYPRESS) {
            return Event.createDeadEvent(event.mCodePoint, 0, null);
        }
        // Otherwise just pass the event as it is
        return event
    }

    override fun getCombiningStateFeedback(): CharSequence {
        return ""
        // This combiner has no state, so no state feedback
    }

    override fun reset() {
        // Nothing, this combiner has no state
    }
}