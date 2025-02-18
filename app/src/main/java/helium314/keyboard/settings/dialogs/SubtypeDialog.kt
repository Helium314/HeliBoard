package helium314.keyboard.settings.dialogs

import android.content.Context
import android.view.inputmethod.InputMethodSubtype
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.keyboard_parser.POPUP_KEYS_ALL
import helium314.keyboard.keyboard.internal.keyboard_parser.POPUP_KEYS_MAIN
import helium314.keyboard.keyboard.internal.keyboard_parser.POPUP_KEYS_MORE
import helium314.keyboard.keyboard.internal.keyboard_parser.POPUP_KEYS_NORMAL
import helium314.keyboard.keyboard.internal.keyboard_parser.hasLocalizedNumberRow
import helium314.keyboard.keyboard.internal.keyboard_parser.morePopupKeysResId
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Constants.Separators
import helium314.keyboard.latin.common.Constants.Subtype.ExtraValue
import helium314.keyboard.latin.common.LocaleUtils
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.LayoutType
import helium314.keyboard.latin.utils.LayoutType.Companion.displayNameId
import helium314.keyboard.latin.utils.LayoutUtils
import helium314.keyboard.latin.utils.LayoutUtilsCustom
import helium314.keyboard.latin.utils.ScriptUtils.SCRIPT_LATIN
import helium314.keyboard.latin.utils.ScriptUtils.script
import helium314.keyboard.latin.utils.SettingsSubtype
import helium314.keyboard.latin.utils.SettingsSubtype.Companion.toSettingsSubtype
import helium314.keyboard.latin.utils.SubtypeLocaleUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.SubtypeUtilsAdditional
import helium314.keyboard.latin.utils.getDictionaryLocales
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.screens.GetIcon
import java.util.Locale

// todo:
//  save when "editing" a resource subtypes is not working
//  settings upgrade to move the override settings to extra values, and actually use them (via getSelectedSubtype, not RichIMM)
@Composable
fun SubtypeDialog(
    onDismissRequest: () -> Unit,
    subtype: InputMethodSubtype,
    onConfirmed: (SettingsSubtype) -> Unit,
) {
    // todo: make sure the values are always correct (e.g. if using rememberSaveable and rotating)
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    var currentSubtype by remember { mutableStateOf(subtype.toSettingsSubtype()) }
    val availableLocalesForScript = getAvailableSecondaryLocales(ctx, currentSubtype.locale).sortedBy { it.toLanguageTag() }
    var showSecondaryLocaleDialog by remember { mutableStateOf(false) }
    var showKeyOrderDialog by remember { mutableStateOf(false) }
    var showHintOrderDialog by remember { mutableStateOf(false) }
    var showMorePopupsDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = { onConfirmed(currentSubtype) },
        neutralButtonText = if (SubtypeSettings.isAdditionalSubtype(subtype)) null else stringResource(R.string.delete),
        onNeutral = {
            SubtypeUtilsAdditional.removeAdditionalSubtype(prefs, subtype)
            SubtypeSettings.removeEnabledSubtype(prefs, subtype)

        }, // maybe confirm dialog?
        title = { Text(SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(subtype)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WithSmallTitle(stringResource(R.string.keyboard_layout_set)) {
                    val appLayouts = LayoutUtils.getAvailableLayouts(LayoutType.MAIN, ctx, currentSubtype.locale)
                    val customLayouts = LayoutUtilsCustom.getLayoutFiles(LayoutType.MAIN, ctx, currentSubtype.locale).map { it.name }
                    DropDownField(
                        items = appLayouts + customLayouts,
                        selectedItem = currentSubtype.mainLayoutName() ?: "qwerty", // todo: what about qwerty+ and similar?
                        onSelected = {
                            currentSubtype = currentSubtype.withLayout(LayoutType.MAIN, it)
                        }
                    ) {
                        Text(SubtypeLocaleUtils.getDisplayNameInSystemLocale(it, currentSubtype.locale))
                        // todo: edit button? or only for selected layout? and delete button?
                        //  yes, even just to make clear what is custom
                    }
                }
                WithSmallTitle(stringResource(R.string.secondary_locale)) {
                    TextButton(onClick = { showSecondaryLocaleDialog = true }) {
                        val text = currentSubtype.getExtraValueOf(ExtraValue.SECONDARY_LOCALES)
                            ?.split(Separators.KV)?.joinToString(", ") {
                                LocaleUtils.getLocaleDisplayNameInSystemLocale(it.constructLocale(), ctx)
                            } ?: stringResource(R.string.action_none)
                        Text(text, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                WithSmallTitle("dictionaries") {
                    // todo: maybe remove here and use a separate screen for dictionary management
                    //  would be clearer, as dicts are per language (and no intention to change it)
                    Text("not yet implemented")
                }
                // todo: this looks strange without the title
                Row {
                    TextButton(onClick = { showKeyOrderDialog = true }, Modifier.weight(1f))
                    { Text(stringResource(R.string.popup_order), style = MaterialTheme.typography.bodyLarge) }
                    DefaultButton(
                        { currentSubtype = currentSubtype.without(ExtraValue.POPUP_ORDER) },
                        currentSubtype.getExtraValueOf(ExtraValue.POPUP_ORDER) == null
                    )
                }
                Row {
                    TextButton(onClick = { showHintOrderDialog = true }, Modifier.weight(1f))
                    { Text(stringResource(R.string.hint_source), style = MaterialTheme.typography.bodyLarge) }
                    DefaultButton(
                        { currentSubtype = currentSubtype.without(ExtraValue.HINT_ORDER) },
                        currentSubtype.getExtraValueOf(ExtraValue.HINT_ORDER) == null
                    )
                }
                if (currentSubtype.locale.script() == SCRIPT_LATIN) {
                    WithSmallTitle(stringResource(R.string.show_popup_keys_title)) {
                        val explicitValue = currentSubtype.getExtraValueOf(ExtraValue.MORE_POPUPS)
                        val value = explicitValue ?: prefs.getString(Settings.PREF_MORE_POPUP_KEYS, Defaults.PREF_MORE_POPUP_KEYS)!!
                        Row {
                            TextButton(onClick = { showMorePopupsDialog = true }, Modifier.weight(1f))
                            { Text(stringResource(morePopupKeysResId(value))) }
                            DefaultButton(
                                { currentSubtype = currentSubtype.without(ExtraValue.MORE_POPUPS) },
                                explicitValue == null
                            )
                        }
                    }
                }
                if (hasLocalizedNumberRow(currentSubtype.locale, ctx)) {
                    Row {
                        val checked = currentSubtype.getExtraValueOf(ExtraValue.LOCALIZED_NUMBER_ROW)?.toBoolean()
                        Text(stringResource(R.string.localized_number_row), Modifier.weight(1f))
                        Switch(
                            checked = checked ?: prefs.getBoolean(Settings.PREF_LOCALIZED_NUMBER_ROW, Defaults.PREF_LOCALIZED_NUMBER_ROW),
                            onCheckedChange = {
                                currentSubtype = currentSubtype.with(ExtraValue.LOCALIZED_NUMBER_ROW, it.toString())
                            }
                        )
                        DefaultButton(
                            { currentSubtype = currentSubtype.without(ExtraValue.LOCALIZED_NUMBER_ROW) },
                            checked == null
                        )
                    }
                }
                HorizontalDivider()
                Text(stringResource(R.string.settings_screen_secondary_layouts), style = MaterialTheme.typography.titleMedium)
                LayoutType.entries.forEach { type ->
                    if (type == LayoutType.MAIN) return@forEach
                    WithSmallTitle(stringResource(type.displayNameId)) {
                        val explicitLayout = currentSubtype.layoutName(type)
                        val layout = explicitLayout ?: Settings.readDefaultLayoutName(type, prefs)
                        val defaultLayouts = LayoutUtils.getAvailableLayouts(type, ctx)
                        val customLayouts = LayoutUtilsCustom.getLayoutFiles(type, ctx).map { it.name }
                        DropDownField(
                            items = defaultLayouts + customLayouts,
                            selectedItem = layout,
                            onSelected = {
                                currentSubtype = currentSubtype.withLayout(type, it)
                            },
                            onDefault = { currentSubtype = currentSubtype.withoutLayout(type) },
                            isDefault = explicitLayout == null
                        ) {
                            val displayName = if (LayoutUtilsCustom.isCustomLayout(it)) LayoutUtilsCustom.getDisplayName(it)
                            else it.getStringResourceOrName("layout_", ctx)
                            Text(displayName)
                            // content is name, and if it's user layout there is an edit button
                            // also maybe there should be an "add" button similar to the old settings
                        }
                    }
                }
            }
        }
    )
    if (showSecondaryLocaleDialog)
        MultiListPickerDialog(
            onDismissRequest = { showSecondaryLocaleDialog = false },
            onConfirmed = { locales ->
                val newValue = locales.joinToString(Separators.KV) { it.toLanguageTag() }
                currentSubtype = if (newValue.isEmpty()) currentSubtype.without(ExtraValue.SECONDARY_LOCALES)
                else currentSubtype.with(ExtraValue.SECONDARY_LOCALES, newValue)
            },
            items = availableLocalesForScript,
            initialSelection = currentSubtype.getExtraValueOf(ExtraValue.SECONDARY_LOCALES)
                ?.split(Separators.KV)?.map { it.constructLocale() }.orEmpty(),
            getItemName = { LocaleUtils.getLocaleDisplayNameInSystemLocale(it, ctx) }
        )
    if (showKeyOrderDialog) {
        val setting = currentSubtype.getExtraValueOf(ExtraValue.POPUP_ORDER)
        PopupOrderDialog(
            onDismissRequest = { showKeyOrderDialog = false },
            initialValue = setting ?: prefs.getString(Settings.PREF_POPUP_KEYS_ORDER, Defaults.PREF_POPUP_KEYS_ORDER)!!,
            title = stringResource(R.string.popup_order),
            showDefault = setting != null,
            onConfirmed = {
                currentSubtype = if (it == null) currentSubtype.without(ExtraValue.POPUP_ORDER)
                    else currentSubtype.with(ExtraValue.POPUP_ORDER, it)
            }
        )
    }
    if (showHintOrderDialog) {
        val setting = currentSubtype.getExtraValueOf(ExtraValue.HINT_ORDER)
        PopupOrderDialog(
            onDismissRequest = { showHintOrderDialog = false },
            initialValue = setting ?: prefs.getString(Settings.PREF_POPUP_KEYS_LABELS_ORDER, Defaults.PREF_POPUP_KEYS_LABELS_ORDER)!!,
            title = stringResource(R.string.hint_source),
            showDefault = setting != null,
            onConfirmed = {
                currentSubtype = if (it == null) currentSubtype.without(ExtraValue.HINT_ORDER)
                    else currentSubtype.with(ExtraValue.HINT_ORDER, it)
            }
        )
    }
    if (showMorePopupsDialog) {
        val items = listOf(POPUP_KEYS_NORMAL, POPUP_KEYS_MAIN, POPUP_KEYS_MORE, POPUP_KEYS_ALL)
        val explicitValue = currentSubtype.getExtraValueOf(ExtraValue.MORE_POPUPS)
        val value = explicitValue ?: prefs.getString(Settings.PREF_MORE_POPUP_KEYS, Defaults.PREF_MORE_POPUP_KEYS)
        ListPickerDialog(
            onDismissRequest = { showMorePopupsDialog = false },
            items = items,
            getItemName = { stringResource(morePopupKeysResId(it)) },
            selectedItem = value,
            onItemSelected = { currentSubtype = currentSubtype.with(ExtraValue.MORE_POPUPS, it) }
        )
    }
}

// from ReorderSwitchPreference
@Composable
private fun PopupOrderDialog(
    onDismissRequest: () -> Unit,
    initialValue: String,
    onConfirmed: (String?) -> Unit,
    title: String,
    showDefault: Boolean
) {
    class KeyAndState(var name: String, var state: Boolean)
    val items = initialValue.split(Separators.ENTRY).map {
        KeyAndState(it.substringBefore(Separators.KV), it.substringAfter(Separators.KV).toBoolean())
    }
    val ctx = LocalContext.current
    ReorderDialog(
        onConfirmed = { reorderedItems ->
            val value = reorderedItems.joinToString(Separators.ENTRY) { it.name + Separators.KV + it.state }
            onConfirmed(value)
        },
        onDismissRequest = onDismissRequest,
        onNeutral = { onDismissRequest(); onConfirmed(null) },
        neutralButtonText = if (showDefault) stringResource(R.string.button_default) else null,
        items = items,
        title = { Text(title) },
        displayItem = { item ->
            var checked by rememberSaveable { mutableStateOf(item.state) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                KeyboardIconsSet.instance.GetIcon(item.name)
                val text = item.name.lowercase().getStringResourceOrName("popup_keys_", ctx)
                Text(text, Modifier.weight(1f))
                Switch(
                    checked = checked,
                    onCheckedChange = { item.state = it; checked = it }
                )
            }
        },
        getKey = { it.name }
    )
}

@Composable
private fun WithSmallTitle(
    description: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(description, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@Composable
private fun <T>DropDownField(
    items: List<T>,
    selectedItem: T,
    onSelected: (T) -> Unit,
    isDefault: Boolean? = null,
    onDefault: () -> Unit = {},
    itemContent: @Composable (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        Modifier.clickable { expanded = !expanded }
            //.border(2.dp, MaterialTheme.colorScheme.onSecondary)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
        ) {
            Box(Modifier.weight(1f)) {
                itemContent(selectedItem)
            }
            IconButton(
                onClick = { expanded = !expanded },
                enabled = items.size > 1
            ) {
                Icon(
                    painterResource(R.drawable.ic_arrow_left),
                    "show dropdown",
                    Modifier.rotate(-90f)
                )
            }
            if (isDefault != null)
                DefaultButton(onDefault, isDefault)
        }
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        items.forEach {
            DropdownMenuItem(
                text = { itemContent(it) },
                onClick = { expanded = false; onSelected(it) }
            )
        }
    }
}

@Composable
private fun DefaultButton(
    onDefault: () -> Unit,
    isDefault: Boolean
) {
    IconButton(
        onClick = onDefault,
        enabled = !isDefault
    ) {
        Icon(painterResource(R.drawable.sym_keyboard_settings_holo), "default") // todo: more understandable icon!
    }

}

private fun getAvailableSecondaryLocales(context: Context, mainLocale: Locale): List<Locale> =
    getDictionaryLocales(context).filter { it != mainLocale && it.script() == mainLocale.script() }
