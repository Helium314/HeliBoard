package helium314.keyboard.engine.general

import android.annotation.SuppressLint
import android.content.Context
import android.icu.text.BreakIterator
import android.os.SystemClock
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.BackgroundColorSpan
import android.text.style.UnderlineSpan
import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.google.android.apps.inputmethod.libs.mozc.session.MozcJNI
import com.google.common.base.Optional
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import helium314.keyboard.engine.GlobalIMEMessage
import helium314.keyboard.engine.IMEHelper
import helium314.keyboard.engine.IMEInterface
import helium314.keyboard.engine.IMEMessage
import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.internal.KeyboardLayoutKind
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.Subtypes
import helium314.keyboard.latin.SubtypesSetting
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.localeFromString
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.uix.FileKind
import helium314.keyboard.latin.uix.ResourceHelper
import helium314.keyboard.latin.uix.SettingsKey
import helium314.keyboard.latin.uix.UserDictionaryIO
import helium314.keyboard.latin.uix.actions.ArrowDownAction
import helium314.keyboard.latin.uix.actions.ArrowLeftAction
import helium314.keyboard.latin.uix.actions.ArrowRightAction
import helium314.keyboard.latin.uix.actions.ArrowUpAction
import helium314.keyboard.latin.uix.actions.UndoAction
import helium314.keyboard.latin.uix.actions.keyCode
import helium314.keyboard.latin.uix.getImportedUserDictFilesForLocale
import helium314.keyboard.latin.uix.getSetting
import helium314.keyboard.latin.uix.isDirectBootUnlocked
import helium314.keyboard.latin.uix.settings.UserSettingsMenu
import helium314.keyboard.latin.uix.settings.pages.pdict.decodeJapanesePersonalWord
import helium314.keyboard.latin.uix.settings.userSettingToggleDataStore
import helium314.keyboard.latin.utils.InputTypeUtils
import helium314.keyboard.nativelib.mozc.KeycodeConverter
import helium314.keyboard.nativelib.mozc.KeycodeConverter.KeyEventInterface
import helium314.keyboard.nativelib.mozc.KeycodeConverter.getMozcKeyEvent
import helium314.keyboard.nativelib.mozc.MozcLog
import helium314.keyboard.nativelib.mozc.MozcUtil
import helium314.keyboard.nativelib.mozc.keyboard.Keyboard
import helium314.keyboard.nativelib.mozc.model.SelectionTracker
import helium314.keyboard.nativelib.mozc.session.SessionExecutor
import helium314.keyboard.nativelib.mozc.session.SessionHandlerFactory
import helium314.keyboard.v2keyboard.KeyboardLayoutSetV2
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCandidateWindow
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Context.InputFieldType
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Preedit
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.SessionCommand
import org.mozc.android.inputmethod.japanese.protobuf.ProtoConfig
import org.mozc.android.inputmethod.japanese.protobuf.ProtoUserDictionaryStorage
import java.io.File
import java.util.Locale

object JapaneseIMESettings {
    val FlickOnly = SettingsKey(
        booleanPreferencesKey("ime_ja_flick_only"),
        false
    )

    val HalfWidthSpace = SettingsKey(
        booleanPreferencesKey("ime_ja_half_width_space"),
        false
    )

    val menu = UserSettingsMenu(
        title = R.string.japanese_settings_title,
        navPath = "ime/ja", registerNavPath = true,
        settings = listOf(
            userSettingToggleDataStore(
                title = R.string.japanese_settings_toggle_flick_only,
                subtitle = R.string.japanese_settings_toggle_flick_only_subtitle,
                setting = FlickOnly
            ),

            userSettingToggleDataStore(
                title = R.string.japanese_settings_toggle_halfwidth_space,
                subtitle = R.string.japanese_settings_toggle_halfwidth_space_subtitle,
                setting = HalfWidthSpace
            )
        )
    )
}

// Focused segment's attribute.
private val SPAN_CONVERT_HIGHLIGHT = BackgroundColorSpan(0x66EF3566)

// Background color span for non-focused conversion segment.
// We don't create a static CharacterStyle instance since there are multiple segments at the
// same
// time. Otherwise, segments except for the last one cannot have style.
private const val CONVERT_NORMAL_COLOR = 0x19EF3566

// Cursor position.
// Note that InputConnection seems not to be able to show cursor. This is a workaround.
private val SPAN_BEFORE_CURSOR = BackgroundColorSpan(0x664DB6AC)

// Background color span for partial conversion.
private val SPAN_PARTIAL_SUGGESTION_COLOR = BackgroundColorSpan(0x194DB6AC)

// Underline.
private val SPAN_UNDERLINE = UnderlineSpan()

internal fun getInputFieldType(attribute: EditorInfo): InputFieldType {
    val inputType = attribute.inputType
    if (MozcUtil.isPasswordField(inputType)) {
        return InputFieldType.PASSWORD
    }
    val inputClass = inputType and InputType.TYPE_MASK_CLASS
    if (inputClass == InputType.TYPE_CLASS_PHONE) {
        return InputFieldType.TEL
    }
    return if (inputClass == InputType.TYPE_CLASS_NUMBER) {
        InputFieldType.NUMBER
    } else InputFieldType.NORMAL
}

fun mozcUserProfileDir(context: Context) = File(context.applicationInfo.dataDir, ".mozc")

fun initJniDictLocations(context: Context) {
    if(!context.isDirectBootUnlocked) {
        val tmpDir = File("/tmp/.mozc")
        tmpDir.mkdirs()
        MozcJNI.load(
            tmpDir.absolutePath,
            ""
        )
    }

    // Ensure the user profile directory exists.
    val userProfileDirectory = mozcUserProfileDir(context)
    if (!userProfileDirectory.exists()) {
        // No profile directory is found. Create the one.
        if (!userProfileDirectory.mkdirs()) {
            // Failed to create a directory. The mozc conversion engine will be able to run
            // even in this case, but no persistent data (e.g. user history, user dictionary)
            // will be stored, so some fuctions using them won't work well.
            MozcLog.e("Failed to create user profile directory: " + userProfileDirectory.absolutePath)
        }
    }

    val dictFile = ResourceHelper.findFileForKind(
        context,
        Locale.forLanguageTag("ja-JP"),
        FileKind.Dictionary
    ) ?: run {
        // Locate the exact locale in case it's something unexpected (different country, script, etc)
        val subtypes = context.getSetting(SubtypesSetting)
        subtypes.map {
            Subtypes.getLocale(Subtypes.convertToSubtype(it))
        }.firstNotNullOfOrNull {
            if(it.language.lowercase() == "ja") {
                ResourceHelper.findFileForKind(
                    context,
                    it,
                    FileKind.Dictionary
                )
            } else null
        }
    }

    if(BuildConfig.DEBUG) {
        Log.d(TAG, "userProfileDirectory path = ${userProfileDirectory.absolutePath}")
        Log.d(TAG, "dictFile path = ${dictFile?.absolutePath}")
    }

    MozcJNI.load(
        userProfileDirectory.absolutePath,
        dictFile?.absolutePath ?: ""
    )
}

private const val MOZC_DICT_NAME = "FUTO_UserDict"
typealias MozcStatus = ProtoUserDictionaryStorage.UserDictionaryCommandStatus.Status
fun refreshMozcDictionaries(context: Context, executor: SessionExecutor) {
    val necessaryDictionaries = getImportedUserDictFilesForLocale(context, Locale.JAPANESE)
    val missingDictionaries = necessaryDictionaries.toMutableList()
    val userDictionaryIO = UserDictionaryIO(context)

    val sessionResult = executor.sendUserDictionaryCommand(
        ProtoUserDictionaryStorage.UserDictionaryCommand.newBuilder()
            .setType(ProtoUserDictionaryStorage.UserDictionaryCommand.CommandType.CREATE_SESSION)
            .build()
    )

    if(!sessionResult.hasSessionId())
        throw Exception("Failed to create mozc session")

    val sessionId = sessionResult.sessionId
    try {
        executor.sendUserDictionaryCommand(
            ProtoUserDictionaryStorage.UserDictionaryCommand.newBuilder()
                .setType(ProtoUserDictionaryStorage.UserDictionaryCommand.CommandType.LOAD)
                .setSessionId(sessionId)
                .build()
        ).let {
            if (it.status != MozcStatus.USER_DICTIONARY_COMMAND_SUCCESS)
                Log.e("MozcDic", "Loading failed! This is not fatal, we can re-make the user dictionary.")
        }

        val enumerateDictionariesResult = executor.sendUserDictionaryCommand(
            ProtoUserDictionaryStorage.UserDictionaryCommand.newBuilder()
                .setType(ProtoUserDictionaryStorage.UserDictionaryCommand.CommandType.GET_USER_DICTIONARY_NAME_LIST)
                .setSessionId(sessionId)
                .build()
        )

        if(enumerateDictionariesResult.status != MozcStatus.USER_DICTIONARY_COMMAND_SUCCESS || !enumerateDictionariesResult.hasStorage())
            throw Exception("Listing dictionaries failed!")

        val dicts = enumerateDictionariesResult.storage.dictionariesList
        if(BuildConfig.DEBUG) Log.d("MozcDic", "Dictionary enumeration: ${dicts.size} entries, data: ${dicts.map { it.id to it.name }}")

        val extraDictionaries = mutableListOf<ProtoUserDictionaryStorage.UserDictionary>()
        dicts.forEach { mozcDict ->
            val matchesExpected = missingDictionaries.removeAll { dict ->
                mozcDict.name == dict.first.nameWithoutExtension
            }

            if(!matchesExpected) {
                extraDictionaries.add(mozcDict)
            }
        }

        // Delete extra (no longer necessary) dictionaries
        // (this will also delete $MOZC_DICT_NAME)
        extraDictionaries.forEach {
            if(BuildConfig.DEBUG) Log.d("MozcDic", "Delete dict ${it.name} ${it.id}")
            executor.sendUserDictionaryCommand(
                ProtoUserDictionaryStorage.UserDictionaryCommand.newBuilder()
                    .setType(ProtoUserDictionaryStorage.UserDictionaryCommand.CommandType.DELETE_DICTIONARY)
                    .setDictionaryId(it.id)
                    .setSessionId(sessionId)
                    .build()
            ).let {
                if (it.status != MozcStatus.USER_DICTIONARY_COMMAND_SUCCESS)
                    throw Exception("Deleting dictionary failed!")
            }
        }

        // Add missing dictionaries
        missingDictionaries.forEach {
            if(BuildConfig.DEBUG) Log.d("MozcDic", "Create dict ${it.first.nameWithoutExtension}")

            executor.sendUserDictionaryCommand(
                ProtoUserDictionaryStorage.UserDictionaryCommand.newBuilder()
                    .setType(ProtoUserDictionaryStorage.UserDictionaryCommand.CommandType.IMPORT_DATA)
                    .setData(it.first.inputStream().use { it.bufferedReader().readText() } )
                    .setDictionaryName(it.first.nameWithoutExtension)
                    .setSessionId(sessionId)
                    .setIgnoreInvalidEntries(true)
                    .build()
            ).let {
                if (it.status != MozcStatus.USER_DICTIONARY_COMMAND_SUCCESS)
                    throw Exception("Importing data failed! [${it.status}]")
            }
        }

        // Create new user dict
        val result = executor.sendUserDictionaryCommand(
            ProtoUserDictionaryStorage.UserDictionaryCommand.newBuilder()
                .setType(ProtoUserDictionaryStorage.UserDictionaryCommand.CommandType.CREATE_DICTIONARY)
                .setDictionaryName(MOZC_DICT_NAME)
                .setSessionId(sessionId)
                .build()
        )
        result.let {
            if (it.status != MozcStatus.USER_DICTIONARY_COMMAND_SUCCESS || !it.hasDictionaryId())
                throw Exception("Creating FUTO_UserDict failed!")
        }
        val dictId = result.dictionaryId

        // Add every word from personal dictionary
        val words = userDictionaryIO.get()
            .filter { it.locale?.let { localeFromString(it) }?.language == "ja" }
            .mapNotNull { decodeJapanesePersonalWord(it) }

        words.forEach {
            executor.sendUserDictionaryCommand(
                ProtoUserDictionaryStorage.UserDictionaryCommand.newBuilder()
                    .setType(ProtoUserDictionaryStorage.UserDictionaryCommand.CommandType.ADD_ENTRY)
                    .setEntry(
                        ProtoUserDictionaryStorage.UserDictionary.Entry.newBuilder()
                            .setKey(it.furigana)
                            .setValue(it.output)
                            .setPos(it.pos.id)
                            .build()
                    )
                    .setDictionaryId(dictId)
                    .setSessionId(sessionId)
                    .build()
            ).let {
                if (it.status != MozcStatus.USER_DICTIONARY_COMMAND_SUCCESS)
                    throw Exception("Adding word failed!")
            }
        }

        executor.sendUserDictionaryCommand(
            ProtoUserDictionaryStorage.UserDictionaryCommand.newBuilder()
                .setType(ProtoUserDictionaryStorage.UserDictionaryCommand.CommandType.SAVE)
                .setSessionId(sessionId)
                .build()
        ).let {
            if (it.status != MozcStatus.USER_DICTIONARY_COMMAND_SUCCESS)
                throw Exception("Saving user dict failed! [${it.status}]")
        }
    } finally {
        executor.sendUserDictionaryCommand(
            ProtoUserDictionaryStorage.UserDictionaryCommand.newBuilder()
                .setType(ProtoUserDictionaryStorage.UserDictionaryCommand.CommandType.DELETE_SESSION)
                .setSessionId(sessionId)
                .build()
        )
    }
}

private const val SUGGESTION_ID_INVERSION = 10000
private const val TAG = "JapaneseIME"
class JapaneseIME(val helper: IMEHelper) : IMEInterface {
    companion object { init { MozcLog.forceLoggable = BuildConfig.DEBUG }}

    val selectionTracker = SelectionTracker()
    lateinit var executor: SessionExecutor

    var layoutHint: String? = null
    var configId: KeyboardId? = null
    private fun updateConfig(resetSelectionTracker: Boolean = true) {
        val settings = Settings.getInstance().current
        val useFlickOnly = helper.context.getSetting(JapaneseIMESettings.FlickOnly)
        val halfWidthOnly = helper.context.getSetting(JapaneseIMESettings.HalfWidthSpace)
        val id = helper.keyboardSwitcher.keyboard?.mId
        configId = id

        executor.config = ProtoConfig.Config.newBuilder().apply {
            sessionKeymap = ProtoConfig.Config.SessionKeymap.MOBILE
            selectionShortcut = ProtoConfig.Config.SelectionShortcut.NO_SHORTCUT
            useEmojiConversion = true

            spaceCharacterForm = when {
                halfWidthOnly -> ProtoConfig.Config.FundamentalCharacterForm.FUNDAMENTAL_HALF_WIDTH
                else -> ProtoConfig.Config.FundamentalCharacterForm.FUNDAMENTAL_INPUT_MODE
            }
            useKanaModifierInsensitiveConversion = true
            useTypingCorrection = true

            yenSignCharacter = ProtoConfig.Config.YenSignCharacter.YEN_SIGN

            historyLearningLevel = when {
                settings.mInputAttributes.mNoLearning || !settings.isPersonalizationEnabled ->
                    ProtoConfig.Config.HistoryLearningLevel.READ_ONLY

                //BuildConfig.DEBUG ->
                //    ProtoConfig.Config.HistoryLearningLevel.READ_ONLY

                else ->
                    ProtoConfig.Config.HistoryLearningLevel.DEFAULT_HISTORY
            }
            incognitoMode = false
            generalConfig = ProtoConfig.GeneralConfig.newBuilder().apply {
                uploadUsageStats = false
            }.build()
        }.build()

        val keyboardSpecification = when {
            id?.mElement?.kind == KeyboardLayoutKind.Symbols ||
            id?.mElement?.kind == KeyboardLayoutKind.Number ||
            id?.mElement?.kind == KeyboardLayoutKind.NumberBasic ||
            id?.mElement?.kind == KeyboardLayoutKind.Phone ->
                Keyboard.KeyboardSpecification.SYMBOL_NUMBER

            id?.mElement?.kind == KeyboardLayoutKind.Alphabet0 && layoutHint == "qwerty" ->
                Keyboard.KeyboardSpecification.QWERTY_KANA

            id?.mElement?.kind == KeyboardLayoutKind.Alphabet1 && layoutHint == "qwerty" ->
                Keyboard.KeyboardSpecification.QWERTY_ALPHABET

            id?.mElement?.kind == KeyboardLayoutKind.Alphabet1 ||
            id?.mElement?.kind == KeyboardLayoutKind.Alphabet2 ||
            id?.mElement?.kind == KeyboardLayoutKind.Alphabet3 ->
                if(!useFlickOnly)
                    Keyboard.KeyboardSpecification.TWELVE_KEY_TOGGLE_FLICK_ALPHABET
                else
                    Keyboard.KeyboardSpecification.TWELVE_KEY_FLICK_ALPHABET

            useFlickOnly -> Keyboard.KeyboardSpecification.TWELVE_KEY_FLICK_KANA
            else -> Keyboard.KeyboardSpecification.TWELVE_KEY_TOGGLE_FLICK_KANA
        }

        val keyboardRequest = MozcUtil.getRequestBuilder(keyboardSpecification, helper.context.resources.configuration, 3).build()
        executor.updateRequest(
            keyboardRequest,
            emptyList()
        )
        executor.switchInputMode(
            Optional.of(KeycodeConverter.getKeyEventInterface(0)),
            keyboardSpecification.compositionMode,
            resettableEvaluationCallback
        )

        if(resetSelectionTracker) selectionTracker.onConfigurationChanged()
    }

    internal fun mozcInit() {
        initJniDictLocations(helper.context)

        if(!::executor.isInitialized) {
            executor = SessionExecutor.getInstanceInitializedIfNecessary(
                SessionHandlerFactory(Optional.absent()),
                helper.context
            )
        } else {
            executor.reset(
                SessionHandlerFactory(Optional.absent()),
                helper.context
            )
        }

        executor.setLogging(BuildConfig.DEBUG == true)

        updateConfig()

        if(helper.context.isDirectBootUnlocked) {
            executor.syncData()
            refreshMozcDictionaries(helper.context, executor)
        }
    }

    override fun onCreate() {
        mozcInit()
        helper.lifecycleScope.launch {
            GlobalIMEMessage.collect { message ->
                when(message) {
                    IMEMessage.ReloadResources -> withContext(Dispatchers.Main) {
                        mozcInit()
                    }
                    IMEMessage.ReloadPersonalDict -> withContext(Dispatchers.Main) {
                        refreshMozcDictionaries(helper.context, executor)
                    }
                    else -> {}
                }
            }
        }
    }

    override fun onDestroy() {
        executor.syncData()
        // Might need to edit mozc library to add a reset function not just for tests
        // This is necessary because reinit JNI after onDestroy -> onCreate and the old
        // session executor is no longer valid
        @SuppressLint("VisibleForTests")
        SessionExecutor.setInstanceForTest(Optional.absent())
    }

    override fun onDeviceUnlocked() {
        mozcInit()
    }

    // Similar to RichInputConnection.tryExtractCursorPosition
    private fun initSelectionTracker() {
        var selStart = -1
        var selEnd = -1

        helper.getCurrentInputConnection()?.let { ic ->
            ic.getExtractedText(ExtractedTextRequest().apply {
                flags = 0
                token = 1
                hintMaxLines = 1
                hintMaxChars = 512
            }, 0)?.let { t ->
                selStart = t.selectionStart + t.startOffset
                selEnd = t.selectionEnd + t.startOffset
            }
        }

        helper.getCurrentEditorInfo()?.let { ei ->
            if(selStart < 0 || selEnd < 0) {
                selStart = ei.initialSelStart
                selEnd = ei.initialSelEnd
            }
        }

        // Any non -1 negative value is invalid, safeguard against it
        if(selStart < 0 || selEnd < 0) {
            selStart = -1
            selEnd = -1
        }

        selectionTracker.onStartInput(selStart, selEnd, false)
    }

    override fun onStartInput() {
        executor.removePendingEvaluations()
        executor.resetContext()
        setNeutralSuggestionStrip()
        updateConfig()
        initSelectionTracker()

        helper.getCurrentEditorInfo()?.let { executor.switchInputFieldType(getInputFieldType(it)) }
    }

    override fun onOrientationChanged() {
        updateConfig()
    }

    override fun onFinishInput() {
        executor.syncData()
        selectionTracker.onFinishInput()
        resetContextAndCommitText()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        composingSpanStart: Int,
        composingSpanEnd: Int
    ) {
        val updateStatus = selectionTracker.onUpdateSelection(
            oldSelStart, oldSelEnd,
            newSelStart, newSelEnd,
            composingSpanStart, composingSpanEnd,
            false
        )

        if(BuildConfig.DEBUG) Log.d(TAG, "Update selection from $oldSelStart:$oldSelEnd to $newSelStart:$newSelEnd [$composingSpanStart:$composingSpanEnd]. Status $updateStatus")

        when(updateStatus) {
            SelectionTracker.DO_NOTHING -> {}
            SelectionTracker.RESET_CONTEXT -> {
                if(BuildConfig.DEBUG) Log.d(TAG, "Asked to reset context.")
                executor.resetContext()
                helper.getCurrentInputConnection()?.finishComposingText()
                setNeutralSuggestionStrip()
            }

            else -> {
                if(updateStatus < 0) throw IllegalStateException("Invalid updateStatus $updateStatus")
                executor.moveCursor(updateStatus, evaluationCallback)
            }
        }
    }

    override fun isGestureHandlingAvailable(): Boolean {
        return false
    }

    private fun createKeyEvent(
        original: KeyEvent,
        eventTime: Long,
        action: Int,
        repeatCount: Int,
    ): KeyEvent {
        return KeyEvent(
            original.downTime,
            eventTime,
            action,
            original.keyCode,
            repeatCount,
            original.metaState,
            original.deviceId,
            original.scanCode,
            original.flags,
        )
    }

    private fun maybeProcessDelete(keyCode: Int): Boolean {
        if(keyCode != Constants.CODE_DELETE) return false

        // If we have a selection, send DEL event to delete the selection
        if(selectionTracker.lastSelectionEnd != selectionTracker.lastSelectionStart) {
            sendDownUpKeyEvent(
                KeyEvent.KEYCODE_DEL,
                0
            )
        } else {
            val text = helper.getCurrentInputConnection()?.getTextBeforeCursor(8, 0).toString()
            val bi = BreakIterator.getCharacterInstance()

            bi.setText(text)
            val end = bi.last()
            val prev = bi.previous()

            if(prev == -1 || end == -1) {
                helper.getCurrentInputConnection()?.deleteSurroundingText(1, 0)
            } else {
                helper.getCurrentInputConnection()?.deleteSurroundingText(end - prev, 0)
            }
        }
        return true
    }

    private fun resetContextAndCommitText(text: CharSequence? = null) {
        executor.resetContext()

        val ic = helper.getCurrentInputConnection() ?: return
        ic.finishComposingText()

        if(text != null) ic.commitText(text, 1)
    }

    private fun sendCodePoint(codePoint: Int): Unit = when(codePoint) {
        Constants.CODE_ENTER -> {
            // TODO: Code duplication between here and InputLogic.handleNonFunctionalEvent
            val ei = helper.getCurrentEditorInfo() ?: return
            val ic = helper.getCurrentInputConnection() ?: return
            val imeOptionsActionId = InputTypeUtils.getImeOptionsActionIdFromEditorInfo(ei)

            val isCustomAction =
                InputTypeUtils.IME_ACTION_CUSTOM_LABEL == imeOptionsActionId
            val isEditorAction =
                EditorInfo.IME_ACTION_NONE != imeOptionsActionId

            if(isCustomAction) {
                ic.performEditorAction(ei.actionId)
            } else if(isEditorAction) {
                ic.performEditorAction(imeOptionsActionId)
            } else {
                resetContextAndCommitText(StringUtils.newSingleCodePointString(Constants.CODE_ENTER))
            }

            Unit
        }

        else -> {
            resetContextAndCommitText(StringUtils.newSingleCodePointString(codePoint))
        }
    }

    /** Sends the `KeyEvent`, which is not consumed by the mozc server. */
    private fun sendKeyEvent(keyEvent: KeyEventInterface?) {
        if (keyEvent == null) {
            return
        }
        val keyCode = keyEvent.keyCode
        // Some keys have a potential to be consumed from mozc client.
        if (maybeProcessDelete(keyCode)) {
            // The key event is consumed.
            return
        }

        if(maybeHandleAction(keyCode)) return

        // Following code is to fallback to target activity.
        val nativeKeyEvent = keyEvent.nativeEvent
        val inputConnection = helper.getCurrentInputConnection()
        if (nativeKeyEvent.isPresent && inputConnection != null) {
            // Meta keys are from this.onKeyDown/Up so fallback each time.
            if (KeycodeConverter.isMetaKey(nativeKeyEvent.get())) {
                inputConnection.sendKeyEvent(
                    createKeyEvent(
                        nativeKeyEvent.get(),
                        SystemClock.uptimeMillis(),
                        nativeKeyEvent.get().action,
                        nativeKeyEvent.get().repeatCount,
                    )
                )
                return
            }

            // Other keys are from this.onKeyDown so create dummy Down/Up events.
            inputConnection.sendKeyEvent(
                createKeyEvent(nativeKeyEvent.get(), SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, 0)
            )
            inputConnection.sendKeyEvent(
                createKeyEvent(nativeKeyEvent.get(), SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, 0)
            )
            return
        }

        // Otherwise, just delegates the key event to the connected application.
        // However space key needs special treatment because it is expected to produce space character
        // instead of sending ACTION_DOWN/UP pair.
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            inputConnection?.commitText(" ", 0)
        } else {
            sendDownUpKeyEvent(keyCode, 0)
        }
    }

    // TODO: Code duplication between InputLogic and here
    fun sendDownUpKeyEvent(keyCode: Int, metaState: Int) {
        val eventTime = SystemClock.uptimeMillis()
        helper.getCurrentInputConnection()?.sendKeyEvent(
            KeyEvent(
                eventTime, eventTime,
                KeyEvent.ACTION_DOWN, keyCode, 0, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
            )
        )
        helper.getCurrentInputConnection()?.sendKeyEvent(
            KeyEvent(
                SystemClock.uptimeMillis(), eventTime,
                KeyEvent.ACTION_UP, keyCode, 0, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
            )
        )
    }


    private fun maybeDeleteSurroundingText(output: ProtoCommands.Output, inputConnection: InputConnection) {
        if (!output.hasDeletionRange()) {
            return
        }
        val range = output.deletionRange
        val leftRange = -range.offset
        val rightRange = range.length - leftRange
        if (leftRange < 0 || rightRange < 0) {
            // If the range does not include the current position, do nothing
            // because Android's API does not expect such situation.
            Log.w(TAG, "Deletion range has unsupported parameters: $range")
            return
        }
        if (!inputConnection.deleteSurroundingText(leftRange, rightRange)) {
            Log.e(TAG, "Failed to delete surrounding text.")
        }
    }

    private fun maybeCommitText(output: ProtoCommands.Output, inputConnection: InputConnection) {
        if (!output.hasResult()) {
            return
        }
        val outputText = output.result.value
        if (outputText == "") {
            // Do nothing for an empty result string.
            return
        }
        var position = MozcUtil.CURSOR_POSITION_TAIL
        if (output.result.hasCursorOffset() && output.result.cursorOffset != 0) {
            if (output.result.cursorOffset == -outputText.codePointCount(0, outputText.length)) {
                position = MozcUtil.CURSOR_POSITION_HEAD
            } else {
                if(BuildConfig.DEBUG) Log.e(TAG, "Unsupported position: " + output.result.toString())
            }
        }
        if (!inputConnection.commitText(outputText, position)) {
            Log.e(TAG, "Failed to commit text.")
        }
    }

    private var hasPreedit = false
    internal fun renderInputConnection(command: ProtoCommands.Command, keyEvent: KeyEventInterface?) {
        val inputConnection = helper.getCurrentInputConnection() ?: return
        val output = command.output
        if(!output.hasConsumed() || !output.consumed) {
            maybeCommitText(output, inputConnection)

            if(keyEvent?.nativeEvent?.isPresent == true) {
                sendKeyEvent(keyEvent)
            } else if(keyEvent?.keyCode != null) {
                sendCodePoint(keyEvent.keyCode)
            }
            return
        }

        // Meta key may invoke a command for Mozc server like SWITCH_INPUT_MODE session command. In this
        // case, the command is consumed by Mozc server and the application cannot get the key event.
        // To avoid such situation, we should send the key event back to application. b/13238551
        // The command itself is consumed by Mozc server, so we should NOT put a return statement here.
        if (
            keyEvent != null &&
            keyEvent.nativeEvent.isPresent &&
            KeycodeConverter.isMetaKey(keyEvent.nativeEvent.get())
        ) {
            sendKeyEvent(keyEvent)
        }

        // Here the key is consumed by the Mozc server.
        inputConnection.beginBatchEdit()
        try {
            maybeDeleteSurroundingText(output, inputConnection)
            maybeCommitText(output, inputConnection)
            setComposingText(command, inputConnection)
            maybeSetSelection(output, inputConnection)
            selectionTracker.onRender(
                if (output.hasDeletionRange()) output.deletionRange else null,
                if (output.hasResult()) output.result.value else null,
                if (output.hasPreedit()) output.preedit else null,
            )
            if(BuildConfig.DEBUG) Log.d(TAG, "onRender selectionTracker ${if (output.hasDeletionRange()) output.deletionRange else null}, ${if (output.hasResult()) output.result.value else null}, ${if (output.hasPreedit()) output.preedit else null}")
        } finally {
            inputConnection.endBatchEdit()
        }

        hasPreedit = output.hasPreedit()
        helper.keyboardSwitcher.requestUpdatingShiftState(getCurrentAutoCapsState())
    }

    // We use the "shift" alphabet state to show the language switch key
    override fun getCurrentAutoCapsState() = when {
        !hasPreedit && layoutHint == "12key" -> TextUtils.CAP_MODE_CHARACTERS
        else -> Constants.TextUtils.CAP_MODE_OFF
    }

    private fun setComposingText(command: ProtoCommands.Command, inputConnection: InputConnection) {
        val output = command.output

        helper.updateUiInputState(!output.hasPreedit())

        if (!output.hasPreedit()) {
            // If preedit field is empty, we should clear composing text in the InputConnection
            // because Mozc server asks us to do so.
            // But there is special situation in Android.
            // On onWindowShown, SWITCH_INPUT_MODE command is sent as a step of initialization.
            // In this case we reach here with empty preedit.
            // As described above we should clear the composing text but if we do so
            // texts in selection range (e.g., URL in OmniBox) is always cleared.
            // To avoid from this issue, we don't clear the composing text if the input
            // is SWITCH_INPUT_MODE.
            val input = command.input
            if (
                input.type != ProtoCommands.Input.CommandType.SEND_COMMAND ||
                input.command.type != SessionCommand.CommandType.SWITCH_INPUT_MODE
            ) {
                if (!inputConnection.setComposingText("", 0)) {
                    Log.e(TAG, "Failed to set composing text.")
                }
            }
            return
        }

        // Builds preedit expression.
        val preedit = output.preedit
        val builder = SpannableStringBuilder()
        for (segment in preedit.segmentList) {
            builder.append(segment.value)
        }

        // Set underline for all the preedit text.
        builder.setSpan(SPAN_UNDERLINE, 0, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Draw cursor if in composition mode.
        val cursor = preedit.cursor
        val spanFlags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or Spanned.SPAN_COMPOSING
        if (
            output.hasAllCandidateWords() &&
            output.allCandidateWords.hasCategory() &&
            output.allCandidateWords.category == ProtoCandidateWindow.Category.CONVERSION
        ) {
            var offsetInString = 0
            for (segment in preedit.segmentList) {
                val length = segment.value.length
                builder.setSpan(
                    if (segment.hasAnnotation() && segment.annotation == Preedit.Segment.Annotation.HIGHLIGHT)
                        SPAN_CONVERT_HIGHLIGHT
                    else BackgroundColorSpan(CONVERT_NORMAL_COLOR),
                    offsetInString,
                    offsetInString + length,
                    spanFlags,
                )
                offsetInString += length
            }
        } else {
            // We cannot show system cursor inside preedit here.
            // Instead we change text style before the preedit's cursor.
            val cursorOffsetInString = builder.toString().offsetByCodePoints(0, cursor)
            if (cursor != builder.length) {
                builder.setSpan(
                    SPAN_PARTIAL_SUGGESTION_COLOR,
                    cursorOffsetInString,
                    builder.length,
                    spanFlags,
                )
            }
            if (cursor > 0) {
                builder.setSpan(SPAN_BEFORE_CURSOR, 0, cursorOffsetInString, spanFlags)
            }
        }

        // System cursor will be moved to the tail of preedit.
        // It triggers onUpdateSelection again.
        val cursorPosition = if (cursor > 0) MozcUtil.CURSOR_POSITION_TAIL else 0
        if (!inputConnection.setComposingText(builder, cursorPosition)) {
            Log.e(TAG, "Failed to set composing text.")
        }
    }

    private fun getPreeditLength(preedit: Preedit): Int {
        var result = 0
        for (i in 0 until preedit.segmentCount) {
            result += preedit.getSegment(i).valueLength
        }
        return result
    }

    private fun maybeSetSelection(output: ProtoCommands.Output, inputConnection: InputConnection) {
        if (!output.hasPreedit()) {
            return
        }
        val preedit = output.preedit
        val cursor = preedit.cursor
        if (cursor == 0 || cursor == getPreeditLength(preedit)) {
            // The cursor is at the beginning/ending of the preedit. So we don't anything about the
            // caret setting.
            return
        }
        var caretPosition = selectionTracker.preeditStartPosition
        if (output.hasDeletionRange()) {
            caretPosition += output.deletionRange.offset
        }
        if (output.hasResult()) {
            caretPosition += output.result.value.length
        }
        if (output.hasPreedit()) {
            caretPosition += output.preedit.cursor
        }
        if (!inputConnection.setSelection(caretPosition, caretPosition)) {
            Log.e(TAG, "Failed to set selection.")
        }
    }

    // If switch input mode fails for whatever reason, we'll try resetting mozc
    private val resettableEvaluationCallback = object : SessionExecutor.EvaluationCallback {
        override fun onCompleted(
            command: Optional<ProtoCommands.Command>,
            triggeringKeyEvent: Optional<KeyEventInterface>
        ) {
            if(!command.isPresent) return
            val output = command.get().output
            if(output.hasErrorCode() && output.errorCode == ProtoCommands.Output.ErrorCode.SESSION_FAILURE) {
                mozcInit()
            } else {
                evaluationCallback.onCompleted(command, triggeringKeyEvent)
            }
        }
    }

    private val evaluationCallback = object : SessionExecutor.EvaluationCallback {
        override fun onCompleted(
            command: Optional<ProtoCommands.Command>,
            triggeringKeyEvent: Optional<KeyEventInterface>
        ) {
            if(!command.isPresent) return

            renderInputConnection(command.get(), triggeringKeyEvent.orNull())

            val output = command.get().output

            if(output.allCandidateWords.candidatesCount == 0) {
                setNeutralSuggestionStrip()
            } else {
                val candidateList = output.allCandidateWords.candidatesList
                val selectedIndex = if(output.hasCandidateWindow()) {
                    if(output.candidateWindow.hasFocusedIndex()) {
                        output.candidateWindow.focusedIndex
                    } else {
                        null
                    }
                } else {
                    null
                }
                val suggestedWordList = candidateList.map {
                    SuggestedWordInfo(
                        it.value,
                        "",
                        SUGGESTION_ID_INVERSION - it.index,
                        1,
                        null,
                        SuggestedWordInfo.NOT_AN_INDEX,
                        SuggestedWordInfo.NOT_A_CONFIDENCE,
                        it.id,
                        if(it.hasAnnotation() && it.annotation.hasDescription()) {
                            it.annotation.description
                        } else { null }
                    )
                }.let { ArrayList(it) }

                showSuggestionStrip(SuggestedWords(
                        suggestedWordList,
                        suggestedWordList,
                        null,
                        true,
                        true,
                        false,
                        0,
                        0,
                        selectedIndex
                    )
                )
            }
        }

    }

    // TODO: This rough code pattern appears 3 times in the codebase, probably time to make a util function
    private fun maybeHandleAction(keyCode: Int): Boolean {
        if (keyCode <= Constants.CODE_ACTION_MAX && keyCode >= Constants.CODE_ACTION_0) {
            val actionId: Int = keyCode - Constants.CODE_ACTION_0
            helper.triggerAction(actionId, false)
            return true
        }

        if (keyCode <= Constants.CODE_ALT_ACTION_MAX && keyCode >= Constants.CODE_ALT_ACTION_0) {
            val actionId: Int = keyCode - Constants.CODE_ALT_ACTION_0
            helper.triggerAction(actionId, true)
            return true
        }

        return false
    }

    override fun onEvent(event: Event) {
        helper.requestCursorUpdate()
        when (event.eventType) {
            Event.EVENT_TYPE_INPUT_KEYPRESS,
            Event.EVENT_TYPE_INPUT_KEYPRESS_RESUMED -> {
                if(helper.keyboardSwitcher.keyboard?.mId != configId) updateConfig(false)

                val triggeringKeyEvent = if (event.mKeyCode != Event.NOT_A_KEY_CODE) {
                    KeycodeConverter.getKeyEventInterface(
                        KeyEvent(
                            System.currentTimeMillis(),
                            System.currentTimeMillis(),
                            KeyEvent.ACTION_DOWN,
                            event.mKeyCode,
                            0,
                            0,
                            KeyCharacterMap.VIRTUAL_KEYBOARD,
                            0,
                            KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
                        )
                    )
                } else {
                    KeycodeConverter.getKeyEventInterface(event.mCodePoint)
                }

                val mozcEvent = when (event.mCodePoint) {
                    Constants.CODE_SPACE -> KeycodeConverter.SPECIALKEY_SPACE
                    Constants.CODE_ENTER -> KeycodeConverter.SPECIALKEY_VIRTUAL_ENTER
                    Constants.NOT_A_CODE -> when (event.mKeyCode) {
                        Constants.CODE_SWITCH_ALPHA_SYMBOL -> null
                        Constants.CODE_DELETE -> KeycodeConverter.SPECIALKEY_BACKSPACE
                        ArrowLeftAction.keyCode -> KeycodeConverter.SPECIALKEY_VIRTUAL_LEFT
                        ArrowRightAction.keyCode -> KeycodeConverter.SPECIALKEY_VIRTUAL_RIGHT
                        ArrowUpAction.keyCode -> KeycodeConverter.SPECIALKEY_UP
                        ArrowDownAction.keyCode -> KeycodeConverter.SPECIALKEY_DOWN
                        UndoAction.keyCode -> {
                            // TODO: Should probably update mozc-lib to pass key event here explicitly instead of doing it like this
                            executor.undoOrRewind(emptyList(), { command, _ ->
                                evaluationCallback.onCompleted(command, Optional.of(triggeringKeyEvent))
                            })
                            return
                        }

                        else -> {
                            if(!maybeHandleAction(event.mKeyCode)) {
                                if(BuildConfig.DEBUG) Log.e(TAG, "Unknown keycode for that event (${event.mCodePoint} ${event.mKeyCode})")
                            }
                            null
                        }
                    }

                    else -> getMozcKeyEvent(event.mCodePoint)
                }

                val touchEvents = emptyList<ProtoCommands.Input.TouchEvent>()

                if(mozcEvent != null) {
                    executor.sendKey(
                        mozcEvent,
                        triggeringKeyEvent,
                        touchEvents,
                        evaluationCallback
                    )
                }
            }

            Event.EVENT_TYPE_SUGGESTION_PICKED -> {
                val suggestion = event.mSuggestedWordInfo ?: return
                val mozcId = suggestion.mCandidateIndex
                val rowIdx = SUGGESTION_ID_INVERSION - suggestion.mScore
                executor.submitCandidate(mozcId, Optional.of(rowIdx), evaluationCallback)
            }

            Event.EVENT_TYPE_SOFTWARE_GENERATED_STRING -> {
                resetContextAndCommitText(event.mText)
            }

            Event.EVENT_TYPE_DOWN_UP_KEYEVENT -> {
                // TODO: Should we be calling this here just to be sure?
                resetContextAndCommitText()

                sendDownUpKeyEvent(event.mKeyCode, event.mX)
            }

            else -> {
                if(BuildConfig.DEBUG) Log.e(TAG, "Unhandled event type ${event.eventType}: $event")
            }
        }
    }

    override fun onStartBatchInput() {

    }

    override fun onUpdateBatchInput(batchPointers: InputPointers?) {

    }

    override fun onEndBatchInput(batchPointers: InputPointers?) {

    }

    override fun onCancelBatchInput() {

    }

    override fun onCancelInput() {

    }

    override fun onFinishSlidingInput() {

    }

    override fun onCustomRequest(requestCode: Int): Boolean {
        return false
    }

    override fun onMovePointer(steps: Int, stepOverWords: Boolean, select: Boolean?) {

    }

    override fun onMoveDeletePointer(steps: Int) {

    }

    override fun onUpWithDeletePointerActive() {

    }

    override fun onUpWithPointerActive() {

    }

    override fun onSwipeLanguage(direction: Int) {

    }

    override fun onMovingCursorLockEvent(canMoveCursor: Boolean) {

    }

    override fun clearUserHistoryDictionaries() {
        if(helper.context.isDirectBootUnlocked) {
            executor.clearUserHistory()
            executor.clearUserPrediction()
        }
    }

    private var prevSuggestions: SuggestedWords? = null
    override fun requestSuggestionRefresh() {
        if(prevSuggestions != null) showSuggestionStrip(prevSuggestions)
    }

    override fun onLayoutUpdated(layout: KeyboardLayoutSetV2) {
        layoutHint = layout.mainLayout.imeHint
        updateConfig()
    }

    private val useExpandableUi = true
    fun setNeutralSuggestionStrip() {
        prevSuggestions = null
        helper.setNeutralSuggestionStrip(useExpandableUi)
    }

    fun showSuggestionStrip(suggestedWords: SuggestedWords?) {
        prevSuggestions = suggestedWords
        helper.showSuggestionStrip(suggestedWords, useExpandableUi)
    }
}