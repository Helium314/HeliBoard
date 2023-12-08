// SPDX-License-Identifier: GPL-3.0-only

package org.dslul.openboard.inputmethod.keyboard.clipboard

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import org.dslul.openboard.inputmethod.keyboard.KeyboardActionListener
import org.dslul.openboard.inputmethod.keyboard.internal.KeyDrawParams
import org.dslul.openboard.inputmethod.keyboard.internal.KeyVisualAttributes
import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardIconsSet
import org.dslul.openboard.inputmethod.latin.ClipboardHistoryManager
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.common.BackgroundType
import org.dslul.openboard.inputmethod.latin.common.ColorType
import org.dslul.openboard.inputmethod.latin.common.Constants
import org.dslul.openboard.inputmethod.latin.settings.Settings
import org.dslul.openboard.inputmethod.latin.utils.ResourceUtils

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

    private lateinit var clipboardRecyclerView: ClipboardHistoryRecyclerView
    private lateinit var placeholderView: TextView
    private lateinit var alphabetKey: TextView
    private lateinit var clearKey: ImageButton
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
        functionalKeyBackgroundId = keyboardViewAttr.getResourceId(
                R.styleable.KeyboardView_functionalKeyBackground, keyBackgroundId)
        keyboardViewAttr.recycle()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val res = context.resources
        // The main keyboard expands to the entire this {@link KeyboardView}.
        val width = (ResourceUtils.getDefaultKeyboardWidth(res) + paddingLeft + paddingRight)
        val height = (ResourceUtils.getDefaultKeyboardHeight(res)
                + res.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
                + paddingTop + paddingBottom)
        setMeasuredDimension(width, height)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
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
        findViewById<FrameLayout>(R.id.clipboard_action_bar)?.apply {
            clipboardLayoutParams.setActionBarProperties(this)
        }
        alphabetKey = findViewById<TextView>(R.id.clipboard_keyboard_alphabet).apply {
            tag = Constants.CODE_ALPHA_FROM_CLIPBOARD
            setBackgroundResource(functionalKeyBackgroundId)
            setOnTouchListener(this@ClipboardHistoryView)
            setOnClickListener(this@ClipboardHistoryView)
        }
        clearKey = findViewById<ImageButton>(R.id.clipboard_clear).apply {
            setOnTouchListener(this@ClipboardHistoryView)
            setOnClickListener(this@ClipboardHistoryView)
            colorFilter = colors.setColorFilter(ColorType.CLEAR_KEY)
            colors.setBackgroundColor(background, BackgroundType.SUGGESTION, context)
        }
    }

    private fun setupAlphabetKey(key: TextView?, label: String, params: KeyDrawParams) {
        key?.apply {
            text = label
            typeface = params.mTypeface
            Settings.getInstance().current.mColors.setBackgroundColor(this.background, BackgroundType.FUNCTIONAL, context)
            setTextColor(params.mFunctionalTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, params.mLabelSize.toFloat())
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

    private fun setupClearKey(iconSet: KeyboardIconsSet) {
        val resId = iconSet.getIconResourceId(KeyboardIconsSet.NAME_CLEAR_CLIPBOARD_KEY)
        clearKey.setImageResource(resId)
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
        historyManager.prepareClipboardHistory()
        historyManager.setHistoryChangeListener(this)
        clipboardHistoryManager = historyManager
        clipboardAdapter.clipboardHistoryManager = historyManager

        val params = KeyDrawParams()
        params.updateParams(clipboardLayoutParams.actionBarContentHeight, keyVisualAttr)
        setupAlphabetKey(alphabetKey, switchToAlphaLabel, params)
        setupClipKey(params)
        setupClearKey(iconSet)

        placeholderView.apply {
            typeface = params.mTypeface
            setTextColor(params.mTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, params.mLabelSize.toFloat() * 2)
        }
        clipboardRecyclerView.apply {
            adapter = clipboardAdapter
        }
        Settings.getInstance().current.mColors.setKeyboardBackground(this, context)
    }

    fun stopClipboardHistory() {
        clipboardRecyclerView.adapter = null
        clipboardHistoryManager?.setHistoryChangeListener(null)
        clipboardHistoryManager = null
        clipboardAdapter.clipboardHistoryManager = null
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_DOWN) {
            return false
        }
        when (view) {
            alphabetKey -> keyboardActionListener?.onPressKey(
                    Constants.CODE_ALPHA_FROM_CLIPBOARD, 0 /* repeatCount */,
                    true /* isSinglePointer */)
            clearKey -> keyboardActionListener?.onPressKey(
                    Constants.CODE_UNSPECIFIED, 0 /* repeatCount */,
                    true /* isSinglePointer */)
        }
        // It's important to return false here. Otherwise, {@link #onClick} and touch-down visual
        // feedback stop working.
        return false
    }

    override fun onClick(view: View) {
        when (view) {
            alphabetKey -> {
                keyboardActionListener?.onCodeInput(Constants.CODE_ALPHA_FROM_CLIPBOARD,
                        Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE,
                        false /* isKeyRepeat */)
                keyboardActionListener?.onReleaseKey(Constants.CODE_ALPHA_FROM_CLIPBOARD,
                        false /* withSliding */)
            }
            clearKey -> {
                clipboardHistoryManager?.clearHistory()
                keyboardActionListener?.onReleaseKey(Constants.CODE_UNSPECIFIED,
                        false /* withSliding */)
            }
        }
    }

    override fun onKeyDown(clipId: Long) {
        keyboardActionListener?.onPressKey(Constants.CODE_UNSPECIFIED, 0 /* repeatCount */,
                true /* isSinglePointer */)
    }

    override fun onKeyUp(clipId: Long) {
        val clipContent = clipboardHistoryManager?.getHistoryEntryContent(clipId)
        keyboardActionListener?.onTextInput(clipContent?.content.toString())
        keyboardActionListener?.onReleaseKey(Constants.CODE_UNSPECIFIED,
                false /* withSliding */)
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