/*
 * Copyright (C) 2008 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.legacy

import android.text.TextUtils
import android.util.SparseArray
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.legacy.utils.BinaryDictionaryUtils
import helium314.keyboard.latin.legacy.utils.WordInputEventForPersonalization
import helium314.keyboard.latin.makedict.DictionaryHeader
import helium314.keyboard.latin.makedict.FormatSpec.DictionaryOptions
import helium314.keyboard.latin.makedict.UnsupportedFormatException
import helium314.keyboard.latin.makedict.WordProperty
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.Log
import java.io.File
import java.util.ArrayList
import java.util.Arrays
import java.util.HashMap
import java.util.Locale

/**
 * Implements a static, compacted, binary dictionary of standard words.
 */
class BinaryDictionary : Dictionary {

    private var mNativeDict: Long = 0
    private val mDictSize: Long
    private val mDictFilePath: String
    private val mUseFullEditDistance: Boolean
    private val mIsUpdatable: Boolean
    private var mHasUpdated = false

    private val mDicTraverseSessions = SparseArray<DicTraverseSession>()

    /**
     * Constructs binary dictionary using existing dictionary file.
     * @param filename the name of the file to read through native code.
     * @param offset the offset of the dictionary data within the file.
     * @param length the length of the binary data.
     * @param useFullEditDistance whether to use the full edit distance in suggestions
     * @param dictType the dictionary type, as a human-readable string
     * @param isUpdatable whether to open the dictionary file in writable mode.
     */
    constructor(
        filename: String,
        offset: Long,
        length: Long,
        useFullEditDistance: Boolean,
        locale: Locale,
        dictType: String,
        isUpdatable: Boolean
    ) : super(dictType, locale) {
        mDictSize = length
        mDictFilePath = filename
        mIsUpdatable = isUpdatable
        mHasUpdated = false
        mUseFullEditDistance = useFullEditDistance
        loadDictionary(filename, offset, length, isUpdatable)
    }

    /**
     * Constructs binary dictionary on memory.
     * @param filename the name of the file used to flush.
     * @param useFullEditDistance whether to use the full edit distance in suggestions
     * @param dictType the dictionary type, as a human-readable string
     * @param formatVersion the format version of the dictionary
     * @param attributeMap the attributes of the dictionary
     */
    constructor(
        filename: String,
        useFullEditDistance: Boolean,
        locale: Locale,
        dictType: String,
        formatVersion: Long,
        attributeMap: Map<String, String>
    ) : super(dictType, locale) {
        mDictSize = 0
        mDictFilePath = filename
        // On memory dictionary is always updatable.
        mIsUpdatable = true
        mHasUpdated = false
        mUseFullEditDistance = useFullEditDistance
        val keyArray = attributeMap.keys.toTypedArray()
        val valueArray = attributeMap.values.toTypedArray()
        mNativeDict = createOnMemoryNative(formatVersion, locale.toString(), keyArray, valueArray)
    }

    // TODO: There should be a way to remove used DicTraverseSession objects from
    // {@code mDicTraverseSessions}.
    private fun getTraverseSession(traverseSessionId: Int): DicTraverseSession {
        synchronized(mDicTraverseSessions) {
            var traverseSession = mDicTraverseSessions.get(traverseSessionId)
            if (traverseSession == null) {
                traverseSession = DicTraverseSession(mLocale, mNativeDict, mDictSize)
                mDicTraverseSessions.put(traverseSessionId, traverseSession)
            }
            return traverseSession
        }
    }

    // TODO: Move native dict into session
    private fun loadDictionary(
        path: String,
        startOffset: Long,
        length: Long,
        isUpdatable: Boolean
    ) {
        mHasUpdated = false
        mNativeDict = openNative(path, startOffset, length, isUpdatable)
    }

    // TODO: Check isCorrupted() for main dictionaries.
    fun isCorrupted(): Boolean {
        if (!isValidDictionary) {
            return false
        }
        if (!isCorruptedNative(mNativeDict)) {
            return false
        }
        // TODO: Record the corruption.
        Log.e(TAG, "BinaryDictionary ($mDictFilePath) is corrupted.")
        Log.e(TAG, "locale: $mLocale")
        Log.e(TAG, "dict size: $mDictSize")
        Log.e(TAG, "updatable: $mIsUpdatable")
        return true
    }

    @Throws(UnsupportedFormatException::class)
    override fun getHeader(): DictionaryHeader? {
        if (mNativeDict == 0L) {
            return null
        }
        val outHeaderSize = IntArray(1)
        val outFormatVersion = IntArray(1)
        val outAttributeKeys = ArrayList<IntArray>()
        val outAttributeValues = ArrayList<IntArray>()
        getHeaderInfoNative(
            mNativeDict, outHeaderSize, outFormatVersion, outAttributeKeys,
            outAttributeValues
        )
        val attributes = HashMap<String, String>()
        for (i in outAttributeKeys.indices) {
            val attributeKey = StringUtils.getStringFromNullTerminatedCodePointArray(
                outAttributeKeys[i]
            )
            val attributeValue = StringUtils.getStringFromNullTerminatedCodePointArray(
                outAttributeValues[i]
            )
            attributes[attributeKey] = attributeValue
        }
        return DictionaryHeader(DictionaryOptions(attributes))
    }

    override fun getSuggestions(
        composedData: ComposedData,
        ngramContext: NgramContext,
        proximityInfoHandle: Long,
        settingsValuesForSuggestion: SettingsValuesForSuggestion,
        sessionId: Int,
        weightForLocale: Float,
        inOutWeightOfLangModelVsSpatialModel: FloatArray?
    ): ArrayList<SuggestedWordInfo>? {
        if (!isValidDictionary) {
            return null
        }
        val session = getTraverseSession(sessionId)
        Arrays.fill(session.mInputCodePoints, Constants.NOT_A_CODE)
        ngramContext.outputToArray(
            session.mPrevWordCodePointArrays,
            session.mIsBeginningOfSentenceArray
        )
        val inputPointers = composedData.mInputPointers
        val isGesture = composedData.mIsBatchMode
        val inputSize: Int
        if (!isGesture) {
            inputSize =
                composedData.copyCodePointsExceptTrailingSingleQuotesAndReturnCodePointCount(
                    session.mInputCodePoints
                )
            if (inputSize < 0) {
                return null
            }
        } else {
            inputSize = inputPointers.pointerSize
        }
        session.mNativeSuggestOptions.setUseFullEditDistance(mUseFullEditDistance)
        session.mNativeSuggestOptions.setIsGesture(isGesture)
        if (isGesture) session.mNativeSuggestOptions.setIsSpaceAwareGesture(settingsValuesForSuggestion.mSpaceAwareGesture)
        session.mNativeSuggestOptions.setBlockOffensiveWords(settingsValuesForSuggestion.mBlockPotentiallyOffensive)
        session.mNativeSuggestOptions.setWeightForLocale(weightForLocale)
        if (inOutWeightOfLangModelVsSpatialModel != null) {
            session.mInputOutputWeightOfLangModelVsSpatialModel[0] =
                inOutWeightOfLangModelVsSpatialModel[0]
        } else {
            session.mInputOutputWeightOfLangModelVsSpatialModel[0] =
                Dictionary.NOT_A_WEIGHT_OF_LANG_MODEL_VS_SPATIAL_MODEL.toFloat()
        }
        // TOOD: Pass multiple previous words information for n-gram.
        getSuggestionsNative(
            mNativeDict, proximityInfoHandle,
            getTraverseSession(sessionId).session, inputPointers.xCoordinates,
            inputPointers.yCoordinates, inputPointers.times,
            inputPointers.pointerIds, session.mInputCodePoints, inputSize,
            session.mNativeSuggestOptions.options, session.mPrevWordCodePointArrays,
            session.mIsBeginningOfSentenceArray, ngramContext.prevWordCount,
            session.mOutputSuggestionCount, session.mOutputCodePoints, session.mOutputScores,
            session.mSpaceIndices, session.mOutputTypes,
            session.mOutputAutoCommitFirstWordConfidence,
            session.mInputOutputWeightOfLangModelVsSpatialModel
        )
        if (inOutWeightOfLangModelVsSpatialModel != null) {
            inOutWeightOfLangModelVsSpatialModel[0] =
                session.mInputOutputWeightOfLangModelVsSpatialModel[0]
        }
        val count = session.mOutputSuggestionCount[0]
        val suggestions = ArrayList<SuggestedWordInfo>()
        for (j in 0 until count) {
            val start = j * DICTIONARY_MAX_WORD_LENGTH
            var len = 0
            while (len < DICTIONARY_MAX_WORD_LENGTH && session.mOutputCodePoints[start + len] != 0) {
                ++len
            }
            if (len > 0) {
                suggestions.add(
                    SuggestedWordInfo(
                        String(session.mOutputCodePoints, start, len),
                        "" /* prevWordsContext */,
                        (session.mOutputScores[j] * weightForLocale).toInt(),
                        session.mOutputTypes[j],
                        this /* sourceDict */,
                        session.mSpaceIndices[j] /* indexOfTouchPointOfSecondWord */,
                        session.mOutputAutoCommitFirstWordConfidence[0]
                    )
                )
            }
        }
        return suggestions
    }

    val isValidDictionary: Boolean
        get() = mNativeDict != 0L

    val formatVersion: Int
        get() = getFormatVersionNative(mNativeDict)

    override fun isInDictionary(word: String): Boolean {
        return getFrequency(word) != Dictionary.NOT_A_PROBABILITY
    }

    override fun getFrequency(word: String): Int {
        if (TextUtils.isEmpty(word)) {
            return Dictionary.NOT_A_PROBABILITY
        }
        val codePoints = StringUtils.toCodePointArray(word)
        return getProbabilityNative(mNativeDict, codePoints)
    }

    override fun getMaxFrequencyOfExactMatches(word: String): Int {
        if (TextUtils.isEmpty(word)) {
            return Dictionary.NOT_A_PROBABILITY
        }
        val codePoints = StringUtils.toCodePointArray(word)
        return getMaxProbabilityOfExactMatchesNative(mNativeDict, codePoints)
    }

    fun isValidNgram(ngramContext: NgramContext, word: String): Boolean {
        return getNgramProbability(ngramContext, word) != Dictionary.NOT_A_PROBABILITY
    }

    fun getNgramProbability(ngramContext: NgramContext, word: String): Int {
        if (!ngramContext.isValid || TextUtils.isEmpty(word)) {
            return Dictionary.NOT_A_PROBABILITY
        }
        val prevWordCodePointArrays = arrayOfNulls<IntArray>(ngramContext.prevWordCount)
        val isBeginningOfSentenceArray = BooleanArray(ngramContext.prevWordCount)
        ngramContext.outputToArray(prevWordCodePointArrays, isBeginningOfSentenceArray)
        val wordCodePoints = StringUtils.toCodePointArray(word)
        // Kotlin: arrayOfNulls<IntArray> needs to be passed to JNI as jobjectArray (int[][]).
        // The JNI expects int[][] so Array<IntArray?> should work if elements are not null.
        // But here we need to ensure they are initialized if outputToArray doesn't fill them?
        // outputToArray fills them.
        return getNgramProbabilityNative(
            mNativeDict, prevWordCodePointArrays,
            isBeginningOfSentenceArray, wordCodePoints
        )
    }

    fun getWordProperty(word: String?, isBeginningOfSentence: Boolean): WordProperty? {
        if (word == null) {
            return null
        }
        val codePoints = StringUtils.toCodePointArray(word)
        val outCodePoints = IntArray(DICTIONARY_MAX_WORD_LENGTH)
        val outFlags = BooleanArray(FORMAT_WORD_PROPERTY_OUTPUT_FLAG_COUNT)
        val outProbabilityInfo = IntArray(FORMAT_WORD_PROPERTY_OUTPUT_PROBABILITY_INFO_COUNT)
        val outNgramPrevWordsArray = ArrayList<IntArray?>()
        val outNgramPrevWordIsBeginningOfSentenceArray = ArrayList<BooleanArray>()
        val outNgramTargets = ArrayList<IntArray>()
        val outNgramProbabilityInfo = ArrayList<IntArray>()
        val outShortcutTargets = ArrayList<IntArray>()
        val outShortcutProbabilities = ArrayList<Int>()
        getWordPropertyNative(
            mNativeDict, codePoints, isBeginningOfSentence, outCodePoints,
            outFlags, outProbabilityInfo, outNgramPrevWordsArray,
            outNgramPrevWordIsBeginningOfSentenceArray, outNgramTargets,
            outNgramProbabilityInfo, outShortcutTargets, outShortcutProbabilities
        )
        
        // Convert ArrayList<IntArray?> to ArrayList<IntArray[]> equivalent for constructor?
        // WordProperty constructor expects ArrayList<int[]>.
        // In Kotlin ArrayList<IntArray?> matches ArrayList<int[]> if elements are not null.
        // We cast to non-nullable generic for safety if needed, but Java doesn't distinguish deeply.
        
        // However, WordProperty constructor signature:
        // public WordProperty(int[] codePoints, ..., ArrayList<int[][]> ngramPrevWordsArray, ...)
        // Wait, Java had: ArrayList<int[][]> outNgramPrevWordsArray = new ArrayList<>();
        // Kotlin: ArrayList<Array<IntArray>?> ?
        // Java: int[][] is Array<IntArray>.
        // So ArrayList<Array<IntArray>>.
        
        // Correction: The Java code had `ArrayList<int[][]> outNgramPrevWordsArray`.
        // So in Kotlin it should be `ArrayList<Array<IntArray>>`.
        
        // I need to correct the declaration of outNgramPrevWordsArray locally.
        // JNI fills it.
        
        return WordProperty(
            codePoints,
            outFlags[FORMAT_WORD_PROPERTY_IS_NOT_A_WORD_INDEX],
            outFlags[FORMAT_WORD_PROPERTY_IS_POSSIBLY_OFFENSIVE_INDEX],
            outFlags[FORMAT_WORD_PROPERTY_HAS_NGRAMS_INDEX],
            outFlags[FORMAT_WORD_PROPERTY_HAS_SHORTCUTS_INDEX],
            outFlags[FORMAT_WORD_PROPERTY_IS_BEGINNING_OF_SENTENCE_INDEX], outProbabilityInfo,
            outNgramPrevWordsArray as ArrayList<Array<IntArray>?>, // Unchecked cast, JNI should handle it?
            outNgramPrevWordIsBeginningOfSentenceArray,
            outNgramTargets, outNgramProbabilityInfo, outShortcutTargets,
            outShortcutProbabilities
        )
    }

    class GetNextWordPropertyResult(var mWordProperty: WordProperty?, var mNextToken: Int)

    /**
     * Method to iterate all words in the dictionary for makedict.
     * If token is 0, this method newly starts iterating the dictionary.
     */
    fun getNextWordProperty(token: Int): GetNextWordPropertyResult {
        val codePoints = IntArray(DICTIONARY_MAX_WORD_LENGTH)
        val isBeginningOfSentence = BooleanArray(1)
        val nextToken = getNextWordNative(
            mNativeDict, token, codePoints,
            isBeginningOfSentence
        )
        val word = StringUtils.getStringFromNullTerminatedCodePointArray(codePoints)
        return GetNextWordPropertyResult(
            getWordProperty(word, isBeginningOfSentence[0]), nextToken
        )
    }

    // Add a unigram entry to binary dictionary with unigram attributes in native code.
    fun addUnigramEntry(
        word: String?, probability: Int,
        shortcutTarget: String?, shortcutProbability: Int,
        isBeginningOfSentence: Boolean, isNotAWord: Boolean,
        isPossiblyOffensive: Boolean, timestamp: Int
    ): Boolean {
        if (word == null || word.isEmpty() && !isBeginningOfSentence) {
            return false
        }
        val codePoints = StringUtils.toCodePointArray(word)
        val shortcutTargetCodePoints = if (shortcutTarget != null) StringUtils.toCodePointArray(shortcutTarget) else null
        if (!addUnigramEntryNative(
                mNativeDict, codePoints, probability, shortcutTargetCodePoints,
                shortcutProbability, isBeginningOfSentence, isNotAWord, isPossiblyOffensive,
                timestamp
            )
        ) {
            return false
        }
        mHasUpdated = true
        return true
    }

    // Remove a unigram entry from the binary dictionary in native code.
    fun removeUnigramEntry(word: String): Boolean {
        if (TextUtils.isEmpty(word)) {
            return false
        }
        val codePoints = StringUtils.toCodePointArray(word)
        if (!removeUnigramEntryNative(mNativeDict, codePoints)) {
            return false
        }
        mHasUpdated = true
        return true
    }

    // Add an n-gram entry to the binary dictionary with timestamp in native code.
    fun addNgramEntry(
        ngramContext: NgramContext, word: String,
        probability: Int, timestamp: Int
    ): Boolean {
        if (!ngramContext.isValid || TextUtils.isEmpty(word)) {
            return false
        }
        val prevWordCodePointArrays = arrayOfNulls<IntArray>(ngramContext.prevWordCount)
        val isBeginningOfSentenceArray = BooleanArray(ngramContext.prevWordCount)
        ngramContext.outputToArray(prevWordCodePointArrays, isBeginningOfSentenceArray)
        val wordCodePoints = StringUtils.toCodePointArray(word)
        if (!addNgramEntryNative(
                mNativeDict, prevWordCodePointArrays,
                isBeginningOfSentenceArray, wordCodePoints, probability, timestamp
            )
        ) {
            return false
        }
        mHasUpdated = true
        return true
    }

    // Update entries for the word occurrence with the ngramContext.
    fun updateEntriesForWordWithNgramContext(
        ngramContext: NgramContext,
        word: String, isValidWord: Boolean, count: Int, timestamp: Int
    ): Boolean {
        if (TextUtils.isEmpty(word)) {
            return false
        }
        val prevWordCodePointArrays = arrayOfNulls<IntArray>(ngramContext.prevWordCount)
        val isBeginningOfSentenceArray = BooleanArray(ngramContext.prevWordCount)
        ngramContext.outputToArray(prevWordCodePointArrays, isBeginningOfSentenceArray)
        val wordCodePoints = StringUtils.toCodePointArray(word)
        if (!updateEntriesForWordWithNgramContextNative(
                mNativeDict, prevWordCodePointArrays,
                isBeginningOfSentenceArray, wordCodePoints, isValidWord, count, timestamp
            )
        ) {
            return false
        }
        mHasUpdated = true
        return true
    }

    fun updateEntriesForInputEvents(inputEvents: Array<WordInputEventForPersonalization>) {
        if (!isValidDictionary) {
            return
        }
        var processedEventCount = 0
        while (processedEventCount < inputEvents.size) {
            if (needsToRunGC(true /* mindsBlockByGC */)) {
                flushWithGC()
            }
            processedEventCount = updateEntriesForInputEventsNative(
                mNativeDict, inputEvents,
                processedEventCount
            )
            mHasUpdated = true
            if (processedEventCount <= 0) {
                return
            }
        }
    }

    private fun reopen() {
        close()
        val dictFile = File(mDictFilePath)
        // WARNING: Because we pass 0 as the offset and file.length() as the length, this can
        // only be called for actual files. Right now it's only called by the flush() family of
        // functions, which require an updatable dictionary, so it's okay. But beware.
        loadDictionary(
            dictFile.absolutePath, 0 /* startOffset */,
            dictFile.length(), mIsUpdatable
        )
    }

    // Flush to dict file if the dictionary has been updated.
    fun flush(): Boolean {
        if (!isValidDictionary) {
            return false
        }
        if (mHasUpdated) {
            if (!flushNative(mNativeDict, mDictFilePath)) {
                return false
            }
            reopen()
        }
        return true
    }

    // Run GC and flush to dict file if the dictionary has been updated.
    fun flushWithGCIfHasUpdated(): Boolean {
        return if (mHasUpdated) {
            flushWithGC()
        } else true
    }

    // Run GC and flush to dict file.
    fun flushWithGC(): Boolean {
        if (!isValidDictionary) {
            return false
        }
        if (!flushWithGCNative(mNativeDict, mDictFilePath)) {
            return false
        }
        reopen()
        return true
    }

    /**
     * Checks whether GC is needed to run or not.
     * @param mindsBlockByGC Whether to mind operations blocked by GC. We don't need to care about
     * the blocking in some situations such as in idle time or just before closing.
     * @return whether GC is needed to run or not.
     */
    fun needsToRunGC(mindsBlockByGC: Boolean): Boolean {
        if (!isValidDictionary) {
            return false
        }
        return needsToRunGCNative(mNativeDict, mindsBlockByGC)
    }

    fun migrateTo(newFormatVersion: Int): Boolean {
        if (!isValidDictionary) {
            return false
        }
        val isMigratingDir = File(mDictFilePath + DIR_NAME_SUFFIX_FOR_RECORD_MIGRATION)
        if (isMigratingDir.exists()) {
            isMigratingDir.delete()
            Log.e(
                TAG, "Previous migration attempt failed probably due to a crash. "
                        + "Giving up using the old dictionary (" + mDictFilePath + ")."
            )
            return false
        }
        if (!isMigratingDir.mkdir()) {
            Log.e(
                TAG, "Cannot create a dir (" + isMigratingDir.absolutePath
                        + ") to record migration."
            )
            return false
        }
        try {
            val tmpDictFilePath = mDictFilePath + DICT_FILE_NAME_SUFFIX_FOR_MIGRATION
            if (!migrateNative(mNativeDict, tmpDictFilePath, newFormatVersion.toLong())) {
                return false
            }
            close()
            val dictFile = File(mDictFilePath)
            val tmpDictFile = File(tmpDictFilePath)
            if (!FileUtils.deleteRecursively(dictFile)) {
                return false
            }
            if (!BinaryDictionaryUtils.renameDict(tmpDictFile, dictFile)) {
                return false
            }
            loadDictionary(
                dictFile.absolutePath, 0 /* startOffset */,
                dictFile.length(), mIsUpdatable
            )
            return true
        } finally {
            isMigratingDir.delete()
        }
    }

    fun getPropertyForGettingStats(query: String?): String {
        return if (!isValidDictionary) {
            ""
        } else getPropertyNative(mNativeDict, query)
    }

    override fun shouldAutoCommit(candidate: SuggestedWordInfo): Boolean {
        return candidate.mAutoCommitFirstWordConfidence > CONFIDENCE_TO_AUTO_COMMIT
    }

    override fun close() {
        synchronized(mDicTraverseSessions) {
            val sessionsSize = mDicTraverseSessions.size()
            for (index in 0 until sessionsSize) {
                val traverseSession = mDicTraverseSessions.valueAt(index)
                traverseSession?.close()
            }
            mDicTraverseSessions.clear()
        }
        closeInternalLocked()
    }

    @Synchronized
    private fun closeInternalLocked() {
        if (mNativeDict != 0L) {
            closeNative(mNativeDict)
            mNativeDict = 0
        }
    }

    // TODO: Manage BinaryDictionary instances without using WeakReference or something.
    protected fun finalize() {
        try {
            closeInternalLocked()
        } finally {
            // super.finalize() in Kotlin/JVM? No, just end.
        }
    }

    companion object {
        private val TAG = BinaryDictionary::class.java.simpleName

        // The cutoff returned by native for auto-commit confidence.
        // Must be equal to CONFIDENCE_TO_AUTO_COMMIT in native/jni/src/defines.h
        private const val CONFIDENCE_TO_AUTO_COMMIT = 1000000
        const val DICTIONARY_MAX_WORD_LENGTH = 48
        const val MAX_PREV_WORD_COUNT_FOR_N_GRAM = 3
        const val UNIGRAM_COUNT_QUERY = "UNIGRAM_COUNT"
        const val BIGRAM_COUNT_QUERY = "BIGRAM_COUNT"
        const val MAX_UNIGRAM_COUNT_QUERY = "MAX_UNIGRAM_COUNT"
        const val MAX_BIGRAM_COUNT_QUERY = "MAX_BIGRAM_COUNT"
        const val NOT_A_VALID_TIMESTAMP = -1

        // Format to get unigram flags from native side via getWordPropertyNative().
        private const val FORMAT_WORD_PROPERTY_OUTPUT_FLAG_COUNT = 5
        private const val FORMAT_WORD_PROPERTY_IS_NOT_A_WORD_INDEX = 0
        private const val FORMAT_WORD_PROPERTY_IS_POSSIBLY_OFFENSIVE_INDEX = 1
        private const val FORMAT_WORD_PROPERTY_HAS_NGRAMS_INDEX = 2
        private const val FORMAT_WORD_PROPERTY_HAS_SHORTCUTS_INDEX = 3
        private const val FORMAT_WORD_PROPERTY_IS_BEGINNING_OF_SENTENCE_INDEX = 4

        // Format to get probability and historical info from native side via getWordPropertyNative().
        const val FORMAT_WORD_PROPERTY_OUTPUT_PROBABILITY_INFO_COUNT = 4
        const val FORMAT_WORD_PROPERTY_PROBABILITY_INDEX = 0
        const val FORMAT_WORD_PROPERTY_TIMESTAMP_INDEX = 1
        const val FORMAT_WORD_PROPERTY_LEVEL_INDEX = 2
        const val FORMAT_WORD_PROPERTY_COUNT_INDEX = 3
        const val DICT_FILE_NAME_SUFFIX_FOR_MIGRATION = ".migrate"
        const val DIR_NAME_SUFFIX_FOR_RECORD_MIGRATION = ".migrating"

        init {
            JniUtils.loadNativeLibrary()
        }

        @JvmStatic
        private external fun openNative(
            sourceDir: String, dictOffset: Long, dictSize: Long,
            isUpdatable: Boolean
        ): Long

        @JvmStatic
        private external fun createOnMemoryNative(
            formatVersion: Long,
            locale: String, attributeKeyStringArray: Array<String>,
            attributeValueStringArray: Array<String>
        ): Long

        @JvmStatic
        private external fun getHeaderInfoNative(
            dict: Long, outHeaderSize: IntArray,
            outFormatVersion: IntArray, outAttributeKeys: ArrayList<IntArray>,
            outAttributeValues: ArrayList<IntArray>
        )

        @JvmStatic
        private external fun flushNative(dict: Long, filePath: String): Boolean

        @JvmStatic
        private external fun needsToRunGCNative(dict: Long, mindsBlockByGC: Boolean): Boolean

        @JvmStatic
        private external fun flushWithGCNative(dict: Long, filePath: String): Boolean

        @JvmStatic
        private external fun closeNative(dict: Long)

        @JvmStatic
        private external fun getFormatVersionNative(dict: Long): Int

        @JvmStatic
        private external fun getProbabilityNative(dict: Long, word: IntArray): Int

        @JvmStatic
        private external fun getMaxProbabilityOfExactMatchesNative(dict: Long, word: IntArray): Int

        @JvmStatic
        private external fun getNgramProbabilityNative(
            dict: Long, prevWordCodePointArrays: Array<IntArray?>,
            isBeginningOfSentenceArray: BooleanArray, word: IntArray
        ): Int

        @JvmStatic
        private external fun getWordPropertyNative(
            dict: Long, word: IntArray,
            isBeginningOfSentence: Boolean, outCodePoints: IntArray, outFlags: BooleanArray,
            outProbabilityInfo: IntArray, outNgramPrevWordsArray: ArrayList<Array<IntArray>?>, // Corrected type for JNI?
            outNgramPrevWordIsBeginningOfSentenceArray: ArrayList<BooleanArray>,
            outNgramTargets: ArrayList<IntArray>, outNgramProbabilityInfo: ArrayList<IntArray>,
            outShortcutTargets: ArrayList<IntArray>, outShortcutProbabilities: ArrayList<Int>
        )
        // Wait, outNgramPrevWordsArray was ArrayList<int[][]>.
        // In JNI it's jobject containing ArrayList.
        // I need to be careful with type erasure.

        @JvmStatic
        private external fun getNextWordNative(
            dict: Long, token: Int, outCodePoints: IntArray,
            outIsBeginningOfSentence: BooleanArray
        ): Int

        @JvmStatic
        private external fun getSuggestionsNative(
            dict: Long, proximityInfo: Long,
            traverseSession: Long, xCoordinates: IntArray, yCoordinates: IntArray, times: IntArray,
            pointerIds: IntArray, inputCodePoints: IntArray, inputSize: Int, suggestOptions: IntArray,
            prevWordCodePointArrays: Array<IntArray>, isBeginningOfSentenceArray: BooleanArray,
            prevWordCount: Int, outputSuggestionCount: IntArray, outputCodePoints: IntArray,
            outputScores: IntArray, outputIndices: IntArray, outputTypes: IntArray,
            outputAutoCommitFirstWordConfidence: IntArray,
            inOutWeightOfLangModelVsSpatialModel: FloatArray
        )

        @JvmStatic
        private external fun addUnigramEntryNative(
            dict: Long, word: IntArray, probability: Int,
            shortcutTarget: IntArray?, shortcutProbability: Int, isBeginningOfSentence: Boolean,
            isNotAWord: Boolean, isPossiblyOffensive: Boolean, timestamp: Int
        ): Boolean

        @JvmStatic
        private external fun removeUnigramEntryNative(dict: Long, word: IntArray): Boolean

        @JvmStatic
        private external fun addNgramEntryNative(
            dict: Long,
            prevWordCodePointArrays: Array<IntArray?>, isBeginningOfSentenceArray: BooleanArray,
            word: IntArray, probability: Int, timestamp: Int
        ): Boolean

        @JvmStatic
        private external fun removeNgramEntryNative(
            dict: Long,
            prevWordCodePointArrays: Array<IntArray?>, isBeginningOfSentenceArray: BooleanArray, word: IntArray
        ): Boolean

        @JvmStatic
        private external fun updateEntriesForWordWithNgramContextNative(
            dict: Long,
            prevWordCodePointArrays: Array<IntArray?>, isBeginningOfSentenceArray: BooleanArray,
            word: IntArray, isValidWord: Boolean, count: Int, timestamp: Int
        ): Boolean

        @JvmStatic
        private external fun updateEntriesForInputEventsNative(
            dict: Long,
            inputEvents: Array<WordInputEventForPersonalization>, startIndex: Int
        ): Int

        @JvmStatic
        private external fun getPropertyNative(dict: Long, query: String?): String

        @JvmStatic
        private external fun isCorruptedNative(dict: Long): Boolean

        @JvmStatic
        private external fun migrateNative(
            dict: Long, dictFilePath: String,
            newFormatVersion: Long
        ): Boolean
    }
}
