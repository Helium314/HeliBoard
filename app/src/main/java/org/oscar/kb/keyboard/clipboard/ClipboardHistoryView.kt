// SPDX-License-Identifier: GPL-3.0-only

package org.oscar.kb.keyboard.clipboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import org.oscar.kb.R
import org.oscar.kb.keyboard.KeyboardActionListener
import org.oscar.kb.keyboard.KeyboardSwitcher
import org.oscar.kb.keyboard.internal.KeyDrawParams
import org.oscar.kb.keyboard.internal.KeyVisualAttributes
import org.oscar.kb.keyboard.internal.KeyboardIconsSet
import org.oscar.kb.keyboard.internal.keyboard_parser.floris.KeyCode
import org.oscar.kb.latin.ClipboardHistoryManager
import org.oscar.kb.keyboard.KeyboardId
import org.oscar.kb.keyboard.KeyboardLayoutSet
import org.oscar.kb.keyboard.MainKeyboardView
import org.oscar.kb.keyboard.PointerTracker
import org.oscar.kb.latin.common.ColorType
import org.oscar.kb.latin.common.Constants
import org.oscar.kb.latin.settings.Settings
import org.oscar.kb.latin.utils.DeviceProtectedUtils
import org.oscar.kb.latin.utils.ResourceUtils
import org.oscar.kb.latin.utils.ToolbarKey
import org.oscar.kb.latin.utils.createToolbarKey
import org.oscar.kb.latin.utils.getCodeForToolbarKey
import org.oscar.kb.latin.utils.getCodeForToolbarKeyLongClick
import org.oscar.kb.latin.utils.getEnabledClipboardToolbarKeys


@SuppressLint("CustomViewStyleable")
class ClipboardHistoryView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyle: Int = R.attr.clipboardHistoryViewStyle
) : LinearLayout(context, attrs, defStyle), View.OnClickListener,
        ClipboardHistoryManager.OnHistoryChangeListener, OnKeyEventListener, View.OnLongClickListener {

    private val clipboardLayoutParams = ClipboardLayoutParams(context.resources)
    private val pinIconId: Int
    private val keyBackgroundId: Int
    private var initialized = false

    private lateinit var clipboardRecyclerView: ClipboardHistoryRecyclerView
    private lateinit var placeholderView: TextView
    private val toolbarKeys = mutableListOf<ImageButton>()
    private lateinit var clipboardAdapter: ClipboardAdapter

    var keyboardActionListener: KeyboardActionListener? = null
    var clipboardHistoryManager: ClipboardHistoryManager? = null

    init {
        val clipboardViewAttr = context.obtainStyledAttributes(attrs,
                R.styleable.ClipboardHistoryView, defStyle, R.style.ClipboardHistoryView)
        pinIconId = clipboardViewAttr.getResourceId(R.styleable.ClipboardHistoryView_iconPinnedClip, 0)
        clipboardViewAttr.recycle()
        val keyboardViewAttr = context.obtainStyledAttributes(attrs, R.styleable.KeyboardView, defStyle, R.style.KeyboardView)
        keyBackgroundId = keyboardViewAttr.getResourceId(R.styleable.KeyboardView_keyBackground, 0)
        keyboardViewAttr.recycle()
        val keyboardAttr = context.obtainStyledAttributes(attrs, R.styleable.Keyboard, defStyle, R.style.SuggestionStripView)
        // todo (maybe): setting the correct color only works because the activated state is inverted
        //  even when state is activated, the not activated color is set
        //   in suggestionStripView the same thing works correctly, wtf?
        //  need to properly fix it (and maybe undo the inverted isActivated) when adding a toggle key
        getEnabledClipboardToolbarKeys(DeviceProtectedUtils.getSharedPreferences(context))
            .forEach { toolbarKeys.add(createToolbarKey(context, KeyboardIconsSet.instance, it)) }
        keyboardAttr.recycle()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val res = context.resources
        // The main keyboard expands to the entire this {@link KeyboardView}.
        val width = ResourceUtils.getKeyboardWidth(res, Settings.getInstance().current) + paddingLeft + paddingRight
        val height = ResourceUtils.getKeyboardHeight(res, Settings.getInstance().current) + paddingTop + paddingBottom
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
        val clipboardStrip = KeyboardSwitcher.getInstance().clipboardStrip
        toolbarKeys.forEach {
            clipboardStrip.addView(it)
            it.setOnClickListener(this@ClipboardHistoryView)
            it.setOnLongClickListener(this@ClipboardHistoryView)
            colors.setColor(it, ColorType.TOOL_BAR_KEY)
            colors.setBackground(it, ColorType.STRIP_BACKGROUND)
        }
        initialized = true
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
        val toolbarKeyLayoutParams = LayoutParams(resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_edge_key_width), LayoutParams.MATCH_PARENT)
        toolbarKeys.forEach { it.layoutParams = toolbarKeyLayoutParams }
    }

    private fun setupBottomRowKeyboard(editorInfo: EditorInfo, listener: KeyboardActionListener) {
        val keyboardView = findViewById<MainKeyboardView>(R.id.bottom_row_keyboard)
        keyboardView.setKeyboardActionListener(listener)
        PointerTracker.switchTo(keyboardView)
        val kls = KeyboardLayoutSet.Builder.buildEmojiClipBottomRow(context, editorInfo)
        val keyboard = kls.getKeyboard(KeyboardId.ELEMENT_CLIPBOARD_BOTTOM_ROW)
        keyboardView.setKeyboard(keyboard)
    }

    fun setHardwareAcceleratedDrawingEnabled(enabled: Boolean) {
        if (!enabled) return
        // TODO: Should use LAYER_TYPE_SOFTWARE when hardware acceleration is off?
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun startClipboardHistory(
            historyManager: ClipboardHistoryManager,
            keyVisualAttr: KeyVisualAttributes?,
            editorInfo: EditorInfo,
            keyboardActionListener: KeyboardActionListener
    ) {
        initialize()
        setupToolbarKeys()
        historyManager.prepareClipboardHistory()
        historyManager.setHistoryChangeListener(this)
        clipboardHistoryManager = historyManager
        clipboardAdapter.clipboardHistoryManager = historyManager

        val params = KeyDrawParams()
        params.updateParams(clipboardLayoutParams.bottomRowKeyboardHeight, keyVisualAttr)
        setupClipKey(params)
        setupBottomRowKeyboard(editorInfo, keyboardActionListener)

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

    override fun onClick(view: View) {
        val tag = view.tag
        if (tag is ToolbarKey) {
            val code = getCodeForToolbarKey(tag)
            if (code != KeyCode.UNSPECIFIED) {
                keyboardActionListener?.onCodeInput(code, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
                return
            }
        }
    }

    override fun onLongClick(view: View): Boolean {
        val tag = view.tag
        if (tag is ToolbarKey) {
            val longClickCode = getCodeForToolbarKeyLongClick(tag)
            if (longClickCode != KeyCode.UNSPECIFIED) {
                keyboardActionListener?.onCodeInput(
                    longClickCode,
                    Constants.NOT_A_COORDINATE,
                    Constants.NOT_A_COORDINATE,
                    false
                )
            }
            return true
        }
        return false
    }

    override fun onKeyDown(clipId: Long) {
        keyboardActionListener?.onPressKey(KeyCode.NOT_SPECIFIED, 0, true)
    }

    override fun onKeyUp(clipId: Long) {
        val clipContent = clipboardHistoryManager?.getHistoryEntryContent(clipId)
        keyboardActionListener?.onTextInput(clipContent?.content.toString())
        keyboardActionListener?.onReleaseKey(KeyCode.NOT_SPECIFIED, false)
        if (Settings.getInstance().current.mAlphaAfterClipHistoryEntry)
            keyboardActionListener?.onCodeInput(KeyCode.ALPHA, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
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