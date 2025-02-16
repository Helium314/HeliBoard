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
import helium314.keyboard.keyboard.internal.keyboard_parser.hasLocalizedNumberRow
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
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.screens.GetIcon
import java.util.Locale

// todo:
//  save when "editing" a resource subtypes is not working
//  default buttons missing
//  string resources
@Composable
fun SubtypeDialog(
    // could also use InputMethodSubtype if there is any advantage
    // but as soon as anything is changed we will need an additional subtype anyway...
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
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                WithDescription("main layout") {
                    val appLayouts = LayoutUtils.getAvailableLayouts(LayoutType.MAIN, ctx, currentSubtype.locale)
                    val customLayouts = LayoutUtilsCustom.getLayoutFiles(LayoutType.MAIN, ctx, currentSubtype.locale).map { it.name }
                    DropDownField(
                        items = appLayouts + customLayouts,
                        selectedItem = currentSubtype.mainLayoutName() ?: "qwerty", // todo: what about qwerty+ and similar?
                        onSelected = {
                            currentSubtype = currentSubtype.withLayout(LayoutType.MAIN, it)
                        }
                    ) {
                        // todo: displayName can be complicated and may require an inputmehtodsubtype...
                        //  maybe search for stuff in resource subtypes?
                        Text(it)
                        // todo: edit button? or only for selected layout? and delete button?
                    }
                }
                WithDescription(stringResource(R.string.secondary_locale)) {
                    TextButton(onClick = { showSecondaryLocaleDialog = true }, Modifier.fillMaxWidth()) {
                        val text = currentSubtype.getExtraValueOf(ExtraValue.SECONDARY_LOCALES)
                            ?.split(Separators.KV)?.joinToString(", ") {
                                LocaleUtils.getLocaleDisplayNameInSystemLocale(it.constructLocale(), ctx)
                            } ?: "none"
                        Text(text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                TextButton(onClick = { showSecondaryLocaleDialog = true }) {
                    val text = currentSubtype.getExtraValueOf(ExtraValue.SECONDARY_LOCALES)
                        ?.split(Separators.KV)?.joinToString(", ") {
                            LocaleUtils.getLocaleDisplayNameInSystemLocale(it.constructLocale(), ctx)
                        } ?: ""
                    Column(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.secondary_locale))
                        Text(text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                WithDescription("dictionaries") {
                    // todo: maybe remove here and use a separate screen for dictionary management
                    //  would be clearer, as dicts are per language (and no intention to change it)
                    Text("not yet implemented")
                }
                TextButton(onClick = { showKeyOrderDialog = true })
                    { Text(stringResource(R.string.popup_order), Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge) }
                TextButton(onClick = { showHintOrderDialog = true })
                    { Text(stringResource(R.string.hint_source), Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge) }
                if (currentSubtype.locale.script() == SCRIPT_LATIN)
                    WithDescription(stringResource(R.string.show_popup_keys_title)) {
                        val explicitValue = currentSubtype.getExtraValueOf(ExtraValue.MORE_POPUPS)
                        val value = explicitValue ?: prefs.getString(Settings.PREF_MORE_POPUP_KEYS, Defaults.PREF_MORE_POPUP_KEYS)
                        val textResId = when (value) { // todo: this should not be duplicated... see below
                            "normal" -> R.string.show_popup_keys_normal
                            "more" -> R.string.show_popup_keys_more
                            "all" -> R.string.show_popup_keys_all
                            else -> R.string.show_popup_keys_main
                        }
                        TextButton(onClick = { showMorePopupsDialog = true }, Modifier.fillMaxWidth())
                            { Text(stringResource(textResId)) }
                    }
                if (hasLocalizedNumberRow(currentSubtype.locale, ctx))
                    Row {
                        Text(stringResource(R.string.localized_number_row), Modifier.weight(1f))
                        Switch(
                            checked = currentSubtype.getExtraValueOf(ExtraValue.LOCALIZED_NUMBER_ROW)?.toBoolean()
                                ?: prefs.getBoolean(Settings.PREF_LOCALIZED_NUMBER_ROW, Defaults.PREF_LOCALIZED_NUMBER_ROW),
                            onCheckedChange = {
                                currentSubtype = currentSubtype.with(ExtraValue.LOCALIZED_NUMBER_ROW, it.toString())
                            }
                        )
                        // todo: default button?
                    }
                LayoutType.entries.forEach { type ->
                    if (type == LayoutType.MAIN) return@forEach
                    // todo: also some default button, to be shown when necessary, uses currentSubtype.withoutLayout(type)
                    WithDescription(stringResource(type.displayNameId)) {
                        val explicitLayout = currentSubtype.layoutName(type)
                        val layout = explicitLayout ?: Settings.readDefaultLayoutName(type, prefs)
                        val defaultLayouts = LayoutUtils.getAvailableLayouts(type, ctx)
                        val customLayouts = LayoutUtilsCustom.getLayoutFiles(type, ctx).map { it.name }
                        DropDownField(
                            items = defaultLayouts + customLayouts,
                            selectedItem = layout,
                            onSelected = {
                                currentSubtype = currentSubtype.withLayout(type, it)
                            }
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
            onConfirmed = {
                val newValue = it.joinToString(Separators.KV) { it.toLanguageTag() }
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
                if (it == null) currentSubtype = currentSubtype.without(ExtraValue.POPUP_ORDER)
                else currentSubtype = currentSubtype.with(ExtraValue.POPUP_ORDER, it)
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
                if (it == null) currentSubtype = currentSubtype.without(ExtraValue.HINT_ORDER)
                else currentSubtype = currentSubtype.with(ExtraValue.HINT_ORDER, it)
            }
        )
    }
    if (showMorePopupsDialog) {
        // todo: default button in here? or next to the pref?
        val items = listOf("normal", "main", "more", "all")
        val explicitValue = currentSubtype.getExtraValueOf(ExtraValue.MORE_POPUPS)
        val value = explicitValue ?: prefs.getString(Settings.PREF_MORE_POPUP_KEYS, Defaults.PREF_MORE_POPUP_KEYS)
        ListPickerDialog(
            onDismissRequest = { showMorePopupsDialog = false },
            items = items,
            getItemName = {
                val textResId = when (it) { // todo: this should not be duplicated... now we have it twice here, and in advanced settings
                    "normal" -> R.string.show_popup_keys_normal
                    "more" -> R.string.show_popup_keys_more
                    "all" -> R.string.show_popup_keys_all
                    else -> R.string.show_popup_keys_main
                }
                stringResource(textResId)
            },
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
                val text = item.name.lowercase().getStringResourceOrName("", ctx)
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
private fun WithDescription(
    description: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(description, style = MaterialTheme.typography.bodySmall)
        content()
    }
}

@Composable
private fun <T>DropDownField(
    items: List<T>,
    selectedItem: T,
    onSelected: (T) -> Unit,
    itemContent: @Composable (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        Modifier.clickable { expanded = !expanded }
            //.border(2.dp, MaterialTheme.colorScheme.onSecondary)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp)
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
                    null,
                    Modifier.rotate(-90f)
                )
            }
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

// get locales with same script as main locale, but different language
// todo: do we need any sort of force-ascii like in old variant?
//  now we use hi-Latn and sr-Latn for the relevant subtypes, so it should be fine
//  only potential issue is the Latn-default if we don't have the script for a locale,
//  but in that case we should rather add the script to ScriptUtils
private fun getAvailableSecondaryLocales(context: Context, mainLocale: Locale): List<Locale> {
    val locales = getDictionaryLocales(context)
    locales.removeAll {
//        it.language == mainLocale.language || it.script() != mainLocale.script()
        it == mainLocale || it.script() != mainLocale.script() // todo: check whether this is fine, otherwise go back to the variant above
    }
    return locales.toList()
}
