/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.oscar.aikeyboard.latin.utils;

/**
 * A class for logging and debugging utility methods.
 */
public final class DebugLogUtils {
    /**
     * Get the string representation of the current stack trace, for debugging purposes.
     * @return a readable, carriage-return-separated string for the current stack trace.
     */
    public static String getStackTrace() {
        return getStackTrace(Integer.MAX_VALUE - 1);
    }

    /**
     * Get the string representation of the current stack trace, for debugging purposes.
     * @param limit the maximum number of stack frames to be returned.
     * @return a readable, carriage-return-separated string for the current stack trace.
     */
    public static String getStackTrace(final int limit) {
        final StringBuilder sb = new StringBuilder();
        try {
            throw new RuntimeException();
        } catch (final RuntimeException e) {
            final StackTraceElement[] frames = e.getStackTrace();
            // Start at 1 because the first frame is here and we don't care about it
            for (int j = 1; j < frames.length && j < limit + 1; ++j) {
                sb.append(frames[j].toString()).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Get the stack trace contained in an exception as a human-readable string.
     * @param t the throwable
     * @return the human-readable stack trace
     */
    public static String getStackTrace(final Throwable t) {
        final StringBuilder sb = new StringBuilder();
        final StackTraceElement[] frames = t.getStackTrace();
        for (int j = 0; j < frames.length; ++j) {
            sb.append(frames[j].toString() + "\n");
        }
        return sb.toString();
    }
}
