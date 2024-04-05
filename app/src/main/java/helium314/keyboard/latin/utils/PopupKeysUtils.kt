// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.internal.KeySpecParser
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.PopupSet
import helium314.keyboard.keyboard.internal.keyboard_parser.rtlLabel
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import java.util.Collections

const val POPUP_KEYS_NUMBER = "popup_keys_number"
private const val POPUP_KEYS_LANGUAGE_PRIORITY = "popup_keys_language_priority"
const val POPUP_KEYS_LAYOUT = "popup_keys_layout"
private const val POPUP_KEYS_SYMBOLS = "popup_keys_symbols"
private const val POPUP_KEYS_LANGUAGE = "popup_keys_language"
const val POPUP_KEYS_LABEL_DEFAULT = "$POPUP_KEYS_NUMBER,true;$POPUP_KEYS_LANGUAGE_PRIORITY,false;$POPUP_KEYS_LAYOUT,true;$POPUP_KEYS_SYMBOLS,true;$POPUP_KEYS_LANGUAGE,false"
const val POPUP_KEYS_ORDER_DEFAULT = "$POPUP_KEYS_LANGUAGE_PRIORITY,true;$POPUP_KEYS_NUMBER,true;$POPUP_KEYS_SYMBOLS,true;$POPUP_KEYS_LAYOUT,true;$POPUP_KEYS_LANGUAGE,true"

private val allPopupKeyTypes = listOf(POPUP_KEYS_NUMBER, POPUP_KEYS_LAYOUT, POPUP_KEYS_SYMBOLS, POPUP_KEYS_LANGUAGE, POPUP_KEYS_LANGUAGE_PRIORITY)

fun createPopupKeysArray(popupSet: PopupSet<*>?, params: KeyboardParams, label: String): Array<String>? {
    // often PopupKeys are empty, so we want to avoid unnecessarily creating sets
    val popupKeysDelegate = lazy { mutableSetOf<String>() }
    val popupKeys by popupKeysDelegate
    val types = if (params.mId.isAlphabetKeyboard) params.mPopupKeyTypes else allPopupKeyTypes
    types.forEach { type ->
        when (type) {
            POPUP_KEYS_NUMBER -> params.mLocaleKeyboardInfos.getNumberLabel(popupSet?.numberIndex)?.let { popupKeys.add(it) }
            POPUP_KEYS_LAYOUT -> popupSet?.getPopupKeyLabels(params)?.let { popupKeys.addAll(it) }
            POPUP_KEYS_SYMBOLS -> popupSet?.symbol?.let { popupKeys.add(it) }
            POPUP_KEYS_LANGUAGE -> params.mLocaleKeyboardInfos.getPopupKeys(label)?.let { popupKeys.addAll(it) }
            POPUP_KEYS_LANGUAGE_PRIORITY -> params.mLocaleKeyboardInfos.getPriorityPopupKeys(label)?.let { popupKeys.addAll(it) }
        }
    }
    if (!popupKeysDelegate.isInitialized() || popupKeys.isEmpty())
        return null
    val fco = popupKeys.firstOrNull { it.startsWith(Key.POPUP_KEYS_FIXED_COLUMN_ORDER) }
    if (fco != null && fco.substringAfter(Key.POPUP_KEYS_FIXED_COLUMN_ORDER).toIntOrNull() != popupKeys.size - 1) {
        val fcoExpected = popupKeys.size - popupKeys.count { it.startsWith("!") && it.endsWith("!") } - 1
        if (fco.substringAfter(Key.POPUP_KEYS_FIXED_COLUMN_ORDER).toIntOrNull() != fcoExpected)
            popupKeys.remove(fco) // maybe rather adjust the number instead of remove?
    }
    if (popupKeys.size > 1 && (label == "(" || label == ")")) { // add fixed column order for that case (typically other variants of brackets / parentheses
        // not really fast, but no other way to add first in a LinkedHashSet
        val tmp = popupKeys.toList()
        popupKeys.clear()
        popupKeys.add("${Key.POPUP_KEYS_FIXED_COLUMN_ORDER}${tmp.size}")
        popupKeys.addAll(tmp)
    }
    // autoColumnOrder should be fine

    val array = popupKeys.toTypedArray()
    for (i in array.indices) {
        array[i] = transformLabel(array[i], params)
    }
    return array
}

fun getHintLabel(popupSet: PopupSet<*>?, params: KeyboardParams, label: String): String? {
    var hintLabel: String? = null
    for (type in params.mPopupKeyLabelSources) {
        when (type) {
            POPUP_KEYS_NUMBER -> params.mLocaleKeyboardInfos.getNumberLabel(popupSet?.numberIndex)?.let { hintLabel = it }
            POPUP_KEYS_LAYOUT -> popupSet?.getPopupKeyLabels(params)?.let { hintLabel = it.firstOrNull() }
            POPUP_KEYS_SYMBOLS -> popupSet?.symbol?.let { hintLabel = it }
            POPUP_KEYS_LANGUAGE -> params.mLocaleKeyboardInfos.getPopupKeys(label)?.let { hintLabel = it.firstOrNull() }
            POPUP_KEYS_LANGUAGE_PRIORITY -> params.mLocaleKeyboardInfos.getPriorityPopupKeys(label)?.let { hintLabel = it.firstOrNull() }
        }
        if (hintLabel != null) break
    }

    return hintLabel?.let { KeySpecParser.getLabel(transformLabel(it, params)) }
        // avoid e.g. !autoColumnOrder! as label
        //  this will avoid having labels on comma and period keys
        ?.takeIf { !it.startsWith("!") || it.count { it == '!' } != 2 } // excluding the special labels
}

private fun transformLabel(label: String, params: KeyboardParams): String =
    if (label.startsWith("$$$")) { // currency keys, todo: handing is similar to textKeyData, could it be merged?
        if (label == "$$$") {
            if (params.mId.passwordInput()) "$"
            else params.mLocaleKeyboardInfos.currencyKey.first
        } else {
            val index = label.substringAfter("$$$").toIntOrNull()
            if (index != null && index in 1..5)
                params.mLocaleKeyboardInfos.currencyKey.second[index - 1]
            else label
        }
    } else if (params.mId.mSubtype.isRtlSubtype) {
        label.rtlLabel(params)
    } else label

/** returns a list of enabled popup keys for pref [key] */
fun getEnabledPopupKeys(prefs: SharedPreferences, key: String, defaultSetting: String): List<String> {
    return prefs.getString(key, defaultSetting)?.split(";")?.mapNotNull {
        val split = it.split(",")
        if (split.last() == "true") split.first() else null
    } ?: emptyList()
}

/**
 *  show a dialog that allows re-ordering and dis/enabling the popup keys list for the pref [key]
 *  see e.g. [POPUP_KEYS_LABEL_DEFAULT] for the internally used format
 */
fun reorderPopupKeysDialog(context: Context, key: String, defaultSetting: String, title: Int) {
    val prefs = DeviceProtectedUtils.getSharedPreferences(context)
    val orderedItems = prefs.getString(key, defaultSetting)!!.split(";").mapTo(ArrayList()) {
        val both = it.split(",")
        both.first() to both.last().toBoolean()
    }
    val rv = RecyclerView(context)
    val padding = ResourceUtils.toPx(8, context.resources)
    rv.setPadding(3 * padding, padding, padding, padding)
    rv.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
    val callback = object : DiffUtil.ItemCallback<Pair<String, Boolean>>() {
        override fun areItemsTheSame(p0: Pair<String, Boolean>, p1: Pair<String, Boolean>) = p0 == p1
        override fun areContentsTheSame(p0: Pair<String, Boolean>, p1: Pair<String, Boolean>) = p0 == p1
    }
    val bgColor = ContextCompat.getColor(context, R.color.sliding_items_background)
    val adapter = object : ListAdapter<Pair<String, Boolean>, RecyclerView.ViewHolder>(callback) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val b = LayoutInflater.from(context).inflate(R.layout.popup_keys_list_item, rv, false)
            b.setBackgroundColor(bgColor)
            return object : RecyclerView.ViewHolder(b) { }
        }
        override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
            val (text, wasChecked) = orderedItems[position]
            val displayText = text.lowercase().getStringResourceOrName("", context)
            viewHolder.itemView.findViewById<TextView>(R.id.popup_keys_type)?.text = displayText
            val switch = viewHolder.itemView.findViewById<Switch>(R.id.popup_keys_switch)
            switch?.setOnCheckedChangeListener(null)
            switch?.isChecked = wasChecked
            switch?.setOnCheckedChangeListener { _, isChecked ->
                val pos = orderedItems.indexOfFirst { it.first == text }
                orderedItems[pos] = text to isChecked
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
