/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dslul.openboard.inputmethod.event

import android.view.KeyCharacterMap
import android.view.KeyEvent
import org.dslul.openboard.inputmethod.latin.common.Constants

/**
 * A hardware event decoder for a hardware qwerty-ish keyboard.
 *
 * The events are always hardware keypresses, but they can be key down or key up events, they
 * can be dead keys, they can be meta keys like shift or ctrl... This does not deal with
 * 10-key like keyboards; a different decoder is used for this.
 */
// TODO: get the layout for this hardware keyboard
class HardwareKeyboardEventDecoder(val mDeviceId: Int) : HardwareEventDecoder {
    override fun decodeHardwareKey(keyEvent: KeyEvent): Event {
        // KeyEvent#getUnicodeChar() does not exactly returns a unicode char, but rather a value
        // that includes both the unicode char in the lower 21 bits and flags in the upper bits,
        // hence the name "codePointAndFlags". {@see KeyEvent#getUnicodeChar()} for more info.
        val codePointAndFlags = keyEvent.unicodeChar
        // The keyCode is the abstraction used by the KeyEvent to represent different keys that
        // do not necessarily map to a unicode character. This represents a physical key, like
        // the key for 'A' or Space, but also Backspace or Ctrl or Caps Lock.
        val keyCode = keyEvent.keyCode
        val isKeyRepeat = 0 != keyEvent.repeatCount
        if (KeyEvent.KEYCODE_DEL == keyCode) {
            return Event.createHardwareKeypressEvent(Event.NOT_A_CODE_POINT, Constants.CODE_DELETE, null /* next */, isKeyRepeat)
        }
        if (keyEvent.isPrintingKey || KeyEvent.KEYCODE_SPACE == keyCode || KeyEvent.KEYCODE_ENTER == keyCode) {
            if (0 != codePointAndFlags and KeyCharacterMap.COMBINING_ACCENT) { // A dead key.
                return Event.createDeadEvent(
                        codePointAndFlags and KeyCharacterMap.COMBINING_ACCENT_MASK, keyCode, null /* next */)
            }
            return if (KeyEvent.KEYCODE_ENTER == keyCode) {
                // The Enter key. If the Shift key is not being pressed, this should send a
                // CODE_ENTER to trigger the action if any, or a carriage return otherwise. If the
                // Shift key is being pressed, this should send a CODE_SHIFT_ENTER and let
                // Latin IME decide what to do with it.
                if (keyEvent.isShiftPressed) {
                    Event.createHardwareKeypressEvent(Event.NOT_A_CODE_POINT,
                            Constants.CODE_SHIFT_ENTER, null /* next */, isKeyRepeat)
                } else Event.createHardwareKeypressEvent(Constants.CODE_ENTER, keyCode,
                        null /* next */, isKeyRepeat)
            } else Event.createHardwareKeypressEvent(codePointAndFlags, keyCode, null /* next */, isKeyRepeat)
            // If not Enter, then this is just a regular keypress event for a normal character
            // that can be committed right away, taking into account the current state.
        }
        return Event.createNotHandledEvent()
    }

}