package helium314.keyboard.keyboard

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.inputlogic.InputLogic
import helium314.keyboard.latin.settings.Settings
import kotlin.math.abs

class KeyboardActionListenerImpl(private val latinIME: LatinIME, private val inputLogic: InputLogic) : KeyboardActionListener {

    private val keyboardSwitcher = KeyboardSwitcher.getInstance()
    private val settings = Settings.getInstance()

    override fun onPressKey(primaryCode: Int, repeatCount: Int, isSinglePointer: Boolean) {
        keyboardSwitcher.onPressKey(primaryCode, isSinglePointer, latinIME.currentAutoCapsState, latinIME.currentRecapitalizeState)
        latinIME.hapticAndAudioFeedback(primaryCode, repeatCount)
    }

    override fun onReleaseKey(primaryCode: Int, withSliding: Boolean) {
        keyboardSwitcher.onReleaseKey(primaryCode, withSliding, latinIME.currentAutoCapsState, latinIME.currentRecapitalizeState)
    }

    override fun onCodeInput(primaryCode: Int, x: Int, y: Int, isKeyRepeat: Boolean) =
        latinIME.onCodeInput(primaryCode, x, y, isKeyRepeat)

    override fun getLongClickToolbarKeyCode(primaryCode: Int): Int {
        return latinIME.getLongClickToolbarKeyCode(primaryCode)
    }

    override fun onTextInput(text: String?) = latinIME.onTextInput(text)

    override fun onStartBatchInput() = latinIME.onStartBatchInput()

    override fun onUpdateBatchInput(batchPointers: InputPointers?) = latinIME.onUpdateBatchInput(batchPointers)

    override fun onEndBatchInput(batchPointers: InputPointers?) = latinIME.onEndBatchInput(batchPointers)

    override fun onCancelBatchInput() = latinIME.onCancelBatchInput()

    // User released a finger outside any key
    override fun onCancelInput() { }

    override fun onFinishSlidingInput() =
        keyboardSwitcher.onFinishSlidingInput(latinIME.currentAutoCapsState, latinIME.currentRecapitalizeState)

    override fun onCustomRequest(requestCode: Int): Boolean {
        if (requestCode == Constants.CUSTOM_CODE_SHOW_INPUT_METHOD_PICKER)
            return latinIME.showInputPickerDialog()
        return false
    }

    override fun onHorizontalSpaceSwipe(steps: Int): Boolean = when (Settings.getInstance().current.mSpaceSwipeHorizontal) {
        KeyboardActionListener.SWIPE_MOVE_CURSOR -> onMoveCursorHorizontally(steps)
        KeyboardActionListener.SWIPE_SWITCH_LANGUAGE -> onLanguageSlide(steps)
        else -> false
    }

    override fun onVerticalSpaceSwipe(steps: Int): Boolean = when (Settings.getInstance().current.mSpaceSwipeVertical) {
        KeyboardActionListener.SWIPE_MOVE_CURSOR -> onMoveCursorVertically(steps)
        KeyboardActionListener.SWIPE_SWITCH_LANGUAGE -> onLanguageSlide(steps)
        else -> false
    }

    override fun onMoveDeletePointer(steps: Int) {
        inputLogic.finishInput()
        val end = inputLogic.mConnection.expectedSelectionEnd
        val start = inputLogic.mConnection.expectedSelectionStart + steps
        if (start > end) return
        inputLogic.mConnection.setSelection(start, end)
    }

    override fun onUpWithDeletePointerActive() {
        if (!inputLogic.mConnection.hasSelection()) return
        inputLogic.finishInput()
        onCodeInput(KeyCode.DELETE, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
    }

    private fun onLanguageSlide(steps: Int): Boolean {
        if (abs(steps) < 4) return false
        val subtypes = RichInputMethodManager.getInstance().getMyEnabledInputMethodSubtypeList(false)
        if (subtypes.size <= 1) { // only allow if we have more than one subtype
            return false
        }
        // decide next or previous dependent on up or down
        val current = RichInputMethodManager.getInstance().currentSubtype.rawSubtype
        var wantedIndex = subtypes.indexOf(current) + if (steps > 0) 1 else -1
        wantedIndex %= subtypes.size
        if (wantedIndex < 0)
            wantedIndex += subtypes.size
        KeyboardSwitcher.getInstance().switchToSubtype(subtypes[wantedIndex])
        return true
    }

    private fun onMoveCursorVertically(steps: Int): Boolean {
        if (steps == 0) return false
        val code = if (steps < 0) KeyCode.ARROW_UP else KeyCode.ARROW_DOWN
        onCodeInput(code, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
        return true
    }

    private fun onMoveCursorHorizontally(_steps: Int): Boolean {
        if (_steps == 0) return false
        var steps = _steps
        // for RTL languages we want to invert pointer movement
        if (RichInputMethodManager.getInstance().currentSubtype.isRtlSubtype) steps = -steps
        val moveSteps: Int
        if (steps < 0) {
            val availableCharacters: Int = inputLogic.mConnection.getTextBeforeCursor(64, 0).length
            moveSteps = if (availableCharacters < -steps) -availableCharacters else steps
            if (moveSteps == 0) {
                // some apps don't return any text via input connection, and the cursor can't be moved
                // we fall back to virtually pressing the left/right key one or more times instead
                while (steps != 0) {
                    onCodeInput(KeyCode.ARROW_LEFT, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
                    ++steps
                }
                return true
            }
        } else {
            val availableCharacters: Int = inputLogic.mConnection.getTextAfterCursor(64, 0).length
            moveSteps = availableCharacters.coerceAtMost(steps)
            if (moveSteps == 0) {
                while (steps != 0) {
                    onCodeInput(KeyCode.ARROW_RIGHT, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
                    --steps
                }
                return true
            }
        }
        if (inputLogic.moveCursorByAndReturnIfInsideComposingWord(moveSteps)) {
            // no need to finish input and restart suggestions if we're still in the word
            // this is a noticeable performance improvement
            val newPosition: Int = inputLogic.mConnection.mExpectedSelStart + moveSteps
            inputLogic.mConnection.setSelection(newPosition, newPosition)
            return true
        }
        inputLogic.finishInput()
        val newPosition: Int = inputLogic.mConnection.mExpectedSelStart + moveSteps
        inputLogic.mConnection.setSelection(newPosition, newPosition)
        inputLogic.restartSuggestionsOnWordTouchedByCursor(settings.current, keyboardSwitcher.currentKeyboardScript)
        return true
    }

}
