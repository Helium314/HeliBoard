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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Constants.Separators
import helium314.keyboard.latin.common.Constants.Subtype.ExtraValue
import helium314.keyboard.latin.common.LocaleUtils
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.common.splitOnWhitespace
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.USER_DICTIONARY_SUFFIX
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SettingsSubtype.Companion.toSettingsSubtype
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.SubtypeUtilsAdditional
import helium314.keyboard.latin.utils.displayName
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.dialogs.SubtypeDialog

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
    var selectedSubtype: InputMethodSubtype? by remember { mutableStateOf(null) } // todo: rememberSaveable? maybe with SettingsSubtype?
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
            // todo: maybe better performance with display name cache?
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
                    .clickable { selectedSubtype = item }
                    .padding(vertical = 6.dp, horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.displayName(ctx), style = MaterialTheme.typography.bodyLarge)
                    val description = item.getExtraValueOf(ExtraValue.SECONDARY_LOCALES)?.split(Separators.KV)
                        ?.joinToString(", ") { LocaleUtils.getLocaleDisplayNameInSystemLocale(it.constructLocale(), ctx) }
                    if (description != null)
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                }
                Switch(
                    checked = item in SubtypeSettings.getEnabledSubtypes(prefs),
                    onCheckedChange = {
                        if (it) SubtypeSettings.addEnabledSubtype(prefs, item)
                        else SubtypeSettings.removeEnabledSubtype(prefs, item)
                    }
                )
            }
        }
    )
    if (selectedSubtype != null) {
        val oldSubtype = selectedSubtype!!
        SubtypeDialog(
            onDismissRequest = { selectedSubtype = null },
            onConfirmed = {
                // todo: this does not work when "modifying" a resource subtype
                SubtypeUtilsAdditional.changeAdditionalSubtype(oldSubtype.toSettingsSubtype(), it, prefs)
                sortedSubtypes = getSortedSubtypes(ctx)
            },
            subtype = oldSubtype
        )
    }
}

// todo: sorting is slow, need to cache displayName (overall or just in getSortedSubtypes), and then it should be fine
private fun getSortedSubtypes(context: Context): List<InputMethodSubtype> {
    val systemLocales = SubtypeSettings.getSystemLocales()
    val enabledSubtypes = SubtypeSettings.getEnabledSubtypes(context.prefs(), true)
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) it.languageTag == "zz"
            else it.locale == "zz"
        },
        { it.displayName(context) }
    )
    return SubtypeSettings.getAllAvailableSubtypes().sortedWith(subtypeSortComparator)
}
