/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package helium314.keyboard.latin.suggestions

import android.content.Context
import android.util.AttributeSet
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.keyboard.PopupKeysKeyboardView
import helium314.keyboard.latin.R
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.suggestions.MoreSuggestions.MoreSuggestionKey
import helium314.keyboard.latin.utils.Log

/**
 * A view that renders a virtual [MoreSuggestions]. It handles rendering of keys and detecting
 * key presses and touch movements.
 */
class MoreSuggestionsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet?,
    defStyle: Int = R.attr.popupKeysKeyboardViewStyle
) : PopupKeysKeyboardView(context, attrs, defStyle) {
    abstract class MoreSuggestionsListener : KeyboardActionListener.Adapter() {
        abstract fun onSuggestionSelected(wordInfo: SuggestedWordInfo)
    }

    var isInModalMode = false
        private set

    // TODO: Remove redundant override method.
    override fun setKeyboard(keyboard: Keyboard) {
        super.setKeyboard(keyboard)
        isInModalMode = false
        // With accessibility mode off, mAccessibilityDelegate is set to null at the above PopupKeysKeyboardView#setKeyboard call.
        // With accessibility mode on, mAccessibilityDelegate is set to a PopupKeysKeyboardAccessibilityDelegate object at the above
        // PopupKeysKeyboardView#setKeyboard call.
        if (mAccessibilityDelegate != null) {
            mAccessibilityDelegate.setOpenAnnounce(R.string.spoken_open_more_suggestions)
            mAccessibilityDelegate.setCloseAnnounce(R.string.spoken_close_more_suggestions)
        }
    }

    override fun getDefaultCoordX() = (keyboard as MoreSuggestions).mOccupiedWidth / 2

    fun updateKeyboardGeometry(keyHeight: Int) {
        updateKeyDrawParams(keyHeight)
    }

    fun setModalMode() {
        isInModalMode = true
        // Set vertical correction to zero (Reset popup keys keyboard sliding allowance R#dimen.config_popup_keys_keyboard_slide_allowance).
        mKeyDetector.setKeyboard(keyboard, -paddingLeft.toFloat(), -paddingTop.toFloat())
    }

    override fun onKeyInput(key: Key, x: Int, y: Int) {
        if (key !is MoreSuggestionKey) {
            Log.e(TAG, "Expected key is MoreSuggestionKey, but found ${key.javaClass.name}")
            return
        }
        val keyboard = keyboard
        if (keyboard !is MoreSuggestions) {
            Log.e(TAG, "Expected keyboard is MoreSuggestions, but found ${keyboard?.javaClass?.name}")
            return
        }
        val suggestedWords = keyboard.mSuggestedWords
        val index = key.mSuggestedWordIndex
        if (index < 0 || index >= suggestedWords.size()) {
            Log.e(TAG, "Selected suggestion has an illegal index: $index")
            return
        }
        if (mListener !is MoreSuggestionsListener) {
            Log.e(TAG, "Expected mListener is MoreSuggestionsListener, but found " + mListener.javaClass.name)
            return
        }
        (mListener as MoreSuggestionsListener).onSuggestionSelected(suggestedWords.getInfo(index))
    }

    companion object {
        private val TAG = MoreSuggestionsView::class.java.simpleName
    }
}
