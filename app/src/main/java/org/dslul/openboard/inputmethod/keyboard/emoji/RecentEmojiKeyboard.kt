package org.dslul.openboard.inputmethod.keyboard.emoji

import android.util.Log
import android.util.SparseArray
import androidx.core.util.containsKey
import androidx.core.util.forEach
import androidx.core.util.valueIterator
import org.dslul.openboard.inputmethod.keyboard.Key
import org.dslul.openboard.inputmethod.keyboard.Keyboard
import org.dslul.openboard.inputmethod.latin.LatinIME
import org.dslul.openboard.inputmethod.latin.settings.Settings
import org.dslul.openboard.inputmethod.latin.utils.ExecutorUtils
import org.dslul.openboard.inputmethod.latin.utils.getOrPut
import java.util.*

class RecentEmojiKeyboard(
    private val recentEmojiDbHelper: RecentEmojiDbHelper,
    templateKeyboard: Keyboard?,
    private var emojiBaseKeyboards: Collection<DynamicGridKeyboard>,
    private val maxKeyCount: Int
) : DynamicGridKeyboard(templateKeyboard) {

    var listener: OnRecentEmojiChangedListener? = null

    private val recentEmojis = SparseArray<RecentEmoji>()
    private val pendingKeys = ArrayDeque<Key>()
    private var hasLoadedRecentEmojis = false
    private var emojiUsageSaveEnabled = false

    private val gridKeyComparator by lazy {
        Comparator<GridKey> { key1, key2 ->
            val e1 = recentEmojis[key1.hashCode()]
            val e2 = recentEmojis[key2.hashCode()]
            // Null cases should never append in theory, but we do not want to crash the
            // whole IME if we have a desync between keyboard data and our recent emojis
            when {
                e1 == null && e2 != null -> -1
                e1 != null && e2 == null -> +1
                e1 == null && e2 == null -> 0
                else -> e1.compareTo(e2)
            }
        }
    }

    fun loadRecentEmojis(handler: LatinIME.UIHandler) = startLoadRecentEmojis(handler)

    fun onRecentEmojisAvailable(array: SparseArray<RecentEmoji>) {
        for (i in array.size() - 1 downTo 0) {
            val recentEmoji = array.valueAt(i)
            val key = if (recentEmoji.text != null) getBaseKeyByOutputText(recentEmoji.text)
            else getBaseKeyByCode(recentEmoji.code)
            if (key != null) {
                recentEmojis.put(array.keyAt(i), recentEmoji)
                addKeyFrom(key)
                recentEmoji.updateFrequency()
            } else {
                Log.i(TAG, "No base key found for: code=${recentEmoji.code} text=${recentEmoji.text}")
                startRemoveRecentEmoji(array.keyAt(i))
            }
        }
        sortKeys(gridKeyComparator)
        hasLoadedRecentEmojis = true
        listener?.onRecentEmojiChanged()
    }

    fun notifyEmojiUsed(baseKey: Key) {
        if (!hasLoadedRecentEmojis || Settings.getInstance().current.mIncognitoModeEnabled) {
            // We do not want to log recent keys while being in incognito or waiting for our initial setup
            return
        }
        val exists = updateRecentEmojiFromKey(baseKey)
        if (!exists) {
            addKeyFrom(baseKey)
        }
        sortKeys(gridKeyComparator)
        listener?.onRecentEmojiChanged()
    }

    fun notifyEmojiUsedPending(baseKey: Key) {
        if (!hasLoadedRecentEmojis || Settings.getInstance().current.mIncognitoModeEnabled) {
            // We do not want to log recent keys while being in incognito or waiting for our initial setup
            return
        }
        updateRecentEmojiFromKey(baseKey)
        pendingKeys.add(baseKey)
    }

    fun applyPendingChanges() {
        if (pendingKeys.isEmpty()) {
            return
        }
        while (pendingKeys.isNotEmpty()) {
            addKeyFrom(pendingKeys.remove())
        }
        sortKeys(gridKeyComparator)
        listener?.onRecentEmojiChanged()
    }

    private fun updateRecentEmojiFromKey(baseKey: Key): Boolean {
        checkEmojiUsageSaveEnabled()
        val hash = Objects.hash(baseKey.code, baseKey.label, baseKey.outputText)
        val exists = recentEmojis.containsKey(hash)
        val recentEmoji = recentEmojis.getOrPut(hash) {
            RecentEmoji(baseKey).also { startAddNewEmoji(hash, it) }
        }
        if (emojiUsageSaveEnabled) {
            recentEmoji.addUseNow()
        } else {
            recentEmoji.setSingleUseNow()
        }
        startUpdateEmojisUses(hash, recentEmoji)

        // Update frequency of every emoji to keep them comparable
        recentEmojis.forEach { _, item -> item.updateFrequency() }
        return exists
    }

    private fun getBaseKeyByCode(code: Int) = emojiBaseKeyboards.firstNotNullOfOrNull {
        it.sortedKeys.firstOrNull { key -> key.code == code }
    }

    private fun getBaseKeyByOutputText(outputText: String) = emojiBaseKeyboards.firstNotNullOfOrNull {
        it.sortedKeys.firstOrNull { key -> key.outputText == outputText }
    }

    private fun checkEmojiUsageSaveEnabled() {
        val oldValue = emojiUsageSaveEnabled
        val newValue = Settings.getInstance().current.mEmojiUsageFreqEnabled
        if (oldValue != newValue) {
            emojiUsageSaveEnabled = newValue
            if (!newValue) {
                // Setting is now off, we must forget dynamically usage histories to compute the new simple order.
                recentEmojis.forEach { hash, recentEmoji ->
                    recentEmoji.setSingleUseFromLastUse()
                    startUpdateEmojisUses(hash, recentEmoji)
                }
            }
        }
    }

    override fun addKeyFrom(usedKey: Key?) {
        super.addKeyFrom(usedKey)
        if (keyCount > maxKeyCount) {
            sortKeys(gridKeyComparator)
            while (keyCount > maxKeyCount) {
                removeLastKey()
                // Find recent emoji with the smallest frequency, corresponding to
                // the last key we juste removed.
                val recentEmoji = recentEmojis.valueIterator().asSequence().sorted().last()
                val index = recentEmojis.indexOfValue(recentEmoji)
                recentEmojis.removeAt(index)
                val hash = recentEmojis.keyAt(index)
                startRemoveRecentEmoji(hash)
            }
        }
    }

    override fun makeGridKey(baseKey: Key): GridKey {
        // When a key is added to recents keyboard, we don't want to keep its more keys
        // neither its hint label. Also, we make sure its background type is matching our keyboard
        // if key comes from another keyboard (ie. a {@link MoreKeysKeyboard}).
        val dropMoreKeys = true
        // Check if hint was a more emoji indicator and prevent its copy if more keys aren't copied
        val dropHintLabel = dropMoreKeys && "\u25E5" == baseKey.hintLabel
        return GridKey(baseKey,
            if (dropMoreKeys) null else baseKey.moreKeys,
            if (dropHintLabel) null else baseKey.hintLabel,
            Key.BACKGROUND_TYPE_EMPTY)
    }

    private fun startLoadRecentEmojis(handler: LatinIME.UIHandler) {
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.EMOJIS).execute {
            val result = recentEmojiDbHelper.getRecentEmojis()
            handler.postUpdateRecentEmojis(result)
        }
    }

    private fun startAddNewEmoji(hash: Int, recentEmoji: RecentEmoji) {
        Settings.getInstance().writeEmojiRecentCount(recentEmojis.size())
        val localCopy = recentEmoji.copy()
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.EMOJIS).execute {
            recentEmojiDbHelper.addRecentEmoji(hash, localCopy)
        }
    }

    private fun startUpdateEmojisUses(hash: Int, recentEmoji: RecentEmoji) {
        val localList = recentEmoji.usesToStore
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.EMOJIS).execute {
            recentEmojiDbHelper.updateRecentEmojiUses(hash, localList)
        }
    }

    private fun startRemoveRecentEmoji(hash: Int) {
        Settings.getInstance().writeEmojiRecentCount(recentEmojis.size())
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.EMOJIS).execute {
            recentEmojiDbHelper.removeRecentEmoji(hash)
        }
    }

    interface OnRecentEmojiChangedListener {
        fun onRecentEmojiChanged()
    }

    companion object {
        private const val TAG = "RecentEmojiKeyboard"
    }
}