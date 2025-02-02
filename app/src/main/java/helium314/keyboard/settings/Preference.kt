// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.SharedPreferences
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.dialogs.ListPickerDialog
import helium314.keyboard.settings.dialogs.SliderDialog

// taken from StreetComplete (and a bit SCEE)

@Composable
fun PreferenceCategory(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        HorizontalDivider()
        if (title != null) {
            Text(
                text = title,
                modifier = modifier.padding(top = 12.dp, start = 16.dp, end = 8.dp, bottom = 8.dp),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.titleSmall
            )
        }
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyLarge) {
            Column {
                content()
            }
        }
    }
}

@Composable
fun Preference(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    @DrawableRes icon: Int? = null,
    value: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .heightIn(min = 40.dp)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null)
            Icon(painterResource(icon), name, modifier = Modifier.size(36.dp))
        Column(modifier = Modifier.weight(2 / 3f)) {
            Text(text = name)
            if (description != null) {
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Text(
                        text = description,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
        if (value != null) {
            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(
                    textAlign = TextAlign.End,
                    hyphens = Hyphens.Auto
                ),
                LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(
                        space = 8.dp,
                        alignment = Alignment.End
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1 / 3f)
                ) { value() }
            }
        }
    }
}

@Composable
fun SwitchPreference(
    name: String,
    modifier: Modifier = Modifier,
    pref: String,
    default: Boolean,
    description: String? = null,
    allowCheckedChange: (Boolean) -> Boolean = { true }, // true means ok, usually for showing some dialog
    onCheckedChange: (Boolean) -> Unit = { },
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (ctx.getActivity() as? SettingsActivity2)?.prefChanged?.collectAsState()
    if (b?.value ?: 0 < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    var value = prefs.getBoolean(pref, default)
    fun switched(newValue: Boolean) {
        if (!allowCheckedChange(newValue)) {
            value = !newValue
            return
        }
        value = newValue
        prefs.edit().putBoolean(pref, newValue).apply()
        onCheckedChange(newValue)
    }
    Preference(
        name = name,
        onClick = { switched(!value) },
        modifier = modifier,
        description = description
    ) {
        Switch(
            checked = value,
            onCheckedChange = { switched(it) },
            // switch is really ugly... how
//            colors = SwitchDefaults.colors(uncheckedBorderColor = Color.Transparent)
        )
    }
}

@Composable
fun SwitchPreference(
    def: PrefDef,
    default: Boolean,
    allowCheckedChange: (Boolean) -> Boolean = { true },
    onCheckedChange: (Boolean) -> Unit = { }
) {
    SwitchPreference(
        name = def.title,
        description = def.description,
        pref = def.key,
        default = default,
        allowCheckedChange = allowCheckedChange,
        onCheckedChange = onCheckedChange
    )
}

@Composable
/** Slider preference for Int or Float (weird casting stuff, but should be fine) */
fun <T: Number> SliderPreference(
    name: String,
    modifier: Modifier = Modifier,
    pref: String,
    description: @Composable (T) -> String,
    default: T,
    range: ClosedFloatingPointRange<Float>,
    onValueChanged: (Float) -> Unit = { },
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (ctx.getActivity() as? SettingsActivity2)?.prefChanged?.collectAsState()
    if (b?.value ?: 0 < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val initialValue = if (default is Int || default is Float)
        getPrefOfType(prefs, pref, default)
        else throw IllegalArgumentException("only float and int are supported")

    var showDialog by remember { mutableStateOf(false) }
    Preference(
        name = name,
        onClick = { showDialog = true },
        modifier = modifier,
        description = description(initialValue as T)
    )
    if (showDialog)
        SliderDialog(
            onDismissRequest = { showDialog = false },
            onDone = {
                if (default is Int) prefs.edit().putInt(pref, it.toInt()).apply()
                else prefs.edit().putFloat(pref, it).apply()
            },
            initialValue = initialValue.toFloat(),
            range = range,
            positionString = {
                description((if (default is Int) it.toInt() else it) as T)
            },
            onValueChanged = onValueChanged,
            showDefault = true,
            onDefault = { prefs.edit().remove(pref).apply() }
        )
}

@Composable
fun <T: Any> ListPreference(
    def: PrefDef,
    items: List<Pair<String, T>>,
    default: T,
) {
    var showDialog by remember { mutableStateOf(false) }
    // todo: get rid of the arrays from old settings
    val prefs = LocalContext.current.prefs()
    val selected = items.firstOrNull { it.second == getPrefOfType(prefs, def.key, default) }
    Preference(
        name = def.title,
        description = selected?.first,
        onClick = { showDialog = true }
    )
    if (showDialog) {
        ListPickerDialog(
            onDismissRequest = { showDialog = false },
            items = items,
            onItemSelected = {
                if (it != selected)
                    putPrefOfType(prefs, def.key, it.second)
            },
            selectedItem = selected,
            title = { Text(def.title) },
            getItemName = { it.first }
        )
    }
}

private fun <T: Any> getPrefOfType(prefs: SharedPreferences, key: String, default: T): T =
    when (default) {
        is String -> prefs.getString(key, default)
        is Int -> prefs.getInt(key, default)
        is Long -> prefs.getLong(key, default)
        is Float -> prefs.getFloat(key, default)
        is Boolean -> prefs.getBoolean(key, default)
        else -> throw IllegalArgumentException("unknown type ${default.javaClass}")
    } as T

private fun <T: Any> putPrefOfType(prefs: SharedPreferences, key: String, value: T) =
    prefs.edit {
        when (value) {
            is String -> putString(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
            is Boolean -> putBoolean(key, value)
            else -> throw IllegalArgumentException("unknown type ${value.javaClass}")
        }
    }

@Preview
@Composable
private fun PreferencePreview() {
    PreferenceCategory("Preference Category") {
        Preference(
            name = "Preference",
            onClick = {},
        )
        Preference(
            name = "Preference with icon",
            onClick = {},
            icon = R.drawable.ic_settings_about_foreground
        )
        SliderPreference(
            name = "SliderPreference",
            pref = "",
            default = 1,
            description = { it.toString() },
            range = -5f..5f
        )
        Preference(
            name = "Preference with icon and description",
            description = "some text",
            onClick = {},
            icon = R.drawable.ic_settings_about_foreground
        )
        Preference(
            name = "Preference with switch",
            onClick = {}
        ) {
            Switch(checked = true, onCheckedChange = {})
        }
        SwitchPreference(
            name = "SwitchPreference",
            pref = "none",
            default = true
        )
        Preference(
            name = "Preference",
            onClick = {},
            description = "A long description which may actually be several lines long, so it should wrap."
        ) {
            Icon(painterResource(R.drawable.ic_arrow_left), null)
        }
        Preference(
            name = "Long preference name that wraps",
            onClick = {},
        ) {
            Text("Long preference value")
        }
        Preference(
            name = "Long preference name 2",
            onClick = {},
            description = "hello I am description"
        ) {
            Text("Long preference value")
        }
    }
}
