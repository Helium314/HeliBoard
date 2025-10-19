package helium314.keyboard.settings.screens.gesturedata

import android.content.Context
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.SuggestionResults
import helium314.keyboard.latin.utils.prefs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class WordData(
    val targetWord: String,
    val suggestions: SuggestionResults,
    val composedData: ComposedData,
    val ngramContext: NgramContext,
    keyboard: Keyboard,
    val inputStyle: Int,
    val userId: String,
) {
    // keyboard is not immutable, so better store (potentially) relevant infos immediately
    val keys = keyboard.sortedKeys
    val proxInfo = keyboard.proximityInfo
    val vGap = keyboard.mVerticalGap
    val height = keyboard.mOccupiedHeight
    val width = keyboard.mOccupiedWidth
    val locale = keyboard.mId.locale // might differ from dict locale
    val mode = keyboard.mId.mMode
    val elementId = keyboard.mId.mElementId
    val numberRow = keyboard.mId.mNumberRowEnabled
    val oneHandedMode = keyboard.mId.mOneHandedModeEnabled
    val split = keyboard.mId.mIsSplitLayout
    val inputType = keyboard.mId.mEditorInfo.inputType
    val imeOptions = keyboard.mId.mEditorInfo.imeOptions

    fun save(dict: Dict, context: Context) {
        // todo: guid / hash per gesture (could be hash of all other data)
        val stillGliding = inputStyle == SuggestedWords.INPUT_STYLE_UPDATE_BATCH // todo: use?
        val keyboardInfo = KeyboardInfo(
            width, // baseHeight is without padding, but coordinates include padding
            height,
            keys.map { KeyInfo(it.x + it.width / 2, it.y + it.height / 2, it.code) }
        )
        val data = GestureData(
            BuildConfig.VERSION_CODE,
            context.prefs().getString(Settings.PREF_LIBRARY_CHECKSUM, "")!!,
            userId,
            targetWord,
            listOf(), // todo: this is annoying to create... and currently not relevant
            listOf(DictInfo(dict.hash, dict.locale.toString())),
            suggestions.filter { it.mScore > 0 }.map { Suggestion(it.mWord, it.mScore) }, // todo: there is much more information available
            PointerData.fromPointers(composedData.mInputPointers),
            keyboardInfo
        )
        val string = Json.encodeToString(data)
        getGestureDataFile(context).appendText("$string,\n") // just need to remove trailing ,\n and put inside [ and ] to have an array
    }
}

@Serializable
data class GestureData(
    val appVersionCode: Int,
    val libraryHash: String,
    val uid: String,
    val targetWord: String,
    val precedingWords: List<String>,
    val dictionaries: List<DictInfo>,
    val suggestions: List<Suggestion>,
    val gesture: List<PointerData>,
    val keyboardInfo: KeyboardInfo,
)

@Serializable
data class DictInfo(val hash: String, val name: String)

@Serializable
data class Suggestion(val word: String, val score: Int)

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
// todo: is the center of a key still ok when we have "holes", e.g. in a split keyboard?
@Serializable
data class KeyInfo(val centerX: Int, val centerY: Int, val codePoint: Int)

// todo: more infos like inputType or whatever?
@Serializable
data class KeyboardInfo(val width: Int, val height: Int, val keys: List<KeyInfo>)
