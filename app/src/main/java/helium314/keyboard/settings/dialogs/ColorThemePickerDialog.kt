// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import helium314.keyboard.keyboard.ColorSetting
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Links
import helium314.keyboard.latin.common.decodeBase36
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.appendLink
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.DeleteButton
import helium314.keyboard.settings.EditButton
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.filePicker
import helium314.keyboard.settings.keyboardNeedsReload
import helium314.keyboard.settings.screens.SaveThoseColors
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.util.EnumMap

@Composable
fun ColorThemePickerDialog(
    onDismissRequest: () -> Unit,
    setting: Setting,
    isNight: Boolean,
    default: String
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (LocalContext.current.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")

    val defaultColors = KeyboardTheme.getAvailableDefaultColors(prefs, isNight)

    // prefs.all is null in preview only
    val userColors = (prefs.all ?: mapOf(Settings.PREF_USER_COLORS_PREFIX + "usercolor" to "") ).keys.mapNotNull {
        when {
            it.startsWith(Settings.PREF_USER_COLORS_PREFIX) -> it.substringAfter(Settings.PREF_USER_COLORS_PREFIX)
            it.startsWith(Settings.PREF_USER_ALL_COLORS_PREFIX) -> it.substringAfter(Settings.PREF_USER_ALL_COLORS_PREFIX)
            it.startsWith(Settings.PREF_USER_MORE_COLORS_PREFIX) -> it.substringAfter(Settings.PREF_USER_MORE_COLORS_PREFIX)
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
    var showLoadDialog by remember { mutableStateOf(false) }
    val targetScreen = if (isNight) SettingsDestination.ColorsNight else SettingsDestination.Colors
    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        cancelButtonText = stringResource(R.string.dialog_close),
        onConfirmed = { },
        confirmButtonText = null,
        neutralButtonText = stringResource(R.string.load),
        onNeutral = { showLoadDialog = true },
        title = { Text(setting.title) },
        content = {
            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.bodyLarge
            ) {
                LazyColumn(state = state) {
                    items(colors) { item ->
                        if (item == "") {
                            AddColorRow(onDismissRequest, userColors, targetScreen, setting.key)
                        } else {
                            ColorItemRow(onDismissRequest, item, item == selectedColor, item in userColors, targetScreen, setting.key)
                        }
                    }
                }
            }
        },
    )
    var errorDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val loadFilePicker = filePicker { uri ->
        ctx.getActivity()?.contentResolver?.openInputStream(uri)?.use {
            val text = it.reader().readText()
            // theme not added when done without coroutine (maybe prefs listener is not yet registered?)
            scope.launch { errorDialog = !loadColorString(text, prefs) }
        }
    }
    if (showLoadDialog) {
        ConfirmationDialog(
            onDismissRequest = { showLoadDialog = false },
            title = { Text(stringResource(R.string.load)) },
            content = {
                val text = stringResource(R.string.get_colors_message)
                val annotated = buildAnnotatedString {
                    append(text.substringBefore("%s"))
                    appendLink(stringResource(R.string.discussion_section_link), Links.CUSTOM_COLORS)
                    append(text.substringAfter("%s"))
                }
                Text(annotated)
            },
            onConfirmed = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/*", "application/octet-stream", "application/json"))
                    .setType("*/*")
                loadFilePicker.launch(intent)
            },
            confirmButtonText = stringResource(R.string.button_load_custom),
            onNeutral = {
                showLoadDialog = false
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = cm.primaryClip?.takeIf { it.itemCount > 0 } ?: return@ConfirmationDialog
                val text = clip.getItemAt(0).text
                errorDialog = !loadColorString(text.toString(), prefs)
            },
            neutralButtonText = stringResource(R.string.paste)
        )
    }
    if (errorDialog)
        InfoDialog(stringResource(R.string.file_read_error)) { errorDialog = false } // todo: text (not always a file)
}

@Composable
private fun AddColorRow(onDismissRequest: () -> Unit, userColors: Collection<String>, targetScreen: String, prefKey: String) {
    var textValue by remember { mutableStateOf(TextFieldValue()) }
    val prefs = LocalContext.current.prefs()
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
        EditButton(textValue.text.isNotEmpty() && textValue.text !in userColors) {
            onDismissRequest()
            prefs.edit().putString(prefKey, textValue.text).apply()
            SettingsDestination.navigateTo(targetScreen)
            keyboardNeedsReload = true
        }
    }
}

@Composable
private fun ColorItemRow(onDismissRequest: () -> Unit, item: String, isSelected: Boolean, isUser: Boolean, targetScreen: String, prefKey: String) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable {
                onDismissRequest()
                prefs.edit().putString(prefKey, item).apply()
                keyboardNeedsReload = true
            }
            .padding(start = 6.dp)
            .heightIn(min = 40.dp)
    ) {
        RadioButton(
            selected = isSelected,
            onClick = {
                onDismissRequest()
                prefs.edit().putString(prefKey, item).apply()
                keyboardNeedsReload = true
            }
        )
        Text(
            text = item.getStringResourceOrName("theme_name_", ctx),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (isUser) {
            var showDialog by remember { mutableStateOf(false) }
            DeleteButton { showDialog = true }
            EditButton {
                onDismissRequest()
                SettingsDestination.navigateTo(targetScreen + item)
            }
            if (showDialog)
                ConfirmationDialog(
                    onDismissRequest = { showDialog = false },
                    content = { Text(stringResource(R.string.delete_confirmation, item)) },
                    onConfirmed = {
                        showDialog = false
                        prefs.edit().remove(Settings.PREF_USER_COLORS_PREFIX + item)
                            .remove(Settings.PREF_USER_ALL_COLORS_PREFIX + item)
                            .remove(Settings.PREF_USER_MORE_COLORS_PREFIX + item).apply()
                        if (isSelected)
                            prefs.edit().remove(prefKey).apply()
                        keyboardNeedsReload = true
                    }
                )
        }
    }
}

// returns whether the string was successfully deserialized and stored in prefs
private fun loadColorString(colorString: String, prefs: SharedPreferences): Boolean {
    try {
        val that = Json.decodeFromString<SaveThoseColors>(colorString)
        val themeName = KeyboardTheme.getUnusedThemeName(that.name ?: "imported colors", prefs)
        val colors = that.colors.map { ColorSetting(it.key, it.value.second, it.value.first) }
        KeyboardTheme.writeUserColors(prefs, themeName, colors)
        KeyboardTheme.writeUserMoreColors(prefs, themeName, that.moreColors)
    } catch (e: SerializationException) {
        try {
            val allColorsStringMap = Json.decodeFromString<Map<String, Int>>(colorString)
            val allColors = EnumMap<ColorType, Int>(ColorType::class.java)
            var themeName = "imported colors"
            allColorsStringMap.forEach {
                try {
                    allColors[ColorType.valueOf(it.key)] = it.value
                } catch (_: IllegalArgumentException) {
                    themeName = decodeBase36(it.key)
                }
            }
            themeName = KeyboardTheme.getUnusedThemeName(themeName, prefs)
            KeyboardTheme.writeUserAllColors(prefs, themeName, allColors)
            KeyboardTheme.writeUserMoreColors(prefs, themeName, 2)
        } catch (e: SerializationException) {
            return false
        }
    }
    return true
}

@Preview
@Composable
private fun Preview() {
    ColorThemePickerDialog(
        onDismissRequest = {},
        setting = Setting(LocalContext.current, "", R.string.settings) {},
        default = "dark",
        isNight = true
    )
}
