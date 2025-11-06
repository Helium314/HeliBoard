// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.dialogs.SliderDialog
import kotlin.math.roundToInt

@Composable
/** Slider preference for Int or Float (weird casting stuff, but should be fine) */
fun <T: Number> SliderPreference(
    name: String,
    modifier: Modifier = Modifier,
    key: String,
    description: @Composable (T) -> String,
    default: T,
    range: ClosedFloatingPointRange<Float>,
    stepSize: Int? = null,
    onValueChanged: (Float?) -> Unit = { },
    onConfirmed: (T) -> Unit = { },
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val initialValue = if (default is Int || default is Float)
        getPrefOfType(prefs, key, default)
    else throw IllegalArgumentException("only float and int are supported")

    var showDialog by rememberSaveable { mutableStateOf(false) }
    Preference(
        name = name,
        onClick = { showDialog = true },
        modifier = modifier,
        description = description(initialValue)
    )
    if (showDialog)
        SliderDialog(
            onDismissRequest = { showDialog = false },
            onDone = {
                if (default is Int) {
                    prefs.edit().putInt(key, it.toInt()).apply()
                    onConfirmed(it.toInt() as T)
                } else {
                    prefs.edit().putFloat(key, it).apply()
                    onConfirmed(it as T)
                }
            },
            initialValue = initialValue.toFloat(),
            range = range,
            positionString = {
                @Suppress("UNCHECKED_CAST")
                description((if (default is Int) it.toInt() else it) as T)
            },
            onValueChanged = onValueChanged,
            showDefault = true,
            onDefault = { prefs.edit().remove(key).apply(); onConfirmed(default) },
            intermediateSteps = stepSize?.let {
                // this is not nice, but slider wants it like this...
                ((range.endInclusive - range.start) / it - 1).toInt()
            }
        )
}
