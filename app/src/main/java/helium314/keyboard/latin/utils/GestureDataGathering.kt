// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.ContentValues
import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.core.content.edit
import com.android.inputmethod.latin.BinaryDictionary
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.SingleDictionaryFacilitator
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.database.Database
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.dictionary.ReadOnlyBinaryDictionary
import helium314.keyboard.latin.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

fun isInActiveGatheringMode(editorInfo: EditorInfo) =
    dictTestImeOption == editorInfo.privateImeOptions && gestureDataActiveFacilitator != null

fun setWordIgnoreList(context: Context, list: Collection<String>) {
    val json = Json.encodeToString(list)
    context.prefs().edit { putString(PREF_WORD_EXCLUSIONS, json) }
}

fun getWordIgnoreList(context: Context): Set<String> {
    val json = context.prefs().getString(PREF_WORD_EXCLUSIONS, "[]") ?: "[]"
    if (json.isEmpty()) return sortedSetOf()
    return Json.decodeFromString<List<String>>(json).toSortedSet(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
}

fun setAppIgnoreList(context: Context, list: Collection<String>) {
    context.prefs().edit { putString(PREF_APP_EXCLUSIONS, list.joinToString(",")) }
}

fun getAppIgnoreList(context: Context): List<String> {
    val string = context.prefs().getString(PREF_APP_EXCLUSIONS, "") ?: ""
    return string.split(",").filterNot { it.isEmpty() }
}

fun addExportedActiveDeletionCount(context: Context, count: Int) {
    val oldCount = getExportedActiveDeletionCount(context)
    context.prefs().edit { putInt(PREF_DELETED_ACTIVE, oldCount + count) }
}

fun getExportedActiveDeletionCount(context: Context) = context.prefs().getInt(PREF_DELETED_ACTIVE, 0)

private const val PREF_WORD_EXCLUSIONS = "gesture_data_word_exclusions"
private const val PREF_APP_EXCLUSIONS = "gesture_data_app_exclusions"
private const val PREF_DELETED_ACTIVE = "gesture_data_deleted_active_words"

const val dictTestImeOption = "useTestDictionaryFacilitator,${BuildConfig.APPLICATION_ID}.${Constants.ImeOption.NO_FLOATING_GESTURE_PREVIEW}"

var gestureDataActiveFacilitator: SingleDictionaryFacilitator? = null

// class for storing relevant information
class WordData(
    val targetWord: String,
    val suggestions: SuggestionResults,
    val composedData: ComposedData,
    val ngramContext: NgramContext,
    val keyboard: Keyboard,
    val inputStyle: Int,
    val activeMode: Boolean,
) {
    // keyboard is not immutable, so better store potentially relevant information immediately
    private val keys = keyboard.sortedKeys
    private val height = keyboard.mOccupiedHeight
    private val width = keyboard.mOccupiedWidth

    private val proxInfo = keyboard.proximityInfo // todo: is there any use?

    // if contacts dict is used we keep this information
    private val dictionariesInSuggestions = suggestions.map { it.mSourceDict }.toSet()
    private val packageName = keyboard.mId.mEditorInfo.packageName

    private val timestamp = System.currentTimeMillis()

    fun save(context: Context) {
        if (!isSavingOk(context))
            return
        val dao = GestureDataDao.getInstance(context) ?: return

        val keyboardInfo = KeyboardInfo(
            width, // baseHeight is without padding, but coordinates include padding
            height,
            keys.map { KeyInfo(it.x, it.width, it.y, it.height, it.code) }
        )
        val filteredSuggestions = mutableListOf<SuggestedWords.SuggestedWordInfo>()
        for (word in suggestions) { // suggestions are sorted with highest score first
            if (word.mSourceDict.mDictType == Dictionary.TYPE_CONTACTS
                || suggestions.any { it.mWord == word.mWord && it.mSourceDict.mDictType == Dictionary.TYPE_CONTACTS })
                continue // never store contacts (might be in user history too)
            // for the personal dictionary we rely on the ignore list
            if (word.mScore < 0 && filteredSuggestions.size > 5)
                continue // no need to add bad matches
            if (filteredSuggestions.any { it.mWord == word.mWord })
                continue // only one occurrence per word
            if (filteredSuggestions.size > 12)
                continue // should be enough
            filteredSuggestions.add(word)
        }
        val data = GestureData(
            BuildConfig.VERSION_CODE,
            context.protectedPrefs().getString(Settings.PREF_LIBRARY_CHECKSUM, "")!!,
            targetWord,
            dictionariesInSuggestions.map {
                val hash = (it as? BinaryDictionary)?.hash ?: (it as? ReadOnlyBinaryDictionary)?.hash
                DictInfo(hash, it.mDictType, it.mLocale?.toLanguageTag())
            },
            filteredSuggestions.map { Suggestion(it.mWord, it.mScore) },
            PointerData.fromPointers(composedData.mInputPointers),
            keyboardInfo,
            activeMode,
            null
        )
        dao.add(data, timestamp)
    }

    // find when we should NOT save
    private fun isSavingOk(context: Context): Boolean {
        if (inputStyle != SuggestedWords.INPUT_STYLE_TAIL_BATCH)
            return false
        if (activeMode && dictionariesInSuggestions.size == 1)
            return true // active mode should be fine, the size check is just an addition in case there is a bug that sets the wrong mode or dictionary facilitator
        if (Settings.getValues().mIncognitoModeEnabled)
            return false // don't save in incognito mode
        if (packageName in getAppIgnoreList(context))
            return false // package ignored
        val inputAttributes = InputAttributes(keyboard.mId.mEditorInfo, false, "")
        val isEmailField = InputTypeUtils.isEmailVariation(inputAttributes.mInputType and InputType.TYPE_MASK_VARIATION)
        if (inputAttributes.mIsPasswordField || inputAttributes.mNoLearning || isEmailField)
            return false // probably some more inputAttributes to consider
        val ignoreWords = getWordIgnoreList(context)
        // how to deal with the ignore list?
        // check targetWord and first 5 suggestions?
        // or check only what is in the actually saved suggestions?
        if (targetWord in ignoreWords || suggestions.take(5).any { it.word in ignoreWords })
            return false
        if (suggestions.first().mSourceDict.mDictType == Dictionary.TYPE_CONTACTS)
            return false
        return true
    }
}

data class GestureDataInfo(val id: Long, val targetWord: String, val timestamp: Long, val exported: Boolean, val activeMode: Boolean)

@Serializable
data class GestureData(
    val appVersionCode: Int,
    val libraryHash: String, // todo: just a bool whether it's one of our 4 known hashes
    val targetWord: String?, // this will be tricky for active gathering if user corrects the word
    val dictionaries: List<DictInfo>,
    val suggestions: List<Suggestion>,
    val gesture: List<PointerData>,
    val keyboardInfo: KeyboardInfo,
    val activeMode: Boolean,
    val hash: String?
)

// hash is only available for dictionaries from .dict files
// language can be null (but should not be)
@Serializable
data class DictInfo(val hash: String?, val type: String, val language: String?)

@Serializable
data class Suggestion(val word: String, val score: Int)

@Serializable
data class PointerData(val id: Int, val x: Int, val y: Int, val millis: Int) {
    companion object {
        fun fromPointers(pointers: InputPointers): List<PointerData> {
            val result = mutableListOf<PointerData>()
            for (i in 0..<pointers.pointerSize) {
                result.add(PointerData(
                    pointers.pointerIds[i],
                    pointers.xCoordinates[i],
                    pointers.yCoordinates[i],
                    pointers.times[i]
                ))
            }
            return result
        }
    }
}

// gesture typing only works with code, not with arbitrary labels
@Serializable
data class KeyInfo(val left: Int, val width: Int, val top: Int, val height: Int, val codePoint: Int)

@Serializable
data class KeyboardInfo(val width: Int, val height: Int, val keys: List<KeyInfo>)

class GestureDataDao(val db: Database) {
    fun add(data: GestureData, timestamp: Long) {
        require(data.hash == null)
        val jsonString = Json.encodeToString(data)
        // if hash in the resulting string is replaced with null, we should be able to reproduce it
        val dataWithHash = data.copy(hash = ChecksumCalculator.checksum(jsonString.byteInputStream()))
        val cv = ContentValues(3)
        cv.put(COLUMN_TIMESTAMP, timestamp)
        cv.put(COLUMN_WORD, data.targetWord)
        if (data.activeMode)
            cv.put(COLUMN_SOURCE_ACTIVE, 1)
        cv.put(COLUMN_DATA, Json.encodeToString(dataWithHash))
        db.writableDatabase.insert(TABLE, null, cv)
    }

    fun filterInfos(
        word: String? = null,
        begin: Long? = null,
        end: Long? = null,
        exported: Boolean? = null,
        activeMode: Boolean? = null
    ): List<GestureDataInfo> {
        val result = mutableListOf<GestureDataInfo>()
        val query = mutableListOf<String>()
        if (word != null) query.add("LOWER($COLUMN_WORD) like ?||'%'")
        if (begin != null) query.add("$COLUMN_TIMESTAMP >= $begin")
        if (end != null) query.add("$COLUMN_TIMESTAMP <= $end")
        if (exported != null) query.add("$COLUMN_EXPORTED = ${if (exported) 1 else 0}")
        if (activeMode != null) query.add("$COLUMN_SOURCE_ACTIVE = ${if (activeMode) 1 else 0}")
        db.readableDatabase.query(
            TABLE,
            arrayOf(COLUMN_ID, COLUMN_WORD, COLUMN_TIMESTAMP, COLUMN_EXPORTED, COLUMN_SOURCE_ACTIVE),
            query.joinToString(" AND "),
            word?.let { arrayOf(it.lowercase()) },
            null,
            null,
            null
        ).use {
            while (it.moveToNext()) {
                result.add(GestureDataInfo(
                    it.getLong(0),
                    it.getString(1),
                    it.getLong(2),
                    it.getInt(3) != 0,
                    it.getInt(4) != 0
                ))
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
            null,
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

    fun markAsExported(ids: List<Long>) {
        if (ids.isEmpty()) return
        val cv = ContentValues(1)
        cv.put(COLUMN_EXPORTED, 1)
        db.writableDatabase.update(TABLE, cv, "$COLUMN_ID IN (${ids.joinToString(",")})", null)
    }

    fun delete(ids: List<Long>, onlyExported: Boolean, context: Context): Int {
        if (ids.isEmpty()) return 0
        val where = "$COLUMN_ID IN (${ids.joinToString(",")})"
        val whereExported = " AND $COLUMN_EXPORTED <> 0"
        val count: Int
        if (onlyExported) {
            count = db.writableDatabase.delete(TABLE, where + whereExported, null)
            addExportedActiveDeletionCount(context, count) // actually we could also have a counter in the db
        } else {
            val exportedCount = db.readableDatabase.rawQuery("SELECT COUNT(1) FROM $TABLE WHERE $where$whereExported", null).use {
                it.moveToFirst()
                it.getInt(0)
            }
            count = db.writableDatabase.delete(TABLE, where, null)
            addExportedActiveDeletionCount(context, exportedCount)
        }
        return count
    }

    fun deleteAll() {
        db.writableDatabase.delete(TABLE, null, null)
    }

    fun deletePassiveWords(words: Collection<String>) {
        val wordsString = words.joinToString("','") { it.lowercase() }
        db.writableDatabase.delete(
            TABLE,
            "$COLUMN_SOURCE_ACTIVE <> 0 AND LOWER($COLUMN_WORD) in (?)",
            arrayOf(wordsString)
        )
    }

    fun count(exported: Boolean? = null, activeMode: Boolean? = null): Int {
        val where = mutableListOf<String>()
        if (exported != null)
            where.add("$COLUMN_EXPORTED ${if (exported) "<>" else "="} 0")
        if (activeMode != null)
            where.add("$COLUMN_SOURCE_ACTIVE ${if (activeMode) "<>" else "="} 0")
        val whereString = if (where.isEmpty()) "" else "WHERE ${where.joinToString(" AND ")}"
        return db.readableDatabase.rawQuery("SELECT COUNT(1) FROM $TABLE $whereString", null).use {
            it.moveToFirst()
            it.getInt(0)
        }
    }

    fun isEmpty(): Boolean {
        db.readableDatabase.rawQuery("SELECT EXISTS (SELECT 1 FROM $TABLE)", null).use {
            it.moveToFirst()
            return it.getInt(0) == 0
        }
    }

    init {
        // todo: switch to proper db upgrade before merging
//        db.writableDatabase.execSQL("DROP TABLE $TABLE")
        db.writableDatabase.execSQL(CREATE_TABLE)
    }

    companion object {
        private const val TAG = "GestureDataDao"

        private const val TABLE = "GESTURE_DATA"
        private const val COLUMN_ID = "ID"
        private const val COLUMN_TIMESTAMP = "TIMESTAMP"
        private const val COLUMN_WORD = "WORD"
        private const val COLUMN_EXPORTED = "EXPORTED"
        private const val COLUMN_SOURCE_ACTIVE = "SOURCE_ACTIVE"
        private const val COLUMN_DATA = "DATA" // data is text, blob actually is slower to store, and probably not worth the saved space

        const val CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE (
                $COLUMN_ID INTEGER PRIMARY KEY,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_WORD TEXT NOT NULL,
                $COLUMN_EXPORTED TINYINT NOT NULL DEFAULT 0,
                $COLUMN_SOURCE_ACTIVE TINYINT NOT NULL DEFAULT 0,
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
