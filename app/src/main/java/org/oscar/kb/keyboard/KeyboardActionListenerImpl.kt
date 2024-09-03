package org.oscar.kb.keyboard

import android.view.KeyEvent
import org.oscar.kb.keyboard.internal.keyboard_parser.floris.KeyCode
import org.oscar.kb.latin.LatinIME
import org.oscar.kb.latin.RichInputMethodManager
import org.oscar.kb.latin.common.Constants
import org.oscar.kb.latin.common.InputPointers
import org.oscar.kb.latin.common.StringUtils
import org.oscar.kb.latin.common.loopOverCodePoints
import org.oscar.kb.latin.common.loopOverCodePointsBackwards
import org.oscar.kb.latin.inputlogic.InputLogic
import org.oscar.kb.latin.settings.Settings
import kotlin.math.abs

class KeyboardActionListenerImpl(private val latinIME: _root_ide_package_.org.oscar.kb.latin.LatinIME, private val inputLogic: _root_ide_package_.org.oscar.kb.latin.inputlogic.InputLogic) :
    _root_ide_package_.org.oscar.kb.keyboard.KeyboardActionListener {

    private val keyboardSwitcher =
        _root_ide_package_.org.oscar.kb.keyboard.KeyboardSwitcher.getInstance()
    private val settings = _root_ide_package_.org.oscar.kb.latin.settings.Settings.getInstance()
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

    override fun onCodeInput(primaryCode: Int, x: Int, y: Int, isKeyRepeat: Boolean) =
        latinIME.onCodeInput(primaryCode, metaState, x, y, isKeyRepeat)

    override fun onTextInput(text: String?) = latinIME.onTextInput(text)

    override fun onStartBatchInput() = latinIME.onStartBatchInput()

    override fun onUpdateBatchInput(batchPointers: _root_ide_package_.org.oscar.kb.latin.common.InputPointers?) = latinIME.onUpdateBatchInput(batchPointers)

    override fun onEndBatchInput(batchPointers: _root_ide_package_.org.oscar.kb.latin.common.InputPointers?) = latinIME.onEndBatchInput(batchPointers)

    override fun onCancelBatchInput() = latinIME.onCancelBatchInput()

    // User released a finger outside any key
    override fun onCancelInput() { }

    override fun onFinishSlidingInput() =
        keyboardSwitcher.onFinishSlidingInput(latinIME.currentAutoCapsState, latinIME.currentRecapitalizeState)

    override fun onCustomRequest(requestCode: Int): Boolean {
        if (requestCode == _root_ide_package_.org.oscar.kb.latin.common.Constants.CUSTOM_CODE_SHOW_INPUT_METHOD_PICKER)
            return latinIME.showInputPickerDialog()
        return false
    }

    override fun onHorizontalSpaceSwipe(steps: Int): Boolean = when (_root_ide_package_.org.oscar.kb.latin.settings.Settings.getInstance().current.mSpaceSwipeHorizontal) {
        _root_ide_package_.org.oscar.kb.keyboard.KeyboardActionListener.SWIPE_MOVE_CURSOR -> onMoveCursorHorizontally(steps)
        _root_ide_package_.org.oscar.kb.keyboard.KeyboardActionListener.SWIPE_SWITCH_LANGUAGE -> onLanguageSlide(steps)
        _root_ide_package_.org.oscar.kb.keyboard.KeyboardActionListener.SWIPE_TOGGLE_NUMPAD -> toggleNumpad(false, false)
        else -> false
    }

    override fun onVerticalSpaceSwipe(steps: Int): Boolean = when (_root_ide_package_.org.oscar.kb.latin.settings.Settings.getInstance().current.mSpaceSwipeVertical) {
        _root_ide_package_.org.oscar.kb.keyboard.KeyboardActionListener.SWIPE_MOVE_CURSOR -> onMoveCursorVertically(steps)
        _root_ide_package_.org.oscar.kb.keyboard.KeyboardActionListener.SWIPE_SWITCH_LANGUAGE -> onLanguageSlide(steps)
        _root_ide_package_.org.oscar.kb.keyboard.KeyboardActionListener.SWIPE_TOGGLE_NUMPAD -> toggleNumpad(false, false)
        else -> false
    }

    override fun toggleNumpad(withSliding: Boolean, forceReturnToAlpha: Boolean): Boolean {
        _root_ide_package_.org.oscar.kb.keyboard.KeyboardSwitcher.getInstance().toggleNumpad(withSliding, latinIME.currentAutoCapsState, latinIME.currentRecapitalizeState, forceReturnToAlpha)
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
        onCodeInput(KeyCode.DELETE, _root_ide_package_.org.oscar.kb.latin.common.Constants.NOT_A_COORDINATE, _root_ide_package_.org.oscar.kb.latin.common.Constants.NOT_A_COORDINATE, false)
    }

    override fun resetMetaState() {
        metaState = 0
    }

    private fun onLanguageSlide(steps: Int): Boolean {
        if (abs(steps) < 4) return false
        val subtypes = _root_ide_package_.org.oscar.kb.latin.RichInputMethodManager.getInstance().getMyEnabledInputMethodSubtypeList(false)
        if (subtypes.size <= 1) { // only allow if we have more than one subtype
            return false
        }
        // decide next or previous dependent on up or down
        val current = _root_ide_package_.org.oscar.kb.latin.RichInputMethodManager.getInstance().currentSubtype.rawSubtype
        var wantedIndex = subtypes.indexOf(current) + if (steps > 0) 1 else -1
        wantedIndex %= subtypes.size
        if (wantedIndex < 0)
            wantedIndex += subtypes.size
        _root_ide_package_.org.oscar.kb.keyboard.KeyboardSwitcher.getInstance().switchToSubtype(subtypes[wantedIndex])
        return true
    }

    private fun onMoveCursorVertically(steps: Int): Boolean {
        if (steps == 0) return false
        val code = if (steps < 0) KeyCode.ARROW_UP else KeyCode.ARROW_DOWN
        onCodeInput(code, _root_ide_package_.org.oscar.kb.latin.common.Constants.NOT_A_COORDINATE, _root_ide_package_.org.oscar.kb.latin.common.Constants.NOT_A_COORDINATE, false)
        return true
    }

    private fun onMoveCursorHorizontally(rawSteps: Int): Boolean {
        if (rawSteps == 0) return false
        // for RTL languages we want to invert pointer movement
        val steps = if (_root_ide_package_.org.oscar.kb.latin.RichInputMethodManager.getInstance().currentSubtype.isRtlSubtype) -rawSteps else rawSteps
        val moveSteps: Int
        if (steps < 0) {
            var actualSteps = 0 // corrected steps to avoid splitting chars belonging to the same codepoint
            val text = inputLogic.mConnection.getTextBeforeCursor(-steps * 4, 0) ?: return false
            loopOverCodePointsBackwards(text) {
                if (_root_ide_package_.org.oscar.kb.latin.common.StringUtils.mightBeEmoji(it)) {
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
                    onCodeInput(KeyCode.ARROW_LEFT, _root_ide_package_.org.oscar.kb.latin.common.Constants.NOT_A_COORDINATE, _root_ide_package_.org.oscar.kb.latin.common.Constants.NOT_A_COORDINATE, false)
                }
                return true
            }
        } else {
            var actualSteps = 0 // corrected steps to avoid splitting chars belonging to the same codepoint
            val text = inputLogic.mConnection.getTextAfterCursor(steps * 4, 0) ?: return false
            loopOverCodePoints(text) {
                if (_root_ide_package_.org.oscar.kb.latin.common.StringUtils.mightBeEmoji(it)) {
                    actualSteps = 0
                    return@loopOverCodePoints true
                }
                actualSteps += Character.charCount(it)
                actualSteps >= steps
            }
            moveSteps = text.length.coerceAtMost(actualSteps)
            if (moveSteps == 0) {
                repeat(steps) {
                    onCodeInput(KeyCode.ARROW_RIGHT, _root_ide_package_.org.oscar.kb.latin.common.Constants.NOT_A_COORDINATE, _root_ide_package_.org.oscar.kb.latin.common.Constants.NOT_A_COORDINATE, false)
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
