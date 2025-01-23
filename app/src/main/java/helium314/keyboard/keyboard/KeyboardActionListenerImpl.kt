package helium314.keyboard.keyboard

import android.view.KeyEvent
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.common.loopOverCodePoints
import helium314.keyboard.latin.common.loopOverCodePointsBackwards
import helium314.keyboard.latin.inputlogic.InputLogic
import helium314.keyboard.latin.settings.Settings
import kotlin.math.abs

class KeyboardActionListenerImpl(private val latinIME: LatinIME, private val inputLogic: InputLogic) : KeyboardActionListener {

    private val keyboardSwitcher = KeyboardSwitcher.getInstance()
    private val settings = Settings.getInstance()
    private var mSubtypeSwitchCount = 0 // for use with onLanguageSlide()
    private var metaState = 0 // is this enough, or are there threading issues with the different PointerTrackers?

    // todo: maybe keep meta state presses to KeyboardActionListenerImpl, and avoid calls to press/release key
    private fun adjustMetaState(code: Int, remove: Boolean) {
        val metaCode = when (code) {
            KeyCode.CTRL -> KeyEvent.META_CTRL_ON
            KeyCode.ALT -> KeyEvent.META_ALT_ON
            KeyCode.FN -> KeyEvent.META_FUNCTION_ON
            KeyCode.META -> KeyEvent.META_META_ON
            else -> return
        }
        metaState = if (remove) metaState and metaCode.inv()
            else metaState or metaCode
    }

    override fun onPressKey(primaryCode: Int, repeatCount: Int, isSinglePointer: Boolean) {
        adjustMetaState(primaryCode, false)
        keyboardSwitcher.onPressKey(primaryCode, isSinglePointer, latinIME.currentAutoCapsState, latinIME.currentRecapitalizeState)
        latinIME.hapticAndAudioFeedback(primaryCode, repeatCount)
    }

    override fun onReleaseKey(primaryCode: Int, withSliding: Boolean) {
        adjustMetaState(primaryCode, true)
        keyboardSwitcher.onReleaseKey(primaryCode, withSliding, latinIME.currentAutoCapsState, latinIME.currentRecapitalizeState)
    }

    override fun onCodeInput(primaryCode: Int, x: Int, y: Int, isKeyRepeat: Boolean) {
        val mkv = keyboardSwitcher.mainKeyboardView
        latinIME.onCodeInput(primaryCode, metaState, mkv.getKeyX(x), mkv.getKeyY(y), isKeyRepeat)
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
        KeyboardActionListener.SWIPE_TOGGLE_NUMPAD -> toggleNumpad(false, false)
        else -> false
    }

    override fun onVerticalSpaceSwipe(steps: Int): Boolean = when (Settings.getInstance().current.mSpaceSwipeVertical) {
        KeyboardActionListener.SWIPE_MOVE_CURSOR -> onMoveCursorVertically(steps)
        KeyboardActionListener.SWIPE_SWITCH_LANGUAGE -> onLanguageSlide(steps)
        KeyboardActionListener.SWIPE_TOGGLE_NUMPAD -> toggleNumpad(false, false)
        else -> false
    }

    override fun resetSubtypeSwitchCount(){
        mSubtypeSwitchCount = 0
    }

    override fun toggleNumpad(withSliding: Boolean, forceReturnToAlpha: Boolean): Boolean {
        KeyboardSwitcher.getInstance().toggleNumpad(withSliding, latinIME.currentAutoCapsState, latinIME.currentRecapitalizeState, forceReturnToAlpha)
        return true
    }

    override fun onMoveDeletePointer(steps: Int) {
        inputLogic.finishInput()
        val end = inputLogic.mConnection.expectedSelectionEnd
        var actualSteps = 0 // corrected steps to avoid splitting chars belonging to the same codepoint
        if (steps > 0) {
            val text = inputLogic.mConnection.getSelectedText(0)
            if (text == null) actualSteps = steps
            else loopOverCodePoints(text) {
                actualSteps += Character.charCount(it)
                actualSteps >= steps
            }
        } else {
            val text = inputLogic.mConnection.getTextBeforeCursor(-steps * 4, 0)
            if (text == null) actualSteps = steps
            else loopOverCodePointsBackwards(text) {
                actualSteps -= Character.charCount(it)
                actualSteps <= steps
            }
        }
        val start = inputLogic.mConnection.expectedSelectionStart + actualSteps
        if (start > end) return
        inputLogic.mConnection.setSelection(start, end)
    }

    override fun onUpWithDeletePointerActive() {
        if (!inputLogic.mConnection.hasSelection()) return
        inputLogic.finishInput()
        onCodeInput(KeyCode.DELETE, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
    }

    override fun resetMetaState() {
        metaState = 0
    }

    private fun onLanguageSlide(steps: Int): Boolean {
        if (abs(steps) < settings.current.mLanguageSwipeDistance) return false
        val subtypes = RichInputMethodManager.getInstance().getMyEnabledInputMethodSubtypeList(false)
        if (subtypes.size - 1 <= abs(mSubtypeSwitchCount)) { // only allow if we are yet to cycle through all subtypes
            return false
        }
        // decide next or previous dependent on up or down
        val current = RichInputMethodManager.getInstance().currentSubtype.rawSubtype
        var wantedIndex = subtypes.indexOf(current) + if (steps > 0) 1 else -1
        wantedIndex %= subtypes.size
        if (wantedIndex < 0)
            wantedIndex += subtypes.size
        KeyboardSwitcher.getInstance().switchToSubtype(subtypes[wantedIndex])
        if (steps > 0) mSubtypeSwitchCount++ else mSubtypeSwitchCount--
        return true
    }

    private fun onMoveCursorVertically(steps: Int): Boolean {
        if (steps == 0) return false
        val code = if (steps < 0) KeyCode.ARROW_UP else KeyCode.ARROW_DOWN
        onCodeInput(code, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
        return true
    }

    private fun onMoveCursorHorizontally(rawSteps: Int): Boolean {
        if (rawSteps == 0) return false
        // for RTL languages we want to invert pointer movement
        val steps = if (RichInputMethodManager.getInstance().currentSubtype.isRtlSubtype) -rawSteps else rawSteps
        val moveSteps: Int
        if (steps < 0) {
            var actualSteps = 0 // corrected steps to avoid splitting chars belonging to the same codepoint
            val text = inputLogic.mConnection.getTextBeforeCursor(-steps * 4, 0) ?: return false
            loopOverCodePointsBackwards(text) {
                if (StringUtils.mightBeEmoji(it)) {
                    actualSteps = 0
                    return@loopOverCodePointsBackwards true
                }
                actualSteps -= Character.charCount(it)
                actualSteps <= steps
            }
            moveSteps = -text.length.coerceAtMost(abs(actualSteps))
            if (moveSteps == 0) {
                // some apps don't return any text via input connection, and the cursor can't be moved
                // we fall back to virtually pressing the left/right key one or more times instead
                repeat(-steps) {
                    onCodeInput(KeyCode.ARROW_LEFT, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
                }
                return true
            }
        } else {
            var actualSteps = 0 // corrected steps to avoid splitting chars belonging to the same codepoint
            val text = inputLogic.mConnection.getTextAfterCursor(steps * 4, 0) ?: return false
            loopOverCodePoints(text) {
                if (StringUtils.mightBeEmoji(it)) {
                    actualSteps = 0
                    return@loopOverCodePoints true
                }
                actualSteps += Character.charCount(it)
                actualSteps >= steps
            }
            moveSteps = text.length.coerceAtMost(actualSteps)
            if (moveSteps == 0) {
                repeat(steps) {
                    onCodeInput(KeyCode.ARROW_RIGHT, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
                }
                return true
            }
        }
        if (inputLogic.moveCursorByAndReturnIfInsideComposingWord(moveSteps)) {
            // no need to finish input and restart suggestions if we're still in the word
            // this is a noticeable performance improvement
            val newPosition = inputLogic.mConnection.expectedSelectionStart + moveSteps
            inputLogic.mConnection.setSelection(newPosition, newPosition)
            return true
        }
        inputLogic.finishInput()
        val newPosition = inputLogic.mConnection.expectedSelectionStart + moveSteps
        inputLogic.mConnection.setSelection(newPosition, newPosition)
        inputLogic.restartSuggestionsOnWordTouchedByCursor(settings.current, keyboardSwitcher.currentKeyboardScript)
        return true
    }

}
