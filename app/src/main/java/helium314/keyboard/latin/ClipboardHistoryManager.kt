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
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import kotlin.collections.ArrayList

class ClipboardHistoryManager(
        private val latinIME: LatinIME
) : ClipboardManager.OnPrimaryClipChangedListener {

    private lateinit var clipboardManager: ClipboardManager
    private var onHistoryChangeListener: OnHistoryChangeListener? = null

    fun onCreate() {
        clipboardManager = latinIME.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(this)
        if (historyEntries.isEmpty())
            loadPinnedClips()
        if (Settings.readClipboardHistoryEnabled(DeviceProtectedUtils.getSharedPreferences(latinIME)))
            fetchPrimaryClip()
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
        if (clipData.itemCount == 0 || clipData.description?.hasMimeType("text/*") == false) return
        clipData.getItemAt(0)?.let { clipItem ->
            val timeStamp = ClipboardManagerCompat.getClipTimestamp(clipData) ?: System.currentTimeMillis()
            val content = clipItem.coerceToText(latinIME)
            if (TextUtils.isEmpty(content)) return

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
                updateClipboardSuggestion()
                return
            }
            if (historyEntries.any { it.content.toString() == content.toString() }) return

            val entry = ClipboardHistoryEntry(timeStamp, content)
            historyEntries.add(entry)
            sortHistoryEntries()
            val at = historyEntries.indexOf(entry)
            onHistoryChangeListener?.onClipboardHistoryEntryAdded(at)
            updateClipboardSuggestion()
        }
    }

    private fun updateClipboardSuggestion() {
        if (latinIME.mSettings.current?.mSuggestClipboardContent == true) {
            latinIME.mHandler?.postUpdateSuggestionStrip(latinIME.currentInputEditorInfo?.inputType ?: InputType.TYPE_NULL)
        }
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
        updateClipboardSuggestion() // get rid of any clipboard suggestion
    }

    fun canRemove(index: Int) = historyEntries.getOrNull(index)?.isPinned != true

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

    fun retrieveClipboardContent(): CharSequence {
        val clipData = clipboardManager.primaryClip ?: return ""
        if (clipData.itemCount == 0) return ""
        return clipData.getItemAt(0)?.coerceToText(latinIME) ?: ""
    }

    fun retrieveClipboardSuggestionContent(): String {
        val clipData = clipboardManager.primaryClip ?: return ""
        if (clipData.itemCount == 0 || clipData.description?.hasMimeType("text/*") == false) return ""
        val clipContent = clipData.getItemAt(0)?.coerceToText(latinIME)?.toString() ?: return ""
        val clipTimestamp = ClipboardManagerCompat.getClipTimestamp(clipData)
        val isNewEntry = recentEntry != clipContent
                || clipTimestamp != null && clipTimestamp > recentTimestamp
        if (isNewEntry) {
            suggestionPicked = false
            recentEntry = clipContent
            recentTimestamp = clipTimestamp ?: System.currentTimeMillis()
        } else if ((System.currentTimeMillis() - recentTimestamp) > RECENT_TIME_MILLIS || suggestionPicked) {
            return "" // empty string indicating clipboard is old or has been picked as a suggestion before
        }
        return clipContent
    }

    fun isClipSensitive(isPasswordInputType: Boolean) : Boolean {
        val clipDescription = clipboardManager.primaryClip?.description ?: return isPasswordInputType
        return ClipboardManagerCompat.getClipSensitivity(clipDescription, isPasswordInputType)
    }

    fun markSuggestionAsPicked() {
        suggestionPicked = true
    }

    // pinned clips are stored in default shared preferences, not in device protected preferences!
    private fun loadPinnedClips() {
        val pinnedClipString = Settings.readPinnedClipString(latinIME)
        if (pinnedClipString.isEmpty()) return
        val pinnedClips: List<ClipboardHistoryEntry> = Json.decodeFromString(pinnedClipString)
        historyEntries.addAll(pinnedClips)
        sortHistoryEntries()
        if (onHistoryChangeListener != null) {
            pinnedClips.forEach {
                onHistoryChangeListener?.onClipboardHistoryEntryAdded(historyEntries.indexOf(it))
            }
        }
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
        private var recentEntry: String = ""
        private var recentTimestamp: Long = 0L
        private var suggestionPicked: Boolean = false
        private const val RECENT_TIME_MILLIS = 3 * 60 * 1000L // 3 minutes (for clipboard suggestions)
    }
}
