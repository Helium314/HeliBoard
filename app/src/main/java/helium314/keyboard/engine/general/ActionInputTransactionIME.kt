package helium314.keyboard.engine.general

import helium314.keyboard.engine.IMEHelper
import helium314.keyboard.engine.IMEInterface
import helium314.keyboard.event.Event
import helium314.keyboard.latin.InputConnectionInternalComposingWrapper
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.uix.ActionInputTransaction
import helium314.keyboard.latin.uix.ExperimentalICComposing
import helium314.keyboard.latin.uix.ExperimentalICFix
import helium314.keyboard.latin.uix.getSetting
import helium314.keyboard.latin.uix.utils.TextContext
import helium314.keyboard.v2keyboard.KeyboardLayoutSetV2

class ActionInputTransactionIME(val helper: IMEHelper) : IMEInterface, ActionInputTransaction {
    val ic = if(helper.context.getSetting(ExperimentalICFix)) {
        InputConnectionInternalComposingWrapper(
            helper.context.getSetting(ExperimentalICComposing),
            false,
            helper.getCurrentInputConnection())
    } else {
        helper.getCurrentInputConnection()
    }

    override fun onCreate() {}
    override fun onDestroy() {}
    override fun onDeviceUnlocked() {}
    override fun onStartInput() {}
    override fun onOrientationChanged() {}
    override fun onFinishInput() {}
    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        composingSpanStart: Int,
        composingSpanEnd: Int
    ) {
        if(ic is InputConnectionInternalComposingWrapper) {
            ic.cursorUpdated(oldSelStart, oldSelEnd, newSelStart, newSelEnd)
        }
    }

    override fun isGestureHandlingAvailable(): Boolean = false
    override fun onEvent(event: Event) {}
    override fun onStartBatchInput() {}
    override fun onUpdateBatchInput(batchPointers: InputPointers?) {}
    override fun onEndBatchInput(batchPointers: InputPointers?) {}
    override fun onCancelBatchInput() {}
    override fun onCancelInput() {}
    override fun onFinishSlidingInput() {}
    override fun onCustomRequest(requestCode: Int): Boolean = false
    override fun onMovePointer(steps: Int, stepOverWords: Boolean, select: Boolean?) {}
    override fun onMoveDeletePointer(steps: Int) {}
    override fun onUpWithDeletePointerActive() {}
    override fun onUpWithPointerActive() {}
    override fun onSwipeLanguage(direction: Int) {}
    override fun onMovingCursorLockEvent(canMoveCursor: Boolean) {}
    override fun clearUserHistoryDictionaries() {}
    override fun requestSuggestionRefresh() {}
    override fun onLayoutUpdated(layout: KeyboardLayoutSetV2) { }

    override val textContext: TextContext = TextContext(
        beforeCursor = ic?.getTextBeforeCursor(Constants.VOICE_INPUT_CONTEXT_SIZE, 0),
        afterCursor = ic?.getTextAfterCursor(Constants.VOICE_INPUT_CONTEXT_SIZE, 0)
    )

    private var isFinished = false
    private var partialText = ""
    override fun updatePartial(text: String) {
        if (isFinished) return
        partialText = text
        ic?.setComposingText(
            partialText,
            1
        )

        (ic as? InputConnectionInternalComposingWrapper)?.send()
    }

    override fun commit(text: String) {
        if (isFinished) return
        isFinished = true
        ic?.commitText(
            text,
            1
        )
        helper.endInputTransaction(this)
        (ic as? InputConnectionInternalComposingWrapper)?.send()
    }

    override fun cancel() {
        commit(partialText)
        (ic as? InputConnectionInternalComposingWrapper)?.send()
    }

    fun ensureFinished() {
        isFinished = true
    }
}