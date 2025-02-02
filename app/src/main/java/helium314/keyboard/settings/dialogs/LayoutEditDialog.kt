package helium314.keyboard.settings.dialogs

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.checkLayout
import helium314.keyboard.latin.utils.getCustomLayoutFile
import helium314.keyboard.latin.utils.getLayoutDisplayName
import helium314.keyboard.latin.utils.onCustomLayoutFileListChanged
import helium314.keyboard.settings.keyboardNeedsReload

// todo: height MUST respect keyboard, or it's impossible to fill the bottom part
@Composable
fun LayoutEditDialog(
    onDismissRequest: () -> Unit,
    layoutName: String,
    startContent: String? = null,
    displayName: String? = null
) {
    val ctx = LocalContext.current
    val file = getCustomLayoutFile(layoutName, ctx)
    val initialText = startContent ?: file.readText()
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    // todo: try make it really full width, at least if we have a json file
    // todo: ok button should be "save"
    // todo: if displayName not null, there is an existing file
    TextInputDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = {
            file.parentFile?.mkdir()
            file.writeText(it)
            onCustomLayoutFileListChanged()
            keyboardNeedsReload = true
        },
        initialText = initialText,
        singleLine = false,
        title = { Text(displayName ?: getLayoutDisplayName(layoutName)) },
        checkTextValid = {
            checkLayout(it, ctx) // todo: toast with reason why it doesn't work -> should re-do getting the reason
        },
        // todo: delete button if displayName not null and file exists
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
