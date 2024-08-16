package com.oscar.aikeyboard.keyboard.clipboard

import android.content.ClipboardManager
import android.content.Context

fun Context.getCurrentClip(): String? {
    val clipboardManager = (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
    return clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()
}