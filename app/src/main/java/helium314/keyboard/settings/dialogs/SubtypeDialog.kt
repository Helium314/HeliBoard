// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.tooling.preview.Preview
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
import helium314.keyboard.latin.common.Links
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.common.LocaleUtils.localizedDisplayName
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsSubtype
import helium314.keyboard.latin.settings.SettingsSubtype.Companion.toSettingsSubtype
import helium314.keyboard.latin.utils.LayoutType
import helium314.keyboard.latin.utils.LayoutType.Companion.displayNameId
import helium314.keyboard.latin.utils.LayoutUtils
import helium314.keyboard.latin.utils.LayoutUtilsCustom
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ScriptUtils
import helium314.keyboard.latin.utils.ScriptUtils.script
import helium314.keyboard.latin.utils.SubtypeLocaleUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.SubtypeUtilsAdditional
import helium314.keyboard.latin.utils.appendLink
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.getDictionaryLocales
import helium314.keyboard.latin.utils.getSecondaryLocales
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.mainLayoutName
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.DefaultButton
import helium314.keyboard.settings.DropDownField
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.WithSmallTitle
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.layoutFilePicker
import helium314.keyboard.settings.layoutIntent
import helium314.keyboard.settings.previewDark
import helium314.keyboard.settings.screens.GetIcon
import java.util.Locale

@Composable
fun SubtypeDialog(
    onDismissRequest: () -> Unit,
    initialSubtype: SettingsSubtype,
    onConfirmed: (SettingsSubtype) -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (LocalContext.current.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    var currentSubtypeString by rememberSaveable { mutableStateOf(initialSubtype.toPref()) }
    val currentSubtype = currentSubtypeString.toSettingsSubtype()
    fun setCurrentSubtype(subtype: SettingsSubtype) { currentSubtypeString = subtype.toPref() }
    LaunchedEffect(currentSubtypeString) {
        if (ScriptUtils.scriptSupportsUppercase(currentSubtype.locale)) return@LaunchedEffect
        // update the noShiftKey extra value
        val mainLayout = currentSubtype.mainLayoutName()
        val noShiftKey = if (mainLayout != null && LayoutUtilsCustom.isCustomLayout(mainLayout)) {
            // determine from layout
            val content = LayoutUtilsCustom.getLayoutFile(mainLayout, LayoutType.MAIN, ctx).readText()
            !content.contains("\"shift_state_selector\"")
        } else {
            // determine from subtype with same layout
            SubtypeSettings.getResourceSubtypesForLocale(currentSubtype.locale)
                .firstOrNull { it.mainLayoutName() == mainLayout }
                ?.containsExtraValueKey(ExtraValue.NO_SHIFT_KEY) ?: false
        }
        if (!noShiftKey && currentSubtype.hasExtraValueOf(ExtraValue.NO_SHIFT_KEY))
            setCurrentSubtype(currentSubtype.without(ExtraValue.NO_SHIFT_KEY))
        else if (noShiftKey && !currentSubtype.hasExtraValueOf(ExtraValue.NO_SHIFT_KEY))
            setCurrentSubtype(currentSubtype.with(ExtraValue.NO_SHIFT_KEY))
    }

    val availableLocalesForScript = getAvailableSecondaryLocales(ctx, currentSubtype.locale).sortedBy { it.toLanguageTag() }
    var showSecondaryLocaleDialog by remember { mutableStateOf(false) }
    var showKeyOrderDialog by remember { mutableStateOf(false) }
    var showHintOrderDialog by remember { mutableStateOf(false) }
    var showMorePopupsDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val customMainLayouts = LayoutUtilsCustom.getLayoutFiles(LayoutType.MAIN, ctx, currentSubtype.locale).map { it.name }
    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = { onConfirmed(currentSubtype) },
        neutralButtonText = if (initialSubtype.isAdditionalSubtype(prefs)) stringResource(R.string.delete) else null,
        onNeutral = {
            SubtypeUtilsAdditional.removeAdditionalSubtype(ctx, initialSubtype.toAdditionalSubtype())
            SubtypeSettings.removeEnabledSubtype(ctx, initialSubtype.toAdditionalSubtype())
            onDismissRequest()
        },
        title = {
            val mainLayout = initialSubtype.mainLayoutName() ?: SubtypeLocaleUtils.QWERTY
            Text(SubtypeLocaleUtils.getDisplayNameInSystemLocale(mainLayout, initialSubtype.locale))
        },
        content = {
            Column(
                modifier = Modifier.verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MainLayoutRow(initialSubtype, currentSubtype, customMainLayouts) { setCurrentSubtype(it) }
                if (availableLocalesForScript.size > 1) {
                    WithSmallTitle(stringResource(R.string.secondary_locale)) {
                        TextButton(onClick = { showSecondaryLocaleDialog = true }) {
                            val text = getSecondaryLocales(currentSubtype.extraValues).joinToString(", ") {
                                    it.localizedDisplayName(ctx)
                                }.ifEmpty { stringResource(R.string.action_none) }
                            Text(text, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                Row {
                    TextButton(onClick = { showKeyOrderDialog = true }, Modifier.weight(1f))
                    { Text(stringResource(R.string.popup_order), style = MaterialTheme.typography.bodyLarge) }
                    DefaultButton(currentSubtype.getExtraValueOf(ExtraValue.POPUP_ORDER) == null) {
                        setCurrentSubtype(currentSubtype.without(ExtraValue.POPUP_ORDER))
                    }
                }
                Row {
                    TextButton(onClick = { showHintOrderDialog = true }, Modifier.weight(1f))
                    { Text(stringResource(R.string.hint_source), style = MaterialTheme.typography.bodyLarge) }
                    DefaultButton(currentSubtype.getExtraValueOf(ExtraValue.HINT_ORDER) == null) {
                        setCurrentSubtype(currentSubtype.without(ExtraValue.HINT_ORDER))
                    }
                }
                if (currentSubtype.locale.script() == ScriptUtils.SCRIPT_LATIN) {
                    WithSmallTitle(stringResource(R.string.show_popup_keys_title)) {
                        val explicitValue = currentSubtype.getExtraValueOf(ExtraValue.MORE_POPUPS)
                        val value = explicitValue ?: prefs.getString(Settings.PREF_MORE_POPUP_KEYS, Defaults.PREF_MORE_POPUP_KEYS)!!
                        Row {
                            TextButton(onClick = { showMorePopupsDialog = true }, Modifier.weight(1f))
                            { Text(stringResource(morePopupKeysResId(value))) }
                            DefaultButton(explicitValue == null) {
                                setCurrentSubtype(currentSubtype.without(ExtraValue.MORE_POPUPS))
                            }
                        }
                    }
                }
                if (hasLocalizedNumberRow(currentSubtype.locale, ctx)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val checked = currentSubtype.getExtraValueOf(ExtraValue.LOCALIZED_NUMBER_ROW)?.toBoolean()
                        Text(stringResource(R.string.localized_number_row), Modifier.weight(1f))
                        Switch(
                            checked = checked ?: prefs.getBoolean(Settings.PREF_LOCALIZED_NUMBER_ROW, Defaults.PREF_LOCALIZED_NUMBER_ROW),
                            onCheckedChange = {
                                setCurrentSubtype(currentSubtype.with(ExtraValue.LOCALIZED_NUMBER_ROW, it.toString()))
                            }
                        )
                        DefaultButton(checked == null) {
                            setCurrentSubtype(currentSubtype.without(ExtraValue.LOCALIZED_NUMBER_ROW))
                        }
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
                                setCurrentSubtype(currentSubtype.withLayout(type, it))
                            },
                            extraButton = { DefaultButton(explicitLayout == null) {
                                setCurrentSubtype(currentSubtype.withoutLayout(type))
                            } },
                        ) {
                            val displayName = if (LayoutUtilsCustom.isCustomLayout(it)) LayoutUtilsCustom.getDisplayName(it)
                            else it.getStringResourceOrName("layout_", ctx)
                            var showLayoutEditDialog by remember { mutableStateOf(false) }
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(displayName, Modifier.padding(end = 8.dp))
                                if (LayoutUtilsCustom.isCustomLayout(it))
                                    Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.edit_layout), Modifier.clickable { showLayoutEditDialog = true })
                            }
                            if (showLayoutEditDialog)
                                LayoutEditDialog(
                                    onDismissRequest = { showLayoutEditDialog = false },
                                    layoutType = type,
                                    initialLayoutName = it,
                                    isNameValid = null
                                )
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
                setCurrentSubtype(
                    if (newValue.isEmpty()) currentSubtype.without(ExtraValue.SECONDARY_LOCALES)
                    else currentSubtype.with(ExtraValue.SECONDARY_LOCALES, newValue)
                )
            },
            title = { Text(stringResource(R.string.locales_with_dict)) },
            items = availableLocalesForScript,
            initialSelection = currentSubtype.getExtraValueOf(ExtraValue.SECONDARY_LOCALES)
                ?.split(Separators.KV)?.map { it.constructLocale() }.orEmpty(),
            getItemName = { it.localizedDisplayName(ctx) }
        )
    if (showKeyOrderDialog) {
        val setting = currentSubtype.getExtraValueOf(ExtraValue.POPUP_ORDER)
        PopupOrderDialog(
            onDismissRequest = { showKeyOrderDialog = false },
            initialValue = setting ?: prefs.getString(Settings.PREF_POPUP_KEYS_ORDER, Defaults.PREF_POPUP_KEYS_ORDER)!!,
            title = stringResource(R.string.popup_order),
            showDefault = setting != null,
            onConfirmed = {
                setCurrentSubtype(
                    if (it == null) currentSubtype.without(ExtraValue.POPUP_ORDER)
                    else currentSubtype.with(ExtraValue.POPUP_ORDER, it)
                )
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
                setCurrentSubtype(
                    if (it == null) currentSubtype.without(ExtraValue.HINT_ORDER)
                    else currentSubtype.with(ExtraValue.HINT_ORDER, it)
                )
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
            onItemSelected = { setCurrentSubtype(currentSubtype.with(ExtraValue.MORE_POPUPS, it)) }
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
private fun MainLayoutRow(
    initialSubtype: SettingsSubtype,
    currentSubtype: SettingsSubtype,
    customLayouts: List<String>,
    setCurrentSubtype: (SettingsSubtype) -> Unit,
) {
    val ctx = LocalContext.current
    WithSmallTitle(stringResource(R.string.keyboard_layout_set)) {
        val appLayouts = LayoutUtils.getAvailableLayouts(LayoutType.MAIN, ctx, currentSubtype.locale)
        var showAddLayoutDialog by remember { mutableStateOf(false) }
        var showLayoutEditDialog: Pair<String, String?>? by remember { mutableStateOf(null) }
        val layoutPicker = layoutFilePicker { content, name ->
            showLayoutEditDialog = (name ?: "new layout") to content
        }
        DropDownField(
            items = appLayouts + customLayouts,
            selectedItem = currentSubtype.mainLayoutName() ?: SubtypeLocaleUtils.QWERTY,
            onSelected = {
                setCurrentSubtype(currentSubtype.withLayout(LayoutType.MAIN, it))
            },
            extraButton = {
                IconButton({ showAddLayoutDialog = true })
                { Icon(painterResource(R.drawable.ic_plus), stringResource(R.string.button_title_add_custom_layout)) }
            }
        ) {
            var showLayoutDeleteDialog by remember { mutableStateOf(false) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(SubtypeLocaleUtils.getDisplayNameInSystemLocale(it, currentSubtype.locale))
                Row (verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.edit_layout), Modifier.clickable { showLayoutEditDialog = it to null })
                    if (it in customLayouts && initialSubtype.mainLayoutName() != it) // don't allow current main layout
                        Icon(painterResource(R.drawable.ic_bin), stringResource(R.string.delete), Modifier.clickable { showLayoutDeleteDialog = true })
                }
            }
            if (showLayoutDeleteDialog) {
                val others = SubtypeSettings.getAdditionalSubtypes().filter { st -> st.mainLayoutName() == it }
                    .any { it.toSettingsSubtype() != initialSubtype }
                ConfirmationDialog(
                    onDismissRequest = { showLayoutDeleteDialog = false },
                    confirmButtonText = stringResource(R.string.delete),
                    title = { Text(stringResource(R.string.delete_layout, LayoutUtilsCustom.getDisplayName(it))) },
                    content = { if (others) Text(stringResource(R.string.layout_in_use)) },
                    onConfirmed = {
                        if (it == currentSubtype.mainLayoutName())
                            setCurrentSubtype(currentSubtype.withoutLayout(LayoutType.MAIN))
                        LayoutUtilsCustom.deleteLayout(it, LayoutType.MAIN, ctx)
                        (ctx.getActivity() as? SettingsActivity)?.prefChanged?.value = 1234
                    }
                )
            }
        }
        if (showLayoutEditDialog != null) {
            val layoutName = showLayoutEditDialog!!.first
            val startContent = showLayoutEditDialog?.second
                ?: if (layoutName in appLayouts) LayoutUtils.getContent(LayoutType.MAIN, layoutName, ctx)
                else null
            LayoutEditDialog(
                onDismissRequest = { showLayoutEditDialog = null },
                layoutType = LayoutType.MAIN,
                initialLayoutName = layoutName,
                startContent = startContent,
                locale = currentSubtype.locale,
                // only can edit name for new custom layout
                isNameValid = if (layoutName in customLayouts) null else ({ it !in customLayouts }),
                onEdited = {
                    if (layoutName !in customLayouts)
                        setCurrentSubtype(currentSubtype.withLayout(LayoutType.MAIN, it))
                }
            )
        }
        if (showAddLayoutDialog) {
            // layoutString contains "%s" since we didn't supply a formatArg
            val layoutString = stringResource(R.string.message_add_custom_layout)
            val linkText = stringResource(R.string.dictionary_link_text)
            val discussionSectionText = stringResource(R.string.get_layouts_message)
            val annotated = buildAnnotatedString {
                append(layoutString.substringBefore("%s"))
                appendLink(linkText, Links.LAYOUT_FORMAT_URL)
                append(layoutString.substringAfter("%s"))
                appendLine()
                append(discussionSectionText.substringBefore("%s"))
                appendLink(stringResource(R.string.discussion_section_link), Links.CUSTOM_LAYOUTS)
                append(discussionSectionText.substringAfter("%s"))
            }

            ConfirmationDialog(
                onDismissRequest = { showAddLayoutDialog = false },
                title = { Text(stringResource(R.string.button_title_add_custom_layout)) },
                content = { Text(annotated) },
                onConfirmed = { showLayoutEditDialog = "new layout" to "" },
                neutralButtonText = stringResource(R.string.button_load_custom),
                onNeutral = {
                    showAddLayoutDialog = false
                    layoutPicker.launch(layoutIntent)
                }
            )
        }
    }
}

private fun getAvailableSecondaryLocales(context: Context, mainLocale: Locale): List<Locale> =
    getDictionaryLocales(context).filter { it != mainLocale && it.script() == mainLocale.script() }

@Preview
@Composable
private fun Preview() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        SubtypeDialog({}, SettingsSubtype(Locale.ENGLISH, "")) { }
    }
}
