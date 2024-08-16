/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package com.oscar.aikeyboard.event

import com.oscar.aikeyboard.latin.settings.SettingsValues

/**
 * An object encapsulating a single transaction for input.
 */
class InputTransaction(// Initial conditions
    val mSettingsValues: SettingsValues, val mEvent: Event,
    val mTimestamp: Long, val mSpaceState: Int, val mShiftState: Int) {
    /**
     * Gets what type of shift update this transaction requires.
     * @return The shift update type.
     */
    // Outputs
    var requiredShiftUpdate = SHIFT_NO_UPDATE
        private set
    private var mRequiresUpdateSuggestions = false
    private var mDidAffectContents = false
    private var mDidAutoCorrect = false
    /**
     * Indicate that this transaction requires some type of shift update.
     * @param updateType What type of shift update this requires.
     */
    fun requireShiftUpdate(updateType: Int) {
        requiredShiftUpdate = requiredShiftUpdate.coerceAtLeast(updateType)
    }

    /**
     * Indicate that this transaction requires updating the suggestions.
     */
    fun setRequiresUpdateSuggestions() {
        mRequiresUpdateSuggestions = true
    }

    /**
     * Find out whether this transaction requires updating the suggestions.
     * @return Whether this transaction requires updating the suggestions.
     */
    fun requiresUpdateSuggestions(): Boolean {
        return mRequiresUpdateSuggestions
    }

    /**
     * Indicate that this transaction affected the contents of the editor.
     */
    fun setDidAffectContents() {
        mDidAffectContents = true
    }

    /**
     * Find out whether this transaction affected contents of the editor.
     * @return Whether this transaction affected contents of the editor.
     */
    fun didAffectContents(): Boolean {
        return mDidAffectContents
    }

    /**
     * Indicate that this transaction performed an auto-correction.
     */
    fun setDidAutoCorrect() {
        mDidAutoCorrect = true
    }

    /**
     * Find out whether this transaction performed an auto-correction.
     * @return Whether this transaction performed an auto-correction.
     */
    fun didAutoCorrect(): Boolean {
        return mDidAutoCorrect
    }

    companion object {
        // UPDATE_LATER is stronger than UPDATE_NOW. The reason for this is, if we have to update later,
        // it's because something will change that we can't evaluate now, which means that even if we
        // re-evaluate now we'll have to do it again later. The only case where that wouldn't apply
        // would be if we needed to update now to find out the new state right away, but then we
        // can't do it with this deferred mechanism anyway.
        const val SHIFT_NO_UPDATE = 0
        const val SHIFT_UPDATE_NOW = 1
        const val SHIFT_UPDATE_LATER = 2
    }

}