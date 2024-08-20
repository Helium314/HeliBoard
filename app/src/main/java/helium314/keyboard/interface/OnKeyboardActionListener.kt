package helium314.keyboard.`interface`

import android.view.inputmethod.InputMethodSubtype

/**
 * The SimpleKeyboardIME class uses this interface to communicate with the input connection
 */
interface OnKeyboardActionListener {
    /**
     * Called when the user presses a key. This is sent before the [.onKey] is called. For keys that repeat, this is only called once.
     * @param primaryCode the unicode of the key being pressed. If the touch is not on a valid key, the value will be zero.
     */
    fun onPress(primaryCode: Int)

    /**
     * Send a key press to the listener.
     * @param code this is the key that was pressed
     */
    fun onKey(code: Int)

    /**
     * Called when the finger has been lifted after pressing a key
     */
    fun onActionUp()

    /**
     * Called when the user long presses Space and moves to the left
     */
    fun moveCursorLeft()

    /**
     * Called when the user long presses Space and moves to the right
     */
    fun moveCursorRight()

    /**
     * Sends a sequence of characters to the listener.
     * @param text the string to be displayed.
     */
    fun onText(text: String)

    /**
     * Called to force the KeyboardView to reload the keyboard
     */
    fun reloadKeyboard()

    /**
     * Called when input method is changed in-app.
     */
    fun changeInputMethod(id: String, subtype: InputMethodSubtype)
}
