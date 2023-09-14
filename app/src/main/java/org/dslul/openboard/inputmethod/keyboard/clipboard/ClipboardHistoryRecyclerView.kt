package org.dslul.openboard.inputmethod.keyboard.clipboard

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView

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

}