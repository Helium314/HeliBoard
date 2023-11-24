/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.utils;

import android.util.Log;

import org.dslul.openboard.inputmethod.annotations.UsedForTesting;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Utilities to manage executors.
 */
public class ExecutorUtils {

    private static final String TAG = "ExecutorUtils";

    public static final String KEYBOARD = "Keyboard";
    public static final String SPELLING = "Spelling";

    private static ScheduledExecutorService sKeyboardExecutorService = newExecutorService(KEYBOARD);
    private static ScheduledExecutorService sSpellingExecutorService = newExecutorService(SPELLING);

    private static ScheduledExecutorService newExecutorService(final String name) {
        // use more than a single thread, to reduce the occasional wait (mostly relevant when using multiple languages)
        // limit number to cores / 2 to never interfere with whatever some other app is doing
        final int cores = Runtime.getRuntime().availableProcessors();
        final int threads = Math.max(cores / 2, 1);
        return Executors.newScheduledThreadPool(threads, new ExecutorFactory(name));
    }

    private static class ExecutorFactory implements ThreadFactory {
        private final String mName;

        private ExecutorFactory(final String name) {
            mName = name;
        }

        @Override
        public Thread newThread(final Runnable runnable) {
            Thread thread = new Thread(runnable, TAG);
            thread.setUncaughtExceptionHandler((thread1, ex) ->
                    Log.w(mName + "-" + runnable.getClass().getSimpleName(), ex));
            return thread;
        }
    }

    @UsedForTesting
    private static ScheduledExecutorService sExecutorServiceForTests;

    @UsedForTesting
    public static void setExecutorServiceForTests(
            final ScheduledExecutorService executorServiceForTests) {
        sExecutorServiceForTests = executorServiceForTests;
    }

    //
    // Public methods used to schedule a runnable for execution.
    //

    /**
     * @param name Executor's name.
     * @return scheduled executor service used to run background tasks
     */
    public static ScheduledExecutorService getBackgroundExecutor(final String name) {
        if (sExecutorServiceForTests != null) {
            return sExecutorServiceForTests;
        }
        switch (name) {
            case KEYBOARD:
                return sKeyboardExecutorService;
            case SPELLING:
                return sSpellingExecutorService;
            default:
                throw new IllegalArgumentException("Invalid executor: " + name);
        }
    }

    public static void killTasks(final String name) {
        final ScheduledExecutorService executorService = getBackgroundExecutor(name);
        executorService.shutdownNow();
        try {
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.wtf(TAG, "Failed to shut down: " + name);
        }
        if (executorService == sExecutorServiceForTests) {
            // Don't do anything to the test service.
            return;
        }
        switch (name) {
            case KEYBOARD:
                sKeyboardExecutorService = newExecutorService(KEYBOARD);
                break;
            case SPELLING:
                sSpellingExecutorService = newExecutorService(SPELLING);
                break;
            default:
                throw new IllegalArgumentException("Invalid executor: " + name);
        }
    }

    @UsedForTesting
    public static Runnable chain(final Runnable... runnables) {
        return new RunnableChain(runnables);
    }

    @UsedForTesting
    public static class RunnableChain implements Runnable {
        private final Runnable[] mRunnables;

        private RunnableChain(final Runnable... runnables) {
            if (runnables == null || runnables.length == 0) {
                throw new IllegalArgumentException("Attempting to construct an empty chain");
            }
            mRunnables = runnables;
        }

        @UsedForTesting
        public Runnable[] getRunnables() {
            return mRunnables;
        }

        @Override
        public void run() {
            for (Runnable runnable : mRunnables) {
                if (Thread.interrupted()) {
                    return;
                }
                runnable.run();
            }
        }
    }
}
