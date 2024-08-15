/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.oscar.aikeyboard.latin.utils;

import android.text.Spanned;
import android.text.style.SuggestionSpan;

import java.util.Arrays;

/**
 * Represents a range of text, relative to the current cursor position.
 */
public final class TextRange {
    private final CharSequence mTextAtCursor;
    private final int mWordAtCursorStartIndex;
    private final int mWordAtCursorEndIndex;
    private final int mCursorIndex;

    public final CharSequence mWord;
    public final boolean mHasUrlSpans;

    public int getNumberOfCharsInWordBeforeCursor() {
        return mCursorIndex - mWordAtCursorStartIndex;
    }

    public int getNumberOfCharsInWordAfterCursor() {
        return mWordAtCursorEndIndex - mCursorIndex;
    }

    public int length() {
        return mWord.length();
    }

    /**
     * Gets the suggestion spans that are put squarely on the word, with the exact start
     * and end of the span matching the boundaries of the word.
     * @return the list of spans.
     */
    public SuggestionSpan[] getSuggestionSpansAtWord() {
        if (!(mTextAtCursor instanceof Spanned && mWord instanceof Spanned)) {
            return new SuggestionSpan[0];
        }
        final Spanned text = (Spanned)mTextAtCursor;
        // Note: it's fine to pass indices negative or greater than the length of the string
        // to the #getSpans() method. The reason we need to get from -1 to +1 is that, the
        // spans were cut at the cursor position, and #getSpans(start, end) does not return
        // spans that end at `start' or begin at `end'. Consider the following case:
        //              this| is          (The | symbolizes the cursor position
        //              ---- ---
        // In this case, the cursor is in position 4, so the 0~7 span has been split into
        // a 0~4 part and a 4~7 part.
        // If we called #getSpans(0, 4) in this case, we would only get the part from 0 to 4
        // of the span, and not the part from 4 to 7, so we would not realize the span actually
        // extends from 0 to 7. But if we call #getSpans(-1, 5) we'll get both the 0~4 and
        // the 4~7 spans and we can merge them accordingly.
        // Any span starting more than 1 char away from the word boundaries in any direction
        // does not touch the word, so we don't need to consider it. That's why requesting
        // -1 ~ +1 is enough.
        // Of course this is only relevant if the cursor is at one end of the word. If it's
        // in the middle, the -1 and +1 are not necessary, but they are harmless.
        final SuggestionSpan[] spans = text.getSpans(mWordAtCursorStartIndex - 1,
                mWordAtCursorEndIndex + 1, SuggestionSpan.class);
        int readIndex = 0;
        int writeIndex = 0;
        for (; readIndex < spans.length; ++readIndex) {
            final SuggestionSpan span = spans[readIndex];
            // The span may be null, as we null them when we find duplicates. Cf a few lines
            // down.
            if (null == span) continue;
            // Tentative span start and end. This may be modified later if we realize the
            // same span is also applied to other parts of the string.
            int spanStart = text.getSpanStart(span);
            int spanEnd = text.getSpanEnd(span);
            for (int i = readIndex + 1; i < spans.length; ++i) {
                if (span.equals(spans[i])) {
                    // We found the same span somewhere else. Read the new extent of this
                    // span, and adjust our values accordingly.
                    spanStart = Math.min(spanStart, text.getSpanStart(spans[i]));
                    spanEnd = Math.max(spanEnd, text.getSpanEnd(spans[i]));
                    // ...and mark the span as processed.
                    spans[i] = null;
                }
            }
            if (spanStart == mWordAtCursorStartIndex && spanEnd == mWordAtCursorEndIndex) {
                // If the span does not start and stop here, ignore it. It probably extends
                // past the start or end of the word, as happens in missing space correction
                // or EasyEditSpans put by voice input.
                spans[writeIndex++] = spans[readIndex];
            }
        }
        return writeIndex == readIndex ? spans : Arrays.copyOfRange(spans, 0, writeIndex);
    }

    public TextRange(final CharSequence textAtCursor, final int wordAtCursorStartIndex,
            final int wordAtCursorEndIndex, final int cursorIndex, final boolean hasUrlSpans) {
        if (wordAtCursorStartIndex < 0 || cursorIndex < wordAtCursorStartIndex
                || cursorIndex > wordAtCursorEndIndex
                || wordAtCursorEndIndex > textAtCursor.length()) {
            throw new IndexOutOfBoundsException();
        }
        mTextAtCursor = textAtCursor;
        mWordAtCursorStartIndex = wordAtCursorStartIndex;
        mWordAtCursorEndIndex = wordAtCursorEndIndex;
        mCursorIndex = cursorIndex;
        mHasUrlSpans = hasUrlSpans;
        mWord = mTextAtCursor.subSequence(mWordAtCursorStartIndex, mWordAtCursorEndIndex);
    }
}