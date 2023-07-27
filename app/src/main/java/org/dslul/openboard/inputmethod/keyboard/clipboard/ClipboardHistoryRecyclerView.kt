package org.dslul.openboard.inputmethod.keyboard.clipboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import org.dslul.openboard.inputmethod.latin.settings.Settings

class ClipboardHistoryRecyclerView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    var placeholderView: View? = null

    private val adapterDataObserver: AdapterDataObserver = object : AdapterDataObserver() {

        override fun onChanged() {
            checkAdapterContentChange()
        }

        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
            checkAdapterContentChange()
        }


        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            checkAdapterContentChange()
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            checkAdapterContentChange()
        }

    }

    private fun checkAdapterContentChange() {
        if (placeholderView == null) return
        val adapterIsEmpty = adapter == null || adapter?.itemCount == 0
        if (this@ClipboardHistoryRecyclerView.visibility == VISIBLE && adapterIsEmpty) {
            placeholderView!!.visibility = VISIBLE
            this@ClipboardHistoryRecyclerView.visibility = INVISIBLE
        } else if (this@ClipboardHistoryRecyclerView.visibility == INVISIBLE && !adapterIsEmpty) {
            placeholderView!!.visibility = INVISIBLE
            this@ClipboardHistoryRecyclerView.visibility = VISIBLE
        }
    }

    override fun setAdapter(adapter: Adapter<*>?) {
        this.adapter?.unregisterAdapterDataObserver(adapterDataObserver)
        super.setAdapter(adapter)
        checkAdapterContentChange()
        adapter?.registerAdapterDataObserver(adapterDataObserver)
    }

    class BottomDividerItemDecoration(dividerHeight: Int, dividerColor: Int) : RecyclerView.ItemDecoration() {

        private val paint = Paint()

        init {
            paint.color = dividerColor
            paint.strokeWidth = dividerHeight.toFloat()
            val colors = Settings.getInstance().current.mColors
            if (colors.isCustom)
                paint.colorFilter = colors.backgroundFilter
        }

        override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: State) {
            super.onDrawOver(canvas, parent, state)
            canvas.drawLine(parent.paddingLeft.toFloat(),
                    parent.height - paint.strokeWidth / 2,
                    parent.width.toFloat() - parent.paddingRight.toFloat(),
                    parent.height - paint.strokeWidth / 2 ,
                    paint
            )
        }

    }
}