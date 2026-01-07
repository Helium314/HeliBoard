/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal;

public final class TypingTimeRecorder {
    private final int mStaticTimeThresholdAfterFastTyping; // msec
    private final int mSuppressKeyPreviewAfterBatchInputDuration;
    private long mLastTypingTime;
    private long mLastLetterTypingTime;
    private long mLastBatchInputTime;

    public TypingTimeRecorder(final int staticTimeThresholdAfterFastTyping,
            final int suppressKeyPreviewAfterBatchInputDuration) {
        mStaticTimeThresholdAfterFastTyping = staticTimeThresholdAfterFastTyping;
        mSuppressKeyPreviewAfterBatchInputDuration = suppressKeyPreviewAfterBatchInputDuration;
    }

    public boolean isInFastTyping(final long eventTime) {
        final long elapsedTimeSinceLastLetterTyping = eventTime - mLastLetterTypingTime;
        return elapsedTimeSinceLastLetterTyping < mStaticTimeThresholdAfterFastTyping;
    }

    private boolean wasLastInputTyping() {
        return mLastTypingTime >= mLastBatchInputTime;
    }

    public void onCodeInput(final int code, final long eventTime) {
        // Record the letter typing time when
        // 1. Letter keys are typed successively without any batch input in between.
        // 2. A letter key is typed within the threshold time since the last any key typing.
        // 3. A non-letter key is typed within the threshold time since the last letter key typing.
        if (Character.isLetter(code)) {
            if (wasLastInputTyping()
                    || eventTime - mLastTypingTime < mStaticTimeThresholdAfterFastTyping) {
                mLastLetterTypingTime = eventTime;
            }
        } else {
            if (eventTime - mLastLetterTypingTime < mStaticTimeThresholdAfterFastTyping) {
                // This non-letter typing should be treated as a part of fast typing.
                mLastLetterTypingTime = eventTime;
            }
        }
        mLastTypingTime = eventTime;
    }

    public void onEndBatchInput(final long eventTime) {
        mLastBatchInputTime = eventTime;
    }

    public long getLastLetterTypingTime() {
        return mLastLetterTypingTime;
    }

    public boolean needsToSuppressKeyPreviewPopup(final long eventTime) {
        return !wasLastInputTyping()
                && eventTime - mLastBatchInputTime < mSuppressKeyPreviewAfterBatchInputDuration;
    }
}
