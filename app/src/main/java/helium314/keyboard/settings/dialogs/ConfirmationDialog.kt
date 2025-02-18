// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.latin.R

// taken from StreetComplete
/** Slight specialization of an alert dialog: AlertDialog with OK and Cancel button. Both buttons
 *  call [onDismissRequest] and the OK button additionally calls [onConfirmed]. */
@Composable
fun ConfirmationDialog(
    onDismissRequest: () -> Unit,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButtonText: String = stringResource(android.R.string.ok),
    cancelButtonText: String = stringResource(android.R.string.cancel),
    neutralButtonText: String? = null,
    onNeutral: () -> Unit = { },
) {
    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = onConfirmed,
        confirmButtonText = confirmButtonText,
        cancelButtonText = cancelButtonText,
        neutralButtonText = neutralButtonText,
        onNeutral = onNeutral,
        modifier = modifier,
        title = title,
        text = text,
    )
}

@Preview
@Composable
private fun PreviewConfirmDialog() {
    ConfirmationDialog(
        onDismissRequest = {  },
        onConfirmed = {},
        neutralButtonText = "hi",
        confirmButtonText = "I don't care",
        text = { Text(stringResource(R.string.disable_personalized_dicts_message)) }
    )
}
