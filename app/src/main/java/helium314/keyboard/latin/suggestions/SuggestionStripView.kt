/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package helium314.keyboard.latin.suggestions

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.view.isVisible
import helium314.keyboard.accessibility.AccessibilityUtils
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.MainKeyboardView
import helium314.keyboard.keyboard.PopupKeysPanel
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.Dictionary
import helium314.keyboard.latin.R
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.settings.DebugSettings
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.suggestions.PopupSuggestionsView.MoreSuggestionsListener
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.ToolbarMode
import helium314.keyboard.latin.utils.addPinnedKey
import helium314.keyboard.latin.utils.createToolbarKey
import helium314.keyboard.latin.utils.getCodeForToolbarKey
import helium314.keyboard.latin.utils.getCodeForToolbarKeyLongClick
import helium314.keyboard.latin.utils.getEnabledToolbarKeys
import helium314.keyboard.latin.utils.getPinnedToolbarKeys
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.removeFirst
import helium314.keyboard.latin.utils.removePinnedKey
import helium314.keyboard.latin.utils.setToolbarButtonsActivatedStateOnPrefChange
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.min

class SuggestionStripView(context: Context, attrs: AttributeSet?, defStyle: Int) :
    RelativeLayout(context, attrs, defStyle), View.OnClickListener, OnLongClickListener, OnSharedPreferenceChangeListener {

    /** Construct a [SuggestionStripView] for showing suggestions to be picked by the user. */
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, R.attr.suggestionStripViewStyle)

    interface Listener {
        fun pickSuggestionManually(word: SuggestedWordInfo?)
        fun onCodeInput(primaryCode: Int, x: Int, y: Int, isKeyRepeat: Boolean)
        fun removeSuggestion(word: String?)
    }

    private val moreSuggestionsContainer: View
    private val wordViews = ArrayList<TextView>()
    private val debugInfoViews = ArrayList<TextView>()
    private val dividerViews = ArrayList<View>()

    init {
        val inflater = LayoutInflater.from(context)
        inflater.inflate(R.layout.suggestions_strip, this)
        moreSuggestionsContainer = inflater.inflate(R.layout.more_suggestions, null)

        val colors = Settings.getValues().mColors
        colors.setBackground(this, ColorType.STRIP_BACKGROUND)
        val customTypeface = Settings.getInstance().customTypeface
        for (pos in 0..<SuggestedWords.MAX_SUGGESTIONS) {
            val word = TextView(context, null, R.attr.suggestionWordStyle)
            word.contentDescription = resources.getString(R.string.spoken_empty_suggestion)
            word.setOnClickListener(this)
            word.setOnLongClickListener(this)
            if (customTypeface != null)
                word.typeface = customTypeface
            colors.setBackground(word, ColorType.STRIP_BACKGROUND)
            wordViews.add(word)
            val divider = inflater.inflate(R.layout.suggestion_divider, null)
            dividerViews.add(divider)
            val info = TextView(context, null, R.attr.suggestionWordStyle)
            info.setTextColor(colors.get(ColorType.KEY_TEXT))
            info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, DEBUG_INFO_TEXT_SIZE_IN_DIP)
            debugInfoViews.add(info)
        }

        DEBUG_SUGGESTIONS = context.prefs().getBoolean(DebugSettings.PREF_SHOW_SUGGESTION_INFOS, Defaults.PREF_SHOW_SUGGESTION_INFOS)
    }

    // toolbar views, drawables and setup
    private val toolbar: ViewGroup = findViewById(R.id.toolbar)
    private val toolbarContainer: View = findViewById(R.id.toolbar_container)
    private val pinnedKeys: ViewGroup = findViewById(R.id.pinned_keys)
    private val suggestionsStrip: ViewGroup = findViewById(R.id.suggestions_strip)
    private val toolbarExpandKey = findViewById<ImageButton>(R.id.suggestions_strip_toolbar_key)
    private val incognitoIcon = KeyboardIconsSet.instance.getNewDrawable(ToolbarKey.INCOGNITO.name, context)
    private val toolbarArrowIcon = KeyboardIconsSet.instance.getNewDrawable(KeyboardIconsSet.NAME_TOOLBAR_KEY, context)
    private val defaultToolbarBackground: Drawable = toolbarExpandKey.background
    private val enabledToolKeyBackground = GradientDrawable()
    init {
        val colors = Settings.getValues().mColors

        // expand key
        val toolbarHeight = min(toolbarExpandKey.layoutParams.height, resources.getDimension(R.dimen.config_suggestions_strip_height).toInt())
        toolbarExpandKey.layoutParams.height = toolbarHeight
        toolbarExpandKey.layoutParams.width = toolbarHeight // we want it square
        colors.setBackground(toolbarExpandKey, ColorType.STRIP_BACKGROUND) // necessary because background is re-used for defaultToolbarBackground
        colors.setColor(toolbarExpandKey, ColorType.TOOL_BAR_EXPAND_KEY)
        colors.setColor(toolbarExpandKey.background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)

        // background indicator for pinned keys
        val color = colors.get(ColorType.TOOL_BAR_KEY_ENABLED_BACKGROUND) or -0x1000000 // ignore alpha (in Java this is more readable 0xFF000000)
        enabledToolKeyBackground.colors = intArrayOf(color, Color.TRANSPARENT)
        enabledToolKeyBackground.gradientType = GradientDrawable.RADIAL_GRADIENT
        enabledToolKeyBackground.gradientRadius = resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height) / 2.1f

        val mToolbarMode = Settings.getValues().mToolbarMode
        if (mToolbarMode == ToolbarMode.TOOLBAR_KEYS) {
            setToolbarVisibility(true)
        }

        // toolbar keys setup
        val toolbarKeyLayoutParams = LinearLayout.LayoutParams(
            resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_edge_key_width),
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        if (mToolbarMode == ToolbarMode.TOOLBAR_KEYS || mToolbarMode == ToolbarMode.EXPANDABLE) {
            for (key in getEnabledToolbarKeys(context.prefs())) {
                val button = createToolbarKey(context, KeyboardIconsSet.instance, key)
                button.layoutParams = toolbarKeyLayoutParams
                setupKey(button, colors)
                toolbar.addView(button)
            }
        }
        if (!Settings.getValues().mSuggestionStripHiddenPerUserSettings) {
            for (pinnedKey in getPinnedToolbarKeys(context.prefs())) {
                val button = createToolbarKey(context, KeyboardIconsSet.instance, pinnedKey)
                button.layoutParams = toolbarKeyLayoutParams
                setupKey(button, colors)
                pinnedKeys.addView(button)
                val pinnedKeyInToolbar = toolbar.findViewWithTag<View>(pinnedKey)
                if (pinnedKeyInToolbar != null && Settings.getValues().mQuickPinToolbarKeys)
                    pinnedKeyInToolbar.background = enabledToolKeyBackground
            }
        }

        updateKeys()
    }

    private val layoutHelper = SuggestionStripLayoutHelper(context, attrs, defStyle, wordViews, dividerViews, debugInfoViews)
    private lateinit var listener: Listener
    private lateinit var mainKeyboardView: MainKeyboardView
    private var suggestedWords = SuggestedWords.getEmptyInstance()
    private var startIndexOfMoreSuggestions = 0
    private var direction = 1 // 1 if LTR, -1 if RTL
    private var isExternalSuggestionVisible = false // Required to disable the more suggestions if other suggestions are visible

    // related to more suggestions
    // todo: maybe put most of this in a separate class?
    private val moreSuggestionsView: PopupSuggestionsView = moreSuggestionsContainer.findViewById(R.id.more_suggestions_view)
    private val moreSuggestionsBuilder = MoreSuggestions.Builder(context, moreSuggestionsView) // todo: why actually here?
    private val moreSuggestionsModalTolerance = context.resources.getDimensionPixelOffset(R.dimen.config_more_suggestions_modal_tolerance)
    private val moreSuggestionsListener = object : MoreSuggestionsListener() {
        override fun onSuggestionSelected(wordInfo: SuggestedWordInfo) {
            listener.pickSuggestionManually(wordInfo)
            dismissMoreSuggestionsPanel()
        }

        override fun onCancelInput() {
            dismissMoreSuggestionsPanel()
        }
    }
    private val moreSuggestionsSlidingListener = object : SimpleOnGestureListener() {
        override fun onScroll(down: MotionEvent?, me: MotionEvent, deltaX: Float, deltaY: Float): Boolean {
            if (down == null) return false
            val dy = me.y - down.y
            return if (toolbarContainer.visibility != VISIBLE && deltaY > 0 && dy < 0) showMoreSuggestions()
            else false
        }
    }
    private val moreSuggestionsSlidingDetector = GestureDetector(context, moreSuggestionsSlidingListener)
    // Working variables for onInterceptTouchEvent(MotionEvent) and onTouchEvent(MotionEvent).
    private var lastX = 0
    private var lastY = 0
    private var originX = 0
    private var originY = 0
    private var needsToTransformTouchEventToHoverEvent = false
    private var isDispatchingHoverEventToMoreSuggestions = false
    private val moreSuggestionsController: PopupKeysPanel.Controller = object : PopupKeysPanel.Controller {
        override fun onDismissPopupKeysPanel() {
            mainKeyboardView.onDismissPopupKeysPanel()
        }

        override fun onShowPopupKeysPanel(panel: PopupKeysPanel) {
            mainKeyboardView.onShowPopupKeysPanel(panel)
        }

        override fun onCancelPopupKeysPanel() {
            dismissMoreSuggestionsPanel()
        }
    }

    // public stuff

    val isShowingMoreSuggestionPanel get() = moreSuggestionsView.isShowingInParent

    /** A connection back to the input method. */
    fun setListener(newListener: Listener, inputView: View) {
        listener = newListener
        mainKeyboardView = inputView.findViewById(R.id.keyboard_view)
    }

    fun setRtl(isRtlLanguage: Boolean) {
        val newLayoutDirection: Int
        if (!Settings.getValues().mVarToolbarDirection)
            newLayoutDirection = LAYOUT_DIRECTION_LOCALE
        else {
            newLayoutDirection = if (isRtlLanguage) LAYOUT_DIRECTION_RTL else LAYOUT_DIRECTION_LTR
            direction = if (isRtlLanguage) -1 else 1
            toolbarExpandKey.scaleX = (if (toolbarContainer.visibility != VISIBLE) 1f else -1f) * direction
        }
        layoutDirection = newLayoutDirection
        suggestionsStrip.layoutDirection = newLayoutDirection
    }

    fun setToolbarVisibility(toolbarVisible: Boolean) {
        // avoid showing toolbar keys when locked
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val locked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) // todo: move this check to compat (also other uses)
            km.isDeviceLocked
        else
            km.isKeyguardLocked
        pinnedKeys.isVisible = !locked && !toolbarVisible
        suggestionsStrip.isVisible = locked || !toolbarVisible
        toolbarContainer.isVisible = !locked && toolbarVisible

        if (DEBUG_SUGGESTIONS) {
            for (view in debugInfoViews) {
                view.visibility = suggestionsStrip.visibility
            }
        }

        toolbarExpandKey.scaleX = (if (toolbarVisible && !locked) -1f else 1f) * direction
    }

    fun setSuggestions(suggestions: SuggestedWords, isRtlLanguage: Boolean) {
        clear()
        setRtl(isRtlLanguage)
        suggestedWords = suggestions
        startIndexOfMoreSuggestions = layoutHelper.layoutAndReturnStartIndexOfMoreSuggestions(
            context, suggestedWords, suggestionsStrip, this
        )
        isExternalSuggestionVisible = false
    }

    fun setExternalSuggestionView(view: View?) {
        clear()
        isExternalSuggestionVisible = true
        suggestionsStrip.addView(view)
        if (Settings.getValues().mAutoHideToolbar) setToolbarVisibility(false)
    }

    fun setMoreSuggestionsHeight(remainingHeight: Int) {
        layoutHelper.setMoreSuggestionsHeight(remainingHeight)
    }

    fun dismissMoreSuggestionsPanel() {
        moreSuggestionsView.dismissPopupKeysPanel()
    }

    // overrides: necessarily public, but not used from outside

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        setToolbarButtonsActivatedStateOnPrefChange(pinnedKeys, key)
        setToolbarButtonsActivatedStateOnPrefChange(toolbar, key)
    }

    override fun onVisibilityChanged(view: View, visibility: Int) {
        super.onVisibilityChanged(view, visibility)
        // workaround for a bug with inline suggestions views that just keep showing up otherwise, https://github.com/Helium314/HeliBoard/pull/386
        if (view === this)
            suggestionsStrip.visibility = visibility
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        dismissMoreSuggestionsPanel()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // Called by the framework when the size is known. Show the important notice if applicable.
        // This may be overridden by showing suggestions later, if applicable.
    }

    override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent): Boolean {
        // Don't populate accessibility event with suggested words and voice key.
        return true
    }

    // this is only for moreSuggestionsView
    override fun onInterceptTouchEvent(motionEvent: MotionEvent): Boolean {
        // Disable More Suggestions if external suggestions are visible
        if (isExternalSuggestionVisible) {
            return false
        }

        // Detecting sliding up finger to show MoreSuggestionsView.
        if (!moreSuggestionsView.isShowingInParent) {
            lastX = motionEvent.x.toInt()
            lastY = motionEvent.y.toInt()
            return moreSuggestionsSlidingDetector.onTouchEvent(motionEvent)
        }
        if (moreSuggestionsView.isInModalMode) {
            return false
        }

        val index = motionEvent.actionIndex
        if (abs((motionEvent.getX(index).toInt() - originX).toDouble()) >= moreSuggestionsModalTolerance
            || originY - motionEvent.getY(index).toInt() >= moreSuggestionsModalTolerance
        ) {
            // Decided to be in the sliding suggestion mode only when the touch point has been moved
            // upward. Further MotionEvents will be delivered to onTouchEvent(MotionEvent).
            needsToTransformTouchEventToHoverEvent = AccessibilityUtils.instance.isTouchExplorationEnabled
            isDispatchingHoverEventToMoreSuggestions = false
            return true
        }

        if (motionEvent.action == MotionEvent.ACTION_UP || motionEvent.action == MotionEvent.ACTION_POINTER_UP) {
            // Decided to be in the modal input mode.
            moreSuggestionsView.setModalMode()
        }
        return false
    }

    // this is only for moreSuggestionsView
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(motionEvent: MotionEvent): Boolean {
        if (!moreSuggestionsView.isShowingInParent) {
            // Ignore any touch event while more suggestions panel hasn't been shown.
            return true
        }
        // In the sliding input mode. {@link MotionEvent} should be forwarded to
        // {@link MoreSuggestionsView}.
        val index = motionEvent.actionIndex
        val x = moreSuggestionsView.translateX(motionEvent.getX(index).toInt())
        val y = moreSuggestionsView.translateY(motionEvent.getY(index).toInt())
        motionEvent.setLocation(x.toFloat(), y.toFloat())
        if (!needsToTransformTouchEventToHoverEvent) {
            moreSuggestionsView.onTouchEvent(motionEvent)
            return true
        }
        // In sliding suggestion mode with accessibility mode on, a touch event should be
        // transformed to a hover event.
        val width = moreSuggestionsView.width
        val height = moreSuggestionsView.height
        val onMoreSuggestions = x in 0..<width && y in 0..<height
        if (!onMoreSuggestions && !isDispatchingHoverEventToMoreSuggestions) {
            // Just drop this touch event because dispatching hover event isn't started yet and
            // the touch event isn't on MoreSuggestionsView.
            return true
        }
        val hoverAction: Int
        if (onMoreSuggestions && !isDispatchingHoverEventToMoreSuggestions) {
            // Transform this touch event to a hover enter event and start dispatching a hover event to MoreSuggestionsView.
            isDispatchingHoverEventToMoreSuggestions = true
            hoverAction = MotionEvent.ACTION_HOVER_ENTER
        } else if (motionEvent.actionMasked == MotionEvent.ACTION_UP) {
            // Transform this touch event to a hover exit event and stop dispatching a hover event after this.
            isDispatchingHoverEventToMoreSuggestions = false
            needsToTransformTouchEventToHoverEvent = false
            hoverAction = MotionEvent.ACTION_HOVER_EXIT
        } else {
            // Transform this touch event to a hover move event.
            hoverAction = MotionEvent.ACTION_HOVER_MOVE
        }
        motionEvent.action = hoverAction
        moreSuggestionsView.onHoverEvent(motionEvent)
        return true
    }

    override fun onClick(view: View) {
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, this)
        val tag = view.tag
        if (tag is ToolbarKey) {
            val code = getCodeForToolbarKey(tag)
            if (code != KeyCode.UNSPECIFIED) {
                Log.d(TAG, "click toolbar key $tag")
                listener.onCodeInput(code, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false)
                if (tag === ToolbarKey.INCOGNITO) updateKeys() // update expand key icon
                return
            }
        }
        if (view === toolbarExpandKey) {
            setToolbarVisibility(toolbarContainer.visibility != VISIBLE)
        }

        // tag for word views is set in SuggestionStripLayoutHelper (setupWordViewsTextAndColor, layoutPunctuationSuggestions)
        if (tag is Int) {
            if (tag >= suggestedWords.size()) {
                return
            }
            val wordInfo = suggestedWords.getInfo(tag)
            listener.pickSuggestionManually(wordInfo)
        }
    }

    override fun onLongClick(view: View): Boolean {
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(Constants.NOT_A_CODE, this)
        if (view.tag is ToolbarKey) {
            onLongClickToolbarKey(view)
            return true
        }
        return if (view is TextView && wordViews.contains(view)) {
            onLongClickSuggestion(view)
        } else {
            showMoreSuggestions()
        }
    }

    // actually private stuff

    private fun onLongClickToolbarKey(view: View) {
        val tag = view.tag as? ToolbarKey ?: return
        if (!Settings.getValues().mQuickPinToolbarKeys || view.parent === pinnedKeys) {
            val longClickCode = getCodeForToolbarKeyLongClick(tag)
            if (longClickCode != KeyCode.UNSPECIFIED) {
                listener.onCodeInput(longClickCode, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false)
            }
        } else if (view.parent === toolbar) {
            val pinnedKeyView = pinnedKeys.findViewWithTag<View>(tag)
            if (pinnedKeyView == null) {
                addKeyToPinnedKeys(tag)
                toolbar.findViewWithTag<View>(tag).background = enabledToolKeyBackground
                addPinnedKey(context.prefs(), tag)
            } else {
                removePinnedKey(context.prefs(), tag)
                toolbar.findViewWithTag<View>(tag).background = defaultToolbarBackground.constantState?.newDrawable(resources)
                pinnedKeys.removeView(pinnedKeyView)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility") // no need for View#performClick, we only return false mostly anyway
    private fun onLongClickSuggestion(wordView: TextView): Boolean {
        var showIcon = true
        if (wordView.tag is Int) {
            val index = wordView.tag as Int
            if (index < suggestedWords.size() && suggestedWords.getInfo(index).mSourceDict == Dictionary.DICTIONARY_USER_TYPED)
                showIcon = false
        }
        if (showIcon) {
            val icon = KeyboardIconsSet.instance.getNewDrawable(KeyboardIconsSet.NAME_BIN, context)!!
            Settings.getValues().mColors.setColor(icon, ColorType.REMOVE_SUGGESTION_ICON)
            val w = icon.intrinsicWidth
            val h = icon.intrinsicHeight
            wordView.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
            wordView.ellipsize = TextUtils.TruncateAt.END
            val downOk = AtomicBoolean(false)
            wordView.setOnTouchListener { _, motionEvent ->
                if (motionEvent.action == MotionEvent.ACTION_UP && downOk.get()) {
                    val x = motionEvent.x
                    val y = motionEvent.y
                    if (0 < x && x < w && 0 < y && y < h) {
                        removeSuggestion(wordView)
                        wordView.cancelLongPress()
                        wordView.isPressed = false
                        return@setOnTouchListener true
                    }
                } else if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                    val x = motionEvent.x
                    val y = motionEvent.y
                    if (0 < x && x < w && 0 < y && y < h) {
                        downOk.set(true)
                    }
                }
                false
            }
        }
        if (DebugFlags.DEBUG_ENABLED && (isShowingMoreSuggestionPanel || !showMoreSuggestions())) {
            showSourceDict(wordView)
            return true
        }
        return showMoreSuggestions()
    }

    private fun showMoreSuggestions(): Boolean {
        val parentKeyboard = mainKeyboardView.keyboard ?: return false
        if (suggestedWords.size() <= startIndexOfMoreSuggestions) {
            return false
        }
        val container = moreSuggestionsContainer
        val maxWidth = width - container.paddingLeft - container.paddingRight
        val keyboard = moreSuggestionsBuilder.layout(
            suggestedWords, startIndexOfMoreSuggestions, maxWidth,
            (maxWidth * layoutHelper.mMinMoreSuggestionsWidth).toInt(),
            layoutHelper.maxMoreSuggestionsRow, parentKeyboard
        ).build()
        moreSuggestionsView.setKeyboard(keyboard)
        container.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val pointX = width / 2
        val pointY = -layoutHelper.mMoreSuggestionsBottomGap
        moreSuggestionsView.showPopupKeysPanel(this, moreSuggestionsController, pointX, pointY, moreSuggestionsListener)
        originX = lastX
        originY = lastY
        for (i in 0..<startIndexOfMoreSuggestions) {
            wordViews[i].isPressed = false
        }
        return true
    }

    private fun showSourceDict(wordView: TextView) {
        val word = wordView.text.toString()
        val index = wordView.tag as? Int ?: return
        if (index >= suggestedWords.size()) return
        val info = suggestedWords.getInfo(index)
        if (info.word != word) return

        val text = info.mSourceDict.mDictType + ":" + info.mSourceDict.mLocale
        if (isShowingMoreSuggestionPanel) {
            moreSuggestionsView.dismissPopupKeysPanel()
        }
        KeyboardSwitcher.getInstance().showToast(text, true)
    }

    private fun removeSuggestion(wordView: TextView) {
        val word = wordView.text.toString()
        listener.removeSuggestion(word)
        moreSuggestionsView.dismissPopupKeysPanel()
        // show suggestions, but without the removed word
        val suggestedWordInfos = ArrayList<SuggestedWordInfo>()
        for (i in 0..<suggestedWords.size()) {
            val info = suggestedWords.getInfo(i)
            if (info.word != word) suggestedWordInfos.add(info)
        }
        suggestedWords.mRawSuggestions?.removeFirst { it.word == word }

        val newSuggestedWords = SuggestedWords(
            suggestedWordInfos, suggestedWords.mRawSuggestions, suggestedWords.typedWordInfo, suggestedWords.mTypedWordValid,
            suggestedWords.mWillAutoCorrect, suggestedWords.mIsObsoleteSuggestions, suggestedWords.mInputStyle, suggestedWords.mSequenceNumber
        )
        setSuggestions(newSuggestedWords, direction != 1)
        suggestionsStrip.isVisible = true

        // Show the toolbar if no suggestions are left and the "Auto show toolbar" setting is enabled
        if (this.suggestedWords.isEmpty && Settings.getValues().mAutoShowToolbar) {
            setToolbarVisibility(true)
        }
    }

    private fun clear() {
        suggestionsStrip.removeAllViews()
        if (DEBUG_SUGGESTIONS) removeAllDebugInfoViews()
        if (!toolbarContainer.isVisible)
            suggestionsStrip.isVisible = true
        dismissMoreSuggestionsPanel()
        for (word in wordViews) {
            word.setOnTouchListener(null)
        }
    }

    private fun removeAllDebugInfoViews() {
        for (debugInfoView in debugInfoViews) {
            val parent = debugInfoView.parent
            if (parent is ViewGroup) {
                parent.removeView(debugInfoView)
            }
        }
    }

    private fun updateKeys() {
        val settingsValues = Settings.getValues()
        val toolbarVoiceKey = toolbar.findViewWithTag<View>(ToolbarKey.VOICE)
        if (toolbarVoiceKey != null) toolbarVoiceKey.isVisible = settingsValues.mShowsVoiceInputKey
        val pinnedVoiceKey = pinnedKeys.findViewWithTag<View>(ToolbarKey.VOICE)
        if (pinnedVoiceKey != null) pinnedVoiceKey.isVisible = settingsValues.mShowsVoiceInputKey

        val toolbarIsExpandable = settingsValues.mToolbarMode == ToolbarMode.EXPANDABLE
        if (settingsValues.mIncognitoModeEnabled) {
            toolbarExpandKey.setImageDrawable(incognitoIcon)
            toolbarExpandKey.isVisible = true
        } else {
            toolbarExpandKey.setImageDrawable(toolbarArrowIcon)
            toolbarExpandKey.isVisible = toolbarIsExpandable
        }

        // hide pinned keys if device is locked, and avoid expanding toolbar
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val hideToolbarKeys = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1)
            km.isDeviceLocked
        else
            km.isKeyguardLocked
        toolbarExpandKey.setOnClickListener(if (hideToolbarKeys || !toolbarIsExpandable) null else this)
        pinnedKeys.visibility = if (hideToolbarKeys) GONE else suggestionsStrip.visibility
        isExternalSuggestionVisible = false
    }

    private fun addKeyToPinnedKeys(pinnedKey: ToolbarKey) {
        val original = toolbar.findViewWithTag<ImageButton>(pinnedKey) ?: return
        // copy the original key to a new ImageButton
        val copy = ImageButton(context, null, R.attr.suggestionWordStyle)
        copy.tag = pinnedKey
        copy.scaleType = original.scaleType
        copy.scaleX = original.scaleX
        copy.scaleY = original.scaleY
        copy.contentDescription = original.contentDescription
        copy.setImageDrawable(original.drawable)
        copy.layoutParams = original.layoutParams
        copy.isActivated = original.isActivated
        setupKey(copy, Settings.getValues().mColors)
        pinnedKeys.addView(copy)
    }

    private fun setupKey(view: ImageButton, colors: Colors) {
        view.setOnClickListener(this)
        view.setOnLongClickListener(this)
        colors.setColor(view, ColorType.TOOL_BAR_KEY)
        colors.setBackground(view, ColorType.STRIP_BACKGROUND)
    }

    companion object {
        @JvmField
        var DEBUG_SUGGESTIONS = false
        private const val DEBUG_INFO_TEXT_SIZE_IN_DIP = 6.5f
        private val TAG = SuggestionStripView::class.java.simpleName
    }
}
