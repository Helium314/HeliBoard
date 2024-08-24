// SPDX-License-Identifier: GPL-3.0-only

package org.samyarth.oskey.keyboard.clipboard

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.samyarth.oskey.latin.ClipboardHistoryManager

class ClipboardHistoryRecyclerView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    var placeholderView: View? = null
    val historyManager: ClipboardHistoryManager? get() = (adapter as? ClipboardAdapter?)?.clipboardHistoryManager
    private val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
        override fun onMove(recyclerView: RecyclerView, viewHolder: ViewHolder, target: ViewHolder) = false
        override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: ViewHolder): Int {
            if (historyManager?.canRemove(viewHolder.absoluteAdapterPosition) == false)
                return 0 // block swipe for pinned items
            return super.getSwipeDirs(recyclerView, viewHolder)
        }
        override fun onSwiped(viewHolder: ViewHolder, dir: Int) {
            historyManager?.removeEntry(viewHolder.absoluteAdapterPosition)
            adapter?.notifyItemRemoved(viewHolder.absoluteAdapterPosition)
        }
    }).attachToRecyclerView(this)

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