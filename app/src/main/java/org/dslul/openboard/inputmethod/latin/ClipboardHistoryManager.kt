// SPDX-License-Identifier: GPL-3.0-only

package org.dslul.openboard.inputmethod.latin

import android.content.ClipboardManager
import android.content.Context
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import org.dslul.openboard.inputmethod.compat.ClipboardManagerCompat
import org.dslul.openboard.inputmethod.latin.utils.JsonUtils
import java.io.File
import java.lang.Exception
import java.util.*

class ClipboardHistoryManager(
        private val latinIME: LatinIME
) : ClipboardManager.OnPrimaryClipChangedListener {

    private lateinit var pinnedHistoryClipsFile: File
    private lateinit var clipboardManager: ClipboardManager
    private val historyEntries: MutableList<ClipboardHistoryEntry>
    private var onHistoryChangeListener: OnHistoryChangeListener? = null

    fun onCreate() {
        pinnedHistoryClipsFile = File(latinIME.filesDir, PINNED_CLIPS_DATA_FILE_NAME)
        clipboardManager = latinIME.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        fetchPrimaryClip()
        clipboardManager.addPrimaryClipChangedListener(this)
        startLoadPinnedClipsFromDisk()
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
        if (clipData.itemCount == 0) return
        clipData.getItemAt(0)?.let { clipItem ->
            // Starting from API 30, onPrimaryClipChanged() can be called multiple times
            // for the same clip. We can identify clips with their timestamps since API 26.
            // We use that to prevent unwanted duplicates.
            val timeStamp = ClipboardManagerCompat.getClipTimestamp(clipData)?.also { stamp ->
                if (historyEntries.any { it.timeStamp == stamp }) return
            } ?: System.currentTimeMillis()

            val content = clipItem.coerceToText(latinIME)
            if (TextUtils.isEmpty(content)) return

            val entry = ClipboardHistoryEntry(timeStamp, content)
            historyEntries.add(entry)
            sortHistoryEntries()
            val at = historyEntries.indexOf(entry)
            onHistoryChangeListener?.onClipboardHistoryEntryAdded(at)
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
        startSavePinnedClipsToDisk()
    }

    fun clearHistory() {
        ClipboardManagerCompat.clearPrimaryClip(clipboardManager)
        val pos = historyEntries.indexOfFirst { !it.isPinned }
        val count = historyEntries.count { !it.isPinned }
        historyEntries.removeAll { !it.isPinned }
        if (onHistoryChangeListener != null) {
            onHistoryChangeListener?.onClipboardHistoryEntriesRemoved(pos, count)
        }
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

    private fun startLoadPinnedClipsFromDisk() {
        object : Thread("$TAG-load") {
            override fun run() {
                loadFromDisk()
            }
        }.start()
    }

    private fun loadFromDisk() {
        // Debugging
        if (pinnedHistoryClipsFile.exists() && !pinnedHistoryClipsFile.canRead()) {
            Log.w(TAG, "Attempt to read pinned clips file $pinnedHistoryClipsFile without permission")
        }
        var list = emptyList<ClipboardHistoryEntry>()
        try {
            if (pinnedHistoryClipsFile.exists()) {
                val bytes = Base64.decode(pinnedHistoryClipsFile.readText(), Base64.DEFAULT)
                list = JsonUtils.jsonBytesToHistoryEntryList(bytes)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't retrieve $pinnedHistoryClipsFile content", e)
        }
        latinIME.mHandler.postUpdateClipboardPinnedClips(list)
    }

    private fun startSavePinnedClipsToDisk() {
        val localCopy = historyEntries.filter { it.isPinned }.map { it.copy() }
        object : Thread("$TAG-save") {
            override fun run() {
                saveToDisk(localCopy)
            }
        }.start()
    }

    private fun saveToDisk(list: List<ClipboardHistoryEntry>) {
        // Debugging
        if (pinnedHistoryClipsFile.exists() && !pinnedHistoryClipsFile.canWrite()) {
            Log.w(TAG, "Attempt to write pinned clips file $pinnedHistoryClipsFile without permission")
        }
        try {
            pinnedHistoryClipsFile.createNewFile()
            val jsonStr = JsonUtils.historyEntryListToJsonStr(list)
            if (!TextUtils.isEmpty(jsonStr)) {
                val rawText = Base64.encodeToString(jsonStr.encodeToByteArray(), Base64.DEFAULT)
                pinnedHistoryClipsFile.writeText(rawText)
            } else {
                pinnedHistoryClipsFile.writeText("")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't write to $pinnedHistoryClipsFile", e)
        }
    }

    interface OnHistoryChangeListener {
        fun onClipboardHistoryEntryAdded(at: Int)
        fun onClipboardHistoryEntriesRemoved(pos: Int, count: Int)
        fun onClipboardHistoryEntryMoved(from: Int, to: Int)
    }

    companion object {
        const val PINNED_CLIPS_DATA_FILE_NAME = "pinned_clips.data"
        const val TAG = "ClipboardHistoryManager"
    }

    init {
        historyEntries = LinkedList()
    }
}