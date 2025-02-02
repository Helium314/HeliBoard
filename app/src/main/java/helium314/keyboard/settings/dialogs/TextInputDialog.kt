package helium314.keyboard.settings.dialogs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties

// mostly taken from StreetComplete / SCEE
/** Dialog with which to input text. OK button is only clickable if [checkTextValid] returns true. */
@Composable
fun TextInputDialog(
    onDismissRequest: () -> Unit,
    onConfirmed: (text: String) -> Unit,
    modifier: Modifier = Modifier,
    title: @Composable (() -> Unit)? = null,
    onNeutral: () -> Unit = { },
    neutralButtonText: String? = null,
    confirmButtonText: String = stringResource(android.R.string.ok),
    initialText: String = "",
    textInputLabel: @Composable (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(backgroundColor),
    properties: DialogProperties = DialogProperties(),
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Unspecified,
    checkTextValid: (text: String) -> Boolean = { it.isNotBlank() }
) {
    val focusRequester = remember { FocusRequester() }

    var value by remember {
        mutableStateOf(TextFieldValue(initialText, selection = TextRange(0, initialText.length)))
    }

    LaunchedEffect(initialText) { focusRequester.requestFocus() }

    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = { onConfirmed(value.text) },
        confirmButtonText = confirmButtonText,
        checkOk = { checkTextValid(value.text) },
        neutralButtonText = neutralButtonText,
        onNeutral = onNeutral,
        modifier = modifier,
        title = title,
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                label = textInputLabel,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                singleLine = singleLine
            )
        },
        shape = shape,
        backgroundColor = backgroundColor,
        contentColor = contentColor,
        properties = properties,
    )
}

@Preview
@Composable
private fun Preview() {
    TextInputDialog(
        onDismissRequest = {},
        onConfirmed = {},
        title = { Text("Title") },
        initialText = "some text\nand another line",
        singleLine = false,
        textInputLabel = { Text("fill it") }
    )
}