/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.keyboard.internal;

import android.text.TextUtils;
import org.dslul.openboard.inputmethod.latin.utils.Log;

import androidx.annotation.NonNull;

import org.dslul.openboard.inputmethod.event.Event;
import org.dslul.openboard.inputmethod.latin.common.Constants;
import org.dslul.openboard.inputmethod.latin.settings.Settings;
import org.dslul.openboard.inputmethod.latin.utils.CapsModeUtils;
import org.dslul.openboard.inputmethod.latin.utils.RecapitalizeStatus;

/**
 * Keyboard state machine.
 *
 * This class contains all keyboard state transition logic.
 *
 * The input events are {@link #onLoadKeyboard(int, int, boolean)}, {@link #onSaveKeyboardState()},
 * {@link #onPressKey(int,boolean,int,int)}, {@link #onReleaseKey(int,boolean,int,int)},
 * {@link #onEvent(Event,int,int)}, {@link #onFinishSlidingInput(int,int)},
 * {@link #onUpdateShiftState(int,int)}, {@link #onResetKeyboardStateToAlphabet(int,int)}.
 *
 * The actions are {@link SwitchActions}'s methods.
 */
public final class KeyboardState {
    private static final String TAG = KeyboardState.class.getSimpleName();
    private static final boolean DEBUG_EVENT = false;
    private static final boolean DEBUG_INTERNAL_ACTION = false;

    public interface SwitchActions {
        boolean DEBUG_ACTION = false;

        void setAlphabetKeyboard();
        void setAlphabetManualShiftedKeyboard();
        void setAlphabetAutomaticShiftedKeyboard();
        void setAlphabetShiftLockedKeyboard();
        void setAlphabetShiftLockShiftedKeyboard();
        void setEmojiKeyboard();
        void setClipboardKeyboard();
        void setNumpadKeyboard();
        void setSymbolsKeyboard();
        void setSymbolsShiftedKeyboard();

        /**
         * Request to call back {@link KeyboardState#onUpdateShiftState(int, int)}.
         */
        void requestUpdatingShiftState(final int autoCapsFlags, final int recapitalizeMode);

        boolean DEBUG_TIMER_ACTION = false;

        void startDoubleTapShiftKeyTimer();
        boolean isInDoubleTapShiftKeyTimeout();
        void cancelDoubleTapShiftKeyTimer();

        void setOneHandedModeEnabled(boolean enabled);
        void switchOneHandedMode();
    }

    private final SwitchActions mSwitchActions;

    private final ShiftKeyState mShiftKeyState = new ShiftKeyState("Shift");
    private final ModifierKeyState mSymbolKeyState = new ModifierKeyState("Symbol");
    private final AlphabetShiftState mAlphabetShiftState = new AlphabetShiftState();

    // TODO: Merge {@link #mSwitchState}, {@link #mIsAlphabetMode}, {@link #mAlphabetShiftState},
    // {@link #mIsSymbolShifted}, {@link #mPrevMainKeyboardWasShiftLocked}, and
    // {@link #mPrevSymbolsKeyboardWasShifted} into single state variable.
    private static final int SWITCH_STATE_ALPHA = 0;
    private static final int SWITCH_STATE_SYMBOL_BEGIN = 1;
    private static final int SWITCH_STATE_SYMBOL = 2;
    private static final int SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL = 3;
    private static final int SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE = 4;
    private static final int SWITCH_STATE_MOMENTARY_ALPHA_SHIFT = 5;
    private int mSwitchState = SWITCH_STATE_ALPHA;

    private static final int MODE_ALPHABET = 0;
    private static final int MODE_SYMBOLS = 1;
    private static final int MODE_EMOJI = 2;
    private static final int MODE_CLIPBOARD = 3;
    private static final int MODE_NUMPAD = 4;
    private int mMode = MODE_ALPHABET;
    private boolean mIsSymbolShifted;
    private boolean mPrevMainKeyboardWasShiftLocked;
    private boolean mPrevSymbolsKeyboardWasShifted;
    private int mRecapitalizeMode;

    // For handling double tap.
    private boolean mIsInAlphabetUnshiftedFromShifted;
    private boolean mIsInDoubleTapShiftKey;

    private final SavedKeyboardState mSavedKeyboardState = new SavedKeyboardState();

    static final class SavedKeyboardState {
        public boolean mIsValid;
        public boolean mIsAlphabetShiftLocked;
        public int mMode;
        public int mShiftMode;

        @NonNull
        @Override
        public String toString() {
            if (!mIsValid) {
                return "INVALID";
            }
            if (mMode == MODE_ALPHABET) {
                return mIsAlphabetShiftLocked ? "ALPHABET_SHIFT_LOCKED"
                        : "ALPHABET_" + shiftModeToString(mShiftMode);
            }
            if (mMode == MODE_EMOJI) {
                return "EMOJI";
            }
            if (mMode == MODE_CLIPBOARD) {
                return "CLIPBOARD";
            }
            if (mMode == MODE_NUMPAD) {
                return "NUMPAD";
            }
            return "SYMBOLS_" + shiftModeToString(mShiftMode);
        }
    }

    public KeyboardState(final SwitchActions switchActions) {
        mSwitchActions = switchActions;
        mRecapitalizeMode = RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE;
    }

    public void onLoadKeyboard(final int autoCapsFlags, final int recapitalizeMode,
                   final boolean onHandedModeEnabled) {
        if (DEBUG_EVENT) {
            Log.d(TAG, "onLoadKeyboard: " + stateToString(autoCapsFlags, recapitalizeMode));
        }
        // Reset alphabet shift state.
        mAlphabetShiftState.setShiftLocked(false);
        mPrevMainKeyboardWasShiftLocked = false;
        mPrevSymbolsKeyboardWasShifted = false;
        mShiftKeyState.onRelease();
        mSymbolKeyState.onRelease();
        if (mSavedKeyboardState.mIsValid) {
            onRestoreKeyboardState(autoCapsFlags, recapitalizeMode);
            mSavedKeyboardState.mIsValid = false;
        } else {
            // Reset keyboard to alphabet mode.
            setAlphabetKeyboard(autoCapsFlags, recapitalizeMode);
        }
        mSwitchActions.setOneHandedModeEnabled(onHandedModeEnabled);
    }

    // Constants for {@link SavedKeyboardState#mShiftMode} and {@link #setShifted(int)}.
    private static final int UNSHIFT = 0;
    private static final int MANUAL_SHIFT = 1;
    private static final int AUTOMATIC_SHIFT = 2;
    private static final int SHIFT_LOCK_SHIFTED = 3;

    public void onSaveKeyboardState() {
        final SavedKeyboardState state = mSavedKeyboardState;
        state.mMode = mMode;
        if (mMode == MODE_ALPHABET) {
            state.mIsAlphabetShiftLocked = mAlphabetShiftState.isShiftLocked();
            state.mShiftMode = mAlphabetShiftState.isAutomaticShifted() ? AUTOMATIC_SHIFT
                    : (mAlphabetShiftState.isShiftedOrShiftLocked() ? MANUAL_SHIFT : UNSHIFT);
        } else {
            state.mIsAlphabetShiftLocked = mPrevMainKeyboardWasShiftLocked;
            state.mShiftMode = mIsSymbolShifted ? MANUAL_SHIFT : UNSHIFT;
        }
        state.mIsValid = true;
        if (DEBUG_EVENT) {
            Log.d(TAG, "onSaveKeyboardState: saved=" + state + " " + this);
        }
    }

    private void onRestoreKeyboardState(final int autoCapsFlags, final int recapitalizeMode) {
        final SavedKeyboardState state = mSavedKeyboardState;
        if (DEBUG_EVENT) {
            Log.d(TAG, "onRestoreKeyboardState: saved=" + state
                    + " " + stateToString(autoCapsFlags, recapitalizeMode));
        }
        mPrevMainKeyboardWasShiftLocked = state.mIsAlphabetShiftLocked;
        if (state.mMode == MODE_ALPHABET) {
            setAlphabetKeyboard(autoCapsFlags, recapitalizeMode);
            setShiftLocked(state.mIsAlphabetShiftLocked);
            if (!state.mIsAlphabetShiftLocked) {
                setShifted(state.mShiftMode);
            }
            return;
        }
        if (state.mMode == MODE_EMOJI) {
            setEmojiKeyboard();
            return;
        }
        if (state.mMode == MODE_CLIPBOARD) {
            setClipboardKeyboard();
            return;
        }
        if (state.mMode == MODE_NUMPAD) {
            setNumpadKeyboard();
            return;
        }
        // Symbol mode
        if (state.mShiftMode == MANUAL_SHIFT) {
            setSymbolsShiftedKeyboard();
        } else {
            setSymbolsKeyboard();
        }
    }

    private void setShifted(final int shiftMode) {
        if (DEBUG_INTERNAL_ACTION) {
            Log.d(TAG, "setShifted: shiftMode=" + shiftModeToString(shiftMode) + " " + this);
        }
        if (mMode != MODE_ALPHABET) return;
        final int prevShiftMode;
        if (mAlphabetShiftState.isAutomaticShifted()) {
            prevShiftMode = AUTOMATIC_SHIFT;
        } else if (mAlphabetShiftState.isManualShifted()) {
            prevShiftMode = MANUAL_SHIFT;
        } else {
            prevShiftMode = UNSHIFT;
        }
        switch (shiftMode) {
        case AUTOMATIC_SHIFT:
            mAlphabetShiftState.setAutomaticShifted();
            if (shiftMode != prevShiftMode) {
                mSwitchActions.setAlphabetAutomaticShiftedKeyboard();
            }
            break;
        case MANUAL_SHIFT:
            mAlphabetShiftState.setShifted(true);
            if (shiftMode != prevShiftMode) {
                mSwitchActions.setAlphabetManualShiftedKeyboard();
            }
            break;
        case UNSHIFT:
            mAlphabetShiftState.setShifted(false);
            if (shiftMode != prevShiftMode) {
                mSwitchActions.setAlphabetKeyboard();
            }
            break;
        case SHIFT_LOCK_SHIFTED:
            mAlphabetShiftState.setShifted(true);
            mSwitchActions.setAlphabetShiftLockShiftedKeyboard();
            break;
        }
    }

    private void setShiftLocked(final boolean shiftLocked) {
        if (DEBUG_INTERNAL_ACTION) {
            Log.d(TAG, "setShiftLocked: shiftLocked=" + shiftLocked + " " + this);
        }
        if (mMode != MODE_ALPHABET) return;
        if (shiftLocked && (!mAlphabetShiftState.isShiftLocked()
                || mAlphabetShiftState.isShiftLockShifted())) {
            mSwitchActions.setAlphabetShiftLockedKeyboard();
        }
        if (!shiftLocked && mAlphabetShiftState.isShiftLocked()) {
            mSwitchActions.setAlphabetKeyboard();
        }
        mAlphabetShiftState.setShiftLocked(shiftLocked);
    }

    private void toggleAlphabetAndSymbols(final int autoCapsFlags, final int recapitalizeMode) {
        if (DEBUG_INTERNAL_ACTION) {
            Log.d(TAG, "toggleAlphabetAndSymbols: "
                    + stateToString(autoCapsFlags, recapitalizeMode));
        }
        if (mMode == MODE_ALPHABET) {
            mPrevMainKeyboardWasShiftLocked = mAlphabetShiftState.isShiftLocked();
            if (mPrevSymbolsKeyboardWasShifted) {
                setSymbolsShiftedKeyboard();
            } else {
                setSymbolsKeyboard();
            }
            mPrevSymbolsKeyboardWasShifted = false;
        } else {
            mPrevSymbolsKeyboardWasShifted = mIsSymbolShifted;
            setAlphabetKeyboard(autoCapsFlags, recapitalizeMode);
            if (mPrevMainKeyboardWasShiftLocked) {
                setShiftLocked(true);
            }
            mPrevMainKeyboardWasShiftLocked = false;
        }
    }

    // TODO: Remove this method. Come up with a more comprehensive way to reset the keyboard layout
    // when a keyboard layout set doesn't get reloaded in LatinIME.onStartInputViewInternal().
    private void resetKeyboardStateToAlphabet(final int autoCapsFlags, final int recapitalizeMode) {
        if (DEBUG_INTERNAL_ACTION) {
            Log.d(TAG, "resetKeyboardStateToAlphabet: "
                    + stateToString(autoCapsFlags, recapitalizeMode));
        }
        if (mMode == MODE_ALPHABET) return;

        mPrevSymbolsKeyboardWasShifted = mIsSymbolShifted;
        setAlphabetKeyboard(autoCapsFlags, recapitalizeMode);
        if (mPrevMainKeyboardWasShiftLocked) {
            setShiftLocked(true);
        }
        mPrevMainKeyboardWasShiftLocked = false;
    }

    private void toggleShiftInSymbols() {
        if (mIsSymbolShifted) {
            setSymbolsKeyboard();
        } else {
            setSymbolsShiftedKeyboard();
        }
    }

    private void setAlphabetKeyboard(final int autoCapsFlags, final int recapitalizeMode) {
        if (DEBUG_INTERNAL_ACTION) {
            Log.d(TAG, "setAlphabetKeyboard: " + stateToString(autoCapsFlags, recapitalizeMode));
        }

        mSwitchActions.setAlphabetKeyboard();
        mMode = MODE_ALPHABET;
        mIsSymbolShifted = false;
        mRecapitalizeMode = RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE;
        mSwitchState = SWITCH_STATE_ALPHA;
        mSwitchActions.requestUpdatingShiftState(autoCapsFlags, recapitalizeMode);
    }

    private void setSymbolsKeyboard() {
        if (DEBUG_INTERNAL_ACTION) {
            Log.d(TAG, "setSymbolsKeyboard");
        }
        mSwitchActions.setSymbolsKeyboard();
        mMode = MODE_SYMBOLS;
        mIsSymbolShifted = false;
        mRecapitalizeMode = RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE;
        // Reset alphabet shift state.
        mAlphabetShiftState.setShiftLocked(false);
        mSwitchState = SWITCH_STATE_SYMBOL_BEGIN;
    }

    private void setSymbolsShiftedKeyboard() {
        if (DEBUG_INTERNAL_ACTION) {
            Log.d(TAG, "setSymbolsShiftedKeyboard");
        }
        mSwitchActions.setSymbolsShiftedKeyboard();
        mMode = MODE_SYMBOLS;
        mIsSymbolShifted = true;
        mRecapitalizeMode = RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE;
        // Reset alphabet shift state.
        mAlphabetShiftState.setShiftLocked(false);
        mSwitchState = SWITCH_STATE_SYMBOL_BEGIN;
    }

    private void setEmojiKeyboard() {
        if (DEBUG_INTERNAL_ACTION) {
            Log.d(TAG, "setEmojiKeyboard");
        }
        mMode = MODE_EMOJI;
        mRecapitalizeMode = RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE;
        // Remember caps lock mode and reset alphabet shift state.
        mPrevMainKeyboardWasShiftLocked = mAlphabetShiftState.isShiftLocked();
        mAlphabetShiftState.setShiftLocked(false);
        mSwitchActions.setEmojiKeyboard();
    }

    private void setClipboardKeyboard() {
        if (DEBUG_INTERNAL_ACTION) {
            Log.d(TAG, "setClipboardKeyboard");
        }
        mMode = MODE_CLIPBOARD;
        mRecapitalizeMode = RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE;
        // Remember caps lock mode and reset alphabet shift state.
        mPrevMainKeyboardWasShiftLocked = mAlphabetShiftState.isShiftLocked();
        mAlphabetShiftState.setShiftLocked(false);
        mSwitchActions.setClipboardKeyboard();
    }

    private void setNumpadKeyboard() {
        if (DEBUG_INTERNAL_ACTION) {
            Log.d(TAG, "setNumpadKeyboard");
        }
        mMode = MODE_NUMPAD;
        mRecapitalizeMode = RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE;
        // Remember caps lock mode and reset alphabet shift state.
        mPrevMainKeyboardWasShiftLocked = mAlphabetShiftState.isShiftLocked();
        mAlphabetShiftState.setShiftLocked(false);
        mSwitchActions.setNumpadKeyboard();

    }

    private void setOneHandedModeEnabled(boolean enabled) {
        if (DEBUG_INTERNAL_ACTION) {
            Log.d(TAG, "setOneHandedModeEnabled");
        }
        mSwitchActions.setOneHandedModeEnabled(enabled);
    }

    private void switchOneHandedMode() {
        if (DEBUG_INTERNAL_ACTION) {
            Log.d(TAG, "switchOneHandedMode");
        }
        mSwitchActions.switchOneHandedMode();
    }

    public void onPressKey(final int code, final boolean isSinglePointer, final int autoCapsFlags,
            final int recapitalizeMode) {
        if (DEBUG_EVENT) {
            Log.d(TAG, "onPressKey: code=" + Constants.printableCode(code)
                    + " single=" + isSinglePointer
                    + " " + stateToString(autoCapsFlags, recapitalizeMode));
        }
        if (code != Constants.CODE_SHIFT) {
            // Because the double tap shift key timer is to detect two consecutive shift key press,
            // it should be canceled when a non-shift key is pressed.
            mSwitchActions.cancelDoubleTapShiftKeyTimer();
        }
        if (code == Constants.CODE_SHIFT) {
            onPressShift();
        } else if (code == Constants.CODE_CAPSLOCK) {
            // Nothing to do here. See {@link #onReleaseKey(int,boolean)}.
        } else if (code == Constants.CODE_SWITCH_ALPHA_SYMBOL) {
            onPressSymbol(autoCapsFlags, recapitalizeMode);
        } else {
            mShiftKeyState.onOtherKeyPressed();
            mSymbolKeyState.onOtherKeyPressed();
            // It is required to reset the auto caps state when all of the following conditions
            // are met:
            // 1) two or more fingers are in action
            // 2) in alphabet layout
            // 3) not in all characters caps mode
            // As for #3, please note that it's required to check even when the auto caps mode is
            // off because, for example, we may be in the #1 state within the manual temporary
            // shifted mode.
            if (!isSinglePointer && mMode == MODE_ALPHABET
                    && autoCapsFlags != TextUtils.CAP_MODE_CHARACTERS) {
                final boolean needsToResetAutoCaps = mAlphabetShiftState.isAutomaticShifted()
                        || (mAlphabetShiftState.isManualShifted() && mShiftKeyState.isReleasing());
                if (needsToResetAutoCaps) {
                    mSwitchActions.setAlphabetKeyboard();
                }
            }
        }
    }

    public void onReleaseKey(final int code, final boolean withSliding, final int autoCapsFlags,
            final int recapitalizeMode) {
        if (DEBUG_EVENT) {
            Log.d(TAG, "onReleaseKey: code=" + Constants.printableCode(code)
                    + " sliding=" + withSliding
                    + " " + stateToString(autoCapsFlags, recapitalizeMode));
        }
        if (code == Constants.CODE_SHIFT) {
            onReleaseShift(withSliding, autoCapsFlags, recapitalizeMode);
        } else if (code == Constants.CODE_CAPSLOCK) {
            setShiftLocked(!mAlphabetShiftState.isShiftLocked());
        } else if (code == Constants.CODE_SWITCH_ALPHA_SYMBOL) {
            onReleaseSymbol(withSliding, autoCapsFlags, recapitalizeMode);
        }
    }

    private void onPressSymbol(final int autoCapsFlags,
            final int recapitalizeMode) {
        toggleAlphabetAndSymbols(autoCapsFlags, recapitalizeMode);
        mSymbolKeyState.onPress();
        mSwitchState = SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL;
    }

    private void onReleaseSymbol(final boolean withSliding, final int autoCapsFlags,
            final int recapitalizeMode) {
        if (mSymbolKeyState.isChording()) {
            // Switch back to the previous keyboard mode if the user chords the mode change key and
            // another key, then releases the mode change key.
            toggleAlphabetAndSymbols(autoCapsFlags, recapitalizeMode);
        } else if (!withSliding) {
            // If the mode change key is being released without sliding, we should forget the
            // previous symbols keyboard shift state and simply switch back to symbols layout
            // (never symbols shifted) next time the mode gets changed to symbols layout.
            mPrevSymbolsKeyboardWasShifted = false;
        }
        mSymbolKeyState.onRelease();
    }

    public void onUpdateShiftState(final int autoCapsFlags, final int recapitalizeMode) {
        if (DEBUG_EVENT) {
            Log.d(TAG, "onUpdateShiftState: " + stateToString(autoCapsFlags, recapitalizeMode));
        }
        mRecapitalizeMode = recapitalizeMode;
        updateAlphabetShiftState(autoCapsFlags, recapitalizeMode);
    }

    // TODO: Remove this method. Come up with a more comprehensive way to reset the keyboard layout
    // when a keyboard layout set doesn't get reloaded in LatinIME.onStartInputViewInternal().
    public void onResetKeyboardStateToAlphabet(final int autoCapsFlags,
            final int recapitalizeMode) {
        if (DEBUG_EVENT) {
            Log.d(TAG, "onResetKeyboardStateToAlphabet: "
                    + stateToString(autoCapsFlags, recapitalizeMode));
        }
        resetKeyboardStateToAlphabet(autoCapsFlags, recapitalizeMode);
    }

    private void updateShiftStateForRecapitalize(final int recapitalizeMode) {
        switch (recapitalizeMode) {
        case RecapitalizeStatus.CAPS_MODE_ALL_UPPER:
            setShifted(SHIFT_LOCK_SHIFTED);
            break;
        case RecapitalizeStatus.CAPS_MODE_FIRST_WORD_UPPER:
            setShifted(AUTOMATIC_SHIFT);
            break;
        case RecapitalizeStatus.CAPS_MODE_ALL_LOWER:
        case RecapitalizeStatus.CAPS_MODE_ORIGINAL_MIXED_CASE:
        default:
            setShifted(UNSHIFT);
        }
    }

    private void updateAlphabetShiftState(final int autoCapsFlags, final int recapitalizeMode) {
        if (mMode != MODE_ALPHABET) return;
        if (RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE != recapitalizeMode) {
            // We are recapitalizing. Match the keyboard to the current recapitalize state.
            updateShiftStateForRecapitalize(recapitalizeMode);
            return;
        }
        if (!mShiftKeyState.isReleasing()) {
            // Ignore update shift state event while the shift key is being pressed (including
            // chording).
            return;
        }
        if (!mAlphabetShiftState.isShiftLocked() && !mShiftKeyState.isIgnoring()) {
            if (mShiftKeyState.isReleasing() && autoCapsFlags != Constants.TextUtils.CAP_MODE_OFF) {
                // Only when shift key is releasing, automatic temporary upper case will be set.
                setShifted(AUTOMATIC_SHIFT);
            } else {
                setShifted(mShiftKeyState.isChording() ? MANUAL_SHIFT : UNSHIFT);
            }
        }
    }

    private void onPressShift() {
        // If we are recapitalizing, we don't do any of the normal processing, including
        // importantly the double tap timer.
        if (RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE != mRecapitalizeMode) {
            return;
        }
        if (mMode == MODE_ALPHABET) {
            mIsInDoubleTapShiftKey = mSwitchActions.isInDoubleTapShiftKeyTimeout();
            if (!mIsInDoubleTapShiftKey) {
                // This is first tap.
                mSwitchActions.startDoubleTapShiftKeyTimer();
            }
            if (mIsInDoubleTapShiftKey) {
                if (mAlphabetShiftState.isManualShifted() || mIsInAlphabetUnshiftedFromShifted) {
                    // Shift key has been double tapped while in manual shifted or automatic
                    // shifted state.
                    setShiftLocked(true);
                } else {
                    // Shift key has been double tapped while in normal state. This is the second
                    // tap to disable shift locked state, so just ignore this.
                }
            } else {
                if (mAlphabetShiftState.isShiftLocked()) {
                    // Shift key is pressed while shift locked state, we will treat this state as
                    // shift lock shifted state and mark as if shift key pressed while normal
                    // state.
                    setShifted(SHIFT_LOCK_SHIFTED);
                    mShiftKeyState.onPress();
                } else if (mAlphabetShiftState.isAutomaticShifted()) {
                    // Shift key is pressed while automatic shifted, we have to move to manual
                    // shifted.
                    setShifted(MANUAL_SHIFT);
                    mShiftKeyState.onPress();
                } else if (mAlphabetShiftState.isShiftedOrShiftLocked()) {
                    // In manual shifted state, we just record shift key has been pressing while
                    // shifted state.
                    mShiftKeyState.onPressOnShifted();
                } else {
                    // In base layout, chording or manual shifted mode is started.
                    setShifted(MANUAL_SHIFT);
                    mShiftKeyState.onPress();
                }
            }
        } else {
            // In symbol mode, just toggle symbol and symbol more keyboard.
            toggleShiftInSymbols();
            mSwitchState = SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE;
            mShiftKeyState.onPress();
        }
    }

    private void onReleaseShift(final boolean withSliding, final int autoCapsFlags,
            final int recapitalizeMode) {
        if (RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE != mRecapitalizeMode) {
            // We are recapitalizing. We should match the keyboard state to the recapitalize
            // state in priority.
            updateShiftStateForRecapitalize(mRecapitalizeMode);
        } else if (mMode == MODE_ALPHABET) {
            final boolean isShiftLocked = mAlphabetShiftState.isShiftLocked();
            mIsInAlphabetUnshiftedFromShifted = false;
            if (mIsInDoubleTapShiftKey) {
                // Double tap shift key has been handled in {@link #onPressShift}, so that just
                // ignore this release shift key here.
                mIsInDoubleTapShiftKey = false;
            } else if (mShiftKeyState.isChording()) {
                if (mAlphabetShiftState.isShiftLockShifted()) {
                    // After chording input while shift locked state.
                    setShiftLocked(true);
                } else {
                    // After chording input while normal state.
                    setShifted(UNSHIFT);
                }
                // After chording input, automatic shift state may have been changed depending on
                // what characters were input.
                mShiftKeyState.onRelease();
                mSwitchActions.requestUpdatingShiftState(autoCapsFlags, recapitalizeMode);
                return;
            } else if (mAlphabetShiftState.isShiftLockShifted() && withSliding) {
                // In shift locked state, shift has been pressed and slid out to other key.
                setShiftLocked(true);
            } else if (mAlphabetShiftState.isManualShifted() && withSliding) {
                // Shift has been pressed and slid out to other key.
                mSwitchState = SWITCH_STATE_MOMENTARY_ALPHA_SHIFT;
            } else if (isShiftLocked && !mAlphabetShiftState.isShiftLockShifted()
                    && (mShiftKeyState.isPressing() || mShiftKeyState.isPressingOnShifted())
                    && !withSliding) {
                // Shift has been long pressed, ignore this release.
            } else if (isShiftLocked && !mShiftKeyState.isIgnoring() && !withSliding) {
                // Shift has been pressed without chording while shift locked state.
                setShiftLocked(false);
            } else if (mAlphabetShiftState.isShiftedOrShiftLocked()
                    && mShiftKeyState.isPressingOnShifted() && !withSliding) {
                // Shift has been pressed without chording while shifted state.
                setShifted(UNSHIFT);
                mIsInAlphabetUnshiftedFromShifted = true;
            } else if (mAlphabetShiftState.isManualShiftedFromAutomaticShifted()
                    && mShiftKeyState.isPressing() && !withSliding) {
                // Shift has been pressed without chording while manual shifted transited from
                // automatic shifted
                setShifted(UNSHIFT);
                mIsInAlphabetUnshiftedFromShifted = true;
            }
        } else {
            // In symbol mode, switch back to the previous keyboard mode if the user chords the
            // shift key and another key, then releases the shift key.
            if (mShiftKeyState.isChording()) {
                toggleShiftInSymbols();
            }
        }
        mShiftKeyState.onRelease();
    }

    public void onFinishSlidingInput(final int autoCapsFlags, final int recapitalizeMode) {
        if (DEBUG_EVENT) {
            Log.d(TAG, "onFinishSlidingInput: " + stateToString(autoCapsFlags, recapitalizeMode));
        }
        // Switch back to the previous keyboard mode if the user cancels sliding input.
        switch (mSwitchState) {
        case SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL:
            toggleAlphabetAndSymbols(autoCapsFlags, recapitalizeMode);
            break;
        case SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE:
            toggleShiftInSymbols();
            break;
        case SWITCH_STATE_MOMENTARY_ALPHA_SHIFT:
            setAlphabetKeyboard(autoCapsFlags, recapitalizeMode);
            break;
        }
    }

    private static boolean isSpaceOrEnter(final int c) {
        return c == Constants.CODE_SPACE || c == Constants.CODE_ENTER;
    }

    public void onEvent(final Event event, final int autoCapsFlags, final int recapitalizeMode) {
        final int code = event.isFunctionalKeyEvent() ? event.getMKeyCode() : event.getMCodePoint();
        if (DEBUG_EVENT) {
            Log.d(TAG, "onEvent: code=" + Constants.printableCode(code)
                    + " " + stateToString(autoCapsFlags, recapitalizeMode));
        }

        switch (mSwitchState) {
        case SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL:
            if (code == Constants.CODE_SWITCH_ALPHA_SYMBOL) {
                // Detected only the mode change key has been pressed, and then released.
                if (mMode == MODE_ALPHABET) {
                    mSwitchState = SWITCH_STATE_ALPHA;
                } else {
                    mSwitchState = SWITCH_STATE_SYMBOL_BEGIN;
                }
            }
            break;
        case SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE:
            if (code == Constants.CODE_SHIFT) {
                // Detected only the shift key has been pressed on symbol layout, and then
                // released.
                mSwitchState = SWITCH_STATE_SYMBOL_BEGIN;
            }
            // Switch back to alpha keyboard mode if user types one or more non-space/enter
            // characters followed by a space/enter.
            if (isSpaceOrEnter(code)) {
                toggleAlphabetAndSymbols(autoCapsFlags, recapitalizeMode);
                mPrevSymbolsKeyboardWasShifted = false;
            }
            break;
        case SWITCH_STATE_SYMBOL_BEGIN:
            if (mMode == MODE_EMOJI || mMode == MODE_CLIPBOARD || mMode == MODE_NUMPAD) {
                // When in the Emoji keyboard or clipboard one, we don't want to switch back to the main layout even
                // after the user hits an emoji letter followed by an enter or a space.
                break;
            }
            if (!isSpaceOrEnter(code) && (Constants.isLetterCode(code)
                    || code == Constants.CODE_OUTPUT_TEXT)) {
                mSwitchState = SWITCH_STATE_SYMBOL;
            }
            // Switch back to alpha keyboard mode if user types one or more non-space/enter
            // characters followed by a space/enter.
            if (isSpaceOrEnter(code)) {
                toggleAlphabetAndSymbols(autoCapsFlags, recapitalizeMode);
                mPrevSymbolsKeyboardWasShifted = false;
            }
            break;
        case SWITCH_STATE_SYMBOL:
            // Switch back to alpha keyboard mode if user types one or more non-space/enter
            // characters followed by a space/enter.
            if (isSpaceOrEnter(code)) {
                toggleAlphabetAndSymbols(autoCapsFlags, recapitalizeMode);
                mPrevSymbolsKeyboardWasShifted = false;
            }
            break;
        }

        // If the code is a letter, update keyboard shift state.
        if (Constants.isLetterCode(code)) {
            updateAlphabetShiftState(autoCapsFlags, recapitalizeMode);
        } else if (code == Constants.CODE_EMOJI) {
            setEmojiKeyboard();
        } else if (code == Constants.CODE_ALPHA_FROM_EMOJI) {
            setAlphabetKeyboard(autoCapsFlags, recapitalizeMode);
        } else if (code == Constants.CODE_CLIPBOARD) {
            // Note: Printing clipboard content is handled in
            // {@link InputLogic#handleFunctionalEvent(Event,InputTransaction,int,LatinIME.UIHandler)}.
            if (Settings.getInstance().getCurrent().mClipboardHistoryEnabled) {
                setClipboardKeyboard();
            }
        } else if (code == Constants.CODE_ALPHA_FROM_CLIPBOARD) {
            setAlphabetKeyboard(autoCapsFlags, recapitalizeMode);
        } else if (code == Constants.CODE_NUMPAD) {
            setNumpadKeyboard();
        } else if (code == Constants.CODE_ALPHA_FROM_NUMPAD) {
            setAlphabetKeyboard(autoCapsFlags, recapitalizeMode);
        } else if (code == Constants.CODE_SYMBOL_FROM_NUMPAD) {
            setSymbolsKeyboard();
        } else if (code == Constants.CODE_START_ONE_HANDED_MODE) {
            setOneHandedModeEnabled(true);
        } else if (code == Constants.CODE_STOP_ONE_HANDED_MODE) {
            setOneHandedModeEnabled(false);
        } else if (code == Constants.CODE_SWITCH_ONE_HANDED_MODE) {
            switchOneHandedMode();
        }
    }

    static String shiftModeToString(final int shiftMode) {
        switch (shiftMode) {
        case UNSHIFT: return "UNSHIFT";
        case MANUAL_SHIFT: return "MANUAL";
        case AUTOMATIC_SHIFT: return "AUTOMATIC";
        default: return null;
        }
    }

    private static String switchStateToString(final int switchState) {
        switch (switchState) {
        case SWITCH_STATE_ALPHA: return "ALPHA";
        case SWITCH_STATE_SYMBOL_BEGIN: return "SYMBOL-BEGIN";
        case SWITCH_STATE_SYMBOL: return "SYMBOL";
        case SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL: return "MOMENTARY-ALPHA-SYMBOL";
        case SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE: return "MOMENTARY-SYMBOL-MORE";
        case SWITCH_STATE_MOMENTARY_ALPHA_SHIFT: return "MOMENTARY-ALPHA_SHIFT";
        default: return null;
        }
    }

    @Override
    public String toString() {
        return "[keyboard=" + (mMode == MODE_ALPHABET ? mAlphabetShiftState.toString()
                : (mIsSymbolShifted ? "SYMBOLS_SHIFTED" : "SYMBOLS"))
                + " shift=" + mShiftKeyState
                + " symbol=" + mSymbolKeyState
                + " switch=" + switchStateToString(mSwitchState) + "]";
    }

    private String stateToString(final int autoCapsFlags, final int recapitalizeMode) {
        return this + " autoCapsFlags=" + CapsModeUtils.flagsToString(autoCapsFlags)
                + " recapitalizeMode=" + RecapitalizeStatus.modeToString(recapitalizeMode);
    }
}
