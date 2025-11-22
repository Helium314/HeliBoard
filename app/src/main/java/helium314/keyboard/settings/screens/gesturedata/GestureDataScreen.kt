// SPDX-License-Identifier: GPL-3.0-only
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.window.DialogProperties
import com.android.inputmethod.latin.BinaryDictionary
import helium314.keyboard.compat.locale
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.latin.AppsManager
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
import helium314.keyboard.latin.utils.GestureDataDao
import helium314.keyboard.latin.utils.ScriptUtils
import helium314.keyboard.latin.utils.SubtypeLocaleUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.SuggestionResults
import helium314.keyboard.latin.utils.UncachedInputMethodManagerUtils
import helium314.keyboard.latin.utils.WordData
import helium314.keyboard.latin.utils.dictTestImeOption
import helium314.keyboard.latin.utils.gestureDataActiveFacilitator
import helium314.keyboard.latin.utils.getAppIgnoreList
import helium314.keyboard.latin.utils.getWordIgnoreList
import helium314.keyboard.latin.utils.getSecondaryLocales
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.setAppIgnoreList
import helium314.keyboard.latin.utils.setWordIgnoreList
import helium314.keyboard.settings.DeleteButton
import helium314.keyboard.settings.DropDownField
import helium314.keyboard.settings.NextScreenIcon
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.previewDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.collections.plus
import kotlin.uuid.ExperimentalUuidApi

/**
 *  Simple "settings" screen that shows up when gesture typing is enabled.
 *  Allows "active data gathering", which is input of gesture typing data,
 *  and getting results in a file / sending as mail.
 *  This mode is rather simple: the user chooses a dictionary, and get a
 *  random word. After swiping this word it gets stored along with necessary
 *  information to recreate the input and some additional data.
 *
 *  Will allow enabling "passive data gathering" later, which stores most of
 *  the words entered using gesture typing. Here the user needs the ability
 *  review and redact the data before sending, and additionally exclude some
 *  words and apps from passive gathering.
 */
// todo: disable stuff related to passive gathering only and finish active part + export for now
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
    var activeWordCount by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val words = remember { mutableListOf<String>() }
    val scope = rememberCoroutineScope()
    fun nextWord(save: Boolean) {
        if (!save) {
            lastData = null
        }
        lastData?.let { scope.launch {
            ++activeWordCount
            it.save(ctx)
        } }
        wordFromDict = words.ifEmpty { null }?.random() // randomly choose from dict
        lastData = null
        // reset the data
        focusRequester.requestFocus()
        keyboard?.show()
    }
    @Composable fun useActiveGathering() {
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
                        scope.launch {
                            ++activeWordCount
                            newData.save(ctx)
                        }
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

        Box { // without the box the menu appears at the wrong position
            DropDownField(availableDicts, dict, { dict = it }) {
                val locale = it?.locale?.getDisplayName(LocalConfiguration.current.locale())
                val internal = if (it?.internal == true) "(internal)" else "(downloaded)"
                Text(locale?.let { loc -> "$loc $internal" } ?: "no dictionary")
            }
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
                // todo: make the word bigger
                //  means it needs to be in a separate text
                Text(
                    text = text,
                    modifier = Modifier.alpha(if (wordFromDict == null) 0.5f else 1f)
                )
                val activeWordsInDb by remember {
                    activeWordCount = 0
                    val dbCount = GestureDataDao.getInstance(ctx)?.filterInfos(exported = false, activeMode = true)?.size ?: 0
                    mutableIntStateOf(dbCount)
                }
                Text("${activeWordCount + activeWordsInDb} words, $activeWordCount in this session")
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
                        keyboardActions = KeyboardActions { nextWord(false) },
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
            if (activeGathering)
                useActiveGathering()
            Spacer(Modifier.height(12.dp))
            // PassiveGathering is not finished and will be completed + enabled later
//            HorizontalDivider()
//            PassiveGathering()
//            Spacer(Modifier.height(12.dp))
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
        ExtendedFloatingActionButton(
            onClick = { nextWord(false) },
            text = { Text(stringResource(R.string.gesture_data_next)) },
            icon = { Icon(painter = painterResource(R.drawable.ic_ime_switcher), stringResource(R.string.gesture_data_next)) },
            modifier = Modifier
                .wrapContentSize(Alignment.BottomStart)
                .padding(all = 12.dp)
                .then(Modifier.safeDrawingPadding())
        )
}
/*
@Composable
private fun PassiveGathering() {
    val ctx = LocalContext.current
    Text("passive gathering description") // full description in a popup?
    var passiveGathering by remember { mutableStateOf(false) } // todo (when implemented): read from setting
    var showExcludedWordsDialog by remember { mutableStateOf(false) }
    var showExcludedAppsDialog by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .clickable { passiveGathering = !passiveGathering }
            .fillMaxWidth()
    ) {
        Text("passive gathering")
        Switch(passiveGathering, { passiveGathering = it })
    }
    TextButton({ showExcludedWordsDialog = true }) {
        Text("manage excluded words")
    }
    TextButton({ showExcludedAppsDialog = true }) {
        Text("manage excluded applications")
    }
    if (showExcludedAppsDialog) {
        // todo: inverted mode where apps explicitly need to be enabled?
        var ignorePackages by remember { mutableStateOf(getAppIgnoreList(ctx)) }
        var packagesAndNames by remember { mutableStateOf(
            AppsManager(ctx).getPackagesAndNames()
                .sortedWith( compareBy({ it.first !in ignorePackages }, { it.second.lowercase() }))
        ) }
        var filter by remember { mutableStateOf(TextFieldValue()) }
        val scroll = rememberScrollState()
        // todo: load app list in background on entering the screen (just show nothing / please wait until loaded)
        ThreeButtonAlertDialog(
            title = { Text("select apps to exclude from passive gathering") },
            onDismissRequest = { showExcludedAppsDialog = false },
            content = { Column {
                TextField(
                    value = filter,
                    onValueChange = { filter = it },
                    singleLine = true,
                    label = { Text("filter") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                )
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier.verticalScroll(scroll),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    packagesAndNames.filter {
                        if (filter.text.lowercase() == filter.text)
                            filter.text in it.first || filter.text in it.second.lowercase()
                        else
                            filter.text in it.second
                    }.map { (packag, name) ->
                        val ignored = packag in ignorePackages
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ignorePackages = if (ignored) ignorePackages - packag
                                    else ignorePackages + packag
                                }
                        ) {
                            CompositionLocalProvider(
                                LocalTextStyle provides MaterialTheme.typography.bodyLarge
                            ) {
                                Text(name)
                            }
                            CompositionLocalProvider(
                                LocalTextStyle provides MaterialTheme.typography.bodyMedium
                            ) {
                                Text(
                                    packag + ", ${if (ignored) "ignored" else "used"}",
                                    color = if (ignored) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            } },
            onConfirmed = {
                setAppIgnoreList(ctx, ignorePackages)
            },
            confirmButtonText = stringResource(android.R.string.ok),
            properties = DialogProperties(dismissOnClickOutside = false)
        )
    }
    if (showExcludedWordsDialog) {
        var ignoreWords by remember { mutableStateOf(getWordIgnoreList(ctx)) }
        var newWord by remember { mutableStateOf(TextFieldValue()) }
        val scroll = rememberScrollState()
        fun addWord() {
            if (newWord.text.isNotBlank())
                ignoreWords += newWord.text.trim()
            newWord = TextFieldValue()
        }
        ThreeButtonAlertDialog(
            onDismissRequest = { showExcludedWordsDialog = false },
            content = { Column(Modifier.verticalScroll(scroll)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = newWord,
                        onValueChange = { newWord = it},
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("add new word") },
                        keyboardActions = KeyboardActions { addWord() }
                    )
                    IconButton(
                        { addWord() },
                        Modifier.weight(0.2f)) {
                        Icon(painterResource(R.drawable.ic_plus), stringResource(R.string.add))
                    }
                }
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.bodyLarge
                ) {
                    ignoreWords.map { word ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(word)
                            DeleteButton { ignoreWords = ignoreWords.filterNot { word == it }.toSortedSet() }
                        }
                    }
                }
            } },
            onConfirmed = {
                addWord()
                setWordIgnoreList(ctx, ignoreWords)
                GestureDataDao.getInstance(ctx)?.deletePassiveWords(ignoreWords)
            },
            confirmButtonText = stringResource(android.R.string.ok),
            properties = DialogProperties(dismissOnClickOutside = false)
        )
    }
}
*/
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
    val hasCases = mLocale?.let { ScriptUtils.scriptSupportsUppercase(it) } ?: true
    do {
        val result = getNextWordProperty(token)
        val word = result.mWordProperty.mWord
        if (!result.mWordProperty.mIsNotAWord
                && word.length > 1
                && !(result.mWordProperty.mIsPossiblyOffensive && Settings.getValues().mBlockPotentiallyOffensive)
                && result.mWordProperty.probability > 15 // some minimum value, as there are too many unknown / rare words down there
                && (!hasCases || word.uppercase() != word)
            )
            // todo: more filters?
            //  we could also try showing more frequent words more often
            words.add(word)
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
