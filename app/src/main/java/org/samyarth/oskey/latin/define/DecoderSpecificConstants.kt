/*
 * Copyright (C) 2015 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.samyarth.oskey.latin.define

/**
 * Decoder specific constants for LatinIme.
 */
object DecoderSpecificConstants {
    // Must be equal to MAX_WORD_LENGTH in native/jni/src/defines.h
    const val DICTIONARY_MAX_WORD_LENGTH = 48

    // (MAX_PREV_WORD_COUNT_FOR_N_GRAM + 1)-gram is supported in Java side. Needs to modify
    // MAX_PREV_WORD_COUNT_FOR_N_GRAM in native/jni/src/defines.h for suggestions.
    const val MAX_PREV_WORD_COUNT_FOR_N_GRAM = 3
    const val DECODER_DICT_SUFFIX = ""
    const val SHOULD_VERIFY_MAGIC_NUMBER = true
    const val SHOULD_VERIFY_CHECKSUM = true
    const val SHOULD_USE_DICT_VERSION = true
    const val SHOULD_AUTO_CORRECT_USING_NON_WHITE_LISTED_SUGGESTION = false
    const val SHOULD_REMOVE_PREVIOUSLY_REJECTED_SUGGESTION = true
}