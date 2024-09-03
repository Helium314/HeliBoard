/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.oscar.kb.latin.utils;


import org.oscar.kb.latin.utils.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * This class is a holder of the result of an asynchronous computation.
 *
 * @param <E> the type of the result.
 */
public class AsyncResultHolder<E> {

    private final Object mLock = new Object();

    private E mResult;
    private final String mTag;
    private final CountDownLatch mLatch;

    public AsyncResultHolder(final String tag) {
        mTag = tag;
        mLatch = new CountDownLatch(1);
    }

    /**
     * Sets the result value of this holder.
     *
     * @param result the value to set.
     */
    public void set(final E result) {
        synchronized(mLock) {
            if (mLatch.getCount() > 0) {
                mResult = result;
                mLatch.countDown();
            }
        }
    }

    /**
     * Gets the result value held in this holder.
     * Causes the current thread to wait unless the value is set or the specified time is elapsed.
     *
     * @param defaultValue the default value.
     * @param timeOut the maximum time to wait.
     * @return if the result is set before the time limit then the result, otherwise defaultValue.
     */
    public E get(final E defaultValue, final long timeOut) {
        try {
            return mLatch.await(timeOut, TimeUnit.MILLISECONDS) ? mResult : defaultValue;
        } catch (InterruptedException e) {
            Log.w(mTag, "get() : Interrupted after " + timeOut + " ms");
            return defaultValue;
        }
    }
}
