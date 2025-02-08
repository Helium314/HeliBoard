// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import android.widget.Toast
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.checkLayout
import helium314.keyboard.latin.utils.getCustomLayoutFile
import helium314.keyboard.latin.utils.getLayoutDisplayName
import helium314.keyboard.latin.utils.onCustomLayoutFileListChanged
import helium314.keyboard.settings.keyboardNeedsReload
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LayoutEditDialog(
    onDismissRequest: () -> Unit,
    layoutName: String,
    startContent: String? = null,
    displayName: String? = null
) {
    val ctx = LocalContext.current
    val file = getCustomLayoutFile(layoutName, ctx)
    val scope = rememberCoroutineScope()
    var job: Job? = null
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    TextInputDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = {
            file.parentFile?.mkdir()
            file.writeText(it)
            onCustomLayoutFileListChanged()
            keyboardNeedsReload = true
        },
        confirmButtonText = stringResource(R.string.save),
        neutralButtonText = if (displayName != null && file.exists()) stringResource(R.string.delete) else null,
        onNeutral = {
            if (!file.exists()) return@TextInputDialog
            file.delete()
            onCustomLayoutFileListChanged()
            keyboardNeedsReload = true
        },
        initialText = startContent ?: file.readText(),
        singleLine = false,
        title = { Text(displayName ?: getLayoutDisplayName(layoutName)) },
        checkTextValid = {
            val valid = checkLayout(it, ctx)
            job?.cancel()
            if (!valid) {
                job = scope.launch {
                    delay(3000)
                    val message = Log.getLog(10)
                        .lastOrNull { it.tag == "CustomLayoutUtils" }?.message
                        ?.split("\n")?.take(2)?.joinToString("\n")
                    Toast.makeText(ctx, ctx.getString(R.string.layout_error, message), Toast.LENGTH_LONG).show()
                }
            }
            valid
        },
        modifier = Modifier.imePadding(),
        // decorFitsSystemWindows = false is necessary so the dialog is not covered by keyboard
        // but this also stops the background from being darkened... great idea to combine both
        properties = DialogProperties(decorFitsSystemWindows = false)
    )
    if (showDeleteConfirmation)
        ConfirmationDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            onConfirmed = {
                onDismissRequest()
                file.delete()
                onCustomLayoutFileListChanged()
                keyboardNeedsReload = true
            },
            text = { Text(stringResource(R.string.delete_layout, displayName ?: "")) },
            confirmButtonText = stringResource(R.string.delete)
        )
}
