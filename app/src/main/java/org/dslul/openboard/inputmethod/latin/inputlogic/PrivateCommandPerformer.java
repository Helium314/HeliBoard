/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.inputlogic;

import android.os.Bundle;

/**
 * Provides an interface matching
 * {@link android.view.inputmethod.InputConnection#performPrivateCommand(String,Bundle)}.
 */
public interface PrivateCommandPerformer {
    /**
     * API to send private commands from an input method to its connected
     * editor. This can be used to provide domain-specific features that are
     * only known between certain input methods and their clients.
     *
     * @param action Name of the command to be performed. This must be a scoped
     *            name, i.e. prefixed with a package name you own, so that
     *            different developers will not create conflicting commands.
     * @param data Any data to include with the command.
     * @return true if the command was sent (regardless of whether the
     * associated editor understood it), false if the input connection is no
     * longer valid.
     */
    boolean performPrivateCommand(String action, Bundle data);
}
