// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.keyboard.clipboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.KeyDrawParams
import helium314.keyboard.keyboard.internal.KeyVisualAttributes
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.ClipboardHistoryManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.createToolbarKey
import helium314.keyboard.latin.utils.getCodeForToolbarKey

class ClipboardHistoryView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyle: Int = R.attr.clipboardHistoryViewStyle
) : LinearLayout(context, attrs, defStyle), View.OnTouchListener, View.OnClickListener,
        ClipboardHistoryManager.OnHistoryChangeListener, OnKeyEventListener {

    private val clipboardLayoutParams = ClipboardLayoutParams(context.resources)
    private val pinIconId: Int
    private val functionalKeyBackgroundId: Int
    private val keyBackgroundId: Int
    private val spacebarBackground: Drawable
    private var initialized = false

    private lateinit var clipboardRecyclerView: ClipboardHistoryRecyclerView
    private lateinit var placeholderView: TextView
    private lateinit var alphabetKey: TextView
    private val toolbarKeys = mutableListOf<ImageButton>()
    private lateinit var clipboardAdapter: ClipboardAdapter
    private lateinit var spacebar: View
    private lateinit var deleteKey: ImageButton

    var keyboardActionListener: KeyboardActionListener? = null
    var clipboardHistoryManager: ClipboardHistoryManager? = null

    init {
        val clipboardViewAttr = context.obtainStyledAttributes(attrs,
                R.styleable.ClipboardHistoryView, defStyle, R.style.ClipboardHistoryView)
        pinIconId = clipboardViewAttr.getResourceId(R.styleable.ClipboardHistoryView_iconPinnedClip, 0)
        clipboardViewAttr.recycle()
        val keyboardViewAttr = context.obtainStyledAttributes(attrs, R.styleable.KeyboardView, defStyle, R.style.KeyboardView)
        keyBackgroundId = keyboardViewAttr.getResourceId(R.styleable.KeyboardView_keyBackground, 0)
        functionalKeyBackgroundId = keyboardViewAttr.getResourceId(R.styleable.KeyboardView_functionalKeyBackground, keyBackgroundId)
        spacebarBackground = Settings.getInstance().current.mColors.selectAndColorDrawable(keyboardViewAttr, ColorType.SPACE_BAR_BACKGROUND)
        keyboardViewAttr.recycle()
        val keyboardAttr = context.obtainStyledAttributes(attrs, R.styleable.Keyboard, defStyle, R.style.SuggestionStripView)
        // todo (maybe): setting the correct color only works because the activated state is inverted
        //  even when state is activated, the not activated color is set
        //   in suggestionStripView the same thing works correctly, wtf?
        //  need to properly fix it (and maybe undo the inverted isActivated) when adding a toggle key
        listOf(ToolbarKey.LEFT, ToolbarKey.RIGHT, ToolbarKey.COPY, ToolbarKey.CLEAR_CLIPBOARD, ToolbarKey.SELECT_WORD, ToolbarKey.SELECT_ALL)
            .forEach { toolbarKeys.add(createToolbarKey(context, keyboardAttr, it)) }
        keyboardAttr.recycle()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val res = context.resources
        // The main keyboard expands to the entire this {@link KeyboardView}.
        val width = ResourceUtils.getKeyboardWidth(res, Settings.getInstance().current) + paddingLeft + paddingRight
        val height = ResourceUtils.getKeyboardHeight(res, Settings.getInstance().current) + paddingTop + paddingBottom
        findViewById<LinearLayout>(R.id.action_bar)?.layoutParams?.width = width
        setMeasuredDimension(width, height)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initialize() { // needs to be delayed for access to ClipboardStrip, which is not a child of this view
        if (initialized) return
        val colors = Settings.getInstance().current.mColors
        clipboardAdapter = ClipboardAdapter(clipboardLayoutParams, this).apply {
            itemBackgroundId = keyBackgroundId
            pinnedIconResId = pinIconId
        }
        placeholderView = findViewById(R.id.clipboard_empty_view)
        clipboardRecyclerView = findViewById<ClipboardHistoryRecyclerView>(R.id.clipboard_list).apply {
            val colCount = resources.getInteger(R.integer.config_clipboard_keyboard_col_count)
            layoutManager = StaggeredGridLayoutManager(colCount, StaggeredGridLayoutManager.VERTICAL)
            @Suppress("deprecation") // "no cache" should be fine according to warning in https://developer.android.com/reference/android/view/ViewGroup#setPersistentDrawingCache(int)
            persistentDrawingCache = PERSISTENT_NO_CACHE
            clipboardLayoutParams.setListProperties(this)
            placeholderView = this@ClipboardHistoryView.placeholderView
        }
        alphabetKey = findViewById(R.id.key_alphabet)
        alphabetKey.setBackgroundResource(functionalKeyBackgroundId)
        alphabetKey.tag = Constants.CODE_ALPHA_FROM_CLIPBOARD
        alphabetKey.setOnTouchListener(this)
        alphabetKey.setOnClickListener(this)
        deleteKey = findViewById(R.id.key_delete)
        deleteKey.setBackgroundResource(functionalKeyBackgroundId)
        deleteKey.tag = Constants.CODE_DELETE
        deleteKey.setOnTouchListener(this)
        deleteKey.setOnClickListener(this)
        spacebar = findViewById(R.id.key_space)
        spacebar.background = spacebarBackground
        spacebar.tag = Constants.CODE_SPACE
        spacebar.setOnTouchListener(this)
        spacebar.setOnClickListener(this)
        // todo: add more buttons, like select all, arrow keys, copy, clear (and maybe start/end select?)
        val clipboardStrip = KeyboardSwitcher.getInstance().clipboardStrip
        toolbarKeys.forEach {
            clipboardStrip.addView(it)
            it.setOnTouchListener(this@ClipboardHistoryView)
            it.setOnClickListener(this@ClipboardHistoryView)
            colors.setColor(it, ColorType.TOOL_BAR_KEY)
            colors.setBackground(it, ColorType.STRIP_BACKGROUND)
        }
        initialized = true
    }

    private fun setupAlphabetKey(key: TextView, label: String, params: KeyDrawParams) {
        key.apply {
            text = label
            typeface = params.mTypeface
            Settings.getInstance().current.mColors.setBackground(this, ColorType.FUNCTIONAL_KEY_BACKGROUND)
            setTextColor(params.mFunctionalTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, params.mLabelSize.toFloat())
        }
    }

    private fun setupDeleteKey(key: ImageButton, iconId: Int) {
        key.apply {
            setImageResource(iconId)
            Settings.getInstance().current.mColors.setBackground(this, ColorType.FUNCTIONAL_KEY_BACKGROUND)
            Settings.getInstance().current.mColors.setColor(this, ColorType.KEY_ICON)
        }
    }

    private fun setupClipKey(params: KeyDrawParams) {
        clipboardAdapter.apply {
            itemBackgroundId = keyBackgroundId
            itemTypeFace = params.mTypeface
            itemTextColor = params.mTextColor
            itemTextSize = params.mLabelSize.toFloat()
        }
    }

    private fun setupToolbarKeys() {
        // set layout params
        val toolbarKeyLayoutParams = LayoutParams(getResources().getDimensionPixelSize(R.dimen.config_suggestions_strip_edge_key_width), LayoutParams.MATCH_PARENT)
        toolbarKeys.forEach { it.layoutParams = toolbarKeyLayoutParams }
    }

    fun setHardwareAcceleratedDrawingEnabled(enabled: Boolean) {
        if (!enabled) return
        // TODO: Should use LAYER_TYPE_SOFTWARE when hardware acceleration is off?
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun startClipboardHistory(
            historyManager: ClipboardHistoryManager,
            switchToAlphaLabel: String,
            keyVisualAttr: KeyVisualAttributes?,
            iconSet: KeyboardIconsSet
    ) {
        initialize()
        setupToolbarKeys()
        historyManager.prepareClipboardHistory()
        historyManager.setHistoryChangeListener(this)
        clipboardHistoryManager = historyManager
        clipboardAdapter.clipboardHistoryManager = historyManager
        findViewById<LinearLayout>(R.id.action_bar).apply {
            clipboardLayoutParams.setActionBarProperties(this)
        }

        val params = KeyDrawParams()
        params.updateParams(clipboardLayoutParams.actionBarContentHeight, keyVisualAttr)
        setupAlphabetKey(alphabetKey, switchToAlphaLabel, params)
        setupDeleteKey(deleteKey, iconSet.getIconResourceId(KeyboardIconsSet.NAME_DELETE_KEY))
        setupClipKey(params)

        placeholderView.apply {
            typeface = params.mTypeface
            setTextColor(params.mTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, params.mLabelSize.toFloat() * 2)
        }
        clipboardRecyclerView.apply {
            adapter = clipboardAdapter
            layoutParams.width = ResourceUtils.getKeyboardWidth(context.resources, Settings.getInstance().current)
        }
    }

    fun stopClipboardHistory() {
        if (!initialized) return
        clipboardRecyclerView.adapter = null
        clipboardHistoryManager?.setHistoryChangeListener(null)
        clipboardHistoryManager = null
        clipboardAdapter.clipboardHistoryManager = null
    }

    // the touch & click thing is used to provide haptic and audio feedback if enabled
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_DOWN) {
            return false
        }
        when (view) {
            alphabetKey, spacebar, deleteKey -> keyboardActionListener?.onPressKey(view.tag as Int, 0, true)
        }
        // It's important to return false here. Otherwise, {@link #onClick} and touch-down visual
        // feedback stop working.
        return false
    }

    override fun onClick(view: View) {
        when (view) {
            alphabetKey, spacebar, deleteKey -> {
                keyboardActionListener?.onCodeInput(view.tag as Int,
                    Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
                keyboardActionListener?.onReleaseKey(view.tag as Int, false)
            }
        }
        val tag = view.tag
        if (tag is ToolbarKey) {
            val code = getCodeForToolbarKey(tag)
            if (code != null) {
                keyboardActionListener?.onCodeInput(code, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
                return
            }
            if (tag == ToolbarKey.CLEAR_CLIPBOARD)
                clipboardHistoryManager?.clearHistory()
        }
    }

    override fun onKeyDown(clipId: Long) {
        keyboardActionListener?.onPressKey(Constants.CODE_UNSPECIFIED, 0, true)
    }

    override fun onKeyUp(clipId: Long) {
        val clipContent = clipboardHistoryManager?.getHistoryEntryContent(clipId)
        keyboardActionListener?.onTextInput(clipContent?.content.toString())
        keyboardActionListener?.onReleaseKey(Constants.CODE_UNSPECIFIED, false)
    }

    override fun onClipboardHistoryEntryAdded(at: Int) {
        clipboardAdapter.notifyItemInserted(at)
        clipboardRecyclerView.smoothScrollToPosition(at)
    }

    override fun onClipboardHistoryEntriesRemoved(pos: Int, count: Int) {
        clipboardAdapter.notifyItemRangeRemoved(pos, count)
    }

    override fun onClipboardHistoryEntryMoved(from: Int, to: Int) {
        clipboardAdapter.notifyItemMoved(from, to)
        clipboardAdapter.notifyItemChanged(to)
        if (to < from) clipboardRecyclerView.smoothScrollToPosition(to)
    }
}