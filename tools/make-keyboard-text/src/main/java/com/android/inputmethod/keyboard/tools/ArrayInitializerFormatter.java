/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.android.inputmethod.keyboard.tools;

import java.io.PrintStream;

public class ArrayInitializerFormatter {
    private final PrintStream mOut;
    private final int mMaxWidth;
    private final String mIndent;
    // String resource names array; indexed by {@link #CurrentIndex} and
    // {@link #mStartIndexOfBuffer}.
    private final String[] mResourceNames;

    private int mCurrentIndex = 0;
    private String mLastElement;
    private final StringBuilder mBuffer = new StringBuilder();
    private int mBufferedLen;
    private int mStartIndexOfBuffer = Integer.MIN_VALUE;

    public ArrayInitializerFormatter(final PrintStream out, final int width, final String indent,
            final String[] resourceNames) {
        mOut = out;
        mMaxWidth = width - indent.length();
        mIndent = indent;
        mResourceNames = resourceNames;
    }

    public int getCurrentIndex() {
        return mCurrentIndex;
    }

    public void flush() {
        if (mBuffer.length() == 0) {
            return;
        }
        final int lastIndex = mCurrentIndex - 1;
        if (mStartIndexOfBuffer == lastIndex) {
            mOut.format("%s/* %s */ %s\n",
                    mIndent, mResourceNames[mStartIndexOfBuffer], mBuffer);
        } else if (mStartIndexOfBuffer == lastIndex - 1) {
            final String startElement = mBuffer.toString()
                    .substring(0, mBuffer.length() - mLastElement.length())
                    .trim();
            mOut.format("%s/* %s */ %s\n"
                    + "%s/* %s */ %s\n",
                    mIndent, mResourceNames[mStartIndexOfBuffer], startElement,
                    mIndent, mResourceNames[lastIndex], mLastElement);
        } else {
            mOut.format("%s/* %s ~ */\n"
                    + "%s%s\n"
                    + "%s/* ~ %s */\n",
                    mIndent, mResourceNames[mStartIndexOfBuffer],
                    mIndent, mBuffer,
                    mIndent, mResourceNames[lastIndex]);
        }
        mBuffer.setLength(0);
        mBufferedLen = 0;
    }

    public void outCommentLines(final String lines) {
        flush();
        mOut.print(lines);
        mLastElement = null;
    }

    public void outElement(final String element) {
        if (!element.equals(mLastElement)) {
            flush();
            mStartIndexOfBuffer = mCurrentIndex;
        }
        final int nextLen = mBufferedLen + " ".length() + element.length();
        if (mBufferedLen != 0 && nextLen < mMaxWidth) {
            // Element can fit in the current line.
            mBuffer.append(' ');
            mBuffer.append(element);
            mBufferedLen = nextLen;
        } else {
            // Element should be on the next line.
            if (mBufferedLen != 0) {
                mBuffer.append('\n');
                mBuffer.append(mIndent);
            }
            mBuffer.append(element);
            mBufferedLen = element.length();
        }
        mCurrentIndex++;
        mLastElement = element;
    }
}
