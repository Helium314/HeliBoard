package helium314.keyboard.settings.screens.gesturedata

import android.content.Context
import android.text.InputType
import com.android.inputmethod.latin.BinaryDictionary
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.dictionary.ReadOnlyBinaryDictionary
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.InputTypeUtils
import helium314.keyboard.latin.utils.SuggestionResults
import helium314.keyboard.latin.utils.protectedPrefs
import kotlinx.serialization.Serializable

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

    private val timestamp = System.currentTimeMillis()

    // todo: in background, but careful because we may need synchronize write access to GestureDataDao (just test it)
    fun save(context: Context) {
        if (!isSavingOk(context))
            return
        val dao = GestureDataDao.getInstance(context) ?: return

        val keyboardInfo = KeyboardInfo(
            width, // baseHeight is without padding, but coordinates include padding
            height,
            keys.map { KeyInfo(it.x + it.width / 2, it.y + it.height / 2, it.code) }
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
        val inputAttributes = InputAttributes(keyboard.mId.mEditorInfo, false, "")
        val isEmailField = InputTypeUtils.isEmailVariation(inputAttributes.mInputType and InputType.TYPE_MASK_VARIATION)
        if (inputAttributes.mIsPasswordField || inputAttributes.mNoLearning || isEmailField)
            return false // probably some more inputAttributes to consider
        val ignoreWords = getIgnoreList(context)
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
    val libraryHash: String,
    val targetWord: String, // this will be tricky for active gathering if user corrects the word
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
data class KeyInfo(val centerX: Int, val centerY: Int, val codePoint: Int)

@Serializable
data class KeyboardInfo(val width: Int, val height: Int, val keys: List<KeyInfo>)
