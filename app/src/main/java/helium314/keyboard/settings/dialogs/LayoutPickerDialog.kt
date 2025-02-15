// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import android.app.Activity
import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults.default
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.LayoutType
import helium314.keyboard.latin.utils.LayoutUtils
import helium314.keyboard.latin.utils.LayoutUtilsCustom
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.keyboardNeedsReload

// modified copy of ColorPickerDialog, later check whether stuff can be re-used
@Composable
fun LayoutPickerDialog(
    onDismissRequest: () -> Unit,
    setting: Setting,
    layoutType: LayoutType,
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")

    val currentLayout = Settings.readDefaultLayoutName(layoutType, prefs)
    val internalLayouts = LayoutUtils.getAvailableLayouts(layoutType, ctx)
    // todo: getCustomLayoutFiles does not work nicely for main layout, but currently this dialog is not used for them
    val customLayouts = LayoutUtilsCustom.getCustomLayoutFiles(layoutType, ctx).map { it.name }.sorted()
    val layouts = internalLayouts + customLayouts + ""

    val state = rememberLazyListState()
    LaunchedEffect(currentLayout) {
        val index = layouts.indexOfFirst { it == currentLayout }
        if (index != -1) state.scrollToItem(index, -state.layoutInfo.viewportSize.height / 3)
    }
    var errorDialog by rememberSaveable { mutableStateOf(false) }
    var newLayoutDialog: Pair<String, String?>? by rememberSaveable { mutableStateOf(null) }
    val loadFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = it.data?.data ?: return@rememberLauncherForActivityResult
        val cr = ctx.getActivity()?.contentResolver ?: return@rememberLauncherForActivityResult
        val name = cr.query(uri, null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            val index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index < 0) null
            else c.getString(index)
        }
        cr.openInputStream(uri)?.use {
            val content = it.reader().readText()
            errorDialog = !LayoutUtilsCustom.checkLayout(content, ctx)
            if (!errorDialog)
                newLayoutDialog = (name ?: layoutType.default) to content
        }
    }
    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        cancelButtonText = stringResource(R.string.dialog_close),
        onConfirmed = { },
        confirmButtonText = null,
        neutralButtonText = stringResource(R.string.button_load_custom),
        onNeutral = {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/*", "application/octet-stream", "application/json"))
                .setType("*/*")
            loadFilePicker.launch(intent) },
        title = { Text(setting.title) },
        text = {
            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.bodyLarge
            ) {
                LazyColumn(state = state) {
                    items(layouts) { item ->
                        if (item == "") {
                            AddLayoutRow({ newLayoutDialog = it to "" }, customLayouts)
                        } else {
                            LayoutItemRow(
                                onDismissRequest = onDismissRequest,
                                onClickEdit = { newLayoutDialog = it },
                                onDelete = { deletedLayout ->
                                    if (item == deletedLayout) {
                                        prefs.edit().remove(Settings.PREF_LAYOUT_PREFIX + layoutType.name).apply()
                                        keyboardNeedsReload = true
                                    }
                                    LayoutUtilsCustom.getCustomLayoutFiles(layoutType, ctx).firstOrNull { it.name == deletedLayout }?.delete()
                                    LayoutUtilsCustom.onCustomLayoutFileListChanged()
                                },
                                layoutType = layoutType,
                                layoutName = item,
                                isSelected = item == currentLayout,
                                isCustom = item in customLayouts
                            )
                        }
                    }
                }
            }
        },
    )
    if (errorDialog)
        InfoDialog(stringResource(R.string.file_read_error)) { errorDialog = false }
    if (newLayoutDialog != null) {
        LayoutEditDialog(
            onDismissRequest = { newLayoutDialog = null },
            layoutType = layoutType,
            initialLayoutName = newLayoutDialog?.first ?: layoutType.default,
            startContent = newLayoutDialog?.second,
            isNameValid = { it.isNotBlank() && it !in customLayouts }
        )
    }
}

@Composable
private fun AddLayoutRow(onNewLayout: (String) -> Unit, userLayouts: Collection<String>) {
    var textValue by remember { mutableStateOf(TextFieldValue()) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 10.dp)
    ) {
        Icon(painterResource(R.drawable.ic_plus), stringResource(R.string.add))
        TextField(
            value = textValue,
            onValueChange = { textValue = it },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        IconButton(
            enabled = textValue.text.isNotEmpty() && LayoutUtilsCustom.getCustomLayoutName(textValue.text) !in userLayouts,
            onClick = { onNewLayout(textValue.text) }
        ) { Icon(painterResource(R.drawable.ic_edit), null) }
    }
}

@Composable
private fun LayoutItemRow(
    onDismissRequest: () -> Unit,
    onClickEdit: (Pair<String, String?>) -> Unit,
    onDelete: (String) -> Unit,
    layoutType: LayoutType,
    layoutName: String,
    isSelected: Boolean,
    isCustom: Boolean,
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable {
                onDismissRequest()
                Settings.writeDefaultLayoutName(layoutName, layoutType, prefs)
                keyboardNeedsReload = true
            }
            .padding(start = 6.dp)
            .heightIn(min = 40.dp)
    ) {
        RadioButton(
            selected = isSelected,
            onClick = {
                onDismissRequest()
                Settings.writeDefaultLayoutName(layoutName, layoutType, prefs)
                keyboardNeedsReload = true
            }
        )
        Text(
            text = if (isCustom) LayoutUtilsCustom.getSecondaryLayoutDisplayName(layoutName)
                else layoutName.getStringResourceOrName("layout_", ctx),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (isCustom) {
            var showDeleteDialog by remember { mutableStateOf(false) }
            IconButton(
                onClick = { showDeleteDialog = true }
            ) { Icon(painterResource(R.drawable.ic_bin), null) }
            if (showDeleteDialog)
                ConfirmationDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    text = { Text(stringResource(R.string.delete_layout, LayoutUtilsCustom.getSecondaryLayoutDisplayName(layoutName))) },
                    confirmButtonText = stringResource(R.string.delete),
                    onConfirmed = {
                        showDeleteDialog = false
                        onDelete(layoutName)
                    }
                )
        }
        IconButton(
            onClick = { onClickEdit(layoutName to (if (isCustom) null else LayoutUtils.getContent(layoutType, layoutName, ctx))) }
        ) { Icon(painterResource(R.drawable.ic_edit), null) }
    }
}

@Preview
@Composable
private fun Preview() {
    LayoutPickerDialog(
        onDismissRequest = {},
        setting = Setting(LocalContext.current, "", R.string.settings) {},
        layoutType = LayoutType.SYMBOLS
    )
}
