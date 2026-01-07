// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin.utils

import android.app.AlertDialog
import android.os.IBinder
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.view.WindowManager
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodSubtype
import helium314.keyboard.compat.ImeCompat.switchInputMethodAndSubtype
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.utils.SubtypeLocaleUtils.displayName

// similar to what showSubtypePicker does in https://github.com/rkkr/simple-keyboard/blob/master/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/RichInputMethodManager.java
fun createInputMethodPickerDialog(latinIme: LatinIME, richImm: RichInputMethodManager, windowToken: IBinder): AlertDialog {
    val pm = latinIme.packageManager
    val thisImi = richImm.inputMethodInfoOfThisIme
    val currentSubtype = richImm.currentSubtype.rawSubtype
    val enabledImis = richImm.inputMethodManager.enabledInputMethodList
        .sortedBy { it.hashCode() }.sortedBy { it.loadLabel(pm).toString() } // first label, then hashCode
    val enabledSubtypes = mutableListOf<Pair<InputMethodInfo, InputMethodSubtype?>>()
    var currentSubtypeIndex = 0
    enabledImis.forEach { imi ->
        val subtypes = if (imi != thisImi) richImm.getEnabledInputMethodSubtypes(imi, true)
            else richImm.getEnabledInputMethodSubtypes(imi, true).sortedBy { it.displayName() }
        if (subtypes.isEmpty()) {
            enabledSubtypes.add(imi to null)
        } else {
            subtypes.forEach {
                if (!it.isAuxiliary) {
                    enabledSubtypes.add(imi to it)
                    if (imi == thisImi && it == currentSubtype)
                        currentSubtypeIndex = enabledSubtypes.lastIndex
                }
            }
        }
    }

    val items = mutableListOf<SpannableStringBuilder>()
    for (imiAndSubtype in enabledSubtypes) {
        val (imi, subtype) = imiAndSubtype

        val subtypeName = if (imi == thisImi) subtype?.displayName()
            else subtype?.getDisplayName(latinIme, imi.packageName, imi.serviceInfo.applicationInfo)
        val title = SpannableString(subtypeName?.ifBlank { imi.loadLabel(pm) } ?: imi.loadLabel(pm))
        val subtitle = SpannableString(if (subtype == null) "" else "\n${imi.loadLabel(pm)}")
        title.setSpan(
            RelativeSizeSpan(0.9f), 0, title.length,
            Spannable.SPAN_INCLUSIVE_INCLUSIVE
        )
        subtitle.setSpan(
            RelativeSizeSpan(0.85f), 0, subtitle.length,
            Spannable.SPAN_EXCLUSIVE_INCLUSIVE
        )
        items.add(SpannableStringBuilder().append(title).append(subtitle))
    }

    val dialog = AlertDialog.Builder(getPlatformDialogThemeContext(latinIme))
        .setTitle(R.string.select_input_method)
        .setSingleChoiceItems(items.toTypedArray(), currentSubtypeIndex) { di, i ->
            di.dismiss()
            val (imi, subtype) = enabledSubtypes[i]
            if (imi == thisImi)
                latinIme.switchToSubtype(subtype)
            else if (subtype != null)
                latinIme.switchInputMethodAndSubtype(imi, subtype)
            else
                latinIme.switchInputMethod(imi.id)
        }
        .create()

    val window = dialog.window
    val layoutParams = window?.attributes
    layoutParams?.token = windowToken
    layoutParams?.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
    window?.attributes = layoutParams
    window?.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
    return dialog
}
