/*
 * Copyright (C) 2012 The Android Open Source Project
 * Modifications copyright (C) 2019 OpenBoard
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.event

import android.view.KeyEvent

/**
 * An event decoder for hardware events.
 */
interface HardwareEventDecoder : EventDecoder {
    fun decodeHardwareKey(keyEvent: KeyEvent): Event
}