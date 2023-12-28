/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.utils;

import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;

public abstract class RunInLocale<T> {
    private static final Object sLockForRunInLocale = new Object();

    protected abstract T job(final Resources res);

    /**
     * Execute {@link #job(Resources)} method in specified system locale exclusively.
     *
     * @param res the resources to use.
     * @param newLocale the locale to change to. Run in system locale if null.
     * @return the value returned from {@link #job(Resources)}.
     */
    public T runInLocale(final Resources res, final Locale newLocale) {
        synchronized (sLockForRunInLocale) {
            final Configuration conf = res.getConfiguration();
            if (newLocale == null || newLocale.equals(conf.locale)) {
                return job(res);
            }
            final Locale savedLocale = conf.locale;
            try {
                conf.locale = newLocale;
                res.updateConfiguration(conf, null);
                return job(res);
            } finally {
                conf.locale = savedLocale;
                res.updateConfiguration(conf, null);
            }
        }
    }
}
