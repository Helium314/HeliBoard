/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.annotations

/**
 * Denotes that the class, method or field should not be eliminated by ProGuard,
 * so that unit tests can access it. (See proguard.flags)
 */
annotation class UsedForTesting