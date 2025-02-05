// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.latin.settings.ColorsNightSettingsFragment
import helium314.keyboard.latin.settings.ColorsSettingsFragment
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValues
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.infoDialog
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.switchTo
import helium314.keyboard.settings.AllPrefs
import helium314.keyboard.settings.ListPreference
import helium314.keyboard.settings.NonSettingsPrefs
import helium314.keyboard.settings.PrefDef
import helium314.keyboard.settings.Preference
import helium314.keyboard.settings.PreferenceCategory
import helium314.keyboard.settings.SearchPrefScreen
import helium314.keyboard.settings.SettingsActivity2
import helium314.keyboard.settings.SliderPreference
import helium314.keyboard.settings.SwitchPreference
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.dialogs.CustomizeIconsDialog
import helium314.keyboard.settings.dialogs.InfoDialog
import helium314.keyboard.settings.dialogs.TextInputDialog
import helium314.keyboard.settings.keyboardNeedsReload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AppearanceScreen(
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (LocalContext.current.getActivity() as? SettingsActivity2)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val dayNightMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && Settings.readDayNightPref(prefs, ctx.resources)
    val lightTheme = prefs.getString(Settings.PREF_THEME_COLORS, KeyboardTheme.THEME_LIGHT)
    val darkTheme = prefs.getString(Settings.PREF_THEME_COLORS_NIGHT, KeyboardTheme.THEME_DARK)
    SearchPrefScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_appearance),
    ) {
        PreferenceCategory(stringResource(R.string.settings_screen_theme)) {
            SettingsActivity2.allPrefs.map[Settings.PREF_THEME_STYLE]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_ICON_STYLE]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_CUSTOM_ICON_NAMES]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_THEME_COLORS]!!.Preference()
            if (lightTheme == KeyboardTheme.THEME_USER)
                SettingsActivity2.allPrefs.map[NonSettingsPrefs.ADJUST_COLORS]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_THEME_KEY_BORDERS]!!.Preference()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                SettingsActivity2.allPrefs.map[Settings.PREF_THEME_DAY_NIGHT]!!.Preference()
            if (dayNightMode)
                SettingsActivity2.allPrefs.map[Settings.PREF_THEME_COLORS_NIGHT]!!.Preference()
            if (dayNightMode && darkTheme == KeyboardTheme.THEME_USER_NIGHT)
                SettingsActivity2.allPrefs.map[NonSettingsPrefs.ADJUST_COLORS_NIGHT]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_NAVBAR_COLOR]!!.Preference()
            SettingsActivity2.allPrefs.map[NonSettingsPrefs.BACKGROUND_IMAGE]!!.Preference()
            SettingsActivity2.allPrefs.map[NonSettingsPrefs.BACKGROUND_IMAGE_LANDSCAPE]!!.Preference()
        }
        PreferenceCategory(stringResource(R.string.settings_category_miscellaneous)) {
            SettingsActivity2.allPrefs.map[Settings.PREF_ENABLE_SPLIT_KEYBOARD]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_SPLIT_SPACER_SCALE]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_NARROW_KEY_GAPS]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_KEYBOARD_HEIGHT_SCALE]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_BOTTOM_PADDING_SCALE]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_SPACE_BAR_TEXT]!!.Preference()
            SettingsActivity2.allPrefs.map[NonSettingsPrefs.CUSTOM_FONT]!!.Preference()
        }
    }
}

fun createAppearancePrefs(context: Context) = listOf(
    PrefDef(context, Settings.PREF_THEME_STYLE, R.string.theme_style) { def ->
        val ctx = LocalContext.current
        val prefs = ctx.prefs()
        val items = KeyboardTheme.STYLES.map {
            it.getStringResourceOrName("style_name_", ctx) to it
        }
        ListPreference(
            def,
            items,
            KeyboardTheme.STYLE_MATERIAL
        ) {
            if (it != KeyboardTheme.STYLE_HOLO) {
                // todo: use defaults once they exist
                if (prefs.getString(Settings.PREF_THEME_COLORS, "") == KeyboardTheme.THEME_HOLO_WHITE)
                    prefs.edit().putString(Settings.PREF_THEME_COLORS, KeyboardTheme.THEME_LIGHT).apply()
                if (prefs.getString(Settings.PREF_THEME_COLORS_NIGHT, "") == KeyboardTheme.THEME_HOLO_WHITE)
                    prefs.edit().putString(Settings.PREF_THEME_COLORS_NIGHT, KeyboardTheme.THEME_DARK).apply()
            }
        }
    },
    PrefDef(context, Settings.PREF_ICON_STYLE, R.string.icon_style) { def ->
        val ctx = LocalContext.current
        val items = KeyboardTheme.STYLES.map {
            it.getStringResourceOrName("style_name_", ctx) to it
        }
        ListPreference(
            def,
            items,
            KeyboardTheme.STYLE_MATERIAL
        ) { keyboardNeedsReload = true }
    },
    PrefDef(context, Settings.PREF_CUSTOM_ICON_NAMES, R.string.customize_icons) { def ->
        var showDialog by remember { mutableStateOf(false) }
        Preference(
            name = def.title,
            onClick = { showDialog = true }
        )
        if (showDialog) {
            if (keyboardNeedsReload) {
                KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(LocalContext.current)
                keyboardNeedsReload = false
            }
            CustomizeIconsDialog(def.key) { showDialog = false }
        }
    },
    PrefDef(context, Settings.PREF_THEME_COLORS, R.string.theme_colors) { def ->
        val ctx = LocalContext.current
        val b = (ctx.getActivity() as? SettingsActivity2)?.prefChanged?.collectAsState()
        if ((b?.value ?: 0) < 0)
            Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
        val currentStyle = ctx.prefs().getString(Settings.PREF_THEME_STYLE, KeyboardTheme.STYLE_MATERIAL)
        val items = KeyboardTheme.COLORS.mapNotNull {
            if (it == KeyboardTheme.THEME_HOLO_WHITE && currentStyle != KeyboardTheme.STYLE_HOLO)
                return@mapNotNull null
            it.getStringResourceOrName("theme_name_", ctx) to it
        }
        ListPreference(
            def,
            items,
            KeyboardTheme.THEME_LIGHT
        ) { keyboardNeedsReload = true }
    },
    PrefDef(context, Settings.PREF_THEME_COLORS_NIGHT, R.string.theme_colors_night) { def ->
        val ctx = LocalContext.current
        val b = (ctx.getActivity() as? SettingsActivity2)?.prefChanged?.collectAsState()
        if ((b?.value ?: 0) < 0)
            Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
        val currentStyle = ctx.prefs().getString(Settings.PREF_THEME_STYLE, KeyboardTheme.STYLE_MATERIAL)
        val items = KeyboardTheme.COLORS_DARK.mapNotNull {
            if (it == KeyboardTheme.THEME_HOLO_WHITE && currentStyle == KeyboardTheme.STYLE_HOLO)
                return@mapNotNull null
            it.getStringResourceOrName("theme_name_", ctx) to it
        }
        ListPreference(
            def,
            items,
            KeyboardTheme.THEME_DARK
        ) { keyboardNeedsReload = true }
    },
    PrefDef(context, NonSettingsPrefs.ADJUST_COLORS, R.string.select_user_colors, R.string.select_user_colors_summary) { def ->
        val ctx = LocalContext.current
        Preference(
            name = def.title,
            description = def.description,
            onClick = {
                ctx.getActivity()?.switchTo(ColorsSettingsFragment())
                //SettingsDestination.navigateTo(SettingsDestination.Colors) todo: later
            }
        )
    },
    PrefDef(context, NonSettingsPrefs.ADJUST_COLORS_NIGHT, R.string.select_user_colors_night, R.string.select_user_colors_summary) { def ->
        val ctx = LocalContext.current
        Preference(
            name = def.title,
            description = def.description,
            onClick = {
                ctx.getActivity()?.switchTo(ColorsNightSettingsFragment())
                //SettingsDestination.navigateTo(SettingsDestination.ColorsNight) todo: later
            }
        )
    },
    PrefDef(context, Settings.PREF_THEME_KEY_BORDERS, R.string.key_borders) { def ->
        SwitchPreference(def, false)
    },
    PrefDef(context, Settings.PREF_THEME_DAY_NIGHT, R.string.day_night_mode, R.string.day_night_mode_summary) { def ->
        SwitchPreference(def, Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { keyboardNeedsReload = true }
    },
    PrefDef(context, Settings.PREF_NAVBAR_COLOR, R.string.theme_navbar, R.string.day_night_mode_summary) { def ->
        SwitchPreference(def, Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
    },
    PrefDef(context, NonSettingsPrefs.BACKGROUND_IMAGE, R.string.customize_background_image) { def ->
        var showDayNightDialog by remember { mutableStateOf(false) }
        var showSelectionDialog by remember { mutableStateOf(false) }
        var showErrorDialog by remember { mutableStateOf(false) }
        var isNight by remember { mutableStateOf(false) }
        val ctx = LocalContext.current
        val dayNightPref = Settings.readDayNightPref(ctx.prefs(), ctx.resources)
        val scope = rememberCoroutineScope()
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
            val uri = result.data?.data ?: return@rememberLauncherForActivityResult
            showSelectionDialog = false
            showDayNightDialog = false
            scope.launch(Dispatchers.IO) {
                if (!setBackgroundImage(ctx, uri, isNight, false))
                    showErrorDialog = true
            }
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("image/*")
        Preference(
            name = def.title,
            onClick = {
                if (dayNightPref) {
                    showDayNightDialog = true
                } else if (!Settings.getCustomBackgroundFile(ctx, false, false).exists()) {
                    launcher.launch(intent)
                } else {
                    showSelectionDialog = true
                }
            }
        )
        if (showDayNightDialog) {
            // dialog to set isNight and then show other dialog if image exists
            AlertDialog(
                onDismissRequest = { showDayNightDialog = false },
                confirmButton = {
                    TextButton(onClick = {
                        showDayNightDialog = false
                        isNight = false
                        if (Settings.getCustomBackgroundFile(ctx, isNight, false).exists())
                            showSelectionDialog = true
                        else launcher.launch(intent)
                    }) { Text(stringResource(R.string.day_or_night_day)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDayNightDialog = false
                        isNight = true
                        if (Settings.getCustomBackgroundFile(ctx, isNight, false).exists())
                            showSelectionDialog = true
                        else launcher.launch(intent)
                    }) { Text(stringResource(R.string.day_or_night_night)) }
                },
                title = { Text(stringResource(R.string.day_or_night_image)) },
            )
        }
        if (showSelectionDialog) {
            // todo: delete file or start launcher
        }
        if (showErrorDialog) {
            // todo: infoDialog(requireContext(), R.string.file_read_error)
        }
    },
    PrefDef(context, NonSettingsPrefs.BACKGROUND_IMAGE_LANDSCAPE, R.string.customize_background_image_landscape, R.string.summary_customize_background_image_landscape) { def ->
        var showDialog by remember { mutableStateOf(false) }
        Preference(
            name = def.title,
            description = def.description,
            onClick = { showDialog = true }
        ) // todo: create and show the dialog
    },
    PrefDef(context, Settings.PREF_ENABLE_SPLIT_KEYBOARD, R.string.enable_split_keyboard) {
        SwitchPreference(it, false)
    },
    PrefDef(context, Settings.PREF_SPLIT_SPACER_SCALE, R.string.split_spacer_scale) {
        SliderPreference(
            name = it.title,
            pref = it.key,
            default = SettingsValues.DEFAULT_SIZE_SCALE,
            range = 0.5f..2f,
            description = { "${(100 * it).toInt()}%" }
        ) { keyboardNeedsReload = true }
    },
    PrefDef(context, Settings.PREF_NARROW_KEY_GAPS, R.string.prefs_narrow_key_gaps) {
        SwitchPreference(it, false) { keyboardNeedsReload = true }
    },
    PrefDef(context, Settings.PREF_KEYBOARD_HEIGHT_SCALE, R.string.prefs_keyboard_height_scale) {
        SliderPreference(
            name = it.title,
            pref = it.key,
            default = SettingsValues.DEFAULT_SIZE_SCALE,
            range = 0.5f..1.5f,
            description = { "${(100 * it).toInt()}%" }
        ) { keyboardNeedsReload = true }
    },
    PrefDef(context, Settings.PREF_BOTTOM_PADDING_SCALE, R.string.prefs_bottom_padding_scale) {
        SliderPreference(
            name = it.title,
            pref = it.key,
            default = SettingsValues.DEFAULT_SIZE_SCALE,
            range = 0f..5f,
            description = { "${(100 * it).toInt()}%" }
        ) { keyboardNeedsReload = true }
    },
    PrefDef(context, Settings.PREF_SPACE_BAR_TEXT, R.string.prefs_space_bar_text) { def ->
        var showDialog by remember { mutableStateOf(false) }
        val prefs = LocalContext.current.prefs()
        Preference(
            name = def.title,
            onClick = { showDialog = true },
            description = prefs.getString(def.key, "")
        )
        if (showDialog) {
            TextInputDialog(
                onDismissRequest = { showDialog = false },
                onConfirmed = {
                    prefs.edit().putString(def.key, it).apply()
                    keyboardNeedsReload = true
                },
                initialText = prefs.getString(def.key, "") ?: "",
                title = { Text(def.title) },
                checkTextValid = { true }
            )
        }
    },
    PrefDef(context, NonSettingsPrefs.CUSTOM_FONT, R.string.custom_font) { def ->
        val ctx = LocalContext.current
        var showDialog by remember { mutableStateOf(false) }
        var showErrorDialog by remember { mutableStateOf(false) }
        val fontFile = Settings.getCustomFontFile(ctx)
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
            val uri = it.data?.data ?: return@rememberLauncherForActivityResult
            val tempFile = File(DeviceProtectedUtils.getFilesDir(context), "temp_file")
            FileUtils.copyContentUriToNewFile(uri, ctx, tempFile)
            try {
                val typeface = Typeface.createFromFile(tempFile)
                fontFile.delete()
                tempFile.renameTo(fontFile)
                Settings.clearCachedTypeface()
                keyboardNeedsReload = true
            } catch (_: Exception) {
                showErrorDialog = true
                tempFile.delete()
            }
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
        Preference(
            name = def.title,
            onClick = {
                if (fontFile.exists())
                    showDialog = true
                else launcher.launch(intent)
            },
        )
        if (showDialog)
            ConfirmationDialog(
                onDismissRequest = { showDialog = false },
                onConfirmed = { launcher.launch(intent) },
                onNeutral = {
                    fontFile.delete()
                    Settings.clearCachedTypeface()
                    keyboardNeedsReload = true
                },
                neutralButtonText = stringResource(R.string.delete),
                confirmButtonText = stringResource(R.string.load),
                title = { Text(stringResource(R.string.custom_font)) }
            )
        if (showErrorDialog)
            InfoDialog(stringResource(R.string.file_read_error)) { showErrorDialog = false }
    },
)

private fun setBackgroundImage(ctx: Context, uri: Uri, isNight: Boolean, isLandscape: Boolean): Boolean {
    val imageFile = Settings.getCustomBackgroundFile(ctx, isNight, false)
    FileUtils.copyContentUriToNewFile(uri, ctx, imageFile)
    keyboardNeedsReload = true
    try {
        BitmapFactory.decodeFile(imageFile.absolutePath)
    } catch (_: Exception) {
        imageFile.delete()
        return false
    }
    Settings.clearCachedBackgroundImages()
    return true
}

@Preview
@Composable
private fun Preview() {
    SettingsActivity2.allPrefs = AllPrefs(LocalContext.current)
    Theme(true) {
        Surface {
            AppearanceScreen { }
        }
    }
}
