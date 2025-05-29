package helium314.keyboard.settings.screens

import android.content.Context
import android.content.Intent
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import com.android.inputmethod.latin.BinaryDictionary
import helium314.keyboard.compat.locale
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.Dictionary
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.R
import helium314.keyboard.latin.SingleDictionaryFacilitator
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ChecksumCalculator
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.SubtypeLocaleUtils
import helium314.keyboard.latin.utils.SuggestionResults
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.DropDownField
import helium314.keyboard.settings.NextScreenIcon
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.WithSmallTitle
import helium314.keyboard.settings.filePicker
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.previewDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 *  Simple "settings" screen that shows up when glide typing is enabled.
 *  Allows input of glide typing data, and getting results in a file / sending as mail.
 *
 *  For the start, only single words are requested (randomly chosen from the selected dictionary). The user
 *  should type the word using gesture typing.
 *
 *  Possible extensions:
 *  * ngram / sentence data (how to generate?)
 *  * settings and other inputs (relevant when typing sentences because user might correct it)
 *  * multiple languages / dictionaries
 *  * allow user to provide real data? very risky regarding sensitive data, maybe ask for review of all data?
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Composable
fun GestureDataScreen(
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val dataFile = getDataFile(ctx)
    val scope = rememberCoroutineScope()
    val availableDicts = remember { getAvailableDictionaries(ctx) }
    val currentLocale = Settings.getValues().mLocale
    var dict by remember { mutableStateOf(
        availableDicts.firstOrNull { it.locale == currentLocale } ?: availableDicts.firstOrNull { it.locale.language == "en" }
    ) }
    var words by remember { mutableStateOf<List<String>?>(null) }
    var wordFromDict by remember { mutableStateOf<String?>(null) } // some word from the dictionary
    var typed by remember { mutableStateOf(TextFieldValue()) }
    var userId by remember { mutableStateOf(prefs.getString(PREF_GESTURE_USER_ID, "")!!) }
    var lastData by remember { mutableStateOf<WordData?>(null) }
    val suggestionLogger = remember {
        object : SingleDictionaryFacilitator.Companion.SuggestionLogger {
            override fun onNewSuggestions(
                suggestions: SuggestionResults,
                composedData: ComposedData,
                ngramContext: NgramContext,
                keyboard: Keyboard,
                inputStyle: Int
            ) {
                if (!composedData.mIsBatchMode) return
                val target = wordFromDict ?: return
                // todo: do we want to store intermediate suggestions?
                //  currently they are overwritten by the next ones, but stored if the user presses next-word while typing
                lastData = WordData(target, suggestions, composedData, ngramContext, keyboard, inputStyle, userId)
                if (inputStyle == SuggestedWords.INPUT_STYLE_TAIL_BATCH) {
                    // todo: call nextWord immediately if target has a good score in suggestions?
                }
            }
        }
    }
    fun newUserId() {
        val newId = Uuid.random().toString()
        prefs.edit { putString(PREF_GESTURE_USER_ID, newId) }
        userId = newId
    }
    if (userId == "")
        newUserId()
    fun nextWord(save: Boolean) {
        val dict = dict ?: return
        if (!save)
            lastData = null
        lastData?.save(dict, ctx)
        typed = TextFieldValue()
        wordFromDict = words?.random() // randomly choose from dict
        lastData = null
        // reset the data
    }
    LaunchedEffect(dict) {
        val dict = dict ?: return@LaunchedEffect
        facilitator?.closeDictionaries()
        facilitator = SingleDictionaryFacilitator(dict.getDictionary(ctx))
        facilitator?.suggestionLogger = suggestionLogger
        lastData = null
        scope.launch(Dispatchers.Default) {
            words = dict.getWords(ctx)
            nextWord(false)
        }
    }
    val getDataPicker = getData()
    val scrollState = rememberScrollState()
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(horizontal = 12.dp)
                .then(Modifier.padding(innerPadding)),
        ) {
            TopAppBar(
                title = { Text(stringResource(R.string.gesture_data_screen)) },
                navigationIcon = {
                    IconButton(onClick = onClickBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            stringResource(R.string.spoken_description_action_previous)
                        )
                    }
                },
            )
            Text(stringResource(R.string.gesture_data_description))
            Spacer(Modifier.height(12.dp))
            WithSmallTitle(stringResource(R.string.gesture_data_user_id)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(userId, style = MaterialTheme.typography.bodySmall)
                    TextButton({ newUserId() }) { Text(stringResource(R.string.gesture_data_new_user_id)) }
                }
            }
            // todo: show data for how many words are actually prepared / stored? currently we don't keep track
            DropDownField(availableDicts, dict, { dict = it }) {
                val locale = it?.locale?.getDisplayName(LocalConfiguration.current.locale())
                val internal = if (it?.internal == true) "(internal)" else "(downloaded)"
                Text(locale?.let { loc -> "$loc $internal" } ?: "no dictionary")
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Column {
                    Text(
                        text = wordFromDict?.let { stringResource(R.string.gesture_data_please_type, it) } ?: stringResource(R.string.gesture_data_please_wait),
                        modifier = Modifier.alpha(if (wordFromDict == null) 0.5f else 1f))
                    OutlinedTextField(
                        value = typed,
                        enabled = wordFromDict != null,
                        onValueChange = { typed = it },
                        keyboardOptions = KeyboardOptions(
                            platformImeOptions = PlatformImeOptions(privateImeOptions = dictTestImeOption),
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions { nextWord(true) },
                    )
                }
            }
            TextButton({ getDataPicker.launch(getDataIntent) }, enabled = (dataFile?.length() ?: 0) > 0) { Text(stringResource(R.string.gesture_data_get_data)) }
            TextButton({ dataFile?.delete() }, enabled = (dataFile?.length() ?: 0) > 0) { Text(stringResource(R.string.gesture_data_delete_data)) }
            // todo: test whether sending mail with attachment actually works (and if so: zip the data before sending!)
            TextButton({ ctx.startActivity(sendMailIntent) }, enabled = (dataFile?.length() ?: 0) > 0) { Text(stringResource(R.string.gesture_data_send_mail)) }
        }
    }
    // showing at top left in preview, but correctly on device
    if (lastData != null)
        ExtendedFloatingActionButton(
            onClick = { nextWord(true) },
            text = { Text(stringResource(R.string.gesture_data_next_save) + "\n" + lastData?.suggestions?.firstOrNull()) },
            icon = { NextScreenIcon() },
            modifier = Modifier
                .wrapContentSize(Alignment.BottomEnd)
                .padding(all = 12.dp)
                .then(Modifier.safeDrawingPadding())
        )
    if (wordFromDict != null)
        ExtendedFloatingActionButton(
            onClick = { nextWord(false) },
            text = { Text(stringResource(R.string.gesture_data_next)) },
            icon = { Icon(painter = painterResource(R.drawable.ic_close), stringResource(R.string.gesture_data_next)) },
            modifier = Modifier
                .wrapContentSize(Alignment.BottomStart)
                .padding(all = 12.dp)
                .then(Modifier.safeDrawingPadding())
        )
}

private fun getAvailableDictionaries(context: Context): List<Dict> {
    val allowedHashes = context.assets.open("known_dict_hashes.txt")
        .use { it.reader().readLines() }.filter { it.isNotBlank() }
    val cached = DictionaryInfoUtils.getCacheDirectories(context)
        .mapNotNull { dir -> dir.listFiles()?.filter {
            it.name.startsWith(DictionaryInfoUtils.DEFAULT_MAIN_DICT)
        } }.flatten().map { CacheDict(it) }.filter { it.hash in allowedHashes }
    val assets = DictionaryInfoUtils.getAssetsDictionaryList(context).orEmpty()
        .map { AssetsDict(it, context) }
        .filter { dict -> cached.none { it.hash == dict.hash } && dict.hash in allowedHashes }
    return cached + assets
}

private interface Dict {
    val hash: String
    val locale: Locale
    val internal: Boolean
    fun getDictionary(context: Context): BinaryDictionary
    // not actually suspending, but makes clear that it shouldn't be called on UI thread (because it's slow)
    suspend fun getWords(context: Context) = getDictionary(context).getWords()
}

private class CacheDict(private val file: File): Dict {
    override val locale = (DictionaryInfoUtils.getDictionaryFileHeaderOrNull(file)?.mLocaleString ?: SubtypeLocaleUtils.NO_LANGUAGE)
        .constructLocale()
    override val hash = file.inputStream().use { ChecksumCalculator.checksum(it) } ?: ""
    override val internal = !file.name.endsWith(DictionaryInfoUtils.USER_DICTIONARY_SUFFIX)
    override fun getDictionary(context: Context) =
        BinaryDictionary(file.absolutePath, 0, file.length(), false, locale, Dictionary.TYPE_MAIN, false)
}

private class AssetsDict(private val name: String, context: Context): Dict {
    override val internal = true
    override val locale = DictionaryInfoUtils.extractLocaleFromAssetsDictionaryFile(name)
    override val hash = context.assets.open("dicts${File.separator}$name").use { ChecksumCalculator.checksum(it) } ?: ""
    override fun getDictionary(context: Context): BinaryDictionary {
        val file = DictionaryInfoUtils.extractAssetsDictionary(name, locale, context)!!
        return BinaryDictionary(file.absolutePath, 0, file.length(), false, locale, Dictionary.TYPE_MAIN, false)
    }
}

private fun BinaryDictionary.getWords(): List<String> {
    var token = 0
    val words = mutableListOf<String>()
    do {
        val result = getNextWordProperty(token)
        if (!result.mWordProperty.mIsNotAWord
            && result.mWordProperty.mWord.length > 1
            && !(result.mWordProperty.mIsPossiblyOffensive && Settings.getValues().mBlockPotentiallyOffensive)
            )
            words.add(result.mWordProperty.mWord)
        token = result.mNextToken
    } while (token != 0)
    return words
}

const val dictTestImeOption = "useTestDictionaryFacilitator"

var facilitator: SingleDictionaryFacilitator? = null
private var lastData: WordData? = null
private val getDataIntent = Intent(Intent.ACTION_CREATE_DOCUMENT)
    .addCategory(Intent.CATEGORY_OPENABLE)
    .putExtra(Intent.EXTRA_TITLE, "gesture_data_${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)}.zip")
    .setType("application/zip")
private val sendMailIntent = Intent(Intent.ACTION_SENDTO).apply {
    data = "mailto:".toUri()
    putExtra(Intent.EXTRA_EMAIL, arrayOf("test@123.com"))
    putExtra(Intent.EXTRA_SUBJECT, "Heliboard ${BuildConfig.VERSION_NAME} gesture data")
    putExtra(Intent.EXTRA_TEXT, "this is some text")
    putExtra(Intent.EXTRA_STREAM, "some absolute path") // todo: does this work from an app-private file?
}

private class WordData(
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
    // todo: which of the dimensions do we actually want? i guess occupied?
    val baseHeight = keyboard.mBaseHeight
    val baseWidth = keyboard.mBaseWidth
    val occHeight = keyboard.mOccupiedHeight
    val occWidth = keyboard.mOccupiedWidth
    val height = keyboard.mId.mHeight
    val width = keyboard.mId.mWidth
    val topPadding = keyboard.mTopPadding
    val locale = keyboard.mId.locale
    val mode = keyboard.mId.mMode
    val elementId = keyboard.mId.mElementId
    val numberRow = keyboard.mId.mNumberRowEnabled
    val oneHandedMode = keyboard.mId.mOneHandedModeEnabled
    val split = keyboard.mId.mIsSplitLayout
    val inputType = keyboard.mId.mEditorInfo.inputType
    val imeOptions = keyboard.mId.mEditorInfo.imeOptions

    fun save(dict: Dict, context: Context) {
        // todo: userId, resettable
        // todo: guid / hash per gesture (could be hash of all other data)
        val stillGliding = inputStyle == SuggestedWords.INPUT_STYLE_UPDATE_BATCH
        val keyboardInfo = KeyboardInfo(
            occWidth,
            occHeight,
            keys.map { KeyInfo(it.x + it.width / 2, it.y + it.height / 2, it.code) }
        )
        val data = GestureData(
            userId,
            targetWord,
            listOf(), // todo: this is annoying to create...
            listOf(DictInfo(dict.hash, dict.locale.toString())),
            suggestions.filter { it.mScore > 0 }.map { Suggestion(it.mWord, it.mScore) }, // todo: there is much more information available
            PointerData.fromPointers(composedData.mInputPointers),
            keyboardInfo
        )
        val string = Json.encodeToString(data)
        getDataFile(context)?.appendText("$string,\n") // just need to remove trailing ,\n and put inside [ and ] to have an array
    }
}

@Serializable
private data class GestureData(
    val uid: String,
    val targetWord: String,
    val precedingWords: List<String>,
    val dictionaries: List<DictInfo>,
    val suggestions: List<Suggestion>,
    val gesture: List<PointerData>,
    val keyboardInfo: KeyboardInfo,
)

@Serializable
private data class DictInfo(val hash: String, val name: String)

@Serializable
private data class Suggestion(val word: String, val score: Int)

// todo: time is coming from getHistoricalEventTime, check actual output (milliseconds, but since when?)
@Serializable
private data class PointerData(val id: Int, val x: Int, val y: Int, val millis: Int) {
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
// todo: is the center of a key still ok when we have "holes", e.g. in a split keyboard
@Serializable
private data class KeyInfo(val centerX: Int, val centerY: Int, val codePoint: Int)

// todo: more infos like inputType or whatever?
@Serializable
private data class KeyboardInfo(val width: Int, val height: Int, val keys: List<KeyInfo>)

@Composable
private fun getData(): ManagedActivityResultLauncher<Intent, ActivityResult> {
    val ctx = LocalContext.current
    // check if file exists and not size 0, otherwise don't even offer the button
    return filePicker { uri ->
        val file = getDataFile(ctx) ?: return@filePicker
        ctx.getActivity()?.contentResolver?.openOutputStream(uri)?.use { os ->
            val zipStream = ZipOutputStream(os)
            zipStream.setLevel(9)
            val fileStream = FileInputStream(file).buffered()
            zipStream.putNextEntry(ZipEntry(file.path))
            fileStream.copyTo(zipStream, 1024)
            fileStream.close()
            zipStream.closeEntry()
            zipStream.close()
        }
    }
}

private fun getDataFile(context: Context) = context.filesDir?.let { File(it, "gesture_data.json") }

private const val PREF_GESTURE_USER_ID = "gesture_typing_screen_user_id"

@Preview
@Composable
private fun Preview() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            GestureDataScreen {  }
        }
    }
}
