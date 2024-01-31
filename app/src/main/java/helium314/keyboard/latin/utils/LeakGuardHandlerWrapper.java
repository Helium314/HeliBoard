/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.utils;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;

public class LeakGuardHandlerWrapper<T> extends Handler {
    private final WeakReference<T> mOwnerInstanceRef;

    public LeakGuardHandlerWrapper(@NonNull final T ownerInstance) {
        this(ownerInstance, Looper.myLooper());
    }

    public LeakGuardHandlerWrapper(@NonNull final T ownerInstance, final Looper looper) {
        super(looper);
        mOwnerInstanceRef = new WeakReference<>(ownerInstance);
    }

    @Nullable
    public T getOwnerInstance() {
        return mOwnerInstanceRef.get();
    }
}
