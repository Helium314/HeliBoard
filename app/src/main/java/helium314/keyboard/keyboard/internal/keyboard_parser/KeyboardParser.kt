// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.internal.keyboard_parser

import android.content.Context
import android.content.res.Configuration
import helium314.keyboard.latin.utils.Log
import android.view.inputmethod.EditorInfo
import androidx.annotation.StringRes
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.Key.KeyParams
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyData
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyLabel
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyType
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.SimplePopups
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.TextKeyData
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.common.isEmoji
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.spellcheck.AndroidSpellCheckerService
import helium314.keyboard.latin.utils.InputTypeUtils
import helium314.keyboard.latin.utils.POPUP_KEYS_LAYOUT
import helium314.keyboard.latin.utils.POPUP_KEYS_NUMBER
import helium314.keyboard.latin.utils.ScriptUtils
import helium314.keyboard.latin.utils.ScriptUtils.script
import helium314.keyboard.latin.utils.removeFirst
import helium314.keyboard.latin.utils.replaceFirst
import helium314.keyboard.latin.utils.runInLocale
import helium314.keyboard.latin.utils.splitAt
import helium314.keyboard.latin.utils.sumOf

/**
 * Abstract parser class that handles creation of keyboard from [KeyData] arranged in rows,
 * provided by the extending class.
 *
 * Functional keys are pre-defined and can't be changed, with exception of comma, period and similar
 * keys in symbol layouts.
 * By default, all normal keys have the same width and flags, which may cause issues with the
 * requirements of certain non-latin languages.
 */
class KeyboardParser(private val params: KeyboardParams, private val context: Context) {
    private val infos = layoutInfos(params)
    private val defaultLabelFlags = when {
        params.mId.isAlphabetKeyboard -> params.mLocaleKeyboardInfos.labelFlags
        // reproduce the no-hints in symbol layouts
        // todo: add setting? or put it in TextKeyData to happen only if no label flags specified explicitly?
        params.mId.isAlphaOrSymbolKeyboard -> Key.LABEL_FLAGS_DISABLE_HINT_LABEL
        else -> 0
    }

    fun parseLayout(): ArrayList<ArrayList<KeyParams>> {
        params.readAttributes(context, null)
        params.mProximityCharsCorrectionEnabled = infos.enableProximityCharsCorrection
        if (infos.touchPositionCorrectionData == null) // need to set correctly, as it's not properly done in readAttributes with attr = null
            params.mTouchPositionCorrection.load(emptyArray())
        else
            params.mTouchPositionCorrection.load(context.resources.getStringArray(infos.touchPositionCorrectionData))

        val baseKeys = RawKeyboardParser.parseLayout(params, context)
        val keysInRows = createRows(baseKeys)
        // rescale height if we have anything but the usual 4 rows
        val heightRescale = if (keysInRows.size != 4) 4f / keysInRows.size else 1f
        if (heightRescale != 1f) {
            keysInRows.forEach { row -> row.forEach { it.mHeight *= heightRescale } }
        }

        return keysInRows
    }

    private fun createRows(baseKeys: MutableList<MutableList<KeyData>>): ArrayList<ArrayList<KeyParams>> {
        // add padding for number layouts in landscape mode (maybe do it some other way later)
        if (params.mId.isNumberLayout && params.mId.mElementId != KeyboardId.ELEMENT_NUMPAD
                && context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            params.mLeftPadding = (params.mOccupiedWidth * 0.1f).toInt()
            params.mRightPadding = (params.mOccupiedWidth * 0.1f).toInt()
            params.mBaseWidth = params.mOccupiedWidth - params.mLeftPadding - params.mRightPadding
        }

        addNumberRowOrPopupKeys(baseKeys)
        if (params.mId.isAlphabetKeyboard)
            addSymbolPopupKeys(baseKeys)
        if (params.mId.isAlphaOrSymbolKeyboard && params.mId.mNumberRowEnabled)
            baseKeys.add(0, params.mLocaleKeyboardInfos.getNumberRow()
                .mapTo(mutableListOf()) { it.copy(newLabelFlags = Key.LABEL_FLAGS_DISABLE_HINT_LABEL or defaultLabelFlags) })

        val allFunctionalKeys = RawKeyboardParser.parseLayout(params, context, true)
        adjustBottomFunctionalRowAndBaseKeys(allFunctionalKeys, baseKeys)

        if (allFunctionalKeys.none { it.singleOrNull()?.isKeyPlaceholder() == true })
            // add a placeholder so splitAt does what we really want
            allFunctionalKeys.add(0, mutableListOf(TextKeyData(type = KeyType.PLACEHOLDER)))

        val (functionalKeysTop, functionalKeysBottom) = allFunctionalKeys.splitAt { it.singleOrNull()?.isKeyPlaceholder() == true }

        // offset for bottom, relevant for getting correct functional key rows
        val bottomIndexOffset = baseKeys.size - functionalKeysBottom.size

        val functionalKeys = mutableListOf<Pair<List<KeyParams>, List<KeyParams>>>()
        val baseKeyParams = baseKeys.mapIndexed { i, it ->
            val row: List<KeyData> = if (params.mId.isAlphaOrSymbolKeyboard && i == baseKeys.lastIndex - 1 && Settings.getInstance().isTablet) {
                // add bottom row extra keys
                // todo (later): this can make very customized layouts look awkward
                //  decide when to (not) add it
                //  when not adding, consider that punctuation popup keys should not remove those keys!
                val tabletExtraKeys = params.mLocaleKeyboardInfos.getTabletExtraKeys(params.mId.mElementId)
                tabletExtraKeys.first + it + tabletExtraKeys.second
            } else {
                it
            }

            // build list of functional keys of same size as baseKeys
            val functionalKeysFromTop = functionalKeysTop.getOrNull(i) ?: emptyList()
            val functionalKeysFromBottom = functionalKeysBottom.getOrNull(i - bottomIndexOffset) ?: emptyList()
            functionalKeys.add(getFunctionalKeysBySide(functionalKeysFromTop, functionalKeysFromBottom))

            row.mapNotNull { key ->
                val extraFlags = if (key.label.length > 2 && key.label.codePointCount(0, key.label.length) > 2 && !isEmoji(key.label))
                        Key.LABEL_FLAGS_AUTO_X_SCALE
                    else 0
                val keyData = key.processFunctionalKeys() ?: return@mapNotNull null // all keys could actually be functional keys...
                if (DebugFlags.DEBUG_ENABLED)
                    Log.d(TAG, "adding key ${keyData.label}, ${keyData.code}")
                keyData.toKeyParams(params, defaultLabelFlags or extraFlags)
            }
        }
        return setReasonableWidths(baseKeyParams, functionalKeys)
    }

    /** interprets key width -1, adjusts row size to nicely fit on screen, adds spacers if necessary */
    private fun setReasonableWidths(bassKeyParams: List<List<KeyParams>>, functionalKeys: List<Pair<List<KeyParams>, List<KeyParams>>>): ArrayList<ArrayList<KeyParams>> {
        val keysInRows = ArrayList<ArrayList<KeyParams>>()
        // expand width = -1 keys and make sure rows fit on screen, insert spacers if necessary
        bassKeyParams.forEachIndexed { i, keys ->
            val (functionalKeysLeft, functionalKeysRight) = functionalKeys[i]
            // sum up width, excluding -1 elements (put those in a separate list)
            val varWidthKeys = mutableListOf<KeyParams>()
            var totalWidth = 0f
            val allKeys = (functionalKeysLeft + keys + functionalKeysRight)
            allKeys.forEach {
                if (it.mWidth == -1f) varWidthKeys.add(it)
                else totalWidth += it.mWidth
            }

            // set width for varWidthKeys
            if (varWidthKeys.isNotEmpty()) {
                val width = if (totalWidth + varWidthKeys.size * params.mDefaultKeyWidth > 1)
                    params.mDefaultKeyWidth // never go below default width
                else (1f - totalWidth) / varWidthKeys.size // split remaining space evenly
                varWidthKeys.forEach { it.mWidth = width }

                // re-calculate total width
                totalWidth = allKeys.sumOf { it.mWidth }
            }

            // re-scale total width, or add spacers (or do nothing if totalWidth is near 1)
            if (totalWidth < 0.9999f) { // add spacers
                val spacerWidth = (1f - totalWidth) / 2
                val paramsRow = ArrayList<KeyParams>(functionalKeysLeft + KeyParams.newSpacer(params, spacerWidth) + keys +
                        KeyParams.newSpacer(params, spacerWidth) + functionalKeysRight)
                keysInRows.add(paramsRow)
            } else {
                if (totalWidth > 1.0001f) { // re-scale total width
                    val normalKeysWith = keys.sumOf { it.mWidth }
                    val functionalKeysWidth = totalWidth - normalKeysWith
                    val scaleFactor = (1f - functionalKeysWidth) / normalKeysWith
                    // re-scale normal  keys if factor is > 0.82, otherwise re-scale all keys
                    if (scaleFactor > 0.82f) keys.forEach { it.mWidth *= scaleFactor }
                    else allKeys.forEach { it.mWidth /= totalWidth }
                }
                keysInRows.add(ArrayList(allKeys))
            }
        }

        // adjust last normal row key widths to be aligned with row above, assuming a reasonably close-to-default alpha / symbol layout
        // like in original layouts, e.g. for nordic and swiss layouts
        if (!params.mId.isAlphaOrSymbolKeyboard || bassKeyParams.size < 3 || bassKeyParams.last().isNotEmpty())
            return keysInRows
        val lastNormalRow = bassKeyParams[bassKeyParams.lastIndex - 1]
        val rowAboveLast = bassKeyParams[bassKeyParams.lastIndex - 2]
        val lastNormalRowKeyWidth = lastNormalRow.first().mWidth
        val rowAboveLastNormalRowKeyWidth = rowAboveLast.first().mWidth
        if (lastNormalRowKeyWidth <= rowAboveLastNormalRowKeyWidth + 0.0001f // no need
            || lastNormalRowKeyWidth / rowAboveLastNormalRowKeyWidth > 1.1f // don't resize on large size difference
            || lastNormalRow.any { it.isSpacer } || rowAboveLast.any { it.isSpacer } // annoying to deal with, and probably no resize wanted anyway
            || lastNormalRow.any { it.mWidth != lastNormalRowKeyWidth } || rowAboveLast.any { it.mWidth != rowAboveLastNormalRowKeyWidth })
            return keysInRows
        val numberOfKeysInLast = lastNormalRow.count { it.mBackgroundType == Key.BACKGROUND_TYPE_NORMAL }
        val widthBefore = numberOfKeysInLast * lastNormalRowKeyWidth
        val widthAfter = numberOfKeysInLast * rowAboveLastNormalRowKeyWidth
        val spacerWidth = (widthBefore - widthAfter) / 2
        // resize keys
        lastNormalRow.forEach { if (it.mBackgroundType == Key.BACKGROUND_TYPE_NORMAL) it.mWidth = rowAboveLastNormalRowKeyWidth }
        // add spacers
        val lastNormalFullRow = keysInRows[keysInRows.lastIndex - 1]
        lastNormalFullRow.add(lastNormalFullRow.indexOfFirst { it == lastNormalRow.first() }, KeyParams.newSpacer(params, spacerWidth))
        lastNormalFullRow.add(lastNormalFullRow.indexOfLast { it == lastNormalRow.last() } + 1, KeyParams.newSpacer(params, spacerWidth))

        return keysInRows
    }

    /**
     *  adds / removes keys to the bottom row
     *  assumes a close-to-default bottom row consisting only of functional keys
     *  does nothing if not isAlphaOrSymbolKeyboard or assumptions not met
     *  adds an empty row to baseKeys, to have a baseKey row for the bottom functional row
     */
    private fun adjustBottomFunctionalRowAndBaseKeys(allFunctionalKeys: MutableList<MutableList<KeyData>>, baseKeys: MutableList<MutableList<KeyData>>) {
        val functionalKeysBottom = allFunctionalKeys.lastOrNull() ?: return
        if (!params.mId.isAlphaOrSymbolKeyboard || functionalKeysBottom.isEmpty() || functionalKeysBottom.any { it.isKeyPlaceholder() })
            return
        if (true /* Settings.getInstance().current.mSingleFunctionalLayout */) { // todo with the customizable functional layout
            //   remove unwanted keys (emoji, numpad, language switch)
            if (!Settings.getInstance().current.mShowsEmojiKey || !params.mId.isAlphabetKeyboard)
                functionalKeysBottom.removeFirst { it.label == KeyLabel.EMOJI }
            if (!Settings.getInstance().current.isLanguageSwitchKeyEnabled || !params.mId.isAlphabetKeyboard)
                functionalKeysBottom.removeFirst { it.label == KeyLabel.LANGUAGE_SWITCH }
            if (params.mId.mElementId != KeyboardId.ELEMENT_SYMBOLS)
                functionalKeysBottom.removeFirst { it.label == KeyLabel.NUMPAD }
        }
        //   replace comma / period if 2 keys in normal bottom row
        if (baseKeys.last().size == 2) {
            functionalKeysBottom.replaceFirst(
                { it.label == KeyLabel.COMMA || it.groupId == KeyData.GROUP_COMMA},
                { baseKeys.last()[0].copy(newGroupId = 1, newType = baseKeys.last()[0].type ?: it.type) }
            )
            functionalKeysBottom.replaceFirst(
                { it.label == KeyLabel.PERIOD || it.groupId == KeyData.GROUP_PERIOD},
                { baseKeys.last()[1].copy(newGroupId = 2, newType = baseKeys.last()[1].type ?: it.type) }
            )
            baseKeys.removeLast()
        }
        //   add those extra keys depending on layout (remove later)
        val spaceIndex = functionalKeysBottom.indexOfFirst { it.label == KeyLabel.SPACE && it.width <= 0 } // 0 or -1
        if (spaceIndex >= 0) {
            if (params.mLocaleKeyboardInfos.hasZwnjKey && params.mId.isAlphabetKeyboard) {
                // add zwnj key next to space
                functionalKeysBottom.add(spaceIndex + 1, TextKeyData(label = KeyLabel.ZWNJ))
            } else if (params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS) {
                // add / key next to space, todo (later): not any more, but keep it so this PR can be released without too many people complaining
                functionalKeysBottom.add(spaceIndex + 1, TextKeyData(label = "/", type = KeyType.FUNCTION))
            } else if (params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED) {
                // add < and > keys next to space, todo (later): not any more, but keep it so this PR can be released without too many people complaining
                val key1 = TextKeyData(
                    label = "<",
                    popup = SimplePopups(listOf("!fixedColumnOrder!3", "‹", "≤", "«")),
                    labelFlags = Key.LABEL_FLAGS_HAS_POPUP_HINT,
                    type = KeyType.FUNCTION
                )
                val key2 = TextKeyData(
                    label = ">",
                    popup = SimplePopups(listOf("!fixedColumnOrder!3", "›", "≥", "»")),
                    labelFlags = Key.LABEL_FLAGS_HAS_POPUP_HINT,
                    type = KeyType.FUNCTION
                )
                functionalKeysBottom.add(spaceIndex + 1, key2)
                functionalKeysBottom.add(spaceIndex, key1)
            }
        }
        baseKeys.add(mutableListOf())
    }

    // ideally we would get all functional keys in a nice list of pairs from the start, but at least it works...
    private fun getFunctionalKeysBySide(functionalKeysFromTop: List<KeyData>, functionalKeysFromBottom: List<KeyData>): Pair<List<KeyParams>, List<KeyParams>> {
        val (functionalKeysFromTopLeft, functionalKeysFromTopRight) = functionalKeysFromTop.splitAt { it.isKeyPlaceholder() }
        val (functionalKeysFromBottomLeft, functionalKeysFromBottomRight) = functionalKeysFromBottom.splitAt { it.isKeyPlaceholder() }
        // functional keys from top rows are the outermost, if there are some in the same row
        functionalKeysFromTopLeft.addAll(functionalKeysFromBottomLeft)
        functionalKeysFromBottomRight.addAll(functionalKeysFromTopRight)
        val functionalKeysLeft = functionalKeysFromTopLeft.mapNotNull { it.processFunctionalKeys()?.toKeyParams(params) }
        val functionalKeysRight = functionalKeysFromBottomRight.mapNotNull { it.processFunctionalKeys()?.toKeyParams(params) }
        return functionalKeysLeft to functionalKeysRight
    }

    // this is not nice in here, but otherwise we'd need context, and defaultLabelFlags and infos for toKeyParams
    // improve it later, but currently this messy way is still ok
    private fun KeyData.processFunctionalKeys(): KeyData? = when (label) {
        // todo: why defaultLabelFlags exactly here? is this for armenian or bengali period labels? try removing also check in holo theme
        KeyLabel.PERIOD -> copy(newLabelFlags = labelFlags or defaultLabelFlags)
        KeyLabel.SHIFT -> if (infos.hasShiftKey) this else null
        KeyLabel.ACTION -> copy(
            // todo: evaluating the label should actually only happen in toKeyParams
            //  this label change already makes it necessary to provide the background in here too, because toKeyParams can't use action as label
            newLabel = "${getActionKeyLabel()}|${getActionKeyCode()}",
            newPopup = popup.merge(getActionKeyPopupKeys()?.let { SimplePopups(it) }),
            // the label change is messing with toKeyParams, so we need to supply the appropriate BG type here
            newType = type ?: KeyType.ENTER_EDITING,
            newLabelFlags = Key.LABEL_FLAGS_PRESERVE_CASE or Key.LABEL_FLAGS_AUTO_X_SCALE or
                    Key.LABEL_FLAGS_FOLLOW_KEY_LABEL_RATIO or Key.LABEL_FLAGS_FOLLOW_FUNCTIONAL_TEXT_COLOR or
                    Key.LABEL_FLAGS_HAS_POPUP_HINT or KeyboardTheme.getThemeActionAndEmojiKeyLabelFlags(params.mThemeId)
        )
        else -> {
            // this is ugly...
            if (label.length > 8 && label.startsWith("!string/")) {
                val id = context.resources.getIdentifier(label.substringAfter("!string/"), "string", context.packageName)
                if (id != 0) copy(newLabel = getInLocale(id))
                else this
            } else this
        }
    }

    private fun addNumberRowOrPopupKeys(baseKeys: MutableList<MutableList<KeyData>>) {
        if (!params.mId.mNumberRowEnabled && params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS) {
            // replace first symbols row with number row, but use the labels as popupKeys
            val numberRow = params.mLocaleKeyboardInfos.getNumberRow()
            numberRow.forEachIndexed { index, keyData -> keyData.popup.symbol = baseKeys[0].getOrNull(index)?.label }
            baseKeys[0] = numberRow.toMutableList()
        } else if (!params.mId.mNumberRowEnabled && params.mId.isAlphabetKeyboard && infos.numbersOnTopRow) {
            if (baseKeys[0].any { it.popup.main != null || !it.popup.relevant.isNullOrEmpty() } // first row of baseKeys has any layout popup key
                && params.mPopupKeyLabelSources.let {
                    val layout = it.indexOf(POPUP_KEYS_LAYOUT)
                    val number = it.indexOf(POPUP_KEYS_NUMBER)
                    layout != -1 && layout < number // layout before number label
                }
            ) {
                // remove number from labels, to avoid awkward mix of numbers and others caused by layout popup keys
                params.mPopupKeyLabelSources.remove(POPUP_KEYS_NUMBER)
            }
            // add number to the first 10 keys in first row
            baseKeys.first().take(10).forEachIndexed { index, keyData -> keyData.popup.numberIndex = index }
            if (baseKeys.first().size < 10) {
                Log.w(TAG, "first row only has ${baseKeys.first().size} keys: ${baseKeys.first().map { it.label }}")
            }
        }
    }

    private fun addSymbolPopupKeys(baseKeys: MutableList<MutableList<KeyData>>) {
        val layoutName = if (params.mId.locale.script() == ScriptUtils.SCRIPT_ARABIC) LAYOUT_SYMBOLS_ARABIC else LAYOUT_SYMBOLS
        val layout = RawKeyboardParser.parseLayout(layoutName, params, context)
        layout.forEachIndexed { i, row ->
            val baseRow = baseKeys.getOrNull(i) ?: return@forEachIndexed
            row.forEachIndexed { j, key ->
                baseRow.getOrNull(j)?.popup?.symbol = key.label
            }
        }
    }

    private fun getActionKeyLabel(): String {
        if (params.mId.isMultiLine && (params.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED || params.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED))
            return "!icon/enter_key"
        val iconName = when (params.mId.imeAction()) {
            EditorInfo.IME_ACTION_GO -> KeyboardIconsSet.NAME_GO_KEY
            EditorInfo.IME_ACTION_SEARCH -> KeyboardIconsSet.NAME_SEARCH_KEY
            EditorInfo.IME_ACTION_SEND -> KeyboardIconsSet.NAME_SEND_KEY
            EditorInfo.IME_ACTION_NEXT -> KeyboardIconsSet.NAME_NEXT_KEY
            EditorInfo.IME_ACTION_DONE -> KeyboardIconsSet.NAME_DONE_KEY
            EditorInfo.IME_ACTION_PREVIOUS -> KeyboardIconsSet.NAME_PREVIOUS_KEY
            InputTypeUtils.IME_ACTION_CUSTOM_LABEL -> return params.mId.mCustomActionLabel
            else -> return "!icon/enter_key"
        }
        val replacement = iconName.replaceIconWithLabelIfNoDrawable()
        return if (iconName == replacement) // i.e. icon exists
            "!icon/$iconName"
        else
            replacement
    }

    private fun getActionKeyCode() =
        if (params.mId.isMultiLine && (params.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED || params.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED))
            "!code/key_shift_enter"
        else "!code/key_enter"

    private fun getActionKeyPopupKeys(): Collection<String>? {
        val action = params.mId.imeAction()
        val navigatePrev = params.mId.navigatePrevious()
        val navigateNext = params.mId.navigateNext()
        return when {
            params.mId.passwordInput() -> when {
                navigatePrev && action == EditorInfo.IME_ACTION_NEXT -> createPopupKeys(POPUP_EYS_NAVIGATE_PREVIOUS)
                action == EditorInfo.IME_ACTION_NEXT -> null
                navigateNext && action == EditorInfo.IME_ACTION_PREVIOUS -> createPopupKeys(POPUP_EYS_NAVIGATE_NEXT)
                action == EditorInfo.IME_ACTION_PREVIOUS -> null
                navigateNext && navigatePrev -> createPopupKeys(POPUP_EYS_NAVIGATE_PREVIOUS_NEXT)
                navigateNext -> createPopupKeys(POPUP_EYS_NAVIGATE_NEXT)
                navigatePrev -> createPopupKeys(POPUP_EYS_NAVIGATE_PREVIOUS)
                else -> null
            }
            // could change definition of numbers to query a range, or have a pre-defined list, but not that crucial
            params.mId.isNumberLayout || params.mId.mMode in listOf(KeyboardId.MODE_EMAIL, KeyboardId.MODE_DATE, KeyboardId.MODE_TIME, KeyboardId.MODE_DATETIME) -> when {
                action == EditorInfo.IME_ACTION_NEXT && navigatePrev -> createPopupKeys(POPUP_EYS_NAVIGATE_PREVIOUS)
                action == EditorInfo.IME_ACTION_NEXT -> null
                action == EditorInfo.IME_ACTION_PREVIOUS && navigateNext -> createPopupKeys(POPUP_EYS_NAVIGATE_NEXT)
                action == EditorInfo.IME_ACTION_PREVIOUS -> null
                navigateNext && navigatePrev -> createPopupKeys(POPUP_EYS_NAVIGATE_PREVIOUS_NEXT)
                navigateNext -> createPopupKeys(POPUP_EYS_NAVIGATE_NEXT)
                navigatePrev -> createPopupKeys(POPUP_EYS_NAVIGATE_PREVIOUS)
                else -> null
            }
            action == EditorInfo.IME_ACTION_NEXT && navigatePrev -> createPopupKeys(POPUP_EYS_NAVIGATE_EMOJI_PREVIOUS)
            action == EditorInfo.IME_ACTION_NEXT -> createPopupKeys(POPUP_EYS_NAVIGATE_EMOJI)
            action == EditorInfo.IME_ACTION_PREVIOUS && navigateNext -> createPopupKeys(POPUP_EYS_NAVIGATE_EMOJI_NEXT)
            action == EditorInfo.IME_ACTION_PREVIOUS -> createPopupKeys(POPUP_EYS_NAVIGATE_EMOJI)
            navigateNext && navigatePrev -> createPopupKeys(POPUP_EYS_NAVIGATE_EMOJI_PREVIOUS_NEXT)
            navigateNext -> createPopupKeys(POPUP_EYS_NAVIGATE_EMOJI_NEXT)
            navigatePrev -> createPopupKeys(POPUP_EYS_NAVIGATE_EMOJI_PREVIOUS)
            else -> createPopupKeys(POPUP_EYS_NAVIGATE_EMOJI)
        }
    }

    private fun createPopupKeys(popupKeysDef: String): List<String> {
        val popupKeys = mutableListOf<String>()
        for (popupKey in popupKeysDef.split(",")) {
            val iconPrefixRemoved = popupKey.substringAfter("!icon/")
            if (iconPrefixRemoved == popupKey) { // i.e. there is no !icon/
                popupKeys.add(popupKey)
                continue
            }
            val iconName = iconPrefixRemoved.substringBefore("|")
            val replacementText = iconName.replaceIconWithLabelIfNoDrawable()
            if (replacementText == iconName) { // i.e. we have the drawable
                popupKeys.add(popupKey)
            } else {
                popupKeys.add(Key.POPUP_KEYS_HAS_LABELS)
                popupKeys.add("$replacementText|${iconPrefixRemoved.substringAfter("|")}")
            }
        }
        // remove emoji shortcut on enter in tablet mode (like original, because bottom row always has an emoji key)
        // (probably not necessary, but whatever)
        if (Settings.getInstance().isTablet && popupKeys.remove("!icon/emoji_action_key|!code/key_emoji")) {
            val i = popupKeys.indexOfFirst { it.startsWith(Key.POPUP_KEYS_FIXED_COLUMN_ORDER) }
            if (i > -1) {
                val n = popupKeys[i].substringAfter(Key.POPUP_KEYS_FIXED_COLUMN_ORDER).toIntOrNull()
                if (n != null)
                    popupKeys[i] = popupKeys[i].replace(n.toString(), (n - 1).toString())
            }
        }
        return popupKeys
    }

    private fun String.replaceIconWithLabelIfNoDrawable(): String {
        if (params.mIconsSet.getIconDrawable(this) != null) return this
        if (params.mId.mWidth == AndroidSpellCheckerService.SPELLCHECKER_DUMMY_KEYBOARD_WIDTH
                && params.mId.mHeight == AndroidSpellCheckerService.SPELLCHECKER_DUMMY_KEYBOARD_HEIGHT
                && !params.mId.mSubtype.hasExtraValue(Constants.Subtype.ExtraValue.EMOJI_CAPABLE)
            )
            // fake keyboard that is used by spell checker (for key coordinates), but not shown to the user
            // often this doesn't have any icons loaded, and there is no need to bother with this
            return this
        val id = context.resources.getIdentifier("label_$this", "string", context.packageName)
        if (id == 0) {
            Log.w(TAG, "no resource for label $this in ${params.mId}")
            return this
        }
        return getInLocale(id)
    }

    private fun getInLocale(@StringRes id: Int): String {
        // todo: hi-Latn strings instead of this workaround?
        val locale = if (params.mId.locale.toLanguageTag() == "hi-Latn") "en_IN".constructLocale()
            else params.mId.locale
        return runInLocale(context, locale) { it.getString(id) }
    }

    companion object {
        private const val TAG = "KeyboardParser"

        // todo:
        //  layoutInfos should be stored in method.xml (imeSubtypeExtraValue)
        //  or somewhere else... some replacement for keyboard_layout_set xml maybe
        //  some assets file?
        //  some extended version of locale_key_texts? that would be good, just need to rename the class and file
        // touchPositionCorrectionData is just the resId, needs to be loaded in parser
        //  currently always holo is applied in readAttributes
        private fun layoutInfos(params: KeyboardParams): LayoutInfos {
            val layout = params.mId.mSubtype.keyboardLayoutSetName
            // only for alphabet, but some exceptions for shift layouts
            val enableProximityCharsCorrection = params.mId.isAlphabetKeyboard && when (layout) {
                "bengali_akkhor", "georgian", "hindi", "lao", "nepali_romanized", "nepali_traditional", "sinhala", "thai" ->
                    params.mId.mElementId == KeyboardId.ELEMENT_ALPHABET
                else -> true
            }
            // essentially this is default for 4 row and non-alphabet layouts, maybe this could be determined automatically instead of using a list
            // todo: check the difference between default (i.e. none) and holo (test behavior on keyboard)
            val touchPositionCorrectionData = if (params.mId.isAlphabetKeyboard && layout in listOf("armenian_phonetic", "khmer", "lao", "malayalam", "pcqwerty", "thai"))
                    R.array.touch_position_correction_data_default
                else R.array.touch_position_correction_data_holo
            // custom non-json layout for non-uppercase language should not have shift key
            val hasShiftKey = !params.mId.isAlphabetKeyboard
                    || layout !in listOf("hindi_compact", "bengali", "arabic", "arabic_pc", "hebrew", "kannada", "kannada_extended","malayalam", "marathi", "farsi", "tamil", "telugu")
            val numbersOnTopRow = layout !in listOf("pcqwerty", "lao", "thai", "korean_sebeolsik_390", "korean_sebeolsik_final")
            return LayoutInfos(enableProximityCharsCorrection, touchPositionCorrectionData, hasShiftKey, numbersOnTopRow)
        }
    }

}

// todo: actually this should be in some separate file
data class LayoutInfos(
    // disabled by default, but enabled for all alphabet layouts
    // currently set in keyboardLayoutSet
    val enableProximityCharsCorrection: Boolean = false,
    // there is holo, default and null
    // null only for popupKeys keyboard
    val touchPositionCorrectionData: Int? = null,
    // some layouts do not have a shift key
    val hasShiftKey: Boolean = true,
    // some layouts have different number layout, e.g. thai or korean_sebeolsik
    val numbersOnTopRow: Boolean = true,
)

fun String.rtlLabel(params: KeyboardParams): String {
    if (!params.mId.mSubtype.isRtlSubtype || params.mId.isNumberLayout) return this
    return when (this) {
        "{" -> "{|}"
        "}" -> "}|{"
        "(" -> "(|)"
        ")" -> ")|("
        "[" -> "[|]"
        "]" -> "]|["
        "<" -> "<|>"
        ">" -> ">|<"
        "≤" -> "≤|≥"
        "≥" -> "≥|≤"
        "«" -> "«|»"
        "»" -> "»|«"
        "‹" -> "‹|›"
        "›" -> "›|‹"
        "﴾" -> "﴾|﴿"
        "﴿" -> "﴿|﴾"
        else -> this
    }
}

// could make arrays right away, but they need to be copied anyway as popupKeys arrays are modified when creating KeyParams
private const val POPUP_EYS_NAVIGATE_PREVIOUS = "!icon/previous_key|!code/key_action_previous,!icon/clipboard_action_key|!code/key_clipboard"
private const val POPUP_EYS_NAVIGATE_NEXT = "!icon/clipboard_action_key|!code/key_clipboard,!icon/next_key|!code/key_action_next"
private const val POPUP_EYS_NAVIGATE_PREVIOUS_NEXT = "!fixedColumnOrder!3,!needsDividers!,!icon/previous_key|!code/key_action_previous,!icon/clipboard_action_key|!code/key_clipboard,!icon/next_key|!code/key_action_next"
private const val POPUP_EYS_NAVIGATE_EMOJI_PREVIOUS = "!fixedColumnOrder!3,!needsDividers!,!icon/previous_key|!code/key_action_previous,!icon/clipboard_action_key|!code/key_clipboard,!icon/emoji_action_key|!code/key_emoji"
private const val POPUP_EYS_NAVIGATE_EMOJI = "!icon/clipboard_action_key|!code/key_clipboard,!icon/emoji_action_key|!code/key_emoji"
private const val POPUP_EYS_NAVIGATE_EMOJI_NEXT = "!fixedColumnOrder!3,!needsDividers!,!icon/clipboard_action_key|!code/key_clipboard,!icon/emoji_action_key|!code/key_emoji,!icon/next_key|!code/key_action_next"
private const val POPUP_EYS_NAVIGATE_EMOJI_PREVIOUS_NEXT = "!fixedColumnOrder!4,!needsDividers!,!icon/previous_key|!code/key_action_previous,!icon/clipboard_action_key|!code/key_clipboard,!icon/emoji_action_key|!code/key_emoji,!icon/next_key|!code/key_action_next"

const val LAYOUT_SYMBOLS = "symbols"
const val LAYOUT_SYMBOLS_SHIFTED = "symbols_shifted"
const val LAYOUT_SYMBOLS_ARABIC = "symbols_arabic"
const val LAYOUT_NUMPAD = "numpad"
const val LAYOUT_NUMPAD_LANDSCAPE = "numpad_landscape"
const val LAYOUT_NUMBER = "number"
const val LAYOUT_PHONE = "phone"
const val LAYOUT_PHONE_SYMBOLS = "phone_symbols"
const val FUNCTIONAL_LAYOUT_SYMBOLS_SHIFTED = "functional_keys_symbols_shifted"
const val FUNCTIONAL_LAYOUT_SYMBOLS = "functional_keys_symbols"
const val FUNCTIONAL_LAYOUT = "functional_keys"
const val FUNCTIONAL_LAYOUT_TABLET = "functional_keys_tablet"
