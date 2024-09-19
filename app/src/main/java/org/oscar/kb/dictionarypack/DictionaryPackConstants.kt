/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.oscar.kb.dictionarypack

/**
 * A class to group constants for dictionary pack usage.
 *
 * This class only defines constants. It should not make any references to outside code as far as
 * possible, as it's used to separate cleanly the keyboard code from the dictionary pack code; this
 * is needed in particular to cleanly compile regression tests.
 */
object DictionaryPackConstants {
    /**
     * The root domain for the dictionary pack, upon which authorities and actions will append
     * their own distinctive strings.
     */
    private const val DICTIONARY_DOMAIN = "helium314.keyboard.dictionarypack.aosp"
    /**
     * Authority for the ContentProvider protocol.
     */
    // TODO: find some way to factorize this string with the one in the resources
    const val AUTHORITY = DICTIONARY_DOMAIN
    /**
     * The action of the intent for publishing that new dictionary data is available.
     */
    // TODO: make this different across different packages. A suggested course of action is
    // to use the package name inside this string.
    // NOTE: The appended string should be uppercase like all other actions, but it's not for
    // historical reasons.
    const val NEW_DICTIONARY_INTENT_ACTION = "$DICTIONARY_DOMAIN.newdict"
}
