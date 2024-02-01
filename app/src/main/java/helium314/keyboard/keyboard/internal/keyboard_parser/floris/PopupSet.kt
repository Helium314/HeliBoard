/*
 * Copyright (C) 2020 Patrick Goldinger
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package helium314.keyboard.keyboard.internal.keyboard_parser.floris

import kotlinx.serialization.Serializable
import helium314.keyboard.keyboard.internal.KeySpecParser
import helium314.keyboard.keyboard.internal.KeyboardParams

// only the constructor and name remain from FlorisBoard
// we don't care about the difference between main and relevant (at least for now)
@Serializable
open class PopupSet<T : AbstractKeyData>(
    open val main: T? = null,
    open val relevant: List<T>? = null
) {
    // get labels of all popup keys
    open fun getPopupKeyLabels(params: KeyboardParams): Collection<String>? {
        if (main == null && relevant == null) return null
        val moreKeys = mutableListOf<String>()
        main?.getPopupLabel(params)?.let { moreKeys.add(it) }
        relevant?.let { moreKeys.addAll(it.map { it.getPopupLabel(params) }) }
        if (moreKeys.isEmpty()) return null
        return moreKeys
    }

    var numberIndex: Int? = null
    var symbol: String? = null // maybe list of keys?
}

class SimplePopups(val moreKeys: Collection<String>?) :  PopupSet<AbstractKeyData>() {
    override fun getPopupKeyLabels(params: KeyboardParams) = moreKeys
}
