// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import android.widget.Toast
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.DialogProperties
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.LayoutType
import helium314.keyboard.latin.utils.LayoutUtilsCustom
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.settings.keyboardNeedsReload
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LayoutEditDialog(
    onDismissRequest: () -> Unit,
    layoutType: LayoutType,
    initialLayoutName: String,
    startContent: String? = null,
    isNameValid: (String) -> Boolean
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var job: Job? = null
    val startIsCustom = LayoutUtilsCustom.isCustomLayout(initialLayoutName)
    var displayNameValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(
            if (startIsCustom) LayoutUtilsCustom.getCustomLayoutDisplayName(initialLayoutName)
            else initialLayoutName.getStringResourceOrName("layout_", ctx)
        ))
    }
    val nameValid = displayNameValue.text.isNotBlank()
            && (
                (startIsCustom && LayoutUtilsCustom.getCustomLayoutName(displayNameValue.text) == initialLayoutName)
                || isNameValid(LayoutUtilsCustom.getCustomLayoutName(displayNameValue.text))
            )

    TextInputDialog(
        onDismissRequest = {
            job?.cancel()
            onDismissRequest()
        },
        onConfirmed = {
            val newLayoutName = LayoutUtilsCustom.getCustomLayoutName(displayNameValue.text)
            if (startIsCustom && initialLayoutName != newLayoutName)
                LayoutUtilsCustom.getCustomLayoutFile(initialLayoutName, layoutType, ctx).delete()
            LayoutUtilsCustom.getCustomLayoutFile(newLayoutName, layoutType, ctx).writeText(it)
            LayoutUtilsCustom.onCustomLayoutFileListChanged()
            keyboardNeedsReload = true
        },
        confirmButtonText = stringResource(R.string.save),
        initialText = startContent ?: LayoutUtilsCustom.getCustomLayoutFile(initialLayoutName, layoutType, ctx).readText(),
        singleLine = false,
        title = {
            TextField(
                value = displayNameValue,
                onValueChange = { displayNameValue = it },
                isError = !nameValid,
                supportingText = { if (!nameValid) Text(stringResource(R.string.name_invalid)) },
                trailingIcon = { if (!nameValid) Icon(painterResource(R.drawable.ic_close), null) },
            )
        },
        checkTextValid = {
            val valid = LayoutUtilsCustom.checkLayout(it, ctx)
            job?.cancel()
            if (!valid) {
                job = scope.launch {
                    delay(3000)
                    val message = Log.getLog(10)
                        .lastOrNull { it.tag == "LayoutUtilsCustom" }?.message
                        ?.split("\n")?.take(2)?.joinToString("\n")
                    Toast.makeText(ctx, ctx.getString(R.string.layout_error, message), Toast.LENGTH_LONG).show()
                }
            }
            valid && nameValid // don't allow saving with invalid name, but inform user about issues with layout content
        },
        modifier = Modifier.imePadding(),
        // decorFitsSystemWindows = false is necessary so the dialog is not covered by keyboard
        // but this also stops the background from being darkened... great idea to combine both
        // todo: also it results in an ugly effect when adding a new layout... need to find something else
        properties = DialogProperties(decorFitsSystemWindows = false),
        reducePadding = true,
    )
}
