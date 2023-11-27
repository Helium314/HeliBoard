/*
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.floris

import kotlinx.serialization.Serializable
import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardParams
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.rtlLabel

// taken from FlorisBoard, small modifications
//  mutable set removed (currently the moreKeys assembly is happening in KeyParams)
//  .toMoreKeys added
// PopupKeys not used, but might switch to this later
//  currently hint would be taken from other, and languageMoreKeys are prioritized
/**
 * A popup set for a single key. This set describes, if the key has a [main] character and other [relevant] popups.
 *
 * Note that a hint character cannot and should not be set in a json extended popup file, rather it
 * should only be dynamically set by the LayoutManager.
 */
@Serializable
open class PopupSet<T : AbstractKeyData>(
    open val main: T? = null,
    open val relevant: List<T> = emptyList()
) {
    // todo (idea):
    //  this is very simple, but essentially what happens in the old system
    //  could make use of PopupKeys, and also provide a hint depending on user choice
    //  then language key joining should be done in here too
    //  also what about getting the moreKeys and key hint from chosen symbol layout?
    fun toMoreKeys(params: KeyboardParams): Array<String>? {
        val moreKeys = mutableListOf<String>()
        // number + main + relevant in this order (label is later taken from first element in resulting array)
        moreKeys.addAll(params.mLocaleKeyTexts.getNumberMoreKeys(numberIndex))
        main?.getLabel(params)?.let { moreKeys.add(it) }
        moreKeys.addAll(relevant.map {
            val label = it.getLabel(params)
            if (label == "$$$") { // currency key
                if (params.mId.passwordInput()) "$"
                else params.mLocaleKeyTexts.currencyKey.first
            } else if (params.mId.mSubtype.isRtlSubtype) {
                label.rtlLabel(params)
            } else label
        })
        return moreKeys.takeIf { it.isNotEmpty() }?.toTypedArray()
    }

    private val popupKeys: PopupKeys<T> by lazy {
        PopupKeys(null, listOfNotNull(main), relevant)
    }
    var numberIndex: Int? = null
}

/**
 * A fully configured collection of popup keys. It contains a list of keys to be prioritized
 * during rendering (ordered by relevance descending) by showing those keys close to the
 * popup spawning point.
 *
 * The keys contain a separate [hint] key to ease rendering the hint label, but the hint, if
 * present, also occurs in the [prioritized] list.
 *
 * The popup keys can be accessed like an array with the addition that negative indexes defined
 * within this companion object are allowed (as long as the corresponding [prioritized] list
 * contains the corresponding amount of keys.
 */
class PopupKeys<T>(
    val hint: T?,
    val prioritized: List<T>,
    val other: List<T>
) : Collection<T> {
    companion object {
        const val FIRST_PRIORITIZED = -1
        const val SECOND_PRIORITIZED = -2
        const val THIRD_PRIORITIZED = -3
    }

    override val size: Int
        get() = prioritized.size + other.size

    override fun contains(element: T): Boolean {
        return prioritized.contains(element) || other.contains(element)
    }

    override fun containsAll(elements: Collection<T>): Boolean {
        return (prioritized + other).containsAll(elements)
    }

    override fun isEmpty(): Boolean {
        return prioritized.isEmpty() && other.isEmpty()
    }

    override fun iterator(): Iterator<T> {
        return (prioritized + other).listIterator()
    }

    fun getOrNull(index: Int): T? {
        if (index >= other.size || index < -prioritized.size) {
            return null
        }
        return when (index) {
            FIRST_PRIORITIZED -> prioritized[0]
            SECOND_PRIORITIZED -> prioritized[1]
            THIRD_PRIORITIZED -> prioritized[2]
            else -> other.getOrNull(index)
        }
    }

    operator fun get(index: Int): T {
        val item = getOrNull(index)
        if (item == null) {
            throw IndexOutOfBoundsException(
                "Specified index $index is not an valid entry in this PopupKeys!"
            )
        } else {
            return item
        }
    }
}
