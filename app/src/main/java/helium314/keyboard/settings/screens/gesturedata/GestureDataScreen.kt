package helium314.keyboard.settings.screens.gesturedata

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.intl.LocaleList
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
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.SuggestionResults
import helium314.keyboard.latin.utils.UncachedInputMethodManagerUtils
import helium314.keyboard.latin.utils.WordData
import helium314.keyboard.latin.utils.dictTestImeOption
import helium314.keyboard.latin.utils.gestureDataActiveFacilitator
import helium314.keyboard.latin.utils.getSecondaryLocales
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.settings.DropDownField
import helium314.keyboard.settings.NextScreenIcon
import helium314.keyboard.settings.SettingsDestination
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

    // ideally we'd move all the active gathering stuff into a separate function,
    // but either it has issues with the floating button positioning (if they are in the function)
    // or the keyboard flashes (during recomposition)
    var wordFromDict by remember { mutableStateOf<String?>(null) } // some word from the dictionary
    var lastData by remember { mutableStateOf<WordData?>(null) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val words = remember { mutableListOf<String>() }
    val scope = rememberCoroutineScope()
    fun nextWord(save: Boolean) {
        if (!save)
            lastData = null

        lastData?.let { scope.launch { it.save(ctx) } }
        wordFromDict = words.ifEmpty { null }?.random() // randomly choose from dict
        lastData = null
        // reset the data
        focusRequester.requestFocus()
        keyboard?.show()
    }
    @Composable fun activeGathering() {
        val availableDicts = remember { getAvailableDictionaries(ctx) }
        val currentLocale = Settings.getValues().mLocale
        var dict by remember { mutableStateOf(
            availableDicts.firstOrNull { it.locale == currentLocale } ?: availableDicts.firstOrNull { it.locale.language == "en" }
        ) }
        LaunchedEffect(wordFromDict) { focusRequester.requestFocus() }
        val suggestionLogger = remember {
            object : SingleDictionaryFacilitator.Companion.SuggestionLogger {
                override fun onNewSuggestions(
                    suggestions: SuggestionResults,
                    composedData: ComposedData,
                    ngramContext: NgramContext,
                    keyboard: Keyboard,
                    inputStyle: Int
                ) {
                    if (!composedData.mIsBatchMode || inputStyle != SuggestedWords.INPUT_STYLE_TAIL_BATCH) return
                    val target = wordFromDict ?: return
                    val newData = WordData(target, suggestions, composedData, ngramContext, keyboard, inputStyle, true)
                    if (suggestions.any { it.mWord == target && it.mScore >= 0 }) { // just not negative should be fine
                        scope.launch { newData.save(ctx) }
                        nextWord(false)
                    } else {
                        lastData = newData // use the old flow with button
                    }
                }
            }
        }
        LaunchedEffect(dict) {
            val dict = dict ?: return@LaunchedEffect
            gestureDataActiveFacilitator?.closeDictionaries()
            gestureDataActiveFacilitator = SingleDictionaryFacilitator(dict.getDictionary(ctx))
            gestureDataActiveFacilitator?.suggestionLogger = suggestionLogger
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
                val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                val text = if (UncachedInputMethodManagerUtils.isThisImeCurrent(ctx, imm)) {
                    wordFromDict?.let { stringResource(R.string.gesture_data_please_type, it) }
                        ?: stringResource(R.string.gesture_data_please_wait)
                } else {
                    "please switch to HeliBoard"
                }
                Text(
                    text = text,
                    modifier = Modifier.alpha(if (wordFromDict == null) 0.5f else 1f))
                Box(Modifier.size(1.dp)) { // box hides the field, but we can still interact with it
                    TextField(
                        value = TextFieldValue(),
                        enabled = wordFromDict != null,
                        onValueChange = { },
                        keyboardOptions = KeyboardOptions(
                            platformImeOptions = PlatformImeOptions(privateImeOptions = dictTestImeOption),
                            imeAction = ImeAction.Next,
                            hintLocales = dict?.let { LocaleList(it.locale.toLanguageTag()) }
                        ),
                        //keyboardActions = KeyboardActions { nextWord(true) },
                        modifier = Modifier.focusRequester(focusRequester),
                    )
                }
            }
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
            var activeGathering by remember { mutableStateOf(false) }
            var passiveGathering by remember { mutableStateOf(false) } // todo: read from setting
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
            // explain the project, add a link
            // why do we need data, how can you contribute
            // how to submit, and what is in gesture data: keyboard layout, used dictionaries, gesture track, app version, library hash, target word, suggestions by the current library, active / passive
            Text(stringResource(R.string.gesture_data_description))
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            // full description in a popup?
            // use only built-in dictionaries and what is available on dicts repo (so we can fully reproduce things)
            // choose a dictionary, get a random word, swipe it and the next word will come immediately
            Text("active gathering description")
            TextButton({
                activeGathering = !activeGathering
                if (!activeGathering) {
                    lastData = null
                    wordFromDict = null
                }
            }) {
                Text(if (activeGathering) "stop active gathering" else "start active gathering")
            }
            if (activeGathering) // todo: starting is slow, possibly because of hashing
                activeGathering()
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Text("passive gathering description") // full description in a popup?
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.clickable { passiveGathering = !passiveGathering }.fillMaxWidth()
            ) {
                Text("passive gathering")
                Switch(passiveGathering, { passiveGathering = it })
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            // maybe move the review screen content in here if we have enough space (but landscape mode will be bad)
            TextButton(onClick = { SettingsDestination.navigateTo(SettingsDestination.DataReview) }) {
                Text("review & share gesture data")
            }
            // maybe show how many words are in the db (active, passive, exported, not exported)
        }
    }
    // showing at top left in preview, but correctly on device
    if (lastData != null)
        ExtendedFloatingActionButton(
            onClick = { nextWord(true) },
            // doesn't look good with the long text
            text = { Text(stringResource(R.string.gesture_data_next_save, lastData?.suggestions?.firstOrNull()?.word.toString())) },
            icon = { NextScreenIcon() },
            modifier = Modifier
                .wrapContentSize(Alignment.BottomEnd)
                .padding(all = 12.dp)
                .then(Modifier.safeDrawingPadding())
                .fillMaxWidth(0.5f)
        )
    if (wordFromDict != null)
        // todo: add some indication that this will bring up the keyboard?
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

// we only check dictionaries for enabled locales (main + secondary)
private fun getAvailableDictionaries(context: Context): List<DictWithInfo> {
    // todo: update hashes using the release upgrade script, and never remove an entry!
    val allowedHashes = context.assets.open("known_dict_hashes.txt")
        .use { it.reader().readLines() }.filter { it.isNotBlank() }
    val locales = SubtypeSettings.getEnabledSubtypes(true).flatMap {
        getSecondaryLocales(it.extraValue) + it.locale()
    }
    val languages = locales.mapTo(hashSetOf()) { it.language }
    val cached = DictionaryInfoUtils.getCacheDirectories(context)
        .filter { it.name.constructLocale().language in languages }
        .mapNotNull { dir -> dir.listFiles()?.filter {
            it.name.startsWith(DictionaryInfoUtils.DEFAULT_MAIN_DICT)
        } }.flatten().map { CacheDictWithInfo(it) }.filter { it.hash in allowedHashes }
    val assets = DictionaryInfoUtils.getAssetsDictionaryList(context).orEmpty()
        .filter { it.substringAfter("_").substringBefore(".dict").constructLocale().language in languages }
        .map { AssetsDictWithInfo(it, context) }
        .filter { dict -> cached.none { it.hash == dict.hash } && dict.hash in allowedHashes }
    return cached + assets
}

private interface DictWithInfo {
    val hash: String
    val locale: Locale
    val internal: Boolean
    fun getDictionary(context: Context): BinaryDictionary
    // not actually suspending, but makes clear that it shouldn't be called on UI thread (because it's slow)
    suspend fun addWords(context: Context, words: MutableList<String>) = getDictionary(context).addWords(words)
}

private class CacheDictWithInfo(private val file: File): DictWithInfo {
    override val locale = (DictionaryInfoUtils.getDictionaryFileHeaderOrNull(file)?.mLocaleString ?: SubtypeLocaleUtils.NO_LANGUAGE)
        .constructLocale()
    override val hash = ChecksumCalculator.checksum(file) ?: ""
    override val internal = !file.name.endsWith(DictionaryInfoUtils.USER_DICTIONARY_SUFFIX)
    override fun getDictionary(context: Context) =
        BinaryDictionary(file.absolutePath, 0, file.length(), false, locale, Dictionary.TYPE_MAIN, false)
}

private class AssetsDictWithInfo(private val name: String, context: Context): DictWithInfo {
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
            // todo: filter the words?
            //  e.g. min frequency (mWordProperty.probability), "'s" ending words in english, maybe names, ...
            //  we could also show more frequent words more often
            words.add(result.mWordProperty.mWord)
        token = result.mNextToken
    } while (token != 0)
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
