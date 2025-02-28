// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.os.Build
import android.view.inputmethod.InputMethodSubtype
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Constants.Separators
import helium314.keyboard.latin.common.Constants.Subtype.ExtraValue
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.common.LocaleUtils.localizedDisplayName
import helium314.keyboard.latin.common.splitOnWhitespace
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.USER_DICTIONARY_SUFFIX
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.MissingDictionaryDialog
import helium314.keyboard.latin.utils.SettingsSubtype.Companion.toSettingsSubtype
import helium314.keyboard.latin.utils.SubtypeLocaleUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.SubtypeUtilsAdditional
import helium314.keyboard.latin.utils.displayName
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.dialogs.SubtypeDialog
import java.util.Locale

@Composable
fun LanguageScreen(
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    var sortedSubtypes by remember { mutableStateOf(getSortedSubtypes(ctx)) }
    val prefs = ctx.prefs()
    val b = (LocalContext.current.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    var selectedSubtype: String? by rememberSaveable { mutableStateOf(null) }
    val enabledSubtypes = SubtypeSettings.getEnabledSubtypes()
    SearchScreen(
        onClickBack = onClickBack,
        title = {
            Column {
                Text(stringResource(R.string.language_and_layouts_title))
                Text(stringResource(
                    R.string.text_tap_languages),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        filteredItems = { term ->
            sortedSubtypes.filter {
                it.displayName(ctx).replace("(", "")
                    .splitOnWhitespace().any { it.startsWith(term, true) }
            }
        },
        itemContent = { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedSubtype = item.toSettingsSubtype().toPref() }
                    .padding(vertical = 6.dp, horizontal = 16.dp)
            ) {
                var showNoDictDialog by remember { mutableStateOf(false) }
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.displayName(ctx), style = MaterialTheme.typography.bodyLarge)
                    val description = if (SubtypeSettings.isAdditionalSubtype(item)) {
                        val secondaryLocales = item.getExtraValueOf(ExtraValue.SECONDARY_LOCALES)?.split(Separators.KV)
                            ?.joinToString(", ") { it.constructLocale().localizedDisplayName(ctx) }
                        stringResource(R.string.custom_subtype) + (secondaryLocales?.let { "\n$it" } ?: "")
                    } else null
                    if (description != null)
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                }
                Switch(
                    checked = item in enabledSubtypes,
                    onCheckedChange = {
                        if (it && !dictsAvailable(item.locale(), ctx))
                            showNoDictDialog = true
                        if (it) SubtypeSettings.addEnabledSubtype(prefs, item)
                        else SubtypeSettings.removeEnabledSubtype(ctx, item)
                    }
                )
                if (showNoDictDialog)
                    MissingDictionaryDialog({ showNoDictDialog = false }, item.locale())
            }
        }
    )
    if (selectedSubtype != null) {
        val oldSubtype = selectedSubtype!!.toSettingsSubtype()
        SubtypeDialog(
            onDismissRequest = {
                selectedSubtype = null
                sortedSubtypes = getSortedSubtypes(ctx)
            },
            onConfirmed = {
                SubtypeUtilsAdditional.changeAdditionalSubtype(oldSubtype, it, ctx)
            },
            initialSubtype = oldSubtype
        )
    }
}

private fun dictsAvailable(locale: Locale, context: Context): Boolean {
    val (dicts, hasInternal) = getUserAndInternalDictionaries(context, locale)
    return hasInternal || dicts.isNotEmpty()
}

// sorting by display name is still slow, even with the cache... but probably good enough
private fun getSortedSubtypes(context: Context): List<InputMethodSubtype> {
    val systemLocales = SubtypeSettings.getSystemLocales()
    val enabledSubtypes = SubtypeSettings.getEnabledSubtypes(true)
    val localesWithDictionary = DictionaryInfoUtils.getCachedDirectoryList(context)?.mapNotNull { dir ->
        if (!dir.isDirectory)
            return@mapNotNull null
        if (dir.list()?.any { it.endsWith(USER_DICTIONARY_SUFFIX) } == true)
            dir.name.constructLocale()
        else null
    }.orEmpty()

    val defaultAdditionalSubtypes = Defaults.PREF_ADDITIONAL_SUBTYPES.split(Separators.SETS).map {
        it.substringBefore(Separators.SET) to (it.substringAfter(Separators.SET) + ",AsciiCapable,EmojiCapable,isAdditionalSubtype")
    }
    fun isDefaultSubtype(subtype: InputMethodSubtype): Boolean =
        defaultAdditionalSubtypes.any { it.first == subtype.locale().language && it.second == subtype.extraValue }

    val subtypeSortComparator = compareBy<InputMethodSubtype>(
        { it !in enabledSubtypes },
        { it.locale() !in localesWithDictionary },
        { it.locale() !in systemLocales},
        { !(SubtypeSettings.isAdditionalSubtype(it) && !isDefaultSubtype(it) ) },
        {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) it.languageTag == SubtypeLocaleUtils.NO_LANGUAGE
            else it.locale == SubtypeLocaleUtils.NO_LANGUAGE
        },
        { it.displayName(context) }
    )
    return SubtypeSettings.getAllAvailableSubtypes().sortedWith(subtypeSortComparator)
}
