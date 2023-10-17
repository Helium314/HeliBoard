// SPDX-License-Identifier: GPL-3.0-only

package org.dslul.openboard.inputmethod.latin

data class ClipboardHistoryEntry (
        var timeStamp: Long,
        val content: CharSequence,
        var isPinned: Boolean = false
) : Comparable<ClipboardHistoryEntry> {

    override fun compareTo(other: ClipboardHistoryEntry): Int {
        val result = other.isPinned.compareTo(isPinned)
        return if (result != 0) result else other.timeStamp.compareTo(timeStamp)
    }
}