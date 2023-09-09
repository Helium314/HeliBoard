/*
 * Copyright (C) 2011 The Android Open Source Project
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

package org.dslul.openboard.inputmethod.latin.suggestions;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;

import androidx.annotation.NonNull;

import org.dslul.openboard.inputmethod.keyboard.Key;
import org.dslul.openboard.inputmethod.keyboard.Keyboard;
import org.dslul.openboard.inputmethod.keyboard.KeyboardActionListener;
import org.dslul.openboard.inputmethod.keyboard.MoreKeysKeyboardView;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.SuggestedWords;
import org.dslul.openboard.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import org.dslul.openboard.inputmethod.latin.suggestions.MoreSuggestions.MoreSuggestionKey;

/**
 * A view that renders a virtual {@link MoreSuggestions}. It handles rendering of keys and detecting
 * key presses and touch movements.
 */
public final class MoreSuggestionsView extends MoreKeysKeyboardView {
    private static final String TAG = MoreSuggestionsView.class.getSimpleName();

    public static abstract class MoreSuggestionsListener extends KeyboardActionListener.Adapter {
        public abstract void onSuggestionSelected(final SuggestedWordInfo info);
    }

    private boolean mIsInModalMode;

    public MoreSuggestionsView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.moreKeysKeyboardViewStyle);
    }

    public MoreSuggestionsView(final Context context, final AttributeSet attrs,
            final int defStyle) {
        super(context, attrs, defStyle);
    }

    // TODO: Remove redundant override method.
    @Override
    public void setKeyboard(@NonNull final Keyboard keyboard) {
        super.setKeyboard(keyboard);
        mIsInModalMode = false;
        // With accessibility mode off, {@link #mAccessibilityDelegate} is set to null at the
        // above {@link MoreKeysKeyboardView#setKeyboard(Keyboard)} call.
        // With accessibility mode on, {@link #mAccessibilityDelegate} is set to a
        // {@link MoreKeysKeyboardAccessibilityDelegate} object at the above
        // {@link MoreKeysKeyboardView#setKeyboard(Keyboard)} call.
        if (mAccessibilityDelegate != null) {
            mAccessibilityDelegate.setOpenAnnounce(R.string.spoken_open_more_suggestions);
            mAccessibilityDelegate.setCloseAnnounce(R.string.spoken_close_more_suggestions);
        }
    }

    @Override
    protected int getDefaultCoordX() {
        final MoreSuggestions pane = (MoreSuggestions) getKeyboard();
        return pane.mOccupiedWidth / 2;
    }

    public void updateKeyboardGeometry(final int keyHeight) {
        updateKeyDrawParams(keyHeight);
    }

    public void setModalMode() {
        mIsInModalMode = true;
        // Set vertical correction to zero (Reset more keys keyboard sliding allowance
        // {@link R#dimen.config_more_keys_keyboard_slide_allowance}).
        mKeyDetector.setKeyboard(getKeyboard(), -getPaddingLeft(), -getPaddingTop());
    }

    public boolean isInModalMode() {
        return mIsInModalMode;
    }

    @Override
    protected void onKeyInput(final Key key, final int x, final int y) {
        if (!(key instanceof MoreSuggestionKey)) {
            Log.e(TAG, "Expected key is MoreSuggestionKey, but found "
                    + key.getClass().getName());
            return;
        }
        final Keyboard keyboard = getKeyboard();
        if (!(keyboard instanceof MoreSuggestions)) {
            Log.e(TAG, "Expected keyboard is MoreSuggestions, but found "
                    + keyboard.getClass().getName());
            return;
        }
        final SuggestedWords suggestedWords = ((MoreSuggestions)keyboard).mSuggestedWords;
        final int index = ((MoreSuggestionKey)key).mSuggestedWordIndex;
        if (index < 0 || index >= suggestedWords.size()) {
            Log.e(TAG, "Selected suggestion has an illegal index: " + index);
            return;
        }
        if (!(mListener instanceof MoreSuggestionsListener)) {
            Log.e(TAG, "Expected mListener is MoreSuggestionsListener, but found "
                    + mListener.getClass().getName());
            return;
        }
        ((MoreSuggestionsListener)mListener).onSuggestionSelected(suggestedWords.getInfo(index));
    }
}
