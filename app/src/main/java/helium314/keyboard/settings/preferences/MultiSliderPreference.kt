// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.createPrefKeyForBooleanSettings
import helium314.keyboard.latin.utils.SpacedTokens
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.WithSmallTitle
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog
import helium314.keyboard.settings.previewDark

// too specialized for using a more generic dialog
// actual key for each setting is baseKey with one _true/_false appended per dimension (need to keep order!)
@Composable
fun MultiSliderPreference(
    name: String,
    baseKey: String,
    dimensions: List<String>,
    defaults: Array<Float>,
    range:  ClosedFloatingPointRange<Float>,
    description: (Float) -> String,
    onDone: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    Preference(
        name = name,
        onClick = { showDialog = true },
        // description??
    )
    if (showDialog)
        MultiSliderDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(name) },
            baseKey = baseKey,
            onDone = onDone,
            defaultValues = defaults,
            range = range,
            dimensions = dimensions,
            positionString = description
        )
}

// SliderDialog, but for multiple sliders with same range, each with a different setting and title
@Composable
private fun MultiSliderDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    baseKey: String,
    onDone: () -> Unit,
    defaultValues: Array<Float>,
    range: ClosedFloatingPointRange<Float>,
    dimensions: List<String>,
    modifier: Modifier = Modifier,
    positionString: (Float) -> String,
) {
    val (variants, keys) = createVariantsAndKeys(dimensions, baseKey)
    // todo: store state in some activeDimensions setting?
    //  probably should be a name -> bool map (bad on translation change, but that's ok)
    var shown by remember { mutableStateOf(List(variants.size) { true }) }

    val prefs = LocalContext.current.prefs()
    val done = remember { mutableMapOf<String, () -> Unit>() }

    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = { done.values.forEach { it.invoke() }; onDone() },
        modifier = modifier,
        title = title,
        content = {
            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.bodyLarge
            ) {
                val state = rememberScrollState()
                Column(Modifier.verticalScroll(state)) {
                    dimensions.forEach { dimension ->
                        var checked by remember { mutableStateOf(shown[variants.indexOfFirst { SpacedTokens(it).contains(dimension) }]) }
                        DimensionCheckbox(checked, dimension) {
                            shown = shown.mapIndexed { i, checked ->
                                if (SpacedTokens(variants[i]).contains(dimension)) it
                                else checked
                            }
                            checked = it
                        }
                    }
                    variants.forEachIndexed { i, variant ->
                        val key = keys[i]
                        var sliderPosition by remember { mutableFloatStateOf(prefs.getFloat(key, defaultValues[i])) }
                        if (!done.contains(variant))
                            done[variant] = {
                                if (sliderPosition == defaultValues[i])
                                    prefs.edit().remove(key).apply()
                                else
                                    prefs.edit().putFloat(key, sliderPosition).apply()
                            }
                        // default animations make the dialog flash (see also DictionaryDialog)
                        AnimatedVisibility(shown[i], exit = fadeOut(), enter = fadeIn()) {
                            WithSmallTitle(variant.ifEmpty { stringResource(R.string.button_default) }) {
                                Slider(
                                    value = sliderPosition,
                                    onValueChange = { sliderPosition = it },
                                    valueRange = range,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(positionString(sliderPosition))
                                    TextButton({ sliderPosition = defaultValues[i] }) { Text(stringResource(R.string.button_default)) }
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun DimensionCheckbox(checked: Boolean, dimension: String, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onCheckedChange(it) }
        )
        Text(dimension)
    }
}

private fun createVariantsAndKeys(dimensions: List<String>, baseKey: String): Pair<List<String>, List<String>> {
    val variants = mutableListOf("")
    val keys = mutableListOf(createPrefKeyForBooleanSettings(baseKey, 0, dimensions.size))
    var i = 1
    dimensions.forEach { dimension ->
        variants.toList().forEach { variant ->
            if (variant.isEmpty()) variants.add(dimension)
            else variants.add("$variant / $dimension")
            keys.add(createPrefKeyForBooleanSettings(baseKey, i, dimensions.size))
            i++
        }
    }
    return variants to keys
}

@Preview
@Composable
private fun Preview() {
    Theme(previewDark) {
        MultiSliderDialog(
            onDismissRequest = { },
            onDone = { },
            positionString = { "${it.toInt()}%"},
            defaultValues = Array(8) { 100f - it % 2 * 50f },
            range = 0f..500f,
            title = { Text("bottom padding scale") },
            dimensions = listOf("landscape", "unfolded", "split"),
            baseKey = ""
        )
    }
}
