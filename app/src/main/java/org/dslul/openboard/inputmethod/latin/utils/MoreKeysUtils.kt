// SPDX-License-Identifier: GPL-3.0-only
package org.dslul.openboard.inputmethod.latin.utils

import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
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
import org.dslul.openboard.inputmethod.latin.settings.Settings
import java.util.Collections

const val MORE_KEYS_NUMBER = "more_keys_number"
private const val MORE_KEYS_LANGUAGE_PRIORITY = "more_keys_language_priority"
const val MORE_KEYS_LAYOUT = "more_keys_layout"
private const val MORE_KEYS_SYMBOLS = "more_keys_symbols"
private const val MORE_KEYS_LANGUAGE = "more_keys_language"
const val MORE_KEYS_LABEL_DEFAULT = "$MORE_KEYS_NUMBER,true;$MORE_KEYS_LANGUAGE_PRIORITY,false;$MORE_KEYS_LAYOUT,true;$MORE_KEYS_SYMBOLS,true;$MORE_KEYS_LANGUAGE,false"
const val MORE_KEYS_ORDER_DEFAULT = "$MORE_KEYS_LANGUAGE_PRIORITY,true;$MORE_KEYS_NUMBER,true;$MORE_KEYS_SYMBOLS,true;$MORE_KEYS_LAYOUT,true;$MORE_KEYS_LANGUAGE,true"

fun createMoreKeysArray(popupSet: PopupSet<*>?, params: KeyboardParams, label: String): Array<String>? {
    // often moreKeys are empty, so we want to avoid unnecessarily creating sets
    val moreKeysDelegate = lazy { mutableSetOf<String>() }
    val moreKeys by moreKeysDelegate
    params.mMoreKeyTypes.forEach { type ->
        when (type) {
            MORE_KEYS_NUMBER -> params.mLocaleKeyTexts.getNumberLabel(popupSet?.numberIndex)?.let { moreKeys.add(it) }
            MORE_KEYS_LAYOUT -> popupSet?.getPopupKeyLabels(params)?.let { moreKeys.addAll(it) }
            MORE_KEYS_SYMBOLS -> popupSet?.symbol?.let { moreKeys.add(it) }
            MORE_KEYS_LANGUAGE -> params.mLocaleKeyTexts.getMoreKeys(label)?.let { moreKeys.addAll(it) }
            MORE_KEYS_LANGUAGE_PRIORITY -> params.mLocaleKeyTexts.getPriorityMoreKeys(label)?.let { moreKeys.addAll(it) }
        }
    }
    if (!moreKeysDelegate.isInitialized() || moreKeys.isEmpty())
        return null
    val fco = moreKeys.firstOrNull { it.startsWith(Key.MORE_KEYS_FIXED_COLUMN_ORDER) }
    if (fco != null && fco.substringAfter(Key.MORE_KEYS_FIXED_COLUMN_ORDER).toIntOrNull() != moreKeys.size - 1) {
        val fcoExpected = moreKeys.size - moreKeys.count { it.startsWith("!") && it.endsWith("!") } - 1
        if (fco.substringAfter(Key.MORE_KEYS_FIXED_COLUMN_ORDER).toIntOrNull() != fcoExpected)
            moreKeys.remove(fco) // maybe rather adjust the number instead of remove?
    }
    if (moreKeys.size > 1 && (label == "(" || label == ")")) { // add fixed column order for that case (typically other variants of brackets / parentheses
        // not really fast, but no other way to add first in a LinkedHashSet
        val tmp = moreKeys.toList()
        moreKeys.clear()
        moreKeys.add("${Key.MORE_KEYS_FIXED_COLUMN_ORDER}${tmp.size}")
        moreKeys.addAll(tmp)
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
            MORE_KEYS_SYMBOLS -> popupSet?.symbol?.let { hintLabel = it }
            MORE_KEYS_LANGUAGE -> params.mLocaleKeyTexts.getMoreKeys(label)?.let { hintLabel = it.firstOrNull() }
            MORE_KEYS_LANGUAGE_PRIORITY -> params.mLocaleKeyTexts.getPriorityMoreKeys(label)?.let { hintLabel = it.firstOrNull() }
        }
        if (hintLabel != null) break
    }

    // don't do the rtl transform, hint label is only the label
    return hintLabel?.let { if (it == "$$$") transformLabel(it, params) else it }
        // avoid e.g. !autoColumnOrder! as label
        //  this will avoid having labels on comma and period keys
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
    val bgColor = if (ResourceUtils.isNight(context.resources))
            ContextCompat.getColor(context, androidx.appcompat.R.color.background_floating_material_dark)
        else ContextCompat.getColor(context, androidx.appcompat.R.color.background_floating_material_light)
    val adapter = object : ListAdapter<Pair<String, Boolean>, RecyclerView.ViewHolder>(callback) {
        override fun onCreateViewHolder(p0: ViewGroup, p1: Int): RecyclerView.ViewHolder {
            val b = LayoutInflater.from(context).inflate(R.layout.morekeys_list_item, rv, false)
            b.setBackgroundColor(bgColor)
            return object : RecyclerView.ViewHolder(b) { }
        }
        override fun onBindViewHolder(p0: RecyclerView.ViewHolder, p1: Int) {
            val (text, wasChecked) = orderedItems[p1]
            val displayTextId = context.resources.getIdentifier(text.lowercase(), "string", context.packageName)
            val displayText = if (displayTextId == 0) text else context.getString(displayTextId)
            p0.itemView.findViewById<TextView>(R.id.morekeys_type)?.text = displayText
            val switch = p0.itemView.findViewById<SwitchCompat>(R.id.morekeys_switch)
            switch?.isChecked = wasChecked
            switch?.isEnabled = !(key.contains(Settings.PREF_MORE_KEYS_ORDER) && text == MORE_KEYS_LAYOUT) // layout can't be disabled
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
