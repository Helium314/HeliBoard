/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.utils;

import org.dslul.openboard.inputmethod.latin.utils.Log;

import org.dslul.openboard.inputmethod.latin.define.DebugFlags;

/**
 * A class for logging and debugging utility methods.
 */
public final class DebugLogUtils {
    private final static String TAG = DebugLogUtils.class.getSimpleName();
    private final static boolean sDBG = DebugFlags.DEBUG_ENABLED;

    /**
     * Calls .toString() on its non-null argument or returns "null"
     * @param o the object to convert to a string
     * @return the result of .toString() or null
     */
    public static String s(final Object o) {
        return null == o ? "null" : o.toString();
    }

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
                sb.append(frames[j].toString() + "\n");
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

    /**
     * Helper log method to ease null-checks and adding spaces.
     *
     * This sends all arguments to the log, separated by spaces. Any null argument is converted
     * to the "null" string. It uses a very visible tag and log level for debugging purposes.
     *
     * @param args the stuff to send to the log
     */
    public static void l(final Object... args) {
        if (!sDBG) return;
        final StringBuilder sb = new StringBuilder();
        for (final Object o : args) {
            sb.append(s(o));
            sb.append(" ");
        }
        Log.e(TAG, sb.toString());
    }

    /**
     * Helper log method to put stuff in red.
     *
     * This does the same as #l but prints in red
     *
     * @param args the stuff to send to the log
     */
    public static void r(final Object... args) {
        if (!sDBG) return;
        final StringBuilder sb = new StringBuilder("\u001B[31m");
        for (final Object o : args) {
            sb.append(s(o));
            sb.append(" ");
        }
        sb.append("\u001B[0m");
        Log.e(TAG, sb.toString());
    }
}
