/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.event

import java.util.*

/**
 * A generic interface for combiners. Combiners are objects that transform chains of input events
 * into committable strings and manage feedback to show to the user on the combining state.
 */
interface Combiner {
    /**
     * Process an event, possibly combining it with the existing state and return the new event.
     *
     * If this event does not result in any new event getting passed down the chain, this method
     * returns null. It may also modify the previous event list if appropriate.
     *
     * @param previousEvents the previous events in this composition.
     * @param event the event to combine with the existing state.
     * @return the resulting event.
     */
    fun processEvent(previousEvents: ArrayList<Event>?, event: Event): Event

    /**
     * Get the feedback that should be shown to the user for the current state of this combiner.
     * @return A CharSequence representing the feedback to show users. It may include styles.
     */
    val combiningStateFeedback: CharSequence

    /**
     * Reset the state of this combiner, for example when the cursor was moved.
     */
    fun reset()
}