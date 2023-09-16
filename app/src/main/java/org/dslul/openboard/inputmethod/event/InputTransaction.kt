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
package org.dslul.openboard.inputmethod.event

import org.dslul.openboard.inputmethod.latin.settings.SettingsValues

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
        requiredShiftUpdate = Math.max(requiredShiftUpdate, updateType)
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