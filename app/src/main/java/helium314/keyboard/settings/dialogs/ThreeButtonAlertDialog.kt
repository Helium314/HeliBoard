package helium314.keyboard.settings.dialogs

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties

@Composable
fun ThreeButtonAlertDialog(
    onDismissRequest: () -> Unit,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    onNeutral: () -> Unit = { },
    checkOk: () -> Boolean = { true },
    confirmButtonText: String? = stringResource(android.R.string.ok),
    cancelButtonText: String = stringResource(android.R.string.cancel),
    neutralButtonText: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { // mis-use the confirm button and put everything in there
            Row {
                if (neutralButtonText != null)
                    TextButton(
                        onClick = { onDismissRequest(); onNeutral() }
                    ) { Text(neutralButtonText) }
                Spacer(modifier.weight(1f))
                TextButton(onClick = onDismissRequest) { Text(cancelButtonText) }
                if (confirmButtonText != null)
                    TextButton(
                        enabled = checkOk(),
                        onClick = { onDismissRequest(); onConfirmed() },
                    ) { Text(confirmButtonText) }
            }
        },
        modifier = modifier,
        title = title,
        text = text,
        shape = MaterialTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.surface,
        textContentColor = contentColorFor( MaterialTheme.colorScheme.surface),
        properties = DialogProperties(),
    )
}
