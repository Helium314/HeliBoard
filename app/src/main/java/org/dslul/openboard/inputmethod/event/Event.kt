package org.dslul.openboard.inputmethod.event

import org.dslul.openboard.inputmethod.annotations.ExternallyReferenced
import org.dslul.openboard.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import org.dslul.openboard.inputmethod.latin.common.Constants
import org.dslul.openboard.inputmethod.latin.common.StringUtils

/**
 * Class representing a generic input event as handled by Latin IME.
 *
 * This contains information about the origin of the event, but it is generalized and should
 * represent a software keypress, hardware keypress, or d-pad move alike.
 * Very importantly, this does not necessarily result in inputting one character, or even anything
 * at all - it may be a dead key, it may be a partial input, it may be a special key on the
 * keyboard, it may be a cancellation of a keypress (e.g. in a soft keyboard the finger of the
 * user has slid out of the key), etc. It may also be a batch input from a gesture or handwriting
 * for example.
 * The combiner should figure out what to do with this.
 */
class Event private constructor(
        // The type of event - one of the constants above
        private val mEventType: Int,
        // If applicable, this contains the string that should be input.
        val mText: CharSequence?,
        // The code point associated with the event, if relevant. This is a unicode code point, and
        // has nothing to do with other representations of the key. It is only relevant if this event
        // is of KEYPRESS type, but for a mode key like hankaku/zenkaku or ctrl, there is no code point
        // associated so this should be NOT_A_CODE_POINT to avoid unintentional use of its value when
        // it's not relevant.
        val mCodePoint: Int,
        // The key code associated with the event, if relevant. This is relevant whenever this event
        // has been triggered by a key press, but not for a gesture for example. This has conceptually
        // no link to the code point, although keys that enter a straight code point may often set
        // this to be equal to mCodePoint for convenience. If this is not a key, this must contain
        // NOT_A_KEY_CODE.
        val mKeyCode: Int,
        // Coordinates of the touch event, if relevant. If useful, we may want to replace this with
        // a MotionEvent or something in the future. This is only relevant when the keypress is from
        // a software keyboard obviously, unless there are touch-sensitive hardware keyboards in the
        // future or some other awesome sauce.
        val mX: Int, val mY: Int,
        // If this is of type EVENT_TYPE_SUGGESTION_PICKED, this must not be null (and must be null in
        // other cases).
        val mSuggestedWordInfo: SuggestedWordInfo?,
        // Some flags that can't go into the key code. It's a bit field of FLAG_*
        private val mFlags: Int,
        // The next event, if any. Null if there is no next event yet.
        val mNextEvent: Event?
        // This logic may need to be refined in the future
) {

    // Returns whether this is a function key like backspace, ctrl, settings... as opposed to keys
    // that result in input like letters or space.
    val isFunctionalKeyEvent: Boolean
        get() = NOT_A_CODE_POINT == mCodePoint // This logic may need to be refined in the future

    // Returns whether this event is for a dead character. @see {@link #FLAG_DEAD}
    val isDead: Boolean get() = 0 != FLAG_DEAD and mFlags

    val isKeyRepeat: Boolean get() = 0 != FLAG_REPEAT and mFlags

    val isConsumed: Boolean get() = 0 != FLAG_CONSUMED and mFlags

    val isCombining: Boolean get() = 0 != FLAG_COMBINING and mFlags

    val isGesture: Boolean get() = EVENT_TYPE_GESTURE == mEventType

    // Returns whether this is a fake key press from the suggestion strip. This happens with
    // punctuation signs selected from the suggestion strip.
    val isSuggestionStripPress: Boolean get() = EVENT_TYPE_SUGGESTION_PICKED == mEventType

    val isHandled: Boolean get() = EVENT_TYPE_NOT_HANDLED != mEventType

    // A consumed event should input no text.
    val textToCommit: CharSequence?
        get() {
            if (isConsumed) {
                return "" // A consumed event should input no text.
            }
            when (mEventType) {
                EVENT_TYPE_MODE_KEY, EVENT_TYPE_NOT_HANDLED, EVENT_TYPE_TOGGLE, EVENT_TYPE_CURSOR_MOVE -> return ""
                EVENT_TYPE_INPUT_KEYPRESS -> return StringUtils.newSingleCodePointString(mCodePoint)
                EVENT_TYPE_GESTURE, EVENT_TYPE_SOFTWARE_GENERATED_STRING, EVENT_TYPE_SUGGESTION_PICKED -> return mText
            }
            throw RuntimeException("Unknown event type: $mEventType")
        }

    companion object {
        // Should the types below be represented by separate classes instead? It would be cleaner
        // but probably a bit too much
        // An event we don't handle in Latin IME, for example pressing Ctrl on a hardware keyboard.
        const val EVENT_TYPE_NOT_HANDLED = 0
        // A key press that is part of input, for example pressing an alphabetic character on a
        // hardware qwerty keyboard. It may be part of a sequence that will be re-interpreted later
        // through combination.
        const val EVENT_TYPE_INPUT_KEYPRESS = 1
        // A toggle event is triggered by a key that affects the previous character. An example would
        // be a numeric key on a 10-key keyboard, which would toggle between 1 - a - b - c with
        // repeated presses.
        const val EVENT_TYPE_TOGGLE = 2
        // A mode event instructs the combiner to change modes. The canonical example would be the
        // hankaku/zenkaku key on a Japanese keyboard, or even the caps lock key on a qwerty keyboard
        // if handled at the combiner level.
        const val EVENT_TYPE_MODE_KEY = 3
        // An event corresponding to a gesture.
        const val EVENT_TYPE_GESTURE = 4
        // An event corresponding to the manual pick of a suggestion.
        const val EVENT_TYPE_SUGGESTION_PICKED = 5
        // An event corresponding to a string generated by some software process.
        const val EVENT_TYPE_SOFTWARE_GENERATED_STRING = 6
        // An event corresponding to a cursor move
        const val EVENT_TYPE_CURSOR_MOVE = 7
        // 0 is a valid code point, so we use -1 here.
        const val NOT_A_CODE_POINT = -1
        // -1 is a valid key code, so we use 0 here.
        const val NOT_A_KEY_CODE = 0
        private const val FLAG_NONE = 0
        // This event is a dead character, usually input by a dead key. Examples include dead-acute
        // or dead-abovering.
        private const val FLAG_DEAD = 0x1
        // This event is coming from a key repeat, software or hardware.
        private const val FLAG_REPEAT = 0x2
        // This event has already been consumed.
        private const val FLAG_CONSUMED = 0x4
        // This event is a combining character, usually a hangul input.
        private const val FLAG_COMBINING = 0x8

        @JvmStatic
        fun createSoftwareKeypressEvent(codePoint: Int, keyCode: Int,
                                        x: Int, y: Int, isKeyRepeat: Boolean): Event {
            return Event(EVENT_TYPE_INPUT_KEYPRESS, null /* text */, codePoint, keyCode, x, y,
                    null /* suggestedWordInfo */, if (isKeyRepeat) FLAG_REPEAT else FLAG_NONE, null)
        }

        fun createHardwareKeypressEvent(codePoint: Int, keyCode: Int,
                                        next: Event?, isKeyRepeat: Boolean): Event {
            return Event(EVENT_TYPE_INPUT_KEYPRESS, null /* text */, codePoint, keyCode,
                    Constants.EXTERNAL_KEYBOARD_COORDINATE, Constants.EXTERNAL_KEYBOARD_COORDINATE,
                    null /* suggestedWordInfo */, if (isKeyRepeat) FLAG_REPEAT else FLAG_NONE, next)
        }

        // This creates an input event for a dead character. @see {@link #FLAG_DEAD}
        @ExternallyReferenced
        fun createDeadEvent(codePoint: Int, keyCode: Int, next: Event?): Event { // TODO: add an argument or something if we ever create a software layout with dead keys.
            return Event(EVENT_TYPE_INPUT_KEYPRESS, null /* text */, codePoint, keyCode,
                    Constants.EXTERNAL_KEYBOARD_COORDINATE, Constants.EXTERNAL_KEYBOARD_COORDINATE,
                    null /* suggestedWordInfo */, FLAG_DEAD, next)
        }

        /**
         * Create an input event with nothing but a code point. This is the most basic possible input
         * event; it contains no information on many things the IME requires to function correctly,
         * so avoid using it unless really nothing is known about this input.
         * @param codePoint the code point.
         * @return an event for this code point.
         */
        @JvmStatic
        fun createEventForCodePointFromUnknownSource(codePoint: Int): Event { // TODO: should we have a different type of event for this? After all, it's not a key press.
            return Event(EVENT_TYPE_INPUT_KEYPRESS, null /* text */, codePoint, NOT_A_KEY_CODE,
                    Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE,
                    null /* suggestedWordInfo */, FLAG_NONE, null /* next */)
        }

        /**
         * Creates an input event with a code point and x, y coordinates. This is typically used when
         * resuming a previously-typed word, when the coordinates are still known.
         * @param codePoint the code point to input.
         * @param x the X coordinate.
         * @param y the Y coordinate.
         * @return an event for this code point and coordinates.
         */
        @JvmStatic
        fun createEventForCodePointFromAlreadyTypedText(codePoint: Int,
                                                        x: Int, y: Int): Event { // TODO: should we have a different type of event for this? After all, it's not a key press.
            return Event(EVENT_TYPE_INPUT_KEYPRESS, null /* text */, codePoint, NOT_A_KEY_CODE,
                    x, y, null /* suggestedWordInfo */, FLAG_NONE, null /* next */)
        }

        /**
         * Creates an input event representing the manual pick of a suggestion.
         * @return an event for this suggestion pick.
         */
        @JvmStatic
        fun createSuggestionPickedEvent(suggestedWordInfo: SuggestedWordInfo): Event {
            return Event(EVENT_TYPE_SUGGESTION_PICKED, suggestedWordInfo.mWord,
                    NOT_A_CODE_POINT, NOT_A_KEY_CODE,
                    Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE,
                    suggestedWordInfo, FLAG_NONE, null /* next */)
        }

        /**
         * Creates an input event with a CharSequence. This is used by some software processes whose
         * output is a string, possibly with styling. Examples include press on a multi-character key,
         * or combination that outputs a string.
         * @param text the CharSequence associated with this event.
         * @param keyCode the key code, or NOT_A_KEYCODE if not applicable.
         * @param next the next event, or null if not applicable.
         * @return an event for this text.
         */
        @JvmStatic
        fun createSoftwareTextEvent(text: CharSequence?, keyCode: Int, next: Event?): Event {
            return Event(EVENT_TYPE_SOFTWARE_GENERATED_STRING, text, NOT_A_CODE_POINT, keyCode,
                Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE,
                null /* suggestedWordInfo */, FLAG_NONE, next)
        }

        @JvmStatic
        fun createSoftwareTextEvent(text: CharSequence?, keyCode: Int) =
            createSoftwareTextEvent(text, keyCode, null)

        /**
         * Creates an input event representing the manual pick of a punctuation suggestion.
         * @return an event for this suggestion pick.
         */
        @JvmStatic
        fun createPunctuationSuggestionPickedEvent(
                suggestedWordInfo: SuggestedWordInfo): Event {
            val primaryCode = suggestedWordInfo.mWord[0].code
            return Event(EVENT_TYPE_SUGGESTION_PICKED, suggestedWordInfo.mWord, primaryCode,
                    NOT_A_KEY_CODE, Constants.SUGGESTION_STRIP_COORDINATE,
                    Constants.SUGGESTION_STRIP_COORDINATE, suggestedWordInfo, FLAG_NONE,
                    null /* next */)
        }

        /**
         * Creates an input event representing moving the cursor. The relative move amount is stored
         * in mX.
         * @param moveAmount the relative move amount.
         * @return an event for this cursor move.
         */
        @JvmStatic
        fun createCursorMovedEvent(moveAmount: Int): Event {
            return Event(EVENT_TYPE_CURSOR_MOVE, null, NOT_A_CODE_POINT, NOT_A_KEY_CODE,
                    moveAmount, Constants.NOT_A_COORDINATE, null, FLAG_NONE, null)
        }

        /**
         * Creates an event identical to the passed event, but that has already been consumed.
         * @param source the event to copy the properties of.
         * @return an identical event marked as consumed.
         */
        fun createConsumedEvent(source: Event): Event { // A consumed event should not input any text at all, so we pass the empty string as text.
            return Event(source.mEventType, source.mText, source.mCodePoint, source.mKeyCode,
                    source.mX, source.mY, source.mSuggestedWordInfo, source.mFlags or FLAG_CONSUMED,
                    source.mNextEvent)
        }

        fun createCombiningEvent(source: Event): Event {
            return Event(source.mEventType, source.mText, source.mCodePoint, source.mKeyCode,
                    source.mX, source.mY, source.mSuggestedWordInfo, source.mFlags or FLAG_COMBINING,
                    source.mNextEvent)
        }

        fun createNotHandledEvent(): Event {
            return Event(EVENT_TYPE_NOT_HANDLED, null /* text */, NOT_A_CODE_POINT, NOT_A_KEY_CODE,
                    Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE,
                    null /* suggestedWordInfo */, FLAG_NONE, null)
        }
    }

    // This method is private - to create a new event, use one of the create* utility methods.
    init {
        // Sanity checks
        // mSuggestedWordInfo is non-null if and only if the type is SUGGESTION_PICKED
        if (EVENT_TYPE_SUGGESTION_PICKED == mEventType) {
            if (null == mSuggestedWordInfo) {
                throw RuntimeException("Wrong event: SUGGESTION_PICKED event must have a "
                        + "non-null SuggestedWordInfo")
            }
        } else {
            if (null != mSuggestedWordInfo) {
                throw RuntimeException("Wrong event: only SUGGESTION_PICKED events may have " +
                        "a non-null SuggestedWordInfo")
            }
        }
    }
}
