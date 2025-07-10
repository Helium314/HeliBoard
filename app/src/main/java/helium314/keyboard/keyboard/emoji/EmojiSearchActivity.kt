package helium314.keyboard.keyboard.emoji

import android.R.string.cancel
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.internal.KeyboardBuilder
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.keyboard_parser.EMOJI_HINT_LABEL
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.keyboard.internal.keyboard_parser.getCode
import helium314.keyboard.keyboard.internal.keyboard_parser.getEmojiDefaultVersion
import helium314.keyboard.keyboard.internal.keyboard_parser.getEmojiKeyDimensions
import helium314.keyboard.keyboard.internal.keyboard_parser.getEmojiNeutralVersion
import helium314.keyboard.keyboard.internal.keyboard_parser.getEmojiPopupSpec
import helium314.keyboard.latin.Dictionary
import helium314.keyboard.latin.DictionaryFactory
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.latin.SingleDictionaryFacilitator
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.common.splitOnWhitespace
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.CloseIcon
import helium314.keyboard.settings.SearchIcon

/**
 * This activity is displayed in a gap created for it above the keyboard and below the host app, and partly obscures the host app.
 */
class EmojiSearchActivity : ComponentActivity() {
    private val colors = Settings.getValues().mColors
    private var startup: Boolean = true
    private var emojiPageKeyboardView: EmojiPageKeyboardView? = null
    private var keyboardParams: KeyboardParams? = null
    private var keyWidth: Float? = null
    private var keyHeight: Float? = null
    private var pressedKey: Key? = null
    private var imeClosed: Boolean = false

    @OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        init()
        enableEdgeToEdge()
        setContent {
            LocalContext.current.setTheme(KeyboardTheme.getKeyboardTheme(this).mStyleId)
            Surface(modifier = Modifier.fillMaxSize(), color = Color(0x80000000)) {
                val imeVisible = WindowInsets.isImeVisible
                val localDensity = LocalDensity.current
                var heightPx by remember {
                    mutableIntStateOf(0)
                }
                var heightDp by remember {
                    mutableStateOf(0.dp)
                }
                Column(modifier = Modifier.fillMaxSize().clickable(onClick = { cancel() })
                    .windowInsetsPadding(WindowInsets.safeDrawing.exclude(WindowInsets(bottom = heightDp))),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Column(modifier = Modifier.wrapContentHeight()
                        .background(Color(colors.get(ColorType.MAIN_BACKGROUND))).onGloballyPositioned {
                            if (startup && imeVisible && isAlphaKeyboard()) {
                                search(searchText)
                                return@onGloballyPositioned
                            }
                            if (!startup && !imeVisible) {
                                imeClosed = true
                                cancel()
                                return@onGloballyPositioned
                            }
                            if (!startup && !isAlphaKeyboard()) {
                                cancel()
                                return@onGloballyPositioned
                            }
                            heightPx = it.size.height
                            heightDp = with(localDensity) { it.size.height.toDp() }
                        }) {
                        Row(modifier = Modifier.fillMaxWidth().height(30.dp)) {
                            IconButton(onClick = { cancel() }) {
                                Icon(painter = painterResource(R.drawable.ic_arrow_back),
                                    stringResource(R.string.spoken_description_action_previous),
                                    tint = Color(colors.get(ColorType.EMOJI_KEY_TEXT)))
                            }
                            Text(text = stringResource(R.string.emoji_search_title), fontSize = 18.sp,
                                color = Color(colors.get(ColorType.EMOJI_KEY_TEXT)),
                                modifier = Modifier.fillMaxWidth().align(Alignment.CenterVertically))
                        }
                        AndroidView({ emojiPageKeyboardView!! }, modifier = Modifier.wrapContentHeight().fillMaxWidth())
                        val focusRequester = remember { FocusRequester() }
                        var text by remember { mutableStateOf(TextFieldValue(searchText, selection = TextRange(searchText.length))) }
                        val textFieldColors = TextFieldDefaults.colors()
                            .copy(unfocusedContainerColor = Color(colors.get(ColorType.FUNCTIONAL_KEY_BACKGROUND)),
                                unfocusedTextColor = Color(colors.get(ColorType.FUNCTIONAL_KEY_TEXT)),
                                cursorColor = Color(colors.get(ColorType.FUNCTIONAL_KEY_TEXT)),
                                unfocusedLeadingIconColor = Color(colors.get(ColorType.FUNCTIONAL_KEY_TEXT)),
                                unfocusedTrailingIconColor = Color(colors.get(ColorType.FUNCTIONAL_KEY_TEXT)),
                                unfocusedPlaceholderColor = lerp(Color(colors.get(ColorType.FUNCTIONAL_KEY_BACKGROUND)),
                                    Color(colors.get(ColorType.FUNCTIONAL_KEY_TEXT)), 0.5f))
                        CompositionLocalProvider(LocalTextSelectionColors provides textFieldColors.textSelectionColors) {
                            BasicTextField(
                                value = text,
                                modifier = Modifier.fillMaxWidth().heightIn(20.dp, 30.dp).focusRequester(focusRequester),
                                textStyle = TextStyle(textDirection = TextDirection.Content, color = textFieldColors.unfocusedTextColor),
                                onValueChange = { it: TextFieldValue ->
                                    text = it
                                    search(it.text)
                                },
                                enabled = true,
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Done,
                                    platformImeOptions = PlatformImeOptions(encodePrivateImeOptions(PrivateImeOptions(heightPx)))
                                ),
                                keyboardActions = KeyboardActions(onDone = { finish() }),
                                singleLine = true,
                            ) {
                                TextFieldDefaults.DecorationBox(
                                    value = text.text,
                                    colors = textFieldColors,
                                    contentPadding = PaddingValues(2.dp),
                                    visualTransformation = VisualTransformation.None,
                                    innerTextField = it,
                                    placeholder = { Text(stringResource(R.string.search_field_placeholder)) },
                                    leadingIcon = { SearchIcon() },
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            text = TextFieldValue()
                                            search("")
                                        }) { CloseIcon(cancel) }
                                    },
                                    singleLine = true,
                                    enabled = true,
                                    interactionSource = MutableInteractionSource(),
                                )
                            }
                        }
                        LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    }
                }
            }
        }
    }

    override fun onStop() {
        val intent = Intent(this, LatinIME::class.java).setAction(EMOJI_SEARCH_DONE_ACTION)
            .putExtra(IME_CLOSED_KEY, imeClosed)
        if (pressedKey != null) {
            intent.putExtra(EMOJI_KEY, if (pressedKey!!.code == KeyCode.MULTIPLE_CODE_POINTS)
                pressedKey!!.getOutputText()
            else
                Character.toString(pressedKey!!.code))

            KeyboardSwitcher.getInstance().emojiPalettesView.addRecentKey(pressedKey)
        }
        startService(intent)
        super.onStop()
    }

    private fun init() {
        val keyboardWidth = ResourceUtils.getKeyboardWidth(this, Settings.getValues())
        val layoutSet = KeyboardLayoutSet.Builder(this, null).setSubtype(RichInputMethodSubtype.emojiSubtype)
            .setKeyboardGeometry(keyboardWidth, EmojiLayoutParams(resources).emojiKeyboardHeight).build()

        // Initialize default versions and popup specs
        layoutSet.getKeyboard(KeyboardId.ELEMENT_EMOJI_CATEGORY2)

        val keyboard = DynamicGridKeyboard(prefs(), layoutSet.getKeyboard(KeyboardId.ELEMENT_EMOJI_RECENTS),
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 1 else 2,
            KeyboardId.ELEMENT_EMOJI_CATEGORY16, keyboardWidth)
        val builder = KeyboardBuilder(this, KeyboardParams())
        builder.load(keyboard.mId)
        keyboardParams = builder.mParams
        val (width, height) = getEmojiKeyDimensions(keyboardParams!!, this)
        keyWidth = width
        keyHeight = height
        emojiPageKeyboardView = EmojiPageKeyboardView(this, null)
        emojiPageKeyboardView!!.setKeyboard(keyboard)
        emojiPageKeyboardView!!.layoutParams =
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        emojiPageKeyboardView!!.background = null
        colors.setBackground(emojiPageKeyboardView!!, ColorType.MAIN_BACKGROUND)
        emojiPageKeyboardView!!.setPadding(0, 10, 0, 10)

        emojiPageKeyboardView!!.setEmojiViewCallback(object : EmojiViewCallback {
            override fun onPressKey(key: Key) {
            }

            override fun onReleaseKey(key: Key) {
                pressedKey = key
                finish()
            }

            override fun getDescription(emoji: String): String? = if (Settings.getValues().mShowEmojiDescriptions)
                dictionaryFacilitator?.getWordProperty(getEmojiNeutralVersion(emoji))?.mShortcutTargets[0]?.mWord else null
        })
    }

    private fun isAlphaKeyboard(): Boolean = !(KeyboardSwitcher.getInstance().isShowingEmojiPalettes
        || KeyboardSwitcher.getInstance().isShowingClipboardHistory)

    private fun search(text: String) {
        initDictionaryFacilitator(this)
        if (dictionaryFacilitator == null) {
            cancel()
            return
        }

        if (!startup && text == searchText) {
            return
        }

        searchText = text
        startup = false
        val keyboard = emojiPageKeyboardView!!.keyboard as DynamicGridKeyboard
        keyboard.removeAllKeys()
        pressedKey = null
        dictionaryFacilitator!!.getSuggestions(text.splitOnWhitespace()).filter { StringUtils.mightBeEmoji(it.word) }.forEach {
            val emoji = getEmojiDefaultVersion(it.word)
            val popupSpec = getEmojiPopupSpec(emoji)
            val keyParams = Key.KeyParams(emoji, emoji.getCode(), if (popupSpec != null) EMOJI_HINT_LABEL else null, popupSpec,
                Key.LABEL_FLAGS_FONT_NORMAL, keyboardParams)
            keyParams.mAbsoluteWidth = keyWidth!!
            keyParams.mAbsoluteHeight = keyHeight!!
            val key = keyParams.createKey()
            keyboard.addKeyLast(key)
            if (pressedKey == null && Settings.getValues().mAutoCorrectEnabled)
                pressedKey = key
        }
        emojiPageKeyboardView!!.invalidate()
    }

    private fun cancel() {
        pressedKey = null
        finish()
    }

    @JvmRecord
    data class PrivateImeOptions(val height: Int)

    companion object {
        const val EMOJI_SEARCH_DONE_ACTION: String = "EMOJI_SEARCH_DONE"
        const val IME_CLOSED_KEY: String = "IME_CLOSED"
        const val EMOJI_KEY: String = "EMOJI"
        private const val PRIVATE_IME_OPTIONS_PREFIX: String = "helium314.keyboard.keyboard.emoji.search"
        private var dictionaryFacilitator: SingleDictionaryFacilitator? = null
        private var searchText: String = ""

        fun isSupported(context: Context): Boolean {
            initDictionaryFacilitator(context)
            return dictionaryFacilitator != null
        }

        fun decodePrivateImeOptions(editorInfo: EditorInfo?): PrivateImeOptions = PrivateImeOptions(
            editorInfo?.privateImeOptions?.takeIf { it.startsWith(PRIVATE_IME_OPTIONS_PREFIX) }
                ?.substring(PRIVATE_IME_OPTIONS_PREFIX.length + 1)?.toInt() ?: 0)

        private fun encodePrivateImeOptions(privateImeOptions: PrivateImeOptions) =
            "$PRIVATE_IME_OPTIONS_PREFIX,${privateImeOptions.height}"

        private fun initDictionaryFacilitator(context: Context) {
            val locale = RichInputMethodManager.getInstance().currentSubtype.locale
            if (dictionaryFacilitator?.isForLocale(locale) != true) {
                dictionaryFacilitator?.closeDictionaries()
                dictionaryFacilitator = DictionaryInfoUtils.getCachedDictForLocaleAndType(locale, Dictionary.TYPE_EMOJI, context)
                    ?.let { DictionaryFactory.getDictionary(it, locale) }?.let { SingleDictionaryFacilitator(it) }
            }
        }
    }
}
