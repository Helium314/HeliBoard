// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import helium314.keyboard.latin.R
import helium314.keyboard.settings.SearchScreen

@Composable
fun ColorsScreen(
    night: Boolean,
    onClickBack: () -> Unit
) {

    var availableColors by remember { mutableStateOf(emptyList<ColorSetting>()) } // todo: type?
    // todo: save / load / type selection here? or in ... menu as previously?
    SearchScreen(
        title = stringResource(if (night) R.string.select_user_colors_night else R.string.select_user_colors),
        onClickBack = onClickBack,
        filteredItems = { search -> availableColors.filter { it.displayName.contains(search, true) } },
        itemContent = { }
    )
}

private class ColorSetting(
    val pref: String, // old, this should go away
    val displayName: String,
    var auto: Boolean, // not for all
    var color: Int
)