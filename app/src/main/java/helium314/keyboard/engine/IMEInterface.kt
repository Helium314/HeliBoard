package helium314.keyboard.engine

import helium314.keyboard.annotations.UsedForTesting
import helium314.keyboard.event.Event
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.v2keyboard.KeyboardLayoutSetV2

interface IMEInterface {
    // Basic lifecycle
    fun onCreate()
    fun onDestroy()
    fun onDeviceUnlocked()

    // State
    fun onStartInput()
    fun onLayoutUpdated(layout: KeyboardLayoutSetV2)
    fun onOrientationChanged()
    fun onFinishInput()
    fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        composingSpanStart: Int, composingSpanEnd: Int
    )

    fun isGestureHandlingAvailable(): Boolean

    // Input
    fun onEvent(event: Event)


    /**
     * Called when user started batch input.
     */
    fun onStartBatchInput()

    /**
     * Sends the ongoing batch input points data.
     * @param batchPointers the batch input points representing the user input
     */
    fun onUpdateBatchInput(batchPointers: InputPointers?)

    /**
     * Sends the final batch input points data.
     *
     * @param batchPointers the batch input points representing the user input
     */
    fun onEndBatchInput(batchPointers: InputPointers?)

    fun onCancelBatchInput()

    /**
     * Called when user released a finger outside any key.
     */
    fun onCancelInput()

    /**
     * Called when user finished sliding key input.
     */
    fun onFinishSlidingInput()

    /**
     * Send a non-"code input" custom request to the listener.
     * @return true if the request has been consumed, false otherwise.
     */
    fun onCustomRequest(requestCode: Int): Boolean

    fun onMovePointer(steps: Int, stepOverWords: Boolean, select: Boolean?)
    fun onMoveDeletePointer(steps: Int)
    fun onUpWithDeletePointerActive()
    fun onUpWithPointerActive()
    fun onSwipeLanguage(direction: Int)
    fun onMovingCursorLockEvent(canMoveCursor: Boolean)
    fun clearUserHistoryDictionaries()

    /** Refresh as a result of blacklist update */
    fun requestSuggestionRefresh()

    /**
     * Hints the keyboard switcher whether to auto-shift (capitalize) the layout or not.
     * For immediate changes, call imeHelper.keyboardSwitcher.requestUpdatingShiftState(mode)
     * Value of CAP_MODE_OFF will unshift it, any other value will shift it.
     */
    fun getCurrentAutoCapsState(): Int = Constants.TextUtils.CAP_MODE_OFF

    @UsedForTesting
    fun recycle() { }
}
