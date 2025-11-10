// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.database

import android.content.ContentValues
import android.content.Context
import android.os.SystemClock
import helium314.keyboard.latin.ClipboardHistoryEntry
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log

/*
 possible extension for later: allow non-text
 setting whether to allow it at all (because it could be slow with large files)
 separate retention time setting
 add mime type column
 add file name column
 add hash column (sha 256) for quick unique check (check full content on hash conflict)
 more sophisticated content loading: some getContent that reads the file, with cache
 async file reads and writes
 caches should be dropped on low memory
 */

/** Class providing cached access to the clipboard table */
// currently we should not need to worry about synchronizing access (though maybe we could addClip in a coroutine, then it might be relevant)
class ClipboardDao private constructor(private val db: Database) {
    interface Listener {
        fun onClipInserted(position: Int)
        fun onClipsRemoved(position: Int, count: Int)
        fun onClipMoved(oldPosition: Int, newPosition: Int)
    }

    var listener: Listener? = null

    // we clean up old clips when a new clip is added, but not too frequently
    private var lastClearOldClips = 0L

    // cache is loaded at start and never dropped
    private val cache = mutableListOf<ClipboardHistoryEntry>().apply {
        db.readableDatabase.query(
            TABLE,
            arrayOf(COLUMN_ID, COLUMN_TIMESTAMP, COLUMN_PINNED, COLUMN_TEXT),
            null,
            null,
            null,
            null,
            "$COLUMN_PINNED, $COLUMN_TIMESTAMP DESC" // was only relevant in the initial approach of using a cursor instead of a cache
        ).use {
            while (it.moveToNext()) {
                add(ClipboardHistoryEntry(it.getLong(0), it.getLong(1), it.getInt(2) != 0, it.getString(3)))
            }
        }
        sort()
    }

    fun addClip(timestamp: Long, pinned: Boolean, text: String) {
        clearOldClips()
        val existingIndex = cache.indexOfFirst { it.text == text }
        if (existingIndex >= 0 && cache[existingIndex].timeStamp == timestamp)
            return // nothing to do
        if (existingIndex >= 0) {
            updateTimestampAt(existingIndex, timestamp)
            return
        }
        insertNewEntry(timestamp, pinned, text)
    }

    private fun insertNewEntry(timestamp: Long, pinned: Boolean, text: String) {
        val cv = ContentValues(3)
        cv.put(COLUMN_TIMESTAMP, timestamp)
        cv.put(COLUMN_PINNED, pinned)
        cv.put(COLUMN_TEXT, text)
        val rowId = db.writableDatabase.insert(TABLE, null, cv)

        val entry = ClipboardHistoryEntry(rowId, timestamp, pinned, text)
        cache.add(entry)
        cache.sort()
        listener?.onClipInserted(cache.indexOf(entry))
    }

    private fun updateTimestampAt(index: Int, timestamp: Long) {
        val entry = cache[index]
        entry.timeStamp = timestamp
        cache.sort()
        listener?.onClipMoved(index, cache.indexOf(entry))
        val cv = ContentValues(1)
        cv.put(COLUMN_TIMESTAMP, timestamp)
        db.writableDatabase.update(TABLE, cv, "$COLUMN_ID = ${entry.id}", null)
    }

    fun isPinned(index: Int) = cache[index].isPinned

    fun getAt(index: Int) = cache[index]

    fun get(id: Long) = cache.first { it.id == id }

    fun count() = cache.size

    fun sort() = cache.sort()

    fun togglePinned(id: Long) {
        val entry = cache.first { it.id == id }
        entry.isPinned = !entry.isPinned
        entry.timeStamp = System.currentTimeMillis()
        if (listener != null) {
            val oldPos = cache.indexOf(entry)
            cache.sort()
            val newPos = cache.indexOf(entry)
            listener?.onClipMoved(oldPos, newPos)
        } else {
            cache.sort()
        }
        val cv = ContentValues(2)
        cv.put(COLUMN_PINNED, entry.isPinned)
        cv.put(COLUMN_TIMESTAMP, entry.timeStamp)
        db.writableDatabase.update(TABLE, cv, "$COLUMN_ID = ${entry.id}", null)
    }

    // RecyclerView initiates this, so we don't call listener (or we'll get an IndexOutOfRangeException from RecyclerView)
    fun deleteClipAt(index: Int) {
        val entry = cache[index]
        cache.remove(entry)
        db.writableDatabase.delete(TABLE, "$COLUMN_ID = ${entry.id}", null)
    }

    fun clearOldClips(now: Boolean = false) {
        if (listener != null)
            return // never clear when clipboard is visible
        if (!now && lastClearOldClips > SystemClock.elapsedRealtime() - 5 * 1000)
            return

        lastClearOldClips = SystemClock.elapsedRealtime()
        val retentionTime = Settings.getValues()?.mClipboardHistoryRetentionTime ?: 121L
        if (retentionTime > 120) return
        val minTime = System.currentTimeMillis() - retentionTime * 60 * 1000L
        if (!cache.removeAll { it.timeStamp < minTime && !it.isPinned })
            return // nothing was removed

        db.writableDatabase.delete(TABLE, "$COLUMN_TIMESTAMP < $minTime AND $COLUMN_PINNED = 0", null)
    }

    fun clearNonPinned() {
        if (listener != null) {
            val indicesToRemove = mutableListOf<Int>()
            cache.forEachIndexed { idx, clip ->
                if (!clip.isPinned)
                    indicesToRemove.add(idx)
            }
            if (indicesToRemove.isEmpty())
                return // nothing to remove
            cache.removeAll { !it.isPinned }
            listener?.onClipsRemoved(indicesToRemove[0], indicesToRemove.size)
        } else if (!cache.removeAll { !it.isPinned }) {
            return // no listener, nothing to remove
        }
        db.writableDatabase.delete(TABLE, "$COLUMN_PINNED = 0", null)
    }

    fun clear() {
        if (count() == 0) return
        cache.clear()
        listener?.onClipsRemoved(0, count())
        db.writableDatabase.delete(TABLE, null, null)
    }

    companion object {
        private const val TAG = "ClipboardDao"

        private const val TABLE = "CLIPBOARD"
        // it's possible timestamp is not unique, so we use a separate ID
        // ID is generated and returned on insert, see https://sqlite.org/rowidtable.html
        private const val COLUMN_ID = "ID"
        private const val COLUMN_TIMESTAMP = "TIMESTAMP"
        private const val COLUMN_PINNED = "PINNED"
        private const val COLUMN_TEXT = "TEXT" // we could enforce unique text, but that's only necessary if we can drop the cache (later)
        const val CREATE_TABLE = """
            CREATE TABLE $TABLE (
                $COLUMN_ID INTEGER PRIMARY KEY,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_PINNED TINYINT NOT NULL,
                $COLUMN_TEXT TEXT
            )
        """

        private var instance: ClipboardDao? = null

        /** Returns the instance or creates a new one. Returns null if instance can't be created (e.g. no access to db due to device being locked) */
        fun getInstance(context: Context): ClipboardDao? {
            if (instance == null)
                try {
                    instance = ClipboardDao(Database.getInstance(context))
                } catch (e: Throwable) {
                    Log.e(TAG, "can't create ClipboardDao", e)
                }
            return instance
        }
    }
}
