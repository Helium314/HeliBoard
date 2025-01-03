// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin

import android.content.ClipboardManager
import android.content.Context
import android.text.InputType
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isGone
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import helium314.keyboard.compat.ClipboardManagerCompat
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.isValidNumber
import helium314.keyboard.latin.databinding.ClipboardSuggestionBinding
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.InputTypeUtils
import helium314.keyboard.latin.utils.ToolbarKey
import kotlin.collections.ArrayList

class ClipboardHistoryManager(
        private val latinIME: LatinIME
) : ClipboardManager.OnPrimaryClipChangedListener {

    private lateinit var clipboardManager: ClipboardManager
    private var onHistoryChangeListener: OnHistoryChangeListener? = null
    private var clipboardSuggestionView: View? = null

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
            dontShowCurrentSuggestion = false
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
                return
            }
            if (historyEntries.any { it.content.toString() == content.toString() }) return

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
        removeClipboardSuggestion()
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

    private fun isClipSensitive(inputType: Int): Boolean {
        ClipboardManagerCompat.getClipSensitivity(clipboardManager.primaryClip?.description)?.let { return it }
        return InputTypeUtils.isPasswordInputType(inputType)
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

    fun getClipboardSuggestionView(editorInfo: EditorInfo?, parent: ViewGroup?): View? {
        // maybe no need to create a new view
        // but a cache has to consider a few possible changes, so better don't implement without need
        clipboardSuggestionView = null

        // get the content, or return null
        if (!latinIME.mSettings.current.mSuggestClipboardContent) return null
        if (dontShowCurrentSuggestion) return null
        if (parent == null) return null
        val clipData = clipboardManager.primaryClip ?: return null
        if (clipData.itemCount == 0 || clipData.description?.hasMimeType("text/*") == false) return null
        val clipItem = clipData.getItemAt(0) ?: return null
        val timeStamp = ClipboardManagerCompat.getClipTimestamp(clipData) ?: System.currentTimeMillis()
        if (System.currentTimeMillis() - timeStamp > RECENT_TIME_MILLIS) return null
        val content = clipItem.coerceToText(latinIME)
        if (TextUtils.isEmpty(content)) return null
        val inputType = editorInfo?.inputType ?: InputType.TYPE_NULL
        if (InputTypeUtils.isNumberInputType(inputType) && !content.isValidNumber()) return null

        // create the view
        val binding = ClipboardSuggestionBinding.inflate(LayoutInflater.from(latinIME), parent, false)
        val textView = binding.clipboardSuggestionText
        textView.text = (if (isClipSensitive(inputType)) "*".repeat(content.length) else content)
            .take(200) // truncate displayed text for performance reasons
        val clipIcon = latinIME.mKeyboardSwitcher.keyboard.mIconsSet.getIconDrawable(ToolbarKey.PASTE.name.lowercase())
        textView.setCompoundDrawablesRelativeWithIntrinsicBounds(clipIcon, null, null, null)
        textView.setOnClickListener {
            dontShowCurrentSuggestion = true
            latinIME.onTextInput(content.toString())
            AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, it)
            binding.root.isGone = true
        }
        val closeButton = binding.clipboardSuggestionClose
        closeButton.setImageDrawable(latinIME.mKeyboardSwitcher.keyboard.mIconsSet.getIconDrawable(ToolbarKey.CLOSE_HISTORY.name.lowercase()))
        closeButton.setOnClickListener { removeClipboardSuggestion() }

        val colors = latinIME.mSettings.current.mColors
        textView.setTextColor(colors.get(ColorType.KEY_TEXT))
        clipIcon?.let { colors.setColor(it, ColorType.KEY_ICON) }
        colors.setColor(closeButton, ColorType.REMOVE_SUGGESTION_ICON)
        colors.setBackground(binding.root, ColorType.CLIPBOARD_SUGGESTION_BACKGROUND)

        clipboardSuggestionView = binding.root
        return clipboardSuggestionView
    }

    private fun removeClipboardSuggestion() {
        dontShowCurrentSuggestion = true
        val csv = clipboardSuggestionView ?: return
        if (csv.parent != null && !csv.isGone) {
            // clipboard view is shown ->
            latinIME.setNeutralSuggestionStrip()
            latinIME.mHandler.postResumeSuggestions(false)
        }
        csv.isGone = true
    }

    companion object {
        // store pinned clips in companion object so they survive a keyboard switch (which destroys the current instance)
        private val historyEntries: MutableList<ClipboardHistoryEntry> = ArrayList()
        private var dontShowCurrentSuggestion: Boolean = false
        const val RECENT_TIME_MILLIS = 3 * 60 * 1000L // 3 minutes (for clipboard suggestions)
    }
}
