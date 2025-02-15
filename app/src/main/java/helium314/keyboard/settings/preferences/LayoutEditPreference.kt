// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import helium314.keyboard.latin.utils.CUSTOM_LAYOUT_PREFIX
import helium314.keyboard.latin.utils.getCustomLayoutFiles
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.dialogs.LayoutEditDialog
import helium314.keyboard.settings.dialogs.ListPickerDialog
import java.io.File
/*
@Composable
fun LayoutEditPreference(
    setting: Setting,
    items: List<String>,
    getItemName: @Composable (String) -> String,
    getDefaultLayout: @Composable (String?) -> String?,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    var layout: String? by rememberSaveable { mutableStateOf(null) }
    Preference(
    name = setting.title,
    onClick = { showDialog = true }
    )
    if (showDialog) {
        ListPickerDialog(
            onDismissRequest = { showDialog = false },
            showRadioButtons = false,
            confirmImmediately = true,
            items = items,
            getItemName = getItemName,
            onItemSelected = { layout = it },
            title = { Text(setting.title) }
        )
    }
    if (layout != null) {
        val customLayoutName = getCustomLayoutFiles(ctx).firstOrNull {
            if (layout!!.startsWith(CUSTOM_LAYOUT_PREFIX))
                it.name.startsWith("$layout.")
            else it.name.startsWith("$CUSTOM_LAYOUT_PREFIX$layout.")
        }?.name
        // todo: never set originalLayout null if custom layout file does not exist!
        val originalLayout = if (customLayoutName != null) null
        else getDefaultLayout(layout)?.let { ctx.assets.open("layouts" + File.separator + it).reader().readText() }
        LayoutEditDialog(
            layoutName = customLayoutName ?: "$CUSTOM_LAYOUT_PREFIX$layout.",
            startContent = originalLayout,
            displayName = getItemName(layout!!),
            onDismissRequest = { layout = null }
        )
    }
}
*/