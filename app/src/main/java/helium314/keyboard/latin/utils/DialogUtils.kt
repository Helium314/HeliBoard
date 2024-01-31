package helium314.keyboard.latin.utils

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import helium314.keyboard.latin.R

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
