package org.dslul.openboard.inputmethod.latin

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import org.dslul.openboard.inputmethod.keyboard.KeyboardActionListener
import org.dslul.openboard.inputmethod.latin.common.BackgroundType
import org.dslul.openboard.inputmethod.latin.common.Constants
import org.dslul.openboard.inputmethod.latin.settings.Settings

class KeyboardWrapperView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle), View.OnClickListener {

    var keyboardActionListener: KeyboardActionListener? = null

    private lateinit var stopOneHandedModeBtn: ImageButton
    private lateinit var switchOneHandedModeBtn: ImageButton
    private lateinit var keyboardView: View
    private val iconStopOneHandedModeId: Int
    private val iconSwitchOneHandedModeId: Int

    var oneHandedModeEnabled = false
        set(enabled) {
            field = enabled
            updateViewsVisibility()
            requestLayout()
        }

    var oneHandedGravity = Gravity.NO_GRAVITY
        set(value) {
            field = value
            updateSwitchButtonSide()
            requestLayout()
        }


    override fun onFinishInflate() {
        super.onFinishInflate()
        stopOneHandedModeBtn = findViewById(R.id.btn_stop_one_handed_mode)
        stopOneHandedModeBtn.setImageResource(iconStopOneHandedModeId)
        stopOneHandedModeBtn.visibility = GONE
        switchOneHandedModeBtn = findViewById(R.id.btn_switch_one_handed_mode)
        switchOneHandedModeBtn.setImageResource(iconSwitchOneHandedModeId)
        switchOneHandedModeBtn.visibility = GONE
        keyboardView = findViewById(R.id.keyboard_view)

        stopOneHandedModeBtn.setOnClickListener(this)
        switchOneHandedModeBtn.setOnClickListener(this)

        val colors = Settings.getInstance().current.mColors
        stopOneHandedModeBtn.colorFilter = colors.keyTextFilter
        switchOneHandedModeBtn.colorFilter = colors.keyTextFilter
        colors.setBackgroundColor(stopOneHandedModeBtn.background, BackgroundType.BACKGROUND)
        colors.setBackgroundColor(switchOneHandedModeBtn.background, BackgroundType.BACKGROUND)
        setBackgroundColor(Color.WHITE) // otherwise background might be null
        colors.setKeyboardBackground(this)
    }

    @SuppressLint("RtlHardcoded")
    fun switchOneHandedModeSide() {
        oneHandedGravity = if (oneHandedGravity == Gravity.LEFT) Gravity.RIGHT else Gravity.LEFT
    }

    private fun updateViewsVisibility() {
        stopOneHandedModeBtn.visibility = if (oneHandedModeEnabled) VISIBLE else GONE
        switchOneHandedModeBtn.visibility = if (oneHandedModeEnabled) VISIBLE else GONE
    }

    @SuppressLint("RtlHardcoded")
    private fun updateSwitchButtonSide() {
        switchOneHandedModeBtn.scaleX = if (oneHandedGravity == Gravity.LEFT) -1f else 1f
    }

    override fun onClick(view: View) {
        if (view === stopOneHandedModeBtn) {
            keyboardActionListener?.onCodeInput(Constants.CODE_STOP_ONE_HANDED_MODE,
                Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE,
                false /* isKeyRepeat */)
        } else if (view === switchOneHandedModeBtn) {
            keyboardActionListener?.onCodeInput(Constants.CODE_SWITCH_ONE_HANDED_MODE,
                Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE,
                false /* isKeyRepeat */)
        }
    }

    @SuppressLint("RtlHardcoded")
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (!oneHandedModeEnabled) {
            super.onLayout(changed, left, top, right, bottom)
            return
        }

        val isLeftGravity = oneHandedGravity == Gravity.LEFT
        val width = right - left
        val spareWidth = width - keyboardView.measuredWidth

        val keyboardLeft = if (isLeftGravity) 0 else spareWidth
        keyboardView.layout(
                keyboardLeft,
                0,
                keyboardLeft + keyboardView.measuredWidth,
                keyboardView.measuredHeight
        )

        val scale = Settings.getInstance().current.mKeyboardHeightScale
        // scale one-handed mode button height if keyboard height scale is < 80%
        // more relevant: also change the distance, so the buttons are actually visible
        val heightScale = scale + 0.2f
        val buttonsLeft = if (isLeftGravity) keyboardView.measuredWidth else 0
        stopOneHandedModeBtn.layout(
                buttonsLeft + (spareWidth - stopOneHandedModeBtn.measuredWidth) / 2,
                (heightScale * stopOneHandedModeBtn.measuredHeight / 2).toInt(),
                buttonsLeft + (spareWidth + stopOneHandedModeBtn.measuredWidth) / 2,
                (heightScale * 3 * stopOneHandedModeBtn.measuredHeight / 2).toInt()
        )
        switchOneHandedModeBtn.layout(
                buttonsLeft + (spareWidth - switchOneHandedModeBtn.measuredWidth) / 2,
                (heightScale * 2 * stopOneHandedModeBtn.measuredHeight).toInt(),
                buttonsLeft + (spareWidth + switchOneHandedModeBtn.measuredWidth) / 2,
                (heightScale * (2 * stopOneHandedModeBtn.measuredHeight + switchOneHandedModeBtn.measuredHeight)).toInt()
        )
    }

    init {
        @SuppressLint("CustomViewStyleable")
        val keyboardAttr = context.obtainStyledAttributes(attrs, R.styleable.Keyboard, defStyle, R.style.Keyboard)
        iconStopOneHandedModeId = keyboardAttr.getResourceId(R.styleable.Keyboard_iconStopOneHandedMode, 0)
        iconSwitchOneHandedModeId = keyboardAttr.getResourceId(R.styleable.Keyboard_iconSwitchOneHandedMode, 0)
        keyboardAttr.recycle()
    }
}
