package com.oscar.aikeyboard.keyboard.internal.keyboard_parser.floris

import kotlinx.serialization.Serializable
import com.oscar.aikeyboard.keyboard.internal.KeyboardParams
import com.oscar.aikeyboard.latin.utils.addCollections

// only the constructor and name remain from FlorisBoard
// we don't care about the difference between main and relevant (at least for now)
@Serializable
open class PopupSet<T : AbstractKeyData>(
    open val main: T? = null,
    open val relevant: Collection<T>? = null
) {
    // get labels of all popup keys
    open fun getPopupKeyLabels(params: KeyboardParams): Collection<String>? {
        if (main == null && relevant == null) return null
        val popupKeys = mutableListOf<String>()
        main?.compute(params)?.getPopupLabel(params)?.let { popupKeys.add(it) }
        relevant?.let { popupKeys.addAll(it.mapNotNull { it.compute(params)?.getPopupLabel(params) }) }
        if (popupKeys.isEmpty()) return null
        return popupKeys
    }
    open fun isEmpty(): Boolean = main == null && relevant.isNullOrEmpty()

    var numberLabel: String? = null
    var symbol: String? = null // maybe list of keys?

    fun <U : AbstractKeyData> merge(other: PopupSet<U>?): PopupSet<out AbstractKeyData> {
        if (other == null || other.isEmpty()) return this
        if (this.isEmpty()) return other
        if (this is SimplePopups) {
            if (other is SimplePopups)
                return SimplePopups(addCollections(popupKeys, other.popupKeys))
            return PopupSet(other.main, addCollections(popupKeys?.map { it.toTextKey() }, other.relevant))
        } else if (other is SimplePopups) {
            return PopupSet(main, addCollections(relevant, other.popupKeys?.map { it.toTextKey() }))
        }
        val newMain = if (main == null) other.main else main
        val newRelevant = addCollections(relevant, other.relevant)
        if (main != null && other.main != null)
            return PopupSet(newMain, addCollections(listOf(other.main!!), newRelevant))
        return PopupSet(newMain, newRelevant)
    }
}

class SimplePopups(val popupKeys: Collection<String>?) :  PopupSet<AbstractKeyData>() {
    override fun getPopupKeyLabels(params: KeyboardParams) = popupKeys
    override fun isEmpty(): Boolean = popupKeys.isNullOrEmpty()
}
