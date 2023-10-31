package org.dslul.openboard.inputmethod.latin.settings

import android.content.Context
import androidx.appcompat.app.AlertDialog

// maybe rather put to DialogUtils (and convert that to kotlin)

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
