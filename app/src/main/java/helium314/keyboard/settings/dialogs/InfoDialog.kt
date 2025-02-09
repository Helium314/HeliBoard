// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun InfoDialog(
    message: String,
    onDismissRequest: () -> Unit
) {
    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        text = { Text(message) },
        onConfirmed = { },
        confirmButtonText = null
    )
}
