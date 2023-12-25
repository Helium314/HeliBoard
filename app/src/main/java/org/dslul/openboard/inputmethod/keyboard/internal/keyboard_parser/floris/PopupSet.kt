/*
 * Copyright (C) 2020 Patrick Goldinger
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.floris

import kotlinx.serialization.Serializable
import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardParams

// taken from FlorisBoard, considerably modified
// we don't care about the difference between main and relevant in this app
@Serializable
open class PopupSet<T : AbstractKeyData>(
    open val main: T? = null,
    open val relevant: List<T>? = null
) {
    // get labels of all popup keys
    open fun getPopupKeyLabels(params: KeyboardParams): Collection<String>? {
        if (main == null && relevant == null) return null
        val moreKeys = mutableListOf<String>()
        main?.getLabel(params)?.let { moreKeys.add(it) }
        relevant?.let { moreKeys.addAll(it.map { it.getLabel(params) }) }
        if (moreKeys.isEmpty()) return null
        return moreKeys
    }

    var numberIndex: Int? = null
}

class SimplePopups(val moreKeys: Collection<String>?) :  PopupSet<AbstractKeyData>() {
    override fun getPopupKeyLabels(params: KeyboardParams) = moreKeys
}
