// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.LayoutType
import helium314.keyboard.latin.utils.LayoutUtilsCustom
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.settings.CloseIcon
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.contentTextDirectionStyle
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.previewDark
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun LayoutEditDialog(
    onDismissRequest: () -> Unit,
    layoutType: LayoutType,
    initialLayoutName: String,
    startContent: String? = null,
    locale: Locale? = null,
    onEdited: (newLayoutName: String) -> Unit = { },
    isNameValid: ((String) -> Boolean)?
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val startIsCustom = LayoutUtilsCustom.isCustomLayout(initialLayoutName)
    val bottomInsets by SettingsActivity.bottomInsets.collectAsState()
    var displayNameValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(
            if (startIsCustom) LayoutUtilsCustom.getDisplayName(initialLayoutName)
            else initialLayoutName.getStringResourceOrName("layout_", ctx)
        ))
    }
    val nameValid = displayNameValue.text.isNotBlank()
            && (
                (startIsCustom && LayoutUtilsCustom.getLayoutName(displayNameValue.text, layoutType, locale) == initialLayoutName)
                || isNameValid?.let { it(LayoutUtilsCustom.getLayoutName(displayNameValue.text, layoutType, locale)) } == true
            )

    TextInputDialog(
        onDismissRequest = {
            errorJob?.cancel()
            onDismissRequest()
        },
        onConfirmed = {
            val newLayoutName = LayoutUtilsCustom.getLayoutName(displayNameValue.text, layoutType, locale)
            if (startIsCustom && initialLayoutName != newLayoutName) {
                LayoutUtilsCustom.getLayoutFile(initialLayoutName, layoutType, ctx).delete()
                SubtypeSettings.onRenameLayout(layoutType, initialLayoutName, newLayoutName, ctx)
            }
            LayoutUtilsCustom.getLayoutFile(newLayoutName, layoutType, ctx).writeText(it)
            LayoutUtilsCustom.onLayoutFileChanged()
            onEdited(newLayoutName)
            (ctx.getActivity() as? SettingsActivity)?.prefChanged()
            KeyboardSwitcher.getInstance().setThemeNeedsReload()
        },
        confirmButtonText = stringResource(R.string.save),
        initialText = startContent ?: LayoutUtilsCustom.getLayoutFile(initialLayoutName, layoutType, ctx).readText(),
        singleLine = false,
        title = {
            if (isNameValid == null)
                Text(displayNameValue.text)
            else
                TextField(
                    value = displayNameValue,
                    onValueChange = { displayNameValue = it },
                    isError = !nameValid,
                    supportingText = { if (!nameValid) Text(stringResource(R.string.name_invalid)) },
                    trailingIcon = { if (!nameValid) CloseIcon(R.string.name_invalid) },
                    textStyle = contentTextDirectionStyle,
                )
        },
        checkTextValid = { text ->
            val valid = LayoutUtilsCustom.checkLayout(text, ctx)
            errorJob?.cancel()
            if (!valid) {
                errorJob = scope.launch {
                    val message = Log.getLog(10)
                        .lastOrNull { it.tag == "LayoutUtilsCustom" }?.message
                        ?.split("\n")?.take(2)?.joinToString("\n")
                    delay(3000)
                    Toast.makeText(ctx, ctx.getString(R.string.layout_error, message), Toast.LENGTH_LONG).show()
                }
            }
            valid && nameValid // don't allow saving with invalid name, but inform user about issues with layout content
        },
        // this looks weird when the text field is not covered by the keyboard (long dialog)
        // but better than not seeing the bottom part of the field...
        modifier = Modifier.padding(bottom = with(LocalDensity.current)
            { (bottomInsets / 2 + 36).toDp() }), // why is the /2 necessary?
        reducePadding = true,
    )
}

// the job is here (outside the composable to make sure old jobs are canceled
private var errorJob: Job? = null

@Preview
@Composable
private fun Preview() {
    val content = LocalContext.current.assets.open("layouts/main/dvorak.json").reader().readText()
    initPreview(LocalContext.current)
    Theme(previewDark) {
        LayoutEditDialog({}, LayoutType.MAIN, "qwerty", locale = Locale.ENGLISH, startContent = content) { true }
    }
}
