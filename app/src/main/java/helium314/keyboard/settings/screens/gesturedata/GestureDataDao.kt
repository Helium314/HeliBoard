package helium314.keyboard.settings.screens.gesturedata

import android.content.ContentValues
import android.content.Context
import helium314.keyboard.latin.database.Database
import helium314.keyboard.latin.utils.ChecksumCalculator
import helium314.keyboard.latin.utils.Log
import kotlinx.serialization.json.Json

class GestureDataDao(val db: Database) {
    fun add(data: GestureData, timestamp: Long) {
        require(data.hash == null)
        val jsonString = Json.encodeToString(data)
        val dataWithHash = data.copy(hash = ChecksumCalculator.checksum(jsonString.byteInputStream()))
        val cv = ContentValues(3)
        cv.put(COLUMN_TIMESTAMP, timestamp)
        cv.put(COLUMN_WORD, data.targetWord)
        cv.put(COLUMN_DATA, Json.encodeToString(dataWithHash))
        db.writableDatabase.insert(TABLE, null, cv)
    }

    fun filterInfos(word: String?, begin: Long?, end: Long?, exported: Boolean?): List<GestureDataInfo> {
        val result = mutableListOf<GestureDataInfo>()
        val query = mutableListOf<String>()
        if (word != null) query.add("LOWER($COLUMN_WORD) like '%'||?||'%'")
        if (begin != null) query.add("TIMESTAMP >= $begin")
        if (end != null) query.add("TIMESTAMP <= $end")
        if (exported != null) query.add("EXPORTED = ${if (exported) 1 else 0}")
        db.readableDatabase.query(
            TABLE,
            arrayOf(COLUMN_ID, COLUMN_WORD, COLUMN_TIMESTAMP, COLUMN_EXPORTED),
            query.joinToString(" AND "),
            word?.let { arrayOf(it.lowercase()) },
            null,
            null,
            null
        ).use {
            while (it.moveToNext()) {
                result.add(GestureDataInfo(it.getLong(0), it.getString(1), it.getLong(2), it.getInt(3) != 0))
            }
        }
        return result
    }

    fun getJsonData(ids: List<Long>): List<String> {
        val result = mutableListOf<String>()
        db.readableDatabase.query(
            TABLE,
            arrayOf(COLUMN_DATA),
            "$COLUMN_ID IN (${ids.joinToString(",")})",
            null,
            null,
            null,
            null
        ).use {
            while (it.moveToNext()) {
                result.add(it.getString(0))
            }
        }
        return result
    }

    fun getAllJsonData(): List<String> {
        val result = mutableListOf<String>()
        db.readableDatabase.query(
            TABLE,
            arrayOf(COLUMN_DATA),
            "$COLUMN_ID = id",
            null,
            null,
            null,
            null
        ).use {
            while (it.moveToNext()) {
                result.add(it.getString(0))
            }
        }
        return result
    }

    fun markAllAsExported() {
        val cv = ContentValues(1)
        cv.put(COLUMN_EXPORTED, 1)
        db.writableDatabase.update(TABLE, cv, null, null)
    }

    fun delete(ids: List<Long>) {
        if (ids.isEmpty()) return
        db.writableDatabase.delete(TABLE, "$COLUMN_ID IN (${ids.joinToString(",")})", null)
    }

    fun deleteAll() {
        db.writableDatabase.delete(TABLE, null, null)
    }

    fun isEmpty(): Boolean {
        db.readableDatabase.rawQuery("SELECT EXISTS (SELECT 1 FROM $TABLE)", null).use {
            it.moveToFirst()
            return it.getInt(0) == 0
        }
    }

    init {
        // for now just do it here instead of using the proper upgrade thing
        db.writableDatabase.execSQL(CREATE_TABLE)
    }

    companion object {
        private const val TAG = "GestureDataDao"

        private const val TABLE = "GESTURE_DATA"
        private const val COLUMN_ID = "ID"
        private const val COLUMN_TIMESTAMP = "TIMESTAMP"
        private const val COLUMN_WORD = "WORD"
        private const val COLUMN_EXPORTED = "EXPORTED"
        private const val COLUMN_DATA = "DATA"

        // todo: TEXT or BLOB? we could zip-compress the gesture data
        // todo: active / passive column, we'll want to use it for filtering
        const val CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE (
                $COLUMN_ID INTEGER PRIMARY KEY,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_WORD TEXT NOT NULL,
                $COLUMN_EXPORTED TINYINT NOT NULL DEFAULT 0,
                $COLUMN_DATA TEXT
            )
        """

        private var instance: GestureDataDao? = null

        /** Returns the instance or creates a new one. Returns null if instance can't be created (e.g. no access to db due to device being locked) */
        fun getInstance(context: Context): GestureDataDao? {
            if (instance == null)
                try {
                    instance = GestureDataDao(Database.getInstance(context))
                } catch (e: Throwable) {
                    Log.e(TAG, "can't create ClipboardDao", e)
                }
            return instance
        }
    }
}
