// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin

import android.content.ClipboardManager
import android.content.Context
import android.text.InputType
import android.text.TextUtils
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import helium314.keyboard.compat.ClipboardManagerCompat
import helium314.keyboard.latin.settings.Settings
import kotlin.collections.ArrayList

class ClipboardHistoryManager(
        private val latinIME: LatinIME
) : ClipboardManager.OnPrimaryClipChangedListener {

    private lateinit var clipboardManager: ClipboardManager
    private var onHistoryChangeListener: OnHistoryChangeListener? = null

    fun onCreate() {
        clipboardManager = latinIME.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        fetchPrimaryClip()
        clipboardManager.addPrimaryClipChangedListener(this)
        loadPinnedClips()
    }

    fun onPinnedClipsAvailable(pinnedClips: List<ClipboardHistoryEntry>) {
        historyEntries.addAll(pinnedClips)
        sortHistoryEntries()
        if (onHistoryChangeListener != null) {
            pinnedClips.forEach {
                onHistoryChangeListener?.onClipboardHistoryEntryAdded(historyEntries.indexOf(it))
            }
        }
    }

    fun onDestroy() {
        clipboardManager.removePrimaryClipChangedListener(this)
    }

    override fun onPrimaryClipChanged() {
        // Make sure we read clipboard content only if history settings is set
        if (latinIME.mSettings.current?.mClipboardHistoryEnabled == true) {
            fetchPrimaryClip()
        }
    }

    private fun fetchPrimaryClip() {
        val clipData = clipboardManager.primaryClip ?: return
        val content = retrieveClipboardContent()
        if (TextUtils.isEmpty(content)) return
        val timeStamp = ClipboardManagerCompat.getClipTimestamp(clipData) ?: System.currentTimeMillis()
        val duplicateEntryIndex = historyEntries.indexOfFirst { it.content.toString() == content.toString() }
        if (duplicateEntryIndex != -1) {
            val existingEntry = historyEntries[duplicateEntryIndex]
            if (existingEntry.timeStamp == timeStamp) return // nothing to change (may occur frequently starting with API 30)
            // older entry with the same text already exists, update the timestamp and re-sort the list
            existingEntry.timeStamp = timeStamp
            historyEntries.removeAt(duplicateEntryIndex)
            historyEntries.add(0, existingEntry)
            sortHistoryEntries()
            val newIndex = historyEntries.indexOf(existingEntry)
            onHistoryChangeListener?.onClipboardHistoryEntryMoved(duplicateEntryIndex, newIndex)
            return
        }
        if (historyEntries.any { it.content.toString() == content.toString() }) return

        val entry = ClipboardHistoryEntry(timeStamp, content)
        historyEntries.add(entry)
        sortHistoryEntries()
        val at = historyEntries.indexOf(entry)
        onHistoryChangeListener?.onClipboardHistoryEntryAdded(at)
    }

    fun toggleClipPinned(ts: Long) {
        val from = historyEntries.indexOfFirst { it.timeStamp == ts }
        val historyEntry = historyEntries[from].apply {
            timeStamp = System.currentTimeMillis()
            isPinned = !isPinned
        }
        sortHistoryEntries()
        val to = historyEntries.indexOf(historyEntry)
        onHistoryChangeListener?.onClipboardHistoryEntryMoved(from, to)
        savePinnedClips()
    }

    fun clearHistory() {
        ClipboardManagerCompat.clearPrimaryClip(clipboardManager)
        val pos = historyEntries.indexOfFirst { !it.isPinned }
        val count = historyEntries.count { !it.isPinned }
        historyEntries.removeAll { !it.isPinned }
        if (onHistoryChangeListener != null) {
            onHistoryChangeListener?.onClipboardHistoryEntriesRemoved(pos, count)
        }
        // get rid of any clipboard suggestion
        if (latinIME.mSettings.current.mSuggestClipboardContent) {
            latinIME.mHandler?.postUpdateSuggestionStrip(latinIME.currentInputEditorInfo?.inputType ?: InputType.TYPE_NULL)
        }
    }

    fun canRemove(index: Int) = index in 0 until historyEntries.size && !historyEntries[index].isPinned

    fun removeEntry(index: Int) {
        if (canRemove(index))
            historyEntries.removeAt(index)
    }

    private fun sortHistoryEntries() {
        historyEntries.sort()
    }

    private fun checkClipRetentionElapsed() {
        val mins = latinIME.mSettings.current.mClipboardHistoryRetentionTime
        if (mins <= 0) return // No retention limit
        val maxClipRetentionTime = mins * 60 * 1000L
        val now = System.currentTimeMillis()
        historyEntries.removeAll { !it.isPinned && (now - it.timeStamp) > maxClipRetentionTime }
    }

    // We do not want to update history while user is visualizing it, so we check retention only
    // when history is about to be shown
    fun prepareClipboardHistory() = checkClipRetentionElapsed()

    fun getHistorySize() = historyEntries.size

    fun getHistoryEntry(position: Int) = historyEntries[position]

    fun getHistoryEntryContent(timeStamp: Long) = historyEntries.first { it.timeStamp == timeStamp }

    fun setHistoryChangeListener(l: OnHistoryChangeListener?) {
        onHistoryChangeListener = l
    }

    // This will return the content of the primary clipboard if it is not empty.
    // It may be specified whether only a recent clipboard item
    // should be retrieved (relevant for clipboard suggestions).
    fun retrieveClipboardContent(recentOnly : Boolean = false): CharSequence {
        val clipData = clipboardManager.primaryClip ?: return ""
        if (clipData.itemCount == 0) return ""
        // TODO: remove the following check when clipboard images or other media types are supported
        if (clipData.description?.hasMimeType("text/*") == false) return ""
        val clipContent = clipData.getItemAt(0)?.coerceToText(latinIME) ?: ""
        return if (recentOnly) {
            val timeStamp = ClipboardManagerCompat.getClipTimestamp(clipData) ?: System.currentTimeMillis()
            val isNewEntry = recentEntry.toString() != clipContent.toString()
            val isRecent = (timeStamp - recentTimestamp) < RECENT_TIME_MILLIS
            if (isNewEntry || isRecent && !suggestionPicked) {
                if (isNewEntry) {
                    suggestionPicked = false
                    recentEntry = clipContent
                    recentTimestamp = timeStamp
                    clipSensitivity = ClipboardManagerCompat.getClipSensitivity(clipData)
                }
                clipContent
            } else "" // empty string indicating clipboard is not recent
        } else clipContent
    }

    fun isClipSensitive() : Boolean {
        return clipSensitivity
    }

    fun markSuggestionAsPicked() {
        suggestionPicked = true
    }

    // pinned clips are stored in default shared preferences, not in device protected preferences!
    private fun loadPinnedClips() {
        val pinnedClipString = Settings.readPinnedClipString(latinIME)
        if (pinnedClipString.isEmpty()) return
        val pinnedClips: List<ClipboardHistoryEntry> = Json.decodeFromString(pinnedClipString)
        latinIME.mHandler.postUpdateClipboardPinnedClips(pinnedClips)
    }

    private fun savePinnedClips() {
        val pinnedClips = Json.encodeToString(historyEntries.filter { it.isPinned })
        Settings.writePinnedClipString(latinIME, pinnedClips)
    }

    interface OnHistoryChangeListener {
        fun onClipboardHistoryEntryAdded(at: Int)
        fun onClipboardHistoryEntriesRemoved(pos: Int, count: Int)
        fun onClipboardHistoryEntryMoved(from: Int, to: Int)
    }

    companion object {
        // store pinned clips in companion object so they survive a keyboard switch (which destroys the current instance)
        private val historyEntries: MutableList<ClipboardHistoryEntry> = ArrayList()
        private var recentEntry: CharSequence = ""
        private var recentTimestamp: Long = 0L
        private var suggestionPicked: Boolean = false
        private var clipSensitivity: Boolean = false
        private const val RECENT_TIME_MILLIS = 3 * 60 * 1000L // 3 minutes (for clipboard suggestions)
    }
}
