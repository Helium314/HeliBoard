// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import android.widget.Toast
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.checkLayout
import helium314.keyboard.latin.utils.getCustomLayoutFile
import helium314.keyboard.latin.utils.getLayoutDisplayName
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CustomizeLayoutDialog(
    layoutName: String,
    onDismissRequest: () -> Unit,
    startContent: String? = null,
    displayName: String? = null,
) {
    val ctx = LocalContext.current
    val file = getCustomLayoutFile(layoutName, ctx)
    val scope = rememberCoroutineScope()
    var job: Job? = null

    TextInputDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = { },
        initialText = startContent ?: file.readText(),
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
        singleLine = false,
        modifier = Modifier.imePadding(),
        // decorFitsSystemWindows = false is necessary so the dialog is not covered by keyboard
        // but this also stops the background from being darkened... great idea to combine both
        properties = DialogProperties(decorFitsSystemWindows = false)
    )
}
