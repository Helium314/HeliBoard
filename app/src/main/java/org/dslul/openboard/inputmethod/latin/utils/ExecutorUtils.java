/*
 * Copyright (C) 2014 The Android Open Source Project
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

package org.dslul.openboard.inputmethod.latin.utils;

import android.util.Log;

import org.dslul.openboard.inputmethod.annotations.UsedForTesting;

import java.lang.Thread.UncaughtExceptionHandler;
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
        return Executors.newSingleThreadScheduledExecutor(new ExecutorFactory(name));
    }

    private static class ExecutorFactory implements ThreadFactory {
        private final String mName;

        private ExecutorFactory(final String name) {
            mName = name;
        }

        @Override
        public Thread newThread(final Runnable runnable) {
            Thread thread = new Thread(runnable, TAG);
            thread.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread thread, Throwable ex) {
                    Log.w(mName + "-" + runnable.getClass().getSimpleName(), ex);
                }
            });
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
