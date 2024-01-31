/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.inputlogic;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import helium314.keyboard.latin.LatinIME;
import helium314.keyboard.latin.Suggest.OnGetSuggestedWordsCallback;
import helium314.keyboard.latin.SuggestedWords;
import helium314.keyboard.latin.common.InputPointers;

/**
 * A helper to manage deferred tasks for the input logic.
 */
class InputLogicHandler implements Handler.Callback {
    final Handler mNonUIThreadHandler;
    // TODO: remove this reference.
    final LatinIME mLatinIME;
    final InputLogic mInputLogic;
    private final Object mLock = new Object();
    private boolean mInBatchInput; // synchronized using {@link #mLock}.

    private static final int MSG_GET_SUGGESTED_WORDS = 1;

    // A handler that never does anything. This is used for cases where events come before anything
    // is initialized, though probably only the monkey can actually do this.
    public static final InputLogicHandler NULL_HANDLER = new InputLogicHandler() {
        @Override
        public void reset() {}
        @Override
        public boolean handleMessage(final Message msg) { return true; }
        @Override
        public void onStartBatchInput() {}
        @Override
        public void onUpdateBatchInput(final InputPointers batchPointers,
                final int sequenceNumber) {}
        @Override
        public void onCancelBatchInput() {}
        @Override
        public void updateTailBatchInput(final InputPointers batchPointers,
                final int sequenceNumber) {}
        @Override
        public void getSuggestedWords(final int sessionId, final int sequenceNumber,
                final OnGetSuggestedWordsCallback callback) {}
    };

    InputLogicHandler() {
        mNonUIThreadHandler = null;
        mLatinIME = null;
        mInputLogic = null;
    }

    public InputLogicHandler(final LatinIME latinIME, final InputLogic inputLogic) {
        final HandlerThread handlerThread = new HandlerThread(
                InputLogicHandler.class.getSimpleName());
        handlerThread.start();
        mNonUIThreadHandler = new Handler(handlerThread.getLooper(), this);
        mLatinIME = latinIME;
        mInputLogic = inputLogic;
    }

    public void reset() {
        mNonUIThreadHandler.removeCallbacksAndMessages(null);
    }

    // In unit tests, we create several instances of LatinIME, which results in several instances
    // of InputLogicHandler. To avoid these handlers lingering, we call this.
    public void destroy() {
        mNonUIThreadHandler.getLooper().quitSafely();
    }

    /**
     * Handle a message.
     * @see android.os.Handler.Callback#handleMessage(android.os.Message)
     */
    // Called on the Non-UI handler thread by the Handler code.
    @Override
    public boolean handleMessage(final Message msg) {
        switch (msg.what) {
            case MSG_GET_SUGGESTED_WORDS:
                mLatinIME.getSuggestedWords(msg.arg1 /* inputStyle */,
                        msg.arg2 /* sequenceNumber */, (OnGetSuggestedWordsCallback) msg.obj);
                break;
        }
        return true;
    }

    // Called on the UI thread by InputLogic.
    public void onStartBatchInput() {
        synchronized (mLock) {
            mInBatchInput = true;
        }
    }

    public boolean isInBatchInput() {
        return mInBatchInput;
    }

    /**
     * Fetch suggestions corresponding to an update of a batch input.
     * @param batchPointers the updated pointers, including the part that was passed last time.
     * @param sequenceNumber the sequence number associated with this batch input.
     * @param isTailBatchInput true if this is the end of a batch input, false if it's an update.
     */
    // This method can be called from any thread and will see to it that the correct threads
    // are used for parts that require it. This method will send a message to the Non-UI handler
    // thread to pull suggestions, and get the inlined callback to get called on the Non-UI
    // handler thread. If this is the end of a batch input, the callback will then proceed to
    // send a message to the UI handler in LatinIME so that showing suggestions can be done on
    // the UI thread.
    private void updateBatchInput(final InputPointers batchPointers,
            final int sequenceNumber, final boolean isTailBatchInput) {
        synchronized (mLock) {
            if (!mInBatchInput) {
                // Batch input has ended or canceled while the message was being delivered.
                return;
            }
            mInputLogic.mWordComposer.setBatchInputPointers(batchPointers);
            final OnGetSuggestedWordsCallback callback = new OnGetSuggestedWordsCallback() {
                @Override
                public void onGetSuggestedWords(final SuggestedWords suggestedWords) {
                    showGestureSuggestionsWithPreviewVisuals(suggestedWords, isTailBatchInput);
                }
            };
            getSuggestedWords(isTailBatchInput ? SuggestedWords.INPUT_STYLE_TAIL_BATCH
                    : SuggestedWords.INPUT_STYLE_UPDATE_BATCH, sequenceNumber, callback);
        }
    }

    void showGestureSuggestionsWithPreviewVisuals(final SuggestedWords suggestedWordsForBatchInput,
            final boolean isTailBatchInput) {
        final SuggestedWords suggestedWordsToShowSuggestions;
        // We're now inside the callback. This always runs on the Non-UI thread,
        // no matter what thread updateBatchInput was originally called on.
        if (suggestedWordsForBatchInput.isEmpty()) {
            // Use old suggestions if we don't have any new ones.
            // Previous suggestions are found in InputLogic#mSuggestedWords.
            // Since these are the most recent ones and we just recomputed
            // new ones to update them, then the previous ones are there.
            suggestedWordsToShowSuggestions = mInputLogic.mSuggestedWords;
        } else {
            suggestedWordsToShowSuggestions = suggestedWordsForBatchInput;
        }
        mLatinIME.mHandler.showGesturePreviewAndSuggestionStrip(suggestedWordsToShowSuggestions,
                isTailBatchInput /* dismissGestureFloatingPreviewText */);
        if (isTailBatchInput) {
            mInBatchInput = false;
            // The following call schedules onEndBatchInputInternal
            // to be called on the UI thread.
            mLatinIME.mHandler.showTailBatchInputResult(suggestedWordsToShowSuggestions);
        }
    }

    /**
     * Update a batch input.
     *
     * This fetches suggestions and updates the suggestion strip and the floating text preview.
     *
     * @param batchPointers the updated batch pointers.
     * @param sequenceNumber the sequence number associated with this batch input.
     */
    // Called on the UI thread by InputLogic.
    public void onUpdateBatchInput(final InputPointers batchPointers,
            final int sequenceNumber) {
        updateBatchInput(batchPointers, sequenceNumber, false /* isTailBatchInput */);
    }

    /**
     * Cancel a batch input.
     *
     * Note that as opposed to updateTailBatchInput, we do the UI side of this immediately on the
     * same thread, rather than get this to call a method in LatinIME. This is because
     * canceling a batch input does not necessitate the long operation of pulling suggestions.
     */
    // Called on the UI thread by InputLogic.
    public void onCancelBatchInput() {
        synchronized (mLock) {
            mInBatchInput = false;
        }
    }

    /**
     * Trigger an update for a tail batch input.
     *
     * A tail batch input is the last update for a gesture, the one that is triggered after the
     * user lifts their finger. This method schedules fetching suggestions on the non-UI thread,
     * then when the suggestions are computed it comes back on the UI thread to update the
     * suggestion strip, commit the first suggestion, and dismiss the floating text preview.
     *
     * @param batchPointers the updated batch pointers.
     * @param sequenceNumber the sequence number associated with this batch input.
     */
    // Called on the UI thread by InputLogic.
    public void updateTailBatchInput(final InputPointers batchPointers,
            final int sequenceNumber) {
        updateBatchInput(batchPointers, sequenceNumber, true /* isTailBatchInput */);
    }

    public void getSuggestedWords(final int inputStyle, final int sequenceNumber,
            final OnGetSuggestedWordsCallback callback) {
        mNonUIThreadHandler.obtainMessage(
                MSG_GET_SUGGESTED_WORDS, inputStyle, sequenceNumber, callback).sendToTarget();
    }
}
