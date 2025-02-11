package helium314.keyboard.settings.dialogs

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.keyboardNeedsReload

// specialized variant of ListPickerDialog
@Composable
fun ColorThemePickerDialog(
    onDismissRequest: () -> Unit,
    setting: Setting,
    isNight: Boolean,
    default: String
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val defaultColors = KeyboardTheme.getAvailableDefaultColors(prefs, false)

    // prefs.all is null in preview only
    val userColors = (prefs.all ?: mapOf(Settings.PREF_USER_COLORS_PREFIX + "usercolor" to "") ).keys.mapNotNull {
        when {
            it.startsWith(Settings.PREF_USER_COLORS_PREFIX) -> it.substringAfter(Settings.PREF_USER_COLORS_PREFIX)
            it.startsWith(Settings.PREF_USER_ALL_COLORS_PREFIX) -> it.substringAfter(Settings.PREF_USER_ALL_COLORS_PREFIX)
            else -> null
        }
    }.toSortedSet() // we don't want duplicates, and we want a consistent order
    val selectedColor = prefs.getString(setting.key, default)!!
    if (selectedColor !in defaultColors)
        userColors.add(selectedColor) // there are cases where we have no settings for a user theme

    val colors = defaultColors + userColors + ""
    val state = rememberLazyListState()
    LaunchedEffect(selectedColor) {
        val index = colors.indexOf(selectedColor)
        if (index != -1) state.scrollToItem(index, -state.layoutInfo.viewportSize.height / 3)
    }
    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        cancelButtonText = stringResource(R.string.dialog_close),
        onConfirmed = { },
        confirmButtonText = null,
//        neutralButtonText = stringResource(R.string.load),
        onNeutral = {
            // todo: launcher to select file
            //  when importing make sure name is not in use
        },
        title = { Text(setting.title) },
        text = {
            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.bodyLarge
            ) {
                LazyColumn(state = state) {
                    items(colors) { item ->
                        if (item == "") {
                            var textValue by remember { mutableStateOf(TextFieldValue()) }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(painterResource(R.drawable.ic_plus), stringResource(R.string.add)) // todo: should it be a button?
                                TextField(
                                    value = textValue,
                                    onValueChange = { textValue = it },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                IconButton(
                                    enabled = textValue.text.isNotEmpty() && textValue.text !in userColors,
                                    onClick = {
                                        onDismissRequest()
                                        prefs.edit().putString(setting.key, textValue.text).apply()
                                        if (isNight) SettingsDestination.navigateTo(SettingsDestination.ColorsNight)
                                        else SettingsDestination.navigateTo(SettingsDestination.Colors)
                                        keyboardNeedsReload = true
                                    }
                                ) { Icon(painterResource(R.drawable.ic_edit), null) }
                            }
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable {
                                        onDismissRequest()
                                        prefs.edit().putString(setting.key, item).apply()
                                        keyboardNeedsReload = true
                                    }
                                    .padding(start = 6.dp)
                                    .heightIn(min = 40.dp)
                            ) {
                                RadioButton(
                                    selected = selectedColor == item,
                                    onClick = {
                                        onDismissRequest()
                                        prefs.edit().putString(setting.key, item).apply()
                                        keyboardNeedsReload = true
                                    }
                                )
                                Text(
                                    text = item.getStringResourceOrName("theme_name_", ctx),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                if (item in userColors) {
                                    var showDialog by remember { mutableStateOf(false) }
                                    IconButton(
                                        onClick = { showDialog = true }
                                    ) { Icon(painterResource(R.drawable.ic_bin), null) }
                                    IconButton(
                                        onClick = {
                                            onDismissRequest()
                                            prefs.edit().putString(setting.key, item).apply()
                                            if (isNight) SettingsDestination.navigateTo(SettingsDestination.ColorsNight)
                                            else SettingsDestination.navigateTo(SettingsDestination.Colors)
                                            keyboardNeedsReload = true
                                        }
                                    ) { Icon(painterResource(R.drawable.ic_edit), null) }
                                    if (showDialog)
                                        ConfirmationDialog(
                                            onDismissRequest = { showDialog = false },
                                            text = { Text(stringResource(R.string.delete_confirmation, item)) },
                                            onConfirmed = {
                                                onDismissRequest()
                                                prefs.edit().remove(Settings.PREF_USER_COLORS_PREFIX + item)
                                                    .remove(Settings.PREF_USER_ALL_COLORS_PREFIX + item)
                                                    .remove(Settings.PREF_USER_MORE_COLORS_PREFIX + item).apply()
                                                if (item == selectedColor)
                                                    prefs.edit().remove(setting.key).apply()
                                                keyboardNeedsReload = true
                                            }
                                        )
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

@Preview
@Composable
private fun PreviewListPickerDialog() {
    ColorThemePickerDialog(
        onDismissRequest = {},
        setting = Setting(LocalContext.current, "", R.string.settings) {},
        default = "dark",
        isNight = true
    )
}
