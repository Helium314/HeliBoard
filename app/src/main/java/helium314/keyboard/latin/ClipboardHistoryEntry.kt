// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin

import helium314.keyboard.latin.settings.Settings
import kotlinx.serialization.Serializable

@Serializable
data class ClipboardHistoryEntry (
        var timeStamp: Long,
        val content: String,
        var isPinned: Boolean = false
) : Comparable<ClipboardHistoryEntry> {
    override fun compareTo(other: ClipboardHistoryEntry): Int {
        var result = other.isPinned.compareTo(isPinned)
        if (Settings.getValues()?.mClipboardHistoryUnpinnedFirst == true) result = -result
        return if (result != 0) result else other.timeStamp.compareTo(timeStamp)
    }
}
