package helium314.keyboard.latin.utils

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import helium314.keyboard.latin.R
import java.util.Collections

fun getPlatformDialogThemeContext(context: Context): Context {
    // Because {@link AlertDialog.Builder.create()} doesn't honor the specified theme with
    // createThemeContextWrapper=false, the result dialog box has unneeded paddings around it.
    return ContextThemeWrapper(context, R.style.platformActivityTheme)
}

fun confirmDialog(context: Context, message: String, confirmButton: String, onConfirmed: (() -> Unit)) {
    AlertDialog.Builder(context)
        .setMessage(message)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(confirmButton) { _, _ -> onConfirmed() }
        .show()
}

fun infoDialog(context: Context, messageId: Int) {
    AlertDialog.Builder(context)
        .setMessage(messageId)
        .setNegativeButton(android.R.string.ok, null)
        .show()
}

fun infoDialog(context: Context, message: String) {
    AlertDialog.Builder(context)
        .setMessage(message)
        .setNegativeButton(android.R.string.ok, null)
        .show()
}

/**
 *  Show a dialog that allows re-ordering and dis/enabling items (currently toolbar keys and popup keys).
 *  The items are stored in a string pref in [key]. Each item contains a name and true/false, comma-separated.
 *  Items are semicolon-separated, see e.g. [POPUP_KEYS_LABEL_DEFAULT] for an example.
 */
// this should probably be a class
fun reorderDialog(
    context: Context,
    key: String,
    defaultSetting: String,
    @StringRes dialogTitleId: Int,
    getIcon: (String) -> Drawable? = { null }
) {
    val prefs = DeviceProtectedUtils.getSharedPreferences(context)
    val orderedItems = prefs.getString(key, defaultSetting)!!.split(";").mapTo(ArrayList()) {
        val both = it.split(",")
        both.first() to both.last().toBoolean()
    }
    val rv = RecyclerView(context)
    val bgColor = ContextCompat.getColor(context, R.color.sliding_items_background)
    val padding = ResourceUtils.toPx(8, context.resources)
    rv.setPadding(3 * padding, padding, padding, padding)
    rv.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)

    val callback = object : DiffUtil.ItemCallback<Pair<String, Boolean>>() {
        override fun areItemsTheSame(p0: Pair<String, Boolean>, p1: Pair<String, Boolean>) = p0 == p1
        override fun areContentsTheSame(p0: Pair<String, Boolean>, p1: Pair<String, Boolean>) = p0 == p1
    }

    val adapter = object : ListAdapter<Pair<String, Boolean>, RecyclerView.ViewHolder>(callback) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val b = LayoutInflater.from(context).inflate(R.layout.reorder_dialog_item, rv, false)
            b.setBackgroundColor(bgColor)
            return object : RecyclerView.ViewHolder(b) { }
        }
        override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
            val (text, wasChecked) = orderedItems[position]
            val displayText = text.lowercase().getStringResourceOrName("", context)
            viewHolder.itemView.findViewById<TextView>(R.id.reorder_item_name)?.text = displayText
            val switch = viewHolder.itemView.findViewById<Switch>(R.id.reorder_item_switch)
            switch?.setOnCheckedChangeListener(null)
            switch?.isChecked = wasChecked
            switch?.setOnCheckedChangeListener { _, isChecked ->
                val pos = orderedItems.indexOfFirst { it.first == text }
                orderedItems[pos] = text to isChecked
            }
            val icon = getIcon(text)
            viewHolder.itemView.findViewById<ImageView>(R.id.reorder_item_icon)?.let {
                it.visibility = if (icon == null) View.GONE else View.VISIBLE
                it.setImageDrawable(icon)
            }
        }
    }
    rv.adapter = adapter

    ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
        override fun onMove(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            val pos1 = viewHolder.absoluteAdapterPosition
            val pos2 = target.absoluteAdapterPosition
            Collections.swap(orderedItems, pos1, pos2)
            adapter.notifyItemMoved(pos1, pos2)
            return true
        }
        override fun onSwiped(rv: RecyclerView.ViewHolder, direction: Int) { }
    }).attachToRecyclerView(rv)

    adapter.submitList(orderedItems)

    AlertDialog.Builder(context)
        .setTitle(dialogTitleId)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            val value = orderedItems.joinToString(";") { it.first + "," + it.second }
            prefs.edit().putString(key, value).apply()
        }
        .setNegativeButton(android.R.string.cancel, null)
        .setNeutralButton(R.string.button_default) { _, _ ->
            prefs.edit().remove(key).apply()
        }
        .setView(rv)
        .show()
}
