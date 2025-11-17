package helium314.keyboard.settings.screens.gesturedata

import android.content.Context
import com.android.inputmethod.latin.BinaryDictionary
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.dictionary.ReadOnlyBinaryDictionary
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.SuggestionResults
import helium314.keyboard.latin.utils.protectedPrefs
import kotlinx.serialization.Serializable
import java.util.Locale

class WordData(
    val targetWord: String,
    val suggestions: SuggestionResults,
    val composedData: ComposedData,
    val ngramContext: NgramContext,
    val keyboard: Keyboard,
    val inputStyle: Int,
    val currentLocale: Locale, // for multilingual typing, for a single language it's obvious
    val activeMode: Boolean,
) {
    // keyboard is not immutable, so better store potentially relevant information immediately
    private val keys = keyboard.sortedKeys
    private val height = keyboard.mOccupiedHeight
    private val width = keyboard.mOccupiedWidth

    private val proxInfo = keyboard.proximityInfo // todo: is there any use?
    private val elementId = keyboard.mId.mElementId // alpha, symbol, alpha shifted, ...
    private val mode = keyboard.mId.mMode // text, url, mail, ...
    private val inputType = keyboard.mId.mEditorInfo.inputType // should very much correlate to mode, see KeyboardLayoutSet.getKeyboardMode
    private val oneHandedMode = keyboard.mId.mOneHandedModeEnabled // todo: we should see it in the coordinates, right?
    private val split = keyboard.mId.mIsSplitLayout // todo: we should see it in the coordinates, right?

    private val locale = keyboard.mId.locale.toLanguageTag()
    private val secondaryLocales = keyboard.mId.mSubtype.getExtraValueOf(Constants.Subtype.ExtraValue.SECONDARY_LOCALES)
    private val dictionariesInSuggestions = suggestions.map { it.mSourceDict }.toSet() // contains locales

    // todo: what could we additionally need for passive gathering?
    //  selected word (would be the target, needs to consider manual suggestion pick)

    fun save(context: Context) {
        if (!isSavingOk())
            return
        val dao = GestureDataDao.getInstance(context) ?: return

        val keyboardInfo = KeyboardInfo(
            width, // baseHeight is without padding, but coordinates include padding
            height,
            keys.map { KeyInfo(it.x + it.width / 2, it.y + it.height / 2, it.code) }
        )
        val data = GestureData(
            BuildConfig.VERSION_CODE,
            context.protectedPrefs().getString(Settings.PREF_LIBRARY_CHECKSUM, "")!!,
            targetWord,
            listOf(), // todo: this is annoying to create... and currently not relevant
            dictionariesInSuggestions.map {
                val hash = (it as? BinaryDictionary)?.hash ?: (it as? ReadOnlyBinaryDictionary)?.hash
                Dictionary(hash, it.mDictType, it.mLocale?.toLanguageTag())
            },
            suggestions.filter { it.mScore > 0 }.map { Suggestion(it.mWord, it.mScore) }, // todo: there is much more information available
            PointerData.fromPointers(composedData.mInputPointers),
            keyboardInfo,
            activeMode,
            null
        )
        dao.add(data, System.currentTimeMillis()) // todo: in background, but careful about synchronization
    }

    // find when we should NOT save
    private fun isSavingOk(): Boolean {
        if (inputStyle != SuggestedWords.INPUT_STYLE_TAIL_BATCH) // todo: check whether this is correct
            return false
        val inputAttributes = InputAttributes(keyboard.mId.mEditorInfo, false, "")
        if (inputAttributes.mIsPasswordField || inputAttributes.mNoLearning)
            return false // probably some more inputAttributes to consider
        // todo: check exclusion list
        // todo: don't save if the word is coming from personal or contacts dict?
        return true
    }
}

data class GestureDataInfo(val id: Long, val targetWord: String, val timestamp: Long, val exported: Boolean)

@Serializable
data class GestureData(
    val appVersionCode: Int,
    val libraryHash: String,
    val targetWord: String, // this will be tricky for active gathering
    val precedingWords: List<String>, // todo: should we? might be a privacy issue
    val dictionaries: List<Dictionary>,
    val suggestions: List<Suggestion>,
    val gesture: List<PointerData>,
    val keyboardInfo: KeyboardInfo,
    val activeMode: Boolean,
    val hash: String?
)
// todo: locales, which / how to save?

// hash is only available for dictionaries from .dict files
// language can be null (but should not be)
@Serializable
data class Dictionary(val hash: String?, val type: String, val language: String?)

@Serializable
data class Suggestion(val word: String, val score: Int) // todo: do we want a source dictionary?

@Serializable
data class PointerData(val id: Int, val x: Int, val y: Int, val millis: Int) {
    companion object {
        fun fromPointers(pointers: InputPointers): List<PointerData> {
            val result = mutableListOf<PointerData>()
            for (i in 0..pointers.pointerSize) {
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
