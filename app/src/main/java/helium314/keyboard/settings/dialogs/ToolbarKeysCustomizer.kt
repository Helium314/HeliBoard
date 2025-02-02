package helium314.keyboard.settings.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode.checkAndConvertCode
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.getCodeForToolbarKey
import helium314.keyboard.latin.utils.getCodeForToolbarKeyLongClick
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.readCustomKeyCodes
import helium314.keyboard.latin.utils.readCustomLongpressCodes
import helium314.keyboard.latin.utils.writeCustomKeyCodes
import helium314.keyboard.latin.utils.writeCustomLongpressCodes
import helium314.keyboard.settings.screens.GetIcon
import kotlinx.serialization.json.Json

// todo (later): reading and writing prefs should be done in the preference, or at least with the provided (single!) pref key
@Composable
fun ToolbarKeysCustomizer(
    onDismissRequest: () -> Unit
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    var showKeyCustomizer: ToolbarKey? by remember { mutableStateOf(null) }
    var showDeletePrefConfirmDialog by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.dialog_close)) }
        },
        dismissButton = {
            if (readCustomKeyCodes(prefs).isNotEmpty() || readCustomLongpressCodes(prefs).isNotEmpty())
                TextButton(
                    onClick = { showDeletePrefConfirmDialog = true }
                ) { Text(stringResource(R.string.button_default)) }
        },
        title = { Text(stringResource(R.string.customize_toolbar_key_codes)) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(ToolbarKey.entries) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showKeyCustomizer = it }.fillParentMaxWidth()
                    ) {
                        KeyboardIconsSet.instance.GetIcon(it.name)
                        Text(it.name.lowercase().getStringResourceOrName("", ctx))
                    }
                }
            }
        },
        shape = MaterialTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.surface,
        textContentColor = contentColorFor(MaterialTheme.colorScheme.surface),
        properties = DialogProperties(),
    )
    if (showKeyCustomizer != null) {
        val shownKey = showKeyCustomizer
        if (shownKey != null)
            ToolbarKeyCustomizer(shownKey) { showKeyCustomizer = null }
    }
    if (showDeletePrefConfirmDialog)
        ConfirmationDialog(
            onDismissRequest = { showDeletePrefConfirmDialog = false },
            onConfirmed = {
                showDeletePrefConfirmDialog = false
                onDismissRequest()
                prefs.edit {
                    remove(Settings.PREF_TOOLBAR_CUSTOM_KEY_CODES)
                    remove(Settings.PREF_TOOLBAR_CUSTOM_LONGPRESS_CODES)
                }
            },
            text = { Text(stringResource(R.string.customize_toolbar_key_code_reset_message)) }
        )
}

// todo: show updated ToolbarKeysCustomizer after ok / default? because default button
@Composable
private fun ToolbarKeyCustomizer(
    key: ToolbarKey,
    onDismissRequest: () -> Unit
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    var code by remember { mutableStateOf(TextFieldValue(getCodeForToolbarKey(key).toString())) }
    var longPressCode by remember { mutableStateOf(TextFieldValue(getCodeForToolbarKeyLongClick(key).toString())) }
    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = {
            writeCustomKeyCodes(prefs, readCustomKeyCodes(prefs) + (key.name to checkCode(code)))
            writeCustomLongpressCodes(prefs, readCustomLongpressCodes(prefs) + (key.name to checkCode(longPressCode)))
        },
        checkOk = { checkCode(code) != null && checkCode(longPressCode) != null },
        neutralButtonText = if (readCustomKeyCodes(prefs).containsKey(key.name) || readCustomLongpressCodes(prefs).containsKey(key.name))
                stringResource(R.string.button_default)
            else null,
        onNeutral = {
            val keys = readCustomKeyCodes(prefs).toMutableMap()
            keys.remove(key.name)
            prefs.edit().putString(Settings.PREF_TOOLBAR_CUSTOM_KEY_CODES, Json.encodeToString(keys)).apply()
            val longpressKeys = readCustomLongpressCodes(prefs).toMutableMap()
            longpressKeys.remove(key.name)
            prefs.edit().putString(Settings.PREF_TOOLBAR_CUSTOM_LONGPRESS_CODES, Json.encodeToString(longpressKeys)).apply()
        },
        title = { Text(key.name.lowercase().getStringResourceOrName("", ctx)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.key_code), Modifier.weight(0.5f))
                    TextField(
                        value = code,
                        onValueChange = { code = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.5f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.long_press_code), Modifier.weight(0.5f))
                    TextField(
                        value = longPressCode,
                        onValueChange = { longPressCode = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.5f)
                    )
                }
            }
        },
        shape = MaterialTheme.shapes.medium,
        backgroundColor = MaterialTheme.colorScheme.surface,
        contentColor = contentColorFor(MaterialTheme.colorScheme.surface),
        properties = DialogProperties(),
    )
}

@Preview
@Composable
fun PreviewToolbarKeyCustomizer() {
    Settings.init(LocalContext.current)
    ToolbarKeyCustomizer(ToolbarKey.CUT) { }
}

@Preview
@Composable
fun PreviewToolbarKeysCustomizer() {
    Settings.init(LocalContext.current)
    KeyboardIconsSet.instance.loadIcons(LocalContext.current)
    ToolbarKeysCustomizer { }
}

private fun checkCode(code: TextFieldValue) = runCatching {
    code.text.toIntOrNull()?.takeIf { it.checkAndConvertCode() <= Char.MAX_VALUE.code }
}.getOrNull()
