package helium314.keyboard.settings.screens.gesturedata

import android.content.Context
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.android.inputmethod.latin.BinaryDictionary
import helium314.keyboard.compat.locale
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.R
import helium314.keyboard.latin.SingleDictionaryFacilitator
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ChecksumCalculator
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.SubtypeLocaleUtils
import helium314.keyboard.latin.utils.SuggestionResults
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.DropDownField
import helium314.keyboard.settings.NextScreenIcon
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.previewDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.uuid.ExperimentalUuidApi

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
    val scope = rememberCoroutineScope()
    val availableDicts = remember { getAvailableDictionaries(ctx) }
    val currentLocale = Settings.getValues().mLocale
    var dict by remember { mutableStateOf(
        availableDicts.firstOrNull { it.locale == currentLocale } ?: availableDicts.firstOrNull { it.locale.language == "en" }
    ) }
    val words = remember { mutableListOf<String>() }
    var wordFromDict by remember { mutableStateOf<String?>(null) } // some word from the dictionary
    var typed by remember { mutableStateOf(TextFieldValue()) }
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
                lastData = WordData(target, suggestions, composedData, ngramContext, keyboard, inputStyle)
                if (inputStyle == SuggestedWords.INPUT_STYLE_TAIL_BATCH) {
                    // todo: call nextWord immediately if target has a good score in suggestions?
                }
            }
        }
    }
    fun nextWord(save: Boolean) {
        val dict = dict ?: return
        if (!save)
            lastData = null
        lastData?.save(listOf(dict), ctx)
        typed = TextFieldValue()
        wordFromDict = words.ifEmpty { null }?.random() // randomly choose from dict
        lastData = null
        // reset the data
    }
    LaunchedEffect(dict) {
        val dict = dict ?: return@LaunchedEffect
        facilitator?.closeDictionaries()
        facilitator = SingleDictionaryFacilitator(dict.getDictionary(ctx))
        facilitator?.suggestionLogger = suggestionLogger
        lastData = null
        wordFromDict = null
        scope.launch(Dispatchers.Default) {
            words.clear()
            dict.addWords(ctx, words)
        }
        scope.launch(Dispatchers.Default) {
            delay(500)
            var i = 0
            while (words.isEmpty() && i++ < 20)
                delay(50)
            // at least a few words should be loaded now
            nextWord(false)
        }
    }
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
            ShareGestureData()
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

interface Dict {
    val hash: String
    val locale: Locale
    val internal: Boolean
    fun getDictionary(context: Context): BinaryDictionary
    // not actually suspending, but makes clear that it shouldn't be called on UI thread (because it's slow)
    suspend fun addWords(context: Context, words: MutableList<String>) = getDictionary(context).addWords(words)
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

private fun BinaryDictionary.addWords(words: MutableList<String>) {
    var token = 0
    do {
        val result = getNextWordProperty(token)
        if (!result.mWordProperty.mIsNotAWord
            && result.mWordProperty.mWord.length > 1
            && !(result.mWordProperty.mIsPossiblyOffensive && Settings.getValues().mBlockPotentiallyOffensive)
            )
            words.add(result.mWordProperty.mWord)
        token = result.mNextToken
    } while (token != 0)
}

const val dictTestImeOption = "useTestDictionaryFacilitator"

var facilitator: SingleDictionaryFacilitator? = null

fun getGestureDataFile(context: Context): File = fileGetDelegate(context, context.getString(R.string.gesture_data_json))

fun fileGetDelegate(context: Context, filename: String): File {
    // ensure folder exists
    val dir = File(context.filesDir, context.getString(R.string.gesture_data_directory))
    if (!dir.exists()) {
        dir.mkdirs()
    }

    return File(dir, filename)
}

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
