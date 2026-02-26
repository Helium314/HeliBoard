package helium314.keyboard.engine

import android.content.Context
import android.graphics.Rect
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import helium314.keyboard.engine.general.ActionInputTransactionIME
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.settings.Settings

interface InputMethodConnectionProvider {
    fun getCurrentInputConnection(): InputConnection?
    fun getCurrentEditorInfo(): EditorInfo?
    fun getContextForSettings(): Context
}

class IMEHelper(
    private val latinIME: LatinIME
) : InputMethodConnectionProvider {
    val context: Context
        get() = latinIME.applicationContext

    val lifecycleScope: LifecycleCoroutineScope
        get() = latinIME.lifecycleScope

    public val keyboardSwitcher: KeyboardSwitcher
        get() = latinIME.latinIMELegacy.mKeyboardSwitcher

    public val keyboardShiftMode: Int
        get() = latinIME.latinIMELegacy.mKeyboardSwitcher.keyboardShiftMode

    public val currentKeyboardScriptId: Int
        get() = latinIME.latinIMELegacy.mKeyboardSwitcher.currentKeyboardScriptId

    public val keyboardRect: Rect
        get() = run {
            val kb = latinIME.latinIMELegacy.mKeyboardSwitcher.keyboard
            return Rect(0, 0, kb?.mBaseWidth ?: 1, kb?.mBaseHeight ?: 1)
        }

    public fun updateBoostedCodePoints(codes: Set<Int>?) {
        latinIME.latinIMELegacy.mKeyboardSwitcher?.mainKeyboardView?.mKeyDetector?.let {
            it.updateBoostedCodePoints(codes)
        }
    }

    public fun updateUiInputState(textEmpty: Boolean) {
        latinIME.uixManager.onInputEvent(textEmpty)
    }

    public fun getCodepointCoordinates(codePoints: IntArray): IntArray {
        return latinIME.latinIMELegacy.getCoordinatesForCurrentKeyboard(codePoints)
    }

    public fun triggerAction(actionId: Int, alt: Boolean) {
        latinIME.uixManager.triggerActionInternalFromIme(actionId, alt)
    }

    override fun getCurrentInputConnection(): InputConnection? {
        return latinIME.currentInputConnection
    }

    override fun getCurrentEditorInfo(): EditorInfo? {
        return latinIME.currentInputEditorInfo
    }

    override fun getContextForSettings(): Context = context

    fun setNeutralSuggestionStrip(useExpandableUi: Boolean) {
        latinIME.setSuggestions(
            suggestedWords = SuggestedWords.getEmptyInstance(),
            rtlSubtype = Settings.getInstance().current.mIsRTL,
            useExpandableUi = useExpandableUi
        )
    }

    fun showSuggestionStrip(suggestedWords: SuggestedWords?, useExpandableUi: Boolean) {
        latinIME.setSuggestions(
            suggestedWords = suggestedWords ?: SuggestedWords.getEmptyInstance(),
            rtlSubtype = Settings.getInstance().current.mIsRTL,
            useExpandableUi = useExpandableUi
        )
    }

    fun endInputTransaction(inputTransactionIME: ActionInputTransactionIME) {
        latinIME.imeManager.endInputTransaction(inputTransactionIME)
    }

    fun updateGestureAvailability(to: Boolean) {
        latinIME.latinIMELegacy.mKeyboardSwitcher?.mainKeyboardView?.setImeAllowsGestureInput(to)
    }

    /**
     * Please call this in `onEvent` before processing the event. It will make sure any pending
     * cursor update gets processed (they are typically delayed 20ms for debouncing purposes)
     *
     * In the future, IMEManager should probably call this automatically, but the current control
     * flow doesn't allow that
     */
    fun requestCursorUpdate() {
        latinIME.imeManager.ensureUpdateSelectionFinished()
    }
}