package org.dslul.openboard.inputmethod.keyboard.emoji

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import android.util.SparseArray
import androidx.annotation.WorkerThread

/**
 * Class to deal with recent emojis database
 */
class RecentEmojiDbHelper(context: Context) : SQLiteOpenHelper(
    context,
    RECENT_EMOJI_DATABASE_NAME,
    null,
    RECENT_EMOJI_DATABASE_VERSION
) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(LOG_TABLE_CREATE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Remove all data.
        db.execSQL("DROP TABLE IF EXISTS $RECENT_EMOJI_TABLE_NAME")
        onCreate(db)
    }

    @WorkerThread
    fun getRecentEmojis(): SparseArray<RecentEmoji> {
        return SparseArray<RecentEmoji>().apply {
            readAll(readableDatabase).use { cursor ->
                while (cursor.moveToNext()) {
                    val hash = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_HASH))
                    val code = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_CODE))
                    val text = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TEXT))
                    val rawUses = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USES))
                    put(hash, RecentEmoji(code, text, decodeUses(rawUses)))
                }
            }
        }
    }

    @WorkerThread
    fun addRecentEmoji(hash: Int, recentEmoji: RecentEmoji) {
        val result = insert(writableDatabase, hash, recentEmoji.code, recentEmoji.text, recentEmoji.usesToStore)
        if (result == INVALID_ID) {
            throw IllegalStateException("Trying to add a recent emoji that " +
                    "is already in database for hash=$hash")
        }
    }

    @WorkerThread
    fun updateRecentEmojiUses(hash: Int, uses: List<Long>) {
        val affectedRows = updateUses(writableDatabase, hash, uses)
        if (affectedRows == 0) {
            throw IllegalStateException("Trying to update a recent emoji that " +
                    "is not yet in database for hash=$hash")
        } else if (affectedRows > 1) {
            Log.w(TAG, "Updating recent emoji for hash=$hash led in " +
                    "$affectedRows rows modified.")
        }
    }

    @WorkerThread
    fun removeRecentEmoji(hash: Int) {
        val affectedRows = delete(writableDatabase, hash)
        if (affectedRows == 0) {
            throw IllegalStateException("Trying to remove a recent emoji that " +
                    "is not yet in database for hash=$hash")
        } else if (affectedRows > 1) {
            Log.w(TAG, "Removing recent emoji for hash=$hash led in " +
                    "$affectedRows rows deleted.")
        }
    }

    companion object {

        private const val TAG = "RecentEmojiDbHelper"

        private const val RECENT_EMOJI_DATABASE_NAME = "recentEmoji"
        private const val RECENT_EMOJI_DATABASE_VERSION = 1
        private const val RECENT_EMOJI_TABLE_NAME = "recentEmoji"
        private const val COLUMN_COUNT = 4
        private const val COLUMN_HASH = "hash"
        private const val COLUMN_CODE = "code"
        private const val COLUMN_TEXT = "text"
        private const val COLUMN_USES = "uses"

        private const val USES_SEPARATOR = ';'
        private const val EMPTY_STRING = ""
        private const val INVALID_ID = -1L

        private const val LOG_TABLE_CREATE = ("CREATE TABLE " + RECENT_EMOJI_TABLE_NAME + " ("
                + COLUMN_HASH + " INTEGER,"
                + COLUMN_CODE + " INTEGER,"
                + COLUMN_TEXT + " TEXT,"
                + COLUMN_USES + " TEXT,"
                + "PRIMARY KEY (" + COLUMN_HASH + "));")

        fun readAll(db: SQLiteDatabase): Cursor {
            return db.query(RECENT_EMOJI_TABLE_NAME,
                arrayOf(COLUMN_HASH, COLUMN_CODE, COLUMN_TEXT, COLUMN_USES),
                null,
                null,
                null,
                null,
                null)
        }

        fun insert(db: SQLiteDatabase, hash: Int, code: Int, text: String?, uses: List<Long>): Long {
            val contentValues = ContentValues(COLUMN_COUNT).apply {
                put(COLUMN_HASH, hash)
                put(COLUMN_CODE, code)
                if (text != null) put(COLUMN_TEXT, text) else putNull(COLUMN_TEXT)
                put(COLUMN_USES, encodeUses(uses))
            }
            return db.insert(RECENT_EMOJI_TABLE_NAME, null, contentValues)
        }

        fun delete(db: SQLiteDatabase, hash: Int): Int {
            return db.delete(RECENT_EMOJI_TABLE_NAME,
                "$COLUMN_HASH=?",
                arrayOf(hash.toString()))
        }

        fun updateUses(db: SQLiteDatabase, hash: Int, uses: List<Long>): Int {
            val contentValues = ContentValues(1).apply {
                put(COLUMN_USES, encodeUses(uses))
            }
            return db.update(RECENT_EMOJI_TABLE_NAME,
                contentValues,
                "$COLUMN_HASH=?",
                arrayOf(hash.toString()))
        }

        private fun encodeUses(uses: List<Long>): String {
            if (uses.isEmpty()) return EMPTY_STRING
            if (uses.size == 1) return uses.first().toString()
            val builder = StringBuilder()
            val base = uses.first()
            builder.append(base)
            uses.drop(1).forEach { use ->
                builder.append(USES_SEPARATOR)
                builder.append(use - base)
            }
            return builder.toString()
        }

        private fun decodeUses(rawString: String): LongArray {
            val count = rawString.count { it == USES_SEPARATOR } + 1
            val uses = LongArray(count)
            var base = 0L
            rawString.split(USES_SEPARATOR).forEachIndexed { index, part ->
                if (index == 0) {
                    base = part.toLong()
                    uses[0] = base
                } else {
                    uses[index] = base + part.toLong()
                }
            }
            return uses
        }
    }
}