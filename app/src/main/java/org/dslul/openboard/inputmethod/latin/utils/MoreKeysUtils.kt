// SPDX-License-Identifier: GPL-3.0-only
package org.dslul.openboard.inputmethod.latin.utils

import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.dslul.openboard.inputmethod.keyboard.Key
import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardParams
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.floris.PopupSet
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.rtlLabel
import org.dslul.openboard.inputmethod.latin.R
import java.util.Collections

private const val MORE_KEYS_NUMBER = "more_keys_number"
private const val MORE_KEYS_LANGUAGE_PRIORITY = "more_keys_language_priority"
private const val MORE_KEYS_LAYOUT = "more_keys_layout"
private const val MORE_KEYS_SYMBOLS = "more_keys_symbols"
private const val MORE_KEYS_LANGUAGE = "more_keys_language"
const val MORE_KEYS_LABEL_DEFAULT = "$MORE_KEYS_NUMBER,true;$MORE_KEYS_LANGUAGE_PRIORITY,false;$MORE_KEYS_LAYOUT,true;$MORE_KEYS_SYMBOLS,true;$MORE_KEYS_LANGUAGE,false"
const val MORE_KEYS_ORDER_DEFAULT = "$MORE_KEYS_LANGUAGE_PRIORITY,true;$MORE_KEYS_NUMBER,true;$MORE_KEYS_SYMBOLS,true;$MORE_KEYS_LAYOUT,true;$MORE_KEYS_LANGUAGE,true"

// todo:
//  take moreKeys from symbols layout (in a separate commit)
//   maybe also add a (simple) parser cache... or cache the layout somewhere else?
//   that might be annoying with base and full layout (functional keys and spacers)
//   because base layout not available later... put it to keyParams?
//   or create symbol moreKeys in the parser? that should work best, there we have proper access to layouts

fun createMoreKeysArray(popupSet: PopupSet<*>?, params: KeyboardParams, label: String): Array<String>? {
    val moreKeys = mutableSetOf<String>()
    params.mMoreKeyTypes.forEach { type ->
        when (type) {
            MORE_KEYS_NUMBER -> params.mLocaleKeyTexts.getNumberLabel(popupSet?.numberIndex)?.let { moreKeys.add(it) }
            MORE_KEYS_LAYOUT -> popupSet?.getPopupKeyLabels(params)?.let { moreKeys.addAll(it) }
            MORE_KEYS_SYMBOLS -> {} // todo
            MORE_KEYS_LANGUAGE -> params.mLocaleKeyTexts.getMoreKeys(label)?.let { moreKeys.addAll(it) }
            MORE_KEYS_LANGUAGE_PRIORITY -> params.mLocaleKeyTexts.getPriorityMoreKeys(label)?.let { moreKeys.addAll(it) }
        }
    }
    if (moreKeys.isEmpty()) return null
    val fco = moreKeys.firstOrNull { it.startsWith(Key.MORE_KEYS_FIXED_COLUMN_ORDER) }
    if (fco != null && fco.substringAfter(Key.MORE_KEYS_FIXED_COLUMN_ORDER).toIntOrNull() != moreKeys.size - 1) {
        moreKeys.remove(fco) // maybe rather adjust the number instead of remove?
    }
    // autoColumnOrder should be fine

    val array = moreKeys.toTypedArray()
    for (i in array.indices) {
        array[i] = transformLabel(array[i], params)
    }
    return array
}

fun getHintLabel(popupSet: PopupSet<*>?, params: KeyboardParams, label: String): String? {
    var hintLabel: String? = null
    for (type in params.mMoreKeyLabelSources) {
        when (type) {
            MORE_KEYS_NUMBER -> params.mLocaleKeyTexts.getNumberLabel(popupSet?.numberIndex)?.let { hintLabel = it }
            MORE_KEYS_LAYOUT -> popupSet?.getPopupKeyLabels(params)?.let { hintLabel = it.firstOrNull() }
            MORE_KEYS_SYMBOLS -> {} // todo
            MORE_KEYS_LANGUAGE -> params.mLocaleKeyTexts.getMoreKeys(label)?.let { hintLabel = it.firstOrNull() }
            MORE_KEYS_LANGUAGE_PRIORITY -> params.mLocaleKeyTexts.getPriorityMoreKeys(label)?.let { hintLabel = it.firstOrNull() }
        }
        if (hintLabel != null) break
    }

    // avoid e.g. !autoColumnOrder! as label
    //  this will avoid having labels on comma and period keys
    return hintLabel?.let { transformLabel(it, params) }
        ?.takeIf { !it.startsWith("!") || it == "!" }
}

private fun transformLabel(label: String, params: KeyboardParams): String =
    if (label == "$$$") { // currency key
        if (params.mId.passwordInput()) "$"
        else params.mLocaleKeyTexts.currencyKey.first
    } else if (params.mId.mSubtype.isRtlSubtype) {
        label.rtlLabel(params)
    } else label

/** returns a list of enabled more keys for pref [key] */
fun getEnabledMoreKeys(prefs: SharedPreferences, key: String, defaultSetting: String): List<String> {
    return prefs.getString(key, defaultSetting)?.split(";")?.mapNotNull {
        val split = it.split(",")
        if (split.last() == "true") split.first() else null
    } ?: emptyList()
}

/**
 *  show a dialog that allows re-ordering and dis/enabling the more keys list for the pref [key]
 *  see e.g. [MORE_KEYS_LABEL_DEFAULT] for the internally used format
 */
fun reorderMoreKeysDialog(context: Context, key: String, defaultSetting: String, title: Int) {
    val prefs = DeviceProtectedUtils.getSharedPreferences(context)
    val orderedItems = prefs.getString(key, defaultSetting)!!.split(";").mapTo(ArrayList()) {
        val both = it.split(",")
        both.first() to both.last().toBoolean()
    }
    val rv = RecyclerView(context)
    rv.setPadding(30, 10, 10, 10)
    rv.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
    val callback = object : DiffUtil.ItemCallback<Pair<String, Boolean>>() {
        override fun areItemsTheSame(p0: Pair<String, Boolean>, p1: Pair<String, Boolean>) = p0 == p1
        override fun areContentsTheSame(p0: Pair<String, Boolean>, p1: Pair<String, Boolean>) = p0 == p1
    }
    val adapter = object : ListAdapter<Pair<String, Boolean>, RecyclerView.ViewHolder>(callback) {
        override fun onCreateViewHolder(p0: ViewGroup, p1: Int): RecyclerView.ViewHolder {
            val b = LayoutInflater.from(context).inflate(R.layout.morekeys_list_item, rv, false)
            // wtf? this results in transparent background, but when the background is set in xml it's fine?
            // but of course when setting in xml i need to duplicate the entire thing except for background because of api things
            // why tf is it so complicated to just use the dialog's background?
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                b.setBackgroundColor(android.R.attr.colorBackgroundFloating)
//            }
            return object : RecyclerView.ViewHolder(b) { }
        }
        override fun onBindViewHolder(p0: RecyclerView.ViewHolder, p1: Int) {
            val (text, wasChecked) = orderedItems[p1]
            val displayTextId = context.resources.getIdentifier(text, "string", context.packageName)
            val displayText = if (displayTextId == 0) text else context.getString(displayTextId)
            p0.itemView.findViewById<TextView>(R.id.morekeys_type)?.text = displayText
            val switch = p0.itemView.findViewById<SwitchCompat>(R.id.morekeys_switch)
            switch?.isChecked = wasChecked
            switch?.setOnCheckedChangeListener { _, isChecked ->
                val position = orderedItems.indexOfFirst { it.first == text }
                orderedItems[position] = text to isChecked
            }
        }
    }
    rv.adapter = adapter
    ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
        override fun onMove(p0: RecyclerView, p1: RecyclerView.ViewHolder, p2: RecyclerView.ViewHolder): Boolean {
            val pos1 = p1.absoluteAdapterPosition
            val pos2 = p2.absoluteAdapterPosition
            Collections.swap(orderedItems, pos1, pos2)
            adapter.notifyItemMoved(pos1, pos2)
            return true
        }
        override fun onSwiped(p0: RecyclerView.ViewHolder, p1: Int) { }
    }).attachToRecyclerView(rv)
    adapter.submitList(orderedItems)
    AlertDialog.Builder(context)
        .setTitle(title)
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
